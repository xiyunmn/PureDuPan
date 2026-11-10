package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduThemeHookPoints
import com.xiyunmn.puredupan.hook.ui.HostThemeChangeDispatcher
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal data class BaiduSystemNightModeHookPoints(
    val baseActivityClassName: String,
    val settingsActivityClassName: String,
    val skinLoaderListenerClassName: String,
    val settingsItemViewClassName: String,
    val skinConfigClassName: String,
    val changeSkinKtClassName: String? = null,
    val skinManagerClassName: String? = null,
    val darkSkinTheme: String = "dark_theme.skin",
    val changeSkinMethodNames: Set<String> = setOf("changeSkin"),
    val changeSkinMethodResolver: ((ClassLoader) -> Method?)? = null,
    val settingsSwitchViewIdName: String = "dark_settings",
    val bottomBarHomeFoldedFieldNames: List<String>,
    val bottomBarThemeRefreshMethodNames: List<String>,
    val bottomBarThemeRefreshCompletionMethodName: String? = null,
    val gateColdStartBottomBar: Boolean = false,
)

/** 通过宿主原生换肤流程跟随系统主题，并刷新底栏头像。 */
internal class BaiduSystemNightModeSyncHook(
    private val logTag: String,
    private val hookPoints: BaiduSystemNightModeHookPoints,
) {
    internal companion object {
        private val owners = mutableSetOf<BaiduSystemNightModeSyncHook>()
        private val restoredOwners = mutableMapOf<String, Array<Any?>>()

        fun exportState(): ArrayList<Array<Any?>> = ArrayList(owners.map { owner ->
            arrayOf<Any?>(
                "night-owner", owner.logTag, owner.activeActivityRef, owner.mainActivityRef,
                owner.settingsActivityRef, owner.flutterSearchActivityRef, owner.flutterSearchResumed,
                owner.flutterSearchDefaultSkin, owner.bottomBarThemeRefresh.initializedActivity()?.let(::WeakReference),
            )
        })

        fun restoreOwner(state: Array<Any?>) {
            val tag = state[1] as String
            val owner = owners.firstOrNull { it.logTag == tag && it.hostLoader != null }
            if (owner == null) restoredOwners[tag] = state else owner.restoreTargets(state)
        }
        fun disposeAllForReload() {
            owners.toList().forEach { it.disposeForReload() }
            owners.clear()
            restoredOwners.clear()
        }
        const val SYNC_DELAY_MS = 300L
        const val NIGHT_MODE_POLL_INTERVAL_MS = 700L
        const val AVATAR_REFRESH_DELAY_MS = 160L
        const val AVATAR_REFRESH_STABLE_DELAY_MS = 600L
        const val AVATAR_REFRESH_DEBOUNCE_MS = 500L
    }

    private var hostLoader: ClassLoader? = null
    private val hookState = HookState()
    private val startupSkinSelected = AtomicBoolean(false)
    private val bottomBarThemeRefresh = BaiduBottomBarThemeRefreshCompat(
        logTag = logTag,
        homeFoldedFieldNames = hookPoints.bottomBarHomeFoldedFieldNames,
        themeRefreshMethodNames = hookPoints.bottomBarThemeRefreshMethodNames,
        themeRefreshCompletionMethodName = hookPoints.bottomBarThemeRefreshCompletionMethodName,
        gateColdStartBottomBar = hookPoints.gateColdStartBottomBar,
    )
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
    private var flutterSearchActivityRef: WeakReference<Activity>? = null
    private var flutterSearchResumed = false
    private var flutterSearchRefreshPending = false
    private var flutterSearchDefaultSkin: Boolean? = null
    private var flutterSearchRefreshing = false

    private fun restoreTargets(state: Array<Any?>) {
        fun activityAt(index: Int): Activity? = (state[index] as? WeakReference<*>)?.get() as? Activity
        activeActivityRef = activityAt(2)?.let(::WeakReference)
        mainActivityRef = activityAt(3)?.let(::WeakReference)
        settingsActivityRef = activityAt(4)?.let(::WeakReference)
        flutterSearchActivityRef = activityAt(5)?.let(::WeakReference)
        flutterSearchResumed = state[6] as Boolean
        flutterSearchDefaultSkin = state[7] as? Boolean
        bottomBarThemeRefresh.restoreInitializedActivity(activityAt(8))
        val cl = hostLoader ?: return
        activeActivityRef?.get()?.takeIf { !it.isDestroyed && !it.isFinishing }?.let {
            if (HookSettings.isFollowSystemNightModeEnabled) ensureNightModePolling(cl)
        }
    }

    internal fun disposeForReload() {
        mainHandler.removeCallbacksAndMessages(null)
        bottomBarThemeRefresh.disposeForReload()
        activeActivityRef = null
        mainActivityRef = null
        settingsActivityRef = null
        flutterSearchActivityRef = null
    }

    internal fun hook(cl: ClassLoader) {
        owners += this
        hostLoader = cl
        val mod = XposedCompat.module ?: return
        FileFilterThemeCompat.hook(cl)
        ComposeSearchThemeCompat.hook(cl)
        if (!hookState.markInstalled()) return

        try {
            val baseActivityClass = XposedCompat.findClassOrNull(hookPoints.baseActivityClassName, cl)
                ?: run {
                    hookState.reset()
                    log("BaseActivity class NOT FOUND")
                    return
                }

            installRefreshTabSkinHook(cl)
            installStartupSkinSelectionHook(cl)
            bottomBarThemeRefresh.hook(cl)
            installSettingsActivityHook(cl)
            installChangeSkinObserverHook(cl)
            installSkinManagerObserverHook(cl)

            val queueSyncLogic = sync@ { activity: Activity ->
                if (ModuleLifetime.isRetiring) return@sync
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
                mod.hook(method).setId("BaiduSystemNightModeSyncHook:baseResume:${method.declaringClass.name}").intercept { chain ->
                    if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
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
                mod.hook(method).setId("BaiduSystemNightModeSyncHook:baseConfiguration:${method.declaringClass.name}").intercept { chain ->
                    if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
                    val result = chain.proceed()
                    (chain.thisObject as? Activity)?.let { queueSyncLogic(it) }
                    result
                }
            }

            installFlutterSearchActivityHooks(cl, queueSyncLogic)
            restoredOwners.remove(logTag)?.let(::restoreTargets)

            log(
                "hooks INSTALLED: BaseActivity.onResume + " +
                    "onConfigurationChanged + MainActivity.refreshTabSkin + skin observer",
            )
        } catch (t: Throwable) {
            hookState.reset()
            log("FAILED: ${t.message}")
        }
    }

    private fun installStartupSkinSelectionHook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        try {
            val managerClass = hookPoints.skinManagerClassName
                ?.let { XposedCompat.findClassOrNull(it, cl) } ?: return
            val configClass = XposedCompat.findClassOrNull(hookPoints.skinConfigClassName, cl) ?: return
            val init = XposedCompat.findMethodOrNull(managerClass, "init", Context::class.java) ?: return
            val getSkinPath = XposedCompat.findMethodOrNull(configClass, "getCustomSkinPath", Context::class.java)
                ?: return
            val saveSkinPath = XposedCompat.findMethodOrNull(
                configClass, "saveSkinPath", Context::class.java, String::class.java,
            ) ?: return
            val defaultSkin = configClass.getField("DEFAULT_SKIN").get(null) as? String ?: return

            mod.hook(init).setId("BaiduSystemNightModeSyncHook:startupSkin:${init.declaringClass.name}").intercept { chain ->
                if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
                if (startupSkinSelected.compareAndSet(false, true) && HookSettings.isFollowSystemNightModeEnabled) {
                    val context = chain.args[0] as? Context
                    if (context != null) {
                        try {
                            val night = Resources.getSystem().configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                            val skin = if (night) hookPoints.darkSkinTheme else defaultSkin
                            // 原生 init 随后直接加载本次系统皮肤，避免先恢复旧皮肤再于 onResume 切换。
                            if (getSkinPath.invoke(null, context) != skin) {
                                saveSkinPath.invoke(null, context, skin)
                            }
                        } catch (t: Throwable) {
                            log("Startup skin selection failed: ${t.message}")
                        }
                    }
                }
                chain.proceed()
            }
            log("hook INSTALLED: SkinManager startup skin selection")
        } catch (t: Throwable) {
            log("Startup skin selection hook FAILED: ${t.message}")
        }
    }

    private fun installFlutterSearchActivityHooks(cl: ClassLoader, queueSync: (Activity) -> Unit) {
        val mod = XposedCompat.module ?: return
        val activityClass = XposedCompat.findClassOrNull(BaiduThemeHookPoints.FLUTTER_BUSINESS_ACTIVITY, cl)
            ?: return
        if (!FlutterSearchThemeRefresh.hook(cl)) return
        XposedCompat.findMethodOrNull(activityClass, "onResume")?.let { method ->
            mod.hook(method).setId("BaiduSystemNightModeSyncHook:flutterResume:${method.declaringClass.name}").intercept { chain ->
                if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
                val result = chain.proceed()
                (chain.thisObject as? Activity)?.takeIf(::isFlutterSearchActivity)?.let { activity ->
                    if (flutterSearchActivityRef?.get() !== activity) {
                        flutterSearchDefaultSkin = resolveDefaultSkin(cl, activity)
                        flutterSearchRefreshPending = false
                    }
                    flutterSearchActivityRef = WeakReference(activity)
                    flutterSearchResumed = true
                    requestFlutterSearchRefresh(cl)
                    queueSync(activity)
                }
                result
            }
        }
        XposedCompat.findMethodOrNull(activityClass, "onPause")?.let { method ->
            mod.hook(method).setId("BaiduSystemNightModeSyncHook:flutterPause:${method.declaringClass.name}").intercept { chain ->
                if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
                (chain.thisObject as? Activity)?.let { activity ->
                    if (flutterSearchActivityRef?.get() === activity) flutterSearchResumed = false
                }
                chain.proceed()
            }
        }
        activityClass.superclass?.let { XposedCompat.findMethodOrNull(it, "onDestroy") }?.let { method ->
            mod.hook(method).setId("BaiduSystemNightModeSyncHook:flutterDestroy:${method.declaringClass.name}").intercept { chain ->
                if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
                (chain.thisObject as? Activity)?.let { activity ->
                    FlutterSearchThemeRefresh.cancel(activity)
                    if (flutterSearchActivityRef?.get() === activity) {
                        flutterSearchActivityRef = null
                        flutterSearchResumed = false
                        flutterSearchRefreshPending = false
                        flutterSearchDefaultSkin = null
                        flutterSearchRefreshing = false
                    }
                }
                chain.proceed()
            }
        }
    }

    private fun isFlutterSearchActivity(activity: Activity): Boolean {
        if (activity.javaClass.name != BaiduThemeHookPoints.FLUTTER_BUSINESS_ACTIVITY) return false
        val route = runCatching { activity.javaClass.getField("path").get(activity) as? String }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: activity.intent?.getStringExtra("path")
            ?: activity.intent?.getStringExtra("extra_path")
        return route == BaiduThemeHookPoints.FLUTTER_SEARCH_ROUTE
    }

    private fun resolveDefaultSkin(cl: ClassLoader, activity: Activity): Boolean? = runCatching {
        XposedCompat.findClassOrNull(hookPoints.skinConfigClassName, cl)
            ?.getMethod("isDefaultSkin", android.content.Context::class.java)
            ?.invoke(null, activity.applicationContext) as? Boolean
    }.getOrNull()

    private fun requestFlutterSearchRefresh(cl: ClassLoader) {
        if (ModuleLifetime.isRetiring) return
        val activity = flutterSearchActivityRef?.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val defaultSkin = resolveDefaultSkin(cl, activity) ?: return
        flutterSearchRefreshPending = flutterSearchDefaultSkin != null && flutterSearchDefaultSkin != defaultSkin
        if (flutterSearchResumed && flutterSearchRefreshPending && !flutterSearchRefreshing) {
            refreshFlutterSearchActivity(cl, defaultSkin)
        }
    }

    private fun refreshFlutterSearchActivity(cl: ClassLoader, defaultSkin: Boolean) {
        val activity = flutterSearchActivityRef?.get() ?: return
        if (!flutterSearchRefreshPending || !flutterSearchResumed || activity.isFinishing || activity.isDestroyed) return
        flutterSearchRefreshing = true
        FlutterSearchThemeRefresh.refresh(activity, {
            flutterSearchActivityRef?.get() === activity && flutterSearchResumed
        }) { success ->
            flutterSearchRefreshing = false
            if (flutterSearchActivityRef?.get() === activity && success) {
                flutterSearchDefaultSkin = defaultSkin
                flutterSearchRefreshPending = false
                mainHandler.post { requestFlutterSearchRefresh(cl) }
            }
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
                    mod.hook(method).setId("BaiduSystemNightModeSyncHook:changeSkinObserver:${method.declaringClass.name}").intercept { chain ->
                        if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
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
            XposedCompat.findMethodOrNull(skinManagerClass, "notifySkinUpdate")?.let { method ->
                mod.hook(method).setId("BaiduSystemNightModeSyncHook:skinUpdate:${method.declaringClass.name}").intercept { chain ->
                    if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
                    val result = chain.proceed()
                    // 在浅色恢复和异步深色加载的共同结束点刷新界面。
                    // 浅色分支不会触发 SkinLoaderListener.onSuccess。
                    mainHandler.post {
                        if (!ModuleLifetime.isRetiring) {
                            requestFlutterSearchRefresh(cl)
                            activeActivityRef?.get()?.let { target ->
                                updateMainActivityRef(target)
                                updateSettingsActivityRef(target)
                                HostThemeChangeDispatcher.notifyChanged("skin-update-complete")
                                syncSettingsNightSwitch(resolveSystemNight(target))
                                scheduleForceAvatarRefresh("skin-update-complete")
                                pulseDecor(target)
                            }
                        }
                    }
                    result
                }
            }
            val listenerClass = XposedCompat.findClassOrNull(hookPoints.skinLoaderListenerClassName, cl)
            if (listenerClass != null) {
                XposedCompat.findMethodOrNull(
                    skinManagerClass,
                    "loadDefaultUpdateSkin",
                    String::class.java,
                    listenerClass,
                )?.let { method ->
                    mod.hook(method).setId("BaiduSystemNightModeSyncHook:loadDefaultSkin:${method.declaringClass.name}").intercept { chain ->
                        if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
                        val result = chain.proceed()
                        HostThemeChangeDispatcher.notifyChanged("skinManager.loadDefaultUpdateSkin")
                        result
                    }
                }
            }
            XposedCompat.findMethodOrNull(skinManagerClass, "restoreDefaultTheme")?.let { method ->
                mod.hook(method).setId("BaiduSystemNightModeSyncHook:restoreDefaultTheme:${method.declaringClass.name}").intercept { chain ->
                    if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
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
                mod.hook(method).setId("BaiduSystemNightModeSyncHook:refreshTabSkin:${method.declaringClass.name}").intercept { chain ->
                    if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
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

            mod.hook(onResumeMethod).setId("BaiduSystemNightModeSyncHook:settingsResume:${onResumeMethod.declaringClass.name}").intercept { chain ->
                if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
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
                if (ModuleLifetime.isRetiring || !HookSettings.isFollowSystemNightModeEnabled) {
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
        if (ModuleLifetime.isRetiring) return
        try {
            val isSystemNight = resolveSystemNight(activity)

            if (lastAppliedNightMode == isSystemNight) return
            if (lastAppliedNightMode == null && resolveCurrentHostNightMode(cl, activity) == isSystemNight) {
                lastAppliedNightMode = isSystemNight
                logD("host skin already matches system: night=$isSystemNight")
                return
            }
            val changeMethod = resolveChangeSkinMethod(cl)
            if (changeMethod != null) {
                changeMethod.isAccessible = true
                bottomBarThemeRefresh.expectHostThemeChange()
                applySystemSkin(changeMethod, isSystemNight)
                lastAppliedNightMode = isSystemNight
                return
            }

            log("No available skin backend")
        } catch (e: Throwable) {
            bottomBarThemeRefresh.cancelExpectedHostThemeChange()
            log("Sync error: ${e.message}")
        }
    }

    private fun resolveCurrentHostNightMode(cl: ClassLoader, activity: Activity): Boolean? {
        return runCatching {
            val skinConfigClass = XposedCompat.findClassOrNull(hookPoints.skinConfigClassName, cl)
                ?: return@runCatching null
            val isDefaultSkin = XposedCompat.findMethodOrNull(
                skinConfigClass,
                "isDefaultSkin",
                android.content.Context::class.java,
            ) ?: return@runCatching null
            if (isDefaultSkin.invoke(null, activity) == true) return@runCatching false

            val getCustomSkinPath = XposedCompat.findMethodOrNull(
                skinConfigClass,
                "getCustomSkinPath",
                android.content.Context::class.java,
            ) ?: return@runCatching null
            val path = getCustomSkinPath.invoke(null, activity) as? String ?: return@runCatching null
            path.substringAfterLast('/').substringAfterLast('\\') ==
                hookPoints.darkSkinTheme.substringAfterLast('/').substringAfterLast('\\')
        }.getOrNull()
    }

    private fun resolveChangeSkinMethod(cl: ClassLoader): Method? {
        hookPoints.changeSkinMethodResolver?.invoke(cl)?.let { method ->
            logD("ChangeSkinKt resolved by DexKit: ${method.declaringClass.name}.${method.name}")
            return method
        }
        for (changeSkinKtClassName in changeSkinKtClassNames()) {
            val changeSkinClass = XposedCompat.findClassOrNull(changeSkinKtClassName, cl)
            val changeMethod = changeSkinClass?.let { findChangeSkinMethods(it).firstOrNull() }
            if (changeMethod != null) {
                logD("ChangeSkinKt resolved: ${changeMethod.declaringClass.name}.${changeMethod.name}")
                return changeMethod
            }
        }
        return null
    }

    private fun changeSkinKtClassNames(): List<String> =
        listOfNotNull(hookPoints.changeSkinKtClassName)

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

    private fun applySystemSkin(changeMethod: Method, isSystemNight: Boolean) {
        // 宿主 wrapper 保留自身监听器；模块不向异步加载器发布代理。
        changeMethod.invoke(null, if (isSystemNight) hookPoints.darkSkinTheme else null, null)
        log(if (isSystemNight) "Changed to Dark Skin" else "Changed to Default Skin")
    }

    private fun scheduleForceAvatarRefresh(reason: String) {
        if (ModuleLifetime.isRetiring) return
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
        if (ModuleLifetime.isRetiring) return
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
            if (ModuleLifetime.isRetiring) return@post
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
