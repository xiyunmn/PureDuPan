package com.xiyunmn.puredupan.hook.feature.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.HostThemeChangeDispatcher
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * Syncs Baidu Netdisk skin with system night mode.
 *
 * Avatar refresh stays on the host business path: refreshTabSkin() may reset the bottom avatar,
 * so this hook temporarily clears MainActivity.skinData and invokes refreshAboutmeTabImage().
 */
object SystemNightModeSyncHook {
    private const val BASE_ACTIVITY = StableBaiduPanHookPoints.BASE_ACTIVITY
    private const val MAIN_ACTIVITY = StableBaiduPanHookPoints.MAIN_ACTIVITY
    private const val SETTINGS_ACTIVITY = StableBaiduPanHookPoints.SETTINGS_ACTIVITY
    private const val CHANGE_SKIN_KT = StableBaiduPanHookPoints.CHANGE_SKIN_KT
    private const val SKIN_LOADER_LISTENER = "com.netdisk.themeskin.listener.SkinLoaderListener"
    private const val DARK_SKIN_THEME = "dark_theme.skin"
    private const val SYNC_DELAY_MS = 300L
    private const val NIGHT_MODE_POLL_INTERVAL_MS = 700L
    private const val FALLBACK_REFRESH_DELAY_MS = 400L
    private const val AVATAR_REFRESH_DELAY_MS = 160L
    private const val AVATAR_REFRESH_STABLE_DELAY_MS = 600L
    private const val AVATAR_REFRESH_DEBOUNCE_MS = 500L

    @Volatile private var hooked = false
    @Volatile private var refreshTabSkinHooked = false
    @Volatile private var settingsActivityHooked = false
    @Volatile private var changeSkinObserverHooked = false
    @Volatile private var lastAppliedNightMode: Boolean? = null
    @Volatile private var lastAvatarRefreshRequestMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var syncRunnable: Runnable? = null
    private var pollRunnable: Runnable? = null
    private var activeActivityRef: WeakReference<Activity>? = null
    private var mainActivityRef: WeakReference<Activity>? = null
    private var settingsActivityRef: WeakReference<Activity>? = null

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val baseActivityClass = XposedCompat.findClassOrNull(BASE_ACTIVITY, cl)
                ?: run {
                    XposedCompat.log("[SystemNightModeSyncHook] BaseActivity class NOT FOUND")
                    return
                }

            installRefreshTabSkinHook(cl)
            installSettingsActivityHook(cl)
            installChangeSkinObserverHook(cl)

            val queueSyncLogic = { activity: Activity ->
                activeActivityRef = WeakReference(activity)
                updateMainActivityRef(activity)
                updateSettingsActivityRef(activity)
                if (ConfigManager.isFollowSystemNightModeEnabled) {
                    syncSettingsNightSwitch(resolveSystemNight(activity))
                    syncRunnable?.let { mainHandler.removeCallbacks(it) }
                    syncRunnable = Runnable { runNightModeSync(cl, activity) }
                    mainHandler.postDelayed(syncRunnable!!, SYNC_DELAY_MS)
                    ensureNightModePolling(cl)
                }
            }

