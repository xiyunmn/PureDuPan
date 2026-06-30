package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.ui.HostThemeChangeDispatcher
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

internal data class BaiduSystemNightModeHookPoints(
    val baseActivityClassName: String,
    val settingsActivityClassName: String,
    val skinLoaderListenerClassName: String,
    val settingsItemViewClassName: String,
    val changeSkinKtClassName: String? = null,
    val changeSkinKtFallbackClassNames: List<String> = emptyList(),
    val skinManagerClassName: String? = null,
    val darkSkinTheme: String = "dark_theme.skin",
    val changeSkinMethodNames: Set<String> = setOf("changeSkin"),
    val changeSkinMethodResolver: ((ClassLoader) -> Method?)? = null,
    val settingsSwitchViewIdName: String = "dark_settings",
    val beforeApplyDarkSkin: ((Activity) -> Boolean)? = null,
)

/**
 * Syncs Baidu Netdisk host skin with system night mode.
 *
 * CN follows ChangeSkinKt.changeSkin(), while hosts without a stable ChangeSkinKt
 * symbol can use the retained SkinManager backend. The bottom avatar is refreshed
 * through MainActivity.refreshAboutmeTabImage().
 */
internal class BaiduSystemNightModeSyncHook(
    private val logTag: String,
    private val hookPoints: BaiduSystemNightModeHookPoints,
) {
    private companion object {
        const val SYNC_DELAY_MS = 300L
        const val NIGHT_MODE_POLL_INTERVAL_MS = 700L
        const val FALLBACK_REFRESH_DELAY_MS = 400L
        const val AVATAR_REFRESH_DELAY_MS = 160L
        const val AVATAR_REFRESH_STABLE_DELAY_MS = 600L
        const val AVATAR_REFRESH_DEBOUNCE_MS = 500L
    }

    private val hookState = HookState()
    @Volatile private var refreshTabSkinHooked = false
    @Volatile private var settingsActivityHooked = false
    @Volatile private var changeSkinObserverHooked = false
    @Volatile private var skinManagerObserverHooked = false
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
        if (!hookState.markInstalled()) return

        try {
            val baseActivityClass = XposedCompat.findClassOrNull(hookPoints.baseActivityClassName, cl)
                ?: run {
                    log("BaseActivity class NOT FOUND")
                    return
                }

            installRefreshTabSkinHook(cl)
            installSettingsActivityHook(cl)
            installChangeSkinObserverHook(cl)
            installSkinManagerObserverHook(cl)

            val queueSyncLogic = { activity: Activity ->
                activeActivityRef = WeakReference(activity)
                updateMainActivityRef(activity)
                updateSettingsActivityRef(activity)
                if (HookSettings.isFollowSystemNightModeEnabled) {
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

            log(
                "hooks INSTALLED: BaseActivity.onResume + " +
                    "onConfigurationChanged + MainActivity.refreshTabSkin + skin observer",
            )
        } catch (t: Throwable) {
            hookState.reset()
            log("FAILED: ${t.message}")
        }
    }

    private fun installChangeSkinObserverHook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val changeSkinKtClassNames = changeSkinKtClassNames()
        if (changeSkinKtClassNames.isEmpty()) return
        synchronized(this) {
            if (changeSkinObserverHooked) return
            changeSkinObserverHooked = true
        }

        try {
            var hookedCount = 0
            var foundClassCount = 0

            for (changeSkinKtClassName in changeSkinKtClassNames) {
                val changeSkinClass = XposedCompat.findClassOrNull(changeSkinKtClassName, cl)
                    ?: continue
                foundClassCount++

                val methods = findChangeSkinMethods(changeSkinClass)
                if (methods.isEmpty()) continue

                for (method in methods) {
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        HostThemeChangeDispatcher.notifyChanged("changeSkin")
                        result
                    }
                    hookedCount++
                }
            }

            if (foundClassCount == 0) {
                synchronized(this) { changeSkinObserverHooked = false }
                log("ChangeSkinKt class NOT FOUND: ${changeSkinKtClassNames.joinToString()}")
                return
            }

            if (hookedCount == 0) {
                synchronized(this) { changeSkinObserverHooked = false }
                log("ChangeSkinKt.changeSkin NOT FOUND")
                return
            }

            log("hook INSTALLED: ChangeSkinKt.changeSkin observer ($hookedCount)")
        } catch (t: Throwable) {
            synchronized(this) { changeSkinObserverHooked = false }
            log("ChangeSkinKt.changeSkin observer hook FAILED: ${t.message}")
        }
    }

    private fun installSkinManagerObserverHook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val skinManagerClassName = hookPoints.skinManagerClassName ?: return
        synchronized(this) {
            if (skinManagerObserverHooked) return
            skinManagerObserverHooked = true
        }

        try {
            val skinManagerClass = XposedCompat.findClassOrNull(skinManagerClassName, cl)
                ?: run {
                    synchronized(this) { skinManagerObserverHooked = false }
                    log("SkinManager class NOT FOUND")
                    return
                }
            val listenerClass = XposedCompat.findClassOrNull(hookPoints.skinLoaderListenerClassName, cl)
            if (listenerClass != null) {
                XposedCompat.findMethodOrNull(
                    skinManagerClass,
                    "loadDefaultUpdateSkin",
                    String::class.java,
                    listenerClass,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        HostThemeChangeDispatcher.notifyChanged("skinManager.loadDefaultUpdateSkin")
                        result
                    }
                }
            }
            XposedCompat.findMethodOrNull(skinManagerClass, "restoreDefaultTheme")?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    HostThemeChangeDispatcher.notifyChanged("skinManager.restoreDefaultTheme")
                    result
                }
            }

            log("hook INSTALLED: SkinManager theme observer")
        } catch (t: Throwable) {
            synchronized(this) { skinManagerObserverHooked = false }
            log("SkinManager theme observer hook FAILED: ${t.message}")
        }
    }

    private fun installRefreshTabSkinHook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        synchronized(this) {
            if (refreshTabSkinHooked) return
            refreshTabSkinHooked = true
        }

        try {
            val mainActivityClassName = currentMainActivityClassName() ?: run {
                synchronized(this) { refreshTabSkinHooked = false }
                log("MainActivity host capability missing")
                return
            }
            val mainActivityClass = XposedCompat.findClassOrNull(mainActivityClassName, cl)
                ?: run {
                    synchronized(this) { refreshTabSkinHooked = false }
                    log("MainActivity class NOT FOUND")
                    return
                }

            val methods = mainActivityClass.declaredMethods.filter {
                it.name == "refreshTabSkin" && it.parameterTypes.isEmpty()
            }
            if (methods.isEmpty()) {
                synchronized(this) { refreshTabSkinHooked = false }
                log("MainActivity.refreshTabSkin NOT FOUND")
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

            log("hook INSTALLED: MainActivity.refreshTabSkin")
        } catch (t: Throwable) {
            synchronized(this) { refreshTabSkinHooked = false }
            log("MainActivity.refreshTabSkin hook FAILED: ${t.message}")
        }
    }

    private fun installSettingsActivityHook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        synchronized(this) {
            if (settingsActivityHooked) return
            settingsActivityHooked = true
        }

        try {
            val settingsActivityClass = XposedCompat.findClassOrNull(hookPoints.settingsActivityClassName, cl)
                ?: run {
                    synchronized(this) { settingsActivityHooked = false }
                    log("SettingsActivity class NOT FOUND")
                    return
                }

            val onResumeMethod = XposedCompat.findMethodOrNull(settingsActivityClass, "onResume")
                ?: run {
                    synchronized(this) { settingsActivityHooked = false }
                    log("SettingsActivity.onResume NOT FOUND")
                    return
                }

            mod.hook(onResumeMethod).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Activity)?.let { activity ->
                    updateSettingsActivityRef(activity)
                    if (HookSettings.isFollowSystemNightModeEnabled) {
                        syncSettingsNightSwitch(resolveSystemNight(activity))
                    }
                }
                result
            }

            log("hook INSTALLED: SettingsActivity.onResume")
        } catch (t: Throwable) {
            synchronized(this) { settingsActivityHooked = false }
            log("SettingsActivity.onResume hook FAILED: ${t.message}")
        }
    }

    private fun ensureNightModePolling(cl: ClassLoader) {
        if (pollRunnable != null) return

        val runnable = object : Runnable {
            override fun run() {
                if (!HookSettings.isFollowSystemNightModeEnabled) {
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
        logD("night mode polling started")
    }

    private fun runNightModeSync(cl: ClassLoader, activity: Activity) {
        try {
            val uiMode = activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            val isSystemNight = uiMode == Configuration.UI_MODE_NIGHT_YES

            if (lastAppliedNightMode == isSystemNight) return
            if (isSystemNight && hookPoints.beforeApplyDarkSkin?.invoke(activity) == false) {
                log("Dark skin preparation failed")
                return
            }

            val listenerClass = XposedCompat.findClassOrNull(hookPoints.skinLoaderListenerClassName, cl)
            val changeMethod = resolveChangeSkinMethod(cl)
            if (changeMethod != null) {
                changeMethod.isAccessible = true
                applySystemSkin(activity, changeMethod, listenerClass, isSystemNight)
                lastAppliedNightMode = isSystemNight
                return
            }

            if (applySystemSkinWithSkinManager(cl, activity, listenerClass, isSystemNight)) {
                lastAppliedNightMode = isSystemNight
                return
            }

            log("No available skin backend")
        } catch (e: Throwable) {
            log("Sync error: ${e.message}")
        }
    }

    private fun resolveChangeSkinMethod(cl: ClassLoader): Method? {
        for (changeSkinKtClassName in changeSkinKtClassNames()) {
            val changeSkinClass = XposedCompat.findClassOrNull(changeSkinKtClassName, cl)
            val changeMethod = changeSkinClass?.let { findChangeSkinMethods(it).firstOrNull() }
            if (changeMethod != null) {
                logD("ChangeSkinKt resolved: ${changeMethod.declaringClass.name}.${changeMethod.name}")
                return changeMethod
            }
        }
        return hookPoints.changeSkinMethodResolver?.invoke(cl)
    }

    private fun changeSkinKtClassNames(): List<String> =
        (listOfNotNull(hookPoints.changeSkinKtClassName) + hookPoints.changeSkinKtFallbackClassNames)
            .distinct()

    private fun findChangeSkinMethods(changeSkinClass: Class<*>): List<Method> {
        val methods = (changeSkinClass.methods.asSequence() + changeSkinClass.declaredMethods.asSequence())
            .distinctBy { method ->
                method.name + "#" + method.parameterTypes.joinToString(",") { it.name }
            }
            .filter { isChangeSkinSignature(it) }
            .toList()
        val namedMethods = methods.filter { it.name in hookPoints.changeSkinMethodNames }
        return namedMethods.ifEmpty { methods }
    }

    private fun isChangeSkinSignature(method: Method): Boolean {
        if (!Modifier.isStatic(method.modifiers)) return false
        val parameterTypes = method.parameterTypes
        return parameterTypes.size == 2 &&
            parameterTypes[0] == String::class.java &&
            parameterTypes[1].name == hookPoints.skinLoaderListenerClassName
    }

    private fun applySystemSkin(
        activity: Activity,
        changeMethod: Method,
        listenerClass: Class<*>?,
        isSystemNight: Boolean,
    ) {
        val skinName = if (isSystemNight) hookPoints.darkSkinTheme else null

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
                log("Changed to Dark Skin")
            } else {
                syncSettingsNightSwitch(false)
                scheduleForceAvatarRefresh("changeSkin-default")
                pulseDecor(activity)
                log("Changed to Default Skin")
            }
        } catch (t: Throwable) {
            throw t
        }
    }

    private fun applySystemSkinWithSkinManager(
        cl: ClassLoader,
        activity: Activity,
        listenerClass: Class<*>?,
        isSystemNight: Boolean,
    ): Boolean {
        val skinManagerClassName = hookPoints.skinManagerClassName ?: return false
        val skinManagerClass = XposedCompat.findClassOrNull(skinManagerClassName, cl)
            ?: run {
                log("SkinManager class NOT FOUND")
                return false
            }
        val getInstanceMethod = XposedCompat.findMethodOrNull(skinManagerClass, "getInstance")
            ?: run {
                log("SkinManager.getInstance NOT FOUND")
                return false
            }
        val manager = getInstanceMethod.invoke(null)
            ?: run {
                log("SkinManager.getInstance returned null")
                return false
            }

        return if (isSystemNight) {
            applyDarkSkinWithSkinManager(activity, skinManagerClass, manager, listenerClass)
        } else {
            restoreDefaultSkinWithSkinManager(activity, skinManagerClass, manager)
        }
    }

    private fun applyDarkSkinWithSkinManager(
        activity: Activity,
        skinManagerClass: Class<*>,
        manager: Any,
        listenerClass: Class<*>?,
    ): Boolean {
        val loadMethod = findSkinManagerLoadMethod(skinManagerClass, listenerClass)
            ?: run {
                log("SkinManager.loadDefaultUpdateSkin NOT FOUND")
                return false
            }

        val listener = listenerClass?.let { createSkinLoaderListener(it, activity, true) }
        loadMethod.invoke(manager, hookPoints.darkSkinTheme, listener)
        if (listener == null) {
            syncSettingsNightSwitch(true)
            mainHandler.postDelayed({
                HostThemeChangeDispatcher.notifyChanged("skinManager-dark-fallback")
                scheduleForceAvatarRefresh("skinManager-dark-fallback")
                pulseDecor(activity)
            }, FALLBACK_REFRESH_DELAY_MS)
        }
        log("Changed to Dark Skin")
        return true
    }

    private fun restoreDefaultSkinWithSkinManager(
        activity: Activity,
        skinManagerClass: Class<*>,
        manager: Any,
    ): Boolean {
        val restoreMethod = XposedCompat.findMethodOrNull(skinManagerClass, "restoreDefaultTheme")
            ?: run {
                log("SkinManager.restoreDefaultTheme NOT FOUND")
                return false
            }

        restoreMethod.invoke(manager)
        HostThemeChangeDispatcher.notifyChanged("skinManager-default")
        syncSettingsNightSwitch(false)
        scheduleForceAvatarRefresh("skinManager-default")
        pulseDecor(activity)
        log("Changed to Default Skin")
        return true
    }

    private fun findSkinManagerLoadMethod(
        skinManagerClass: Class<*>,
        listenerClass: Class<*>?,
    ): Method? {
        if (listenerClass != null) {
            XposedCompat.findMethodOrNull(
                skinManagerClass,
                "loadDefaultUpdateSkin",
                String::class.java,
                listenerClass,
            )?.let { return it }
        }
        return skinManagerClass.methods.firstOrNull {
            it.name == "loadDefaultUpdateSkin" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == String::class.java
        }?.apply { isAccessible = true }
    }

    private fun createSkinLoaderListener(
        listenerClass: Class<*>,
        activity: Activity,
        expectedNightMode: Boolean,
    ): Any {
        val activityRef = WeakReference(activity)
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "toString" -> return@InvocationHandler "$logTag.SkinLoaderListenerProxy"
                "hashCode" -> return@InvocationHandler System.identityHashCode(proxy)
                "equals" -> return@InvocationHandler proxy === args?.firstOrNull()
            }

            if (method.name == "onSuccess") {
                val target = activityRef.get()
                mainHandler.post {
                    if (target != null) {
                        updateMainActivityRef(target)
                        updateSettingsActivityRef(target)
                        HostThemeChangeDispatcher.notifyChanged("skin-loader-success")
                        syncSettingsNightSwitch(expectedNightMode)
                        scheduleForceAvatarRefresh("changeSkin-success")
                        pulseDecor(target)
                    }
                }
            } else if (method.name == "onFailed" || method.name.startsWith("mo")) {
                logD("skin loader callback ${method.name}: ${args?.firstOrNull()}")
            }
            HookUtils.getDefaultReturnValue(method.returnType)
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
            logD("avatar refresh skipped: MainActivity unavailable ($reason)")
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
        val mainActivityClassName = currentMainActivityClassName() ?: return
        if (activity == null || activity.javaClass.name != mainActivityClassName) return
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
                    logD("avatar refresh skipped: refreshAboutmeTabImage not found")
                    return
                }
            refreshMethod.invoke(activity)
            logD("avatar refresh invoked natively: $reason")
        } catch (t: Throwable) {
            logD("avatar refresh failed: ${t.message}")
        } finally {
            if (skinDataCleared) {
                try {
                    skinDataField?.set(activity, originalSkinData)
                } catch (t: Throwable) {
                    logD("skinData restore failed: ${t.message}")
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

        logD("skinData field not found")
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
        if (activity.javaClass.name == currentMainActivityClassName()) {
            mainActivityRef = WeakReference(activity)
        }
    }

    private fun currentMainActivityClassName(): String? =
        BaiduFeatureRuntime.currentMainActivityClassName()

    private fun updateSettingsActivityRef(activity: Activity) {
        if (activity.javaClass.name == hookPoints.settingsActivityClassName) {
            settingsActivityRef = WeakReference(activity)
        }
    }

    private fun resolveSystemNight(activity: Activity): Boolean {
        val uiMode = activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun syncSettingsNightSwitch(isNight: Boolean) {
        if (!HookSettings.isFollowSystemNightModeEnabled) return
        val activity = settingsActivityRef?.get() ?: return
        if (activity.javaClass.name != hookPoints.settingsActivityClassName) return
        if (activity.isFinishing || activity.isDestroyed) return

        mainHandler.post {
            try {
                val item = findDarkSettingItem(activity)
                    ?: run {
                        logD("settings night switch skipped: mDarkSetting unavailable")
                        return@post
                    }
                invokeNoArgMethod(item, "switchCheckboxNormalMode")
                invokeBooleanMethod(item, "setChecked", isNight)
                logD("settings night switch synced: isNight=$isNight")
            } catch (t: Throwable) {
                logD("settings night switch sync failed: ${t.message}")
            }
        }
    }

    private fun findDarkSettingItem(activity: Activity): Any? {
        findViewByIdName(activity, hookPoints.settingsSwitchViewIdName)?.let { return it }

        findFieldOrNull(activity.javaClass, "mDarkSetting")?.let { field ->
            field.get(activity)?.let { return it }
        }

        val clazz = activity.javaClass
        for (method in clazz.declaredMethods) {
            if (!Modifier.isStatic(method.modifiers)) continue
            if (method.parameterTypes.size != 1) continue
            if (method.parameterTypes[0] != clazz) continue
            if (method.returnType.name != hookPoints.settingsItemViewClassName) continue
            method.isAccessible = true
            method.invoke(null, activity)?.let { return it }
        }
        return null
    }

    private fun findViewByIdName(activity: Activity, idName: String): Any? {
        val id = runCatching {
            activity.resources.getIdentifier(idName, "id", activity.packageName)
        }.getOrDefault(0)
        if (id == 0) return null
        return runCatching { activity.findViewById<android.view.View>(id) }
            .getOrNull()
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
            logD("pulseDecor failed: ${t.message}")
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

    private fun log(message: String) {
        XposedCompat.log("[$logTag] $message")
    }

    private fun logD(message: String) {
        XposedCompat.logD("[$logTag] $message")
    }
}