            XposedCompat.findMethodOrNull(baseActivityClass, "onResume")?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? Activity)?.let { queueSyncLogic(it) }
                    result
                }
            }

            XposedCompat.findMethodOrNull(
                baseActivityClass,
                "onConfigurationChanged",
                Configuration::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? Activity)?.let { queueSyncLogic(it) }
                    result
                }
            }

            XposedCompat.log(
                "[SystemNightModeSyncHook] hooks INSTALLED: BaseActivity.onResume + " +
                    "onConfigurationChanged + MainActivity.refreshTabSkin + ChangeSkinKt.changeSkin observer",
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[SystemNightModeSyncHook] FAILED: ${t.message}")
        }
    }

    private fun installChangeSkinObserverHook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        synchronized(this) {
            if (changeSkinObserverHooked) return
            changeSkinObserverHooked = true
        }

        try {
            val changeSkinClass = XposedCompat.findClassOrNull(CHANGE_SKIN_KT, cl)
                ?: run {
                    synchronized(this) { changeSkinObserverHooked = false }
                    XposedCompat.log("[SystemNightModeSyncHook] ChangeSkinKt class NOT FOUND")
                    return
                }

            val methods = changeSkinClass.methods.filter { it.name == "changeSkin" }
            if (methods.isEmpty()) {
                synchronized(this) { changeSkinObserverHooked = false }
                XposedCompat.log("[SystemNightModeSyncHook] ChangeSkinKt.changeSkin NOT FOUND")
                return
            }

            for (method in methods) {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    HostThemeChangeDispatcher.notifyChanged("changeSkin")
                    result
                }
            }

            XposedCompat.log("[SystemNightModeSyncHook] hook INSTALLED: ChangeSkinKt.changeSkin observer")
        } catch (t: Throwable) {
            synchronized(this) { changeSkinObserverHooked = false }
            XposedCompat.log("[SystemNightModeSyncHook] ChangeSkinKt.changeSkin observer hook FAILED: ${t.message}")
        }
    }

    private fun installRefreshTabSkinHook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        synchronized(this) {
            if (refreshTabSkinHooked) return
            refreshTabSkinHooked = true
        }

        try {
            val mainActivityClass = XposedCompat.findClassOrNull(MAIN_ACTIVITY, cl)
                ?: run {
                    synchronized(this) { refreshTabSkinHooked = false }
                    XposedCompat.log("[SystemNightModeSyncHook] MainActivity class NOT FOUND")
                    return
                }

            val methods = mainActivityClass.declaredMethods.filter {
                it.name == "refreshTabSkin" && it.parameterTypes.isEmpty()
            }
            if (methods.isEmpty()) {
                synchronized(this) { refreshTabSkinHooked = false }
                XposedCompat.log("[SystemNightModeSyncHook] MainActivity.refreshTabSkin NOT FOUND")
                return
            }

            for (method in methods) {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? Activity)?.let {
                        updateMainActivityRef(it)
                        scheduleForceAvatarRefresh("refreshTabSkin")
                    }
                    result
                }
            }

            XposedCompat.log("[SystemNightModeSyncHook] hook INSTALLED: MainActivity.refreshTabSkin")
        } catch (t: Throwable) {
            synchronized(this) { refreshTabSkinHooked = false }
            XposedCompat.log("[SystemNightModeSyncHook] MainActivity.refreshTabSkin hook FAILED: ${t.message}")
        }
    }

    private fun installSettingsActivityHook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        synchronized(this) {
            if (settingsActivityHooked) return
            settingsActivityHooked = true
        }

        try {
            val settingsActivityClass = XposedCompat.findClassOrNull(SETTINGS_ACTIVITY, cl)
                ?: run {
                    synchronized(this) { settingsActivityHooked = false }
                    XposedCompat.log("[SystemNightModeSyncHook] SettingsActivity class NOT FOUND")
                    return
                }

            val onResumeMethod = XposedCompat.findMethodOrNull(settingsActivityClass, "onResume")
                ?: run {
                    synchronized(this) { settingsActivityHooked = false }
                    XposedCompat.log("[SystemNightModeSyncHook] SettingsActivity.onResume NOT FOUND")
                    return
                }

            mod.hook(onResumeMethod).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Activity)?.let { activity ->
                    updateSettingsActivityRef(activity)
                    if (ConfigManager.isFollowSystemNightModeEnabled) {
                        syncSettingsNightSwitch(resolveSystemNight(activity))
                    }
                }
                result
            }

            XposedCompat.log("[SystemNightModeSyncHook] hook INSTALLED: SettingsActivity.onResume")
        } catch (t: Throwable) {
            synchronized(this) { settingsActivityHooked = false }
            XposedCompat.log("[SystemNightModeSyncHook] SettingsActivity.onResume hook FAILED: ${t.message}")
        }
    }

    private fun ensureNightModePolling(cl: ClassLoader) {
        if (pollRunnable != null) return

        val runnable = object : Runnable {
            override fun run() {
                if (!ConfigManager.isFollowSystemNightModeEnabled) {
                    pollRunnable = null
                    return
                }

                val activity = activeActivityRef?.get()
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    runNightModeSync(cl, activity)
                }
                mainHandler.postDelayed(this, NIGHT_MODE_POLL_INTERVAL_MS)
            }
        }
        pollRunnable = runnable
        mainHandler.postDelayed(runnable, NIGHT_MODE_POLL_INTERVAL_MS)
        XposedCompat.logD("[SystemNightModeSyncHook] night mode polling started")
    }

    private fun runNightModeSync(cl: ClassLoader, activity: Activity) {
        try {
            val uiMode = activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            val isSystemNight = uiMode == Configuration.UI_MODE_NIGHT_YES

            if (lastAppliedNightMode == isSystemNight) return

            val changeSkinClass = XposedCompat.findClassOrNull(CHANGE_SKIN_KT, cl)
            val changeMethod = changeSkinClass?.methods?.firstOrNull {
                it.name == "changeSkin" &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == String::class.java
            }

            if (changeMethod == null) {
                XposedCompat.log("[SystemNightModeSyncHook] ChangeSkinKt.changeSkin NOT FOUND")
                return
            }

            changeMethod.isAccessible = true
            val listenerClass = XposedCompat.findClassOrNull(SKIN_LOADER_LISTENER, cl)
            applySystemSkin(activity, changeMethod, listenerClass, isSystemNight)
            lastAppliedNightMode = isSystemNight
        } catch (e: Throwable) {
            XposedCompat.log("[SystemNightModeSyncHook] Sync error: ${e.message}")
        }
    }

    private fun applySystemSkin(
        activity: Activity,
        changeMethod: Method,
        listenerClass: Class<*>?,
        isSystemNight: Boolean,
    ) {
        val skinName = if (isSystemNight) DARK_SKIN_THEME else null

        try {
            if (listenerClass != null) {
                val listener = createSkinLoaderListener(listenerClass, activity, isSystemNight)
                changeMethod.invoke(null, skinName, listener)
            } else {
                changeMethod.invoke(null, skinName, null)
                syncSettingsNightSwitch(isSystemNight)
                mainHandler.postDelayed({
                    scheduleForceAvatarRefresh("changeSkin-fallback")
                    pulseDecor(activity)
                }, FALLBACK_REFRESH_DELAY_MS)
            }

            if (isSystemNight) {
                XposedCompat.log("[SystemNightModeSyncHook] Changed to Dark Skin")
            } else {
                syncSettingsNightSwitch(false)
                scheduleForceAvatarRefresh("changeSkin-default")
                pulseDecor(activity)
                XposedCompat.log("[SystemNightModeSyncHook] Changed to Default Skin")
            }
        } catch (t: Throwable) {
            throw t
        }
    }

    private fun createSkinLoaderListener(
        listenerClass: Class<*>,
        activity: Activity,
        expectedNightMode: Boolean,
    ): Any {
        val activityRef = WeakReference(activity)
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "toString" -> return@InvocationHandler "SystemNightModeSyncHook.SkinLoaderListenerProxy"
                "hashCode" -> return@InvocationHandler System.identityHashCode(proxy)
                "equals" -> return@InvocationHandler proxy === args?.firstOrNull()
            }

            if (method.name == "onSuccess") {
                val target = activityRef.get()
                mainHandler.post {
                    if (target != null) {
                        updateMainActivityRef(target)
                        updateSettingsActivityRef(target)
                        syncSettingsNightSwitch(expectedNightMode)
                        scheduleForceAvatarRefresh("changeSkin-success")
                        pulseDecor(target)
                    }
                }
            }
            defaultReturnValue(method.returnType)
        }

        return Proxy.newProxyInstance(
            listenerClass.classLoader ?: javaClass.classLoader,
            arrayOf(listenerClass),
            handler,
        )
    }

    private fun scheduleForceAvatarRefresh(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastAvatarRefreshRequestMs < AVATAR_REFRESH_DEBOUNCE_MS) return
        lastAvatarRefreshRequestMs = now

        val targetRef = mainActivityRef
        if (targetRef?.get() == null) {
            XposedCompat.logD("[SystemNightModeSyncHook] avatar refresh skipped: MainActivity unavailable ($reason)")
            return
        }

        mainHandler.postDelayed({
            forceRefreshMineAvatar(targetRef.get(), "$reason-fast")
        }, AVATAR_REFRESH_DELAY_MS)

        mainHandler.postDelayed({
            forceRefreshMineAvatar(targetRef.get(), "$reason-stable")
        }, AVATAR_REFRESH_STABLE_DELAY_MS)
    }

    private fun forceRefreshMineAvatar(activity: Activity?, reason: String) {
        if (activity == null || activity.javaClass.name != MAIN_ACTIVITY) return
        if (activity.isFinishing || activity.isDestroyed) return

        val skinDataField = findSkinDataFieldSafely(activity.javaClass)
        var originalSkinData: Any? = null
        var skinDataCleared = false

        try {
            if (skinDataField != null) {
                originalSkinData = skinDataField.get(activity)
                skinDataField.set(activity, null)
                skinDataCleared = true
            }

            val refreshMethod = findRefreshAboutMeTabImageMethod(activity.javaClass)
                ?: run {
                    XposedCompat.logD("[SystemNightModeSyncHook] avatar refresh skipped: refreshAboutmeTabImage not found")
                    return
                }
            refreshMethod.invoke(activity)
            XposedCompat.logD("[SystemNightModeSyncHook] avatar refresh invoked natively: $reason")
        } catch (t: Throwable) {
            XposedCompat.logD("[SystemNightModeSyncHook] avatar refresh failed: ${t.message}")
        } finally {
            if (skinDataCleared) {
                try {
                    skinDataField?.set(activity, originalSkinData)
                } catch (t: Throwable) {
                    XposedCompat.logD("[SystemNightModeSyncHook] skinData restore failed: ${t.message}")
                }
            }
        }
    }

    private fun findSkinDataFieldSafely(clazz: Class<*>): java.lang.reflect.Field? {
        findFieldOrNull(clazz, "skinData")?.let { return it }

        for (field in clazz.declaredFields) {
            val typeName = field.type.name
            if (isSkinDataTypeName(typeName)) {
                return field.apply { isAccessible = true }
            }
        }

        var current: Class<*>? = clazz.superclass
        while (current != null) {
            for (field in current.declaredFields) {
                val typeName = field.type.name
                if (isSkinDataTypeName(typeName)) {
                    return field.apply { isAccessible = true }
                }
            }
            current = current.superclass
        }

        XposedCompat.logD("[SystemNightModeSyncHook] skinData field not found")
        return null
    }

    private fun isSkinDataTypeName(typeName: String): Boolean {
        return typeName.contains("SkinInfo") ||
            typeName.contains("chainskin", ignoreCase = true)
    }

    private fun findRefreshAboutMeTabImageMethod(clazz: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod("refreshAboutmeTabImage").apply {
                    isAccessible = true
                }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun updateMainActivityRef(activity: Activity) {
        if (activity.javaClass.name == MAIN_ACTIVITY) {
            mainActivityRef = WeakReference(activity)
        }
    }

    private fun updateSettingsActivityRef(activity: Activity) {
        if (activity.javaClass.name == SETTINGS_ACTIVITY) {
            settingsActivityRef = WeakReference(activity)
        }
    }

    private fun resolveSystemNight(activity: Activity): Boolean {
        val uiMode = activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun syncSettingsNightSwitch(isNight: Boolean) {
        if (!ConfigManager.isFollowSystemNightModeEnabled) return
        val activity = settingsActivityRef?.get() ?: return
        if (activity.javaClass.name != SETTINGS_ACTIVITY) return
        if (activity.isFinishing || activity.isDestroyed) return

        mainHandler.post {
            try {
                val item = findDarkSettingItem(activity)
                    ?: run {
                        XposedCompat.logD("[SystemNightModeSyncHook] settings night switch skipped: mDarkSetting unavailable")
                        return@post
                    }
                invokeNoArgMethod(item, "switchCheckboxNormalMode")
                invokeBooleanMethod(item, "setChecked", isNight)
                XposedCompat.logD("[SystemNightModeSyncHook] settings night switch synced: isNight=$isNight")
            } catch (t: Throwable) {
                XposedCompat.logD("[SystemNightModeSyncHook] settings night switch sync failed: ${t.message}")
            }
        }
    }

    private fun findDarkSettingItem(activity: Activity): Any? {
        findFieldOrNull(activity.javaClass, "mDarkSetting")?.let { field ->
            field.get(activity)?.let { return it }
        }

        val clazz = activity.javaClass
        for (method in clazz.declaredMethods) {
            if (!Modifier.isStatic(method.modifiers)) continue
            if (method.parameterTypes.size != 1) continue
            if (method.parameterTypes[0] != clazz) continue
            if (method.returnType.name != "com.baidu.netdisk.ui.widget.SettingsItemView") continue
            method.isAccessible = true
            method.invoke(null, activity)?.let { return it }
        }
        return null
    }

    private fun invokeNoArgMethod(target: Any, name: String): Any? {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            try {
                return current.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun invokeBooleanMethod(target: Any, name: String, value: Boolean): Any? {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, java.lang.Boolean.TYPE)
                    .apply { isAccessible = true }
                    .invoke(target, value)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun pulseDecor(activity: Activity) {
        try {
            activity.window?.decorView?.apply {
                requestLayout()
                invalidate()
            }
        } catch (t: Throwable) {
            XposedCompat.logD("[SystemNightModeSyncHook] pulseDecor failed: ${t.message}")
        }
    }

    private fun findFieldOrNull(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun defaultReturnValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
