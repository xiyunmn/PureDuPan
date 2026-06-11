package com.xiyunmn.puredupan.hook.feature.ad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.ref.WeakReference

/**
 * 冷启动开屏容器快退 Hook。
 *
 * 受 [ConfigManager.KEY_BLOCK_SPLASH_INTERSTITIAL] 控制，默认开启。
 */
object SplashLifeHolderFastFinishHook {
    @Volatile private var hooked = false
    @Volatile private var restoreUntil = 0L
    @Volatile private var splashFullscreenBypassUntil = 0L
    @Volatile private var forceNoAdBranchUntil = 0L
    @Volatile private var replayingDelayedDestroy = false
    @Volatile private var systemBarTraceUntil = 0L
    @Volatile private var windowBackgroundGuardUntil = 0L
    @Volatile private var tracedWindows: List<WeakReference<Window>> = emptyList()
    @Volatile private var tracedDecors: List<WeakReference<View>> = emptyList()
    @Volatile private var tracedActivityName: String = ""

    private const val STATUS_BAR_RESTORE_WINDOW_MS = 3600L
    private const val SPLASH_FULLSCREEN_BYPASS_WINDOW_MS = 1500L
    private const val FORCE_NO_AD_BRANCH_WINDOW_MS = 1500L
    private const val NO_AD_DESTROY_DELAY_MS = 680L
    private const val SYSTEM_BAR_TRACE_WINDOW_MS = 2200L
    private const val STATUS_BAR_SHIELD_TAG = "PureDuPan:splash_status_bar_shield"

    // Home background resource candidates
    private val HOME_BACKGROUND_DRAWABLE_CANDIDATES = listOf(
        "homepage_background",
        "home_background",
        "main_background",
        "home_bg",
        "activity_home_bg",
        "bg_home",
        "bg_homepage",
        "home_page_bg",
        "background_home"
    )

    private val HOME_BACKGROUND_COLOR_CANDIDATES = listOf(
        "ui_color_gc8",
        "bg_dn_home_page",
        "color_home_bg",
        "home_bg_color",
        "bg_home",
        "bg_main",
        "home_background_color",
        "main_bg_color",
        "color_background_home"
    )

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isSplashInterstitialBlockEnabled) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            var installed = 0

            installed += hookSetupSplashAdViewSkip(cl)
            installed += hookMainActivityOnCreate(cl)
            installed += hookPhoneWindowStatusBarColorTransform()
            if (ConfigManager.shouldOutputDetailedLogs()) {
                installed += hookSystemBarTraceMethods()
            }
            installed += hookWindowBackgroundGuard()
            installed += hookSplashLoadAdGuard(cl)
            installed += hookSplashLimitCountNoAdBranch(cl)
            installed += hookDelayedNoAdDestroy(cl)
            installed += hookSplashFullscreenBypass(
                cl = cl,
                className = StableBaiduPanHookPoints.ADVERTISE_NOTCH_SCREEN_UTILS,
                methodName = StableBaiduPanHookPoints.NOTCH_SCREEN_UTILS_NOTCH_FULL_SCREEN_METHOD,
                label = "advertise NotchScreenUtils.notchFullScreen",
            )
            installed += hookSplashFullscreenBypass(
                cl = cl,
                className = StableBaiduPanHookPoints.BUSINESS_NOTCH_SCREEN_UTILS,
                methodName = StableBaiduPanHookPoints.NOTCH_SCREEN_UTILS_NOTCH_FULL_SCREEN_METHOD,
                label = "business NotchScreenUtils.notchFullScreen",
            )
            installed += hookSplashFullscreenBypass(
                cl = cl,
                className = StableBaiduPanHookPoints.BUSINESS_ACTIVITY_KT,
                methodName = StableBaiduPanHookPoints.ACTIVITY_KT_SET_P_FULL_SCREEN_METHOD,
                label = "ActivityKt.setPFullScreen",
            )

            installed += hookWindowFocusRestore(
                cl = cl,
                className = StableBaiduPanHookPoints.MAIN_ACTIVITY,
                label = "MainActivity",
                deferFirstRestore = true,
            )
            installed += hookWindowFocusRestore(
                cl = cl,
                className = StableBaiduPanHookPoints.HOME_ACTIVITY,
                label = "HomeActivity",
                deferFirstRestore = false,
            )
            installed += hookHomeSplashFinishRestore(cl)

            if (installed == 0) { resetHooked(); return }
            XposedCompat.log("[SplashLifeHolderFastFinishHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[SplashLifeHolderFastFinishHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun markStatusBarRestoreWindow() {
        restoreUntil = android.os.SystemClock.uptimeMillis() + STATUS_BAR_RESTORE_WINDOW_MS
        windowBackgroundGuardUntil = restoreUntil
    }

    private fun shouldRestoreStatusBar(): Boolean {
        return ConfigManager.isSplashInterstitialBlockEnabled &&
            android.os.SystemClock.uptimeMillis() <= restoreUntil
    }

    private fun markForceNoAdBranchWindow() {
        forceNoAdBranchUntil =
            android.os.SystemClock.uptimeMillis() + FORCE_NO_AD_BRANCH_WINDOW_MS
    }

    private fun shouldForceNoAdBranch(): Boolean {
        return ConfigManager.isSplashInterstitialBlockEnabled &&
            android.os.SystemClock.uptimeMillis() <= forceNoAdBranchUntil
    }

    private fun hookSetupSplashAdViewSkip(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(StableBaiduPanHookPoints.MAIN_ACTIVITY, cl)
        if (clazz == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] MainActivity class NOT FOUND for setupSplashAdView")
            return 0
        }
        val methods = clazz.declaredMethods.filter { it.name == "setupSplashAdView" }
        if (methods.isEmpty()) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] setupSplashAdView method NOT FOUND")
            return 0
        }
        methods.forEach { method ->
            mod.hook(method).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    try {
                        XposedCompat.callMethod(activity, "skipSplashAd")
                        XposedCompat.logD("[SplashLifeHolderFastFinishHook] setupSplashAdView redirected to skipSplashAd")
                    } catch (t: Throwable) {
                        XposedCompat.log("[SplashLifeHolderFastFinishHook] skipSplashAd call failed: ${t.message}")
                        return@intercept chain.proceed()
                    }
                    return@intercept null
                }
                chain.proceed()
            }
        }
        XposedCompat.log("[SplashLifeHolderFastFinishHook] setupSplashAdView hook INSTALLED")
        return methods.size
    }

    private fun hookSplashLoadAdGuard(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            StableBaiduPanHookPoints.SPLASH_LIFE_HOLDER_CONTAINER,
            cl,
        )
        if (clazz == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] SplashLifeHolderContainer class NOT FOUND")
            return 0
        }
        var count = 0
        clazz.declaredMethods
            .filter { it.name == StableBaiduPanHookPoints.SPLASH_LIFE_HOLDER_CONTAINER_LOAD_AD_METHOD }
            .forEach { method ->
                mod.hook(method).intercept { chain ->
                    val splashView = chain.thisObject as? View
                    val activity = splashView?.context?.let { findActivity(it) }
                    activity?.let {
                        markStatusBarRestoreWindow()
                        markSystemBarTraceTarget(it)
                        applyTemporaryHomeWindowBackground(it)
                    }
                    markForceNoAdBranchWindow()
                    markSplashFullscreenBypassWindow()
                    traceSystemBarEvent("enter SplashLifeHolderContainer.loadAD method=${method.name}")
                    chain.proceed()
                }
                count++
            }
        if (count > 0) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] SplashLifeHolderContainer.loadAD hooks INSTALLED: count=$count")
        } else {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] SplashLifeHolderContainer.loadAD NOT FOUND")
        }
        return count
    }

    private fun hookSplashLimitCountNoAdBranch(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(StableBaiduPanHookPoints.SPLASH_MANAGER, cl)
        if (clazz == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] SplashManager limit-count class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            StableBaiduPanHookPoints.SPLASH_MANAGER_LIMIT_COUNT_AD_SHOW_METHOD,
        )
        if (method == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] SplashManager.limitCountAdShow NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            if (shouldForceNoAdBranch()) {
                traceSystemBarEvent("SplashManager.limitCountAdShow forced true")
                true
            } else {
                chain.proceed()
            }
        }
        XposedCompat.log("[SplashLifeHolderFastFinishHook] SplashManager.limitCountAdShow hooked")
        return 1
    }

    private fun hookDelayedNoAdDestroy(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            StableBaiduPanHookPoints.SPLASH_LIFE_HOLDER_CONTAINER,
            cl,
        )
        if (clazz == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] delayed destroy class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            StableBaiduPanHookPoints.SPLASH_LIFE_HOLDER_CONTAINER_DESTROY_METHOD,
        )
        if (method == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] SplashLifeHolderContainer.destroyContainer NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            if (replayingDelayedDestroy || !shouldForceNoAdBranch()) {
                return@intercept chain.proceed()
            }
            val container = chain.thisObject
            val view = container as? View
            if (container == null || view == null) {
                return@intercept chain.proceed()
            }
            scheduleDelayedNoAdDestroy(view, container, method, cl)
            null
        }
        XposedCompat.log("[SplashLifeHolderFastFinishHook] SplashLifeHolderContainer.destroyContainer delayed hook INSTALLED")
        return 1
    }

    private fun scheduleDelayedNoAdDestroy(
        view: View,
        container: Any,
        method: java.lang.reflect.Method,
        cl: ClassLoader,
    ) {
        traceSystemBarEvent("no-ad destroyContainer delayed delay=${NO_AD_DESTROY_DELAY_MS}ms")
        val activity = findActivity(view.context)
        view.postDelayed(
            {
                try {
                    replayingDelayedDestroy = true
                    method.invoke(container)
                    traceSystemBarEvent("no-ad destroyContainer replayed delay=${NO_AD_DESTROY_DELAY_MS}ms")
                    activity?.let { schedulePostDestroyStatusBarRestore(it, cl) }
                } catch (t: Throwable) {
                    XposedCompat.logD("[SplashLifeHolderFastFinishHook] delayed no-ad destroy failed: ${t.message}")
                } finally {
                    replayingDelayedDestroy = false
                }
            },
            NO_AD_DESTROY_DELAY_MS,
        )
    }

    private fun hookSplashFullscreenBypass(
        cl: ClassLoader,
        className: String,
        methodName: String,
        label: String,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(className, cl)
        if (clazz == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] $label class NOT FOUND")
            return 0
        }
        val methods = clazz.declaredMethods.filter { method ->
            method.name == methodName &&
                method.parameterTypes.size == 1 &&
                Activity::class.java.isAssignableFrom(method.parameterTypes[0])
        }
        if (methods.isEmpty()) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] $label method NOT FOUND")
            return 0
        }
        methods.forEach { method ->
            mod.hook(method).intercept { chain ->
                if (shouldBypassSplashFullscreen()) {
                    XposedCompat.log("[SplashLifeHolderFastFinishHook] bypassed $label")
                    null
                } else {
                    chain.proceed()
                }
            }
        }
        XposedCompat.log("[SplashLifeHolderFastFinishHook] $label hooks INSTALLED: count=${methods.size}")
        return methods.size
    }

    private fun markSplashFullscreenBypassWindow() {
        splashFullscreenBypassUntil =
            android.os.SystemClock.uptimeMillis() + SPLASH_FULLSCREEN_BYPASS_WINDOW_MS
    }

    private fun shouldBypassSplashFullscreen(): Boolean {
        return ConfigManager.isSplashInterstitialBlockEnabled &&
            android.os.SystemClock.uptimeMillis() <= splashFullscreenBypassUntil
    }

    private fun hookMainActivityOnCreate(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(StableBaiduPanHookPoints.MAIN_ACTIVITY, cl)
        if (clazz == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] MainActivity class NOT FOUND for onCreate")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, "onCreate", Bundle::class.java)
        if (method == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] MainActivity.onCreate NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            (chain.thisObject as? Activity)?.let { activity ->
                markStatusBarRestoreWindow()
                markSystemBarTraceTarget(activity, "MainActivity.onCreate:after")
                applyTemporaryHomeWindowBackground(activity)
                restoreMainStatusBar(activity, cl)
            }
            result
        }
        XposedCompat.log("[SplashLifeHolderFastFinishHook] MainActivity.onCreate hook INSTALLED")
        return 1
    }

    private fun hookSystemBarTraceMethods(): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        try {
            val setSystemUiVisibility = View::class.java.getDeclaredMethod(
                "setSystemUiVisibility",
                Integer.TYPE,
            )
            mod.hook(setSystemUiVisibility).intercept { chain ->
                if (shouldTraceDecor(chain.thisObject as? View)) {
                    val value = chain.args.getOrNull(0) as? Int ?: 0
                    traceSystemBarWrite("View.setSystemUiVisibility", "0x${value.toString(16)}")
                }
                chain.proceed()
            }
            count++
        } catch (t: Throwable) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] trace View.setSystemUiVisibility hook FAILED: ${t.message}")
        }
        try {
            val setStatusBarColor = Window::class.java.getDeclaredMethod(
                "setStatusBarColor",
                Integer.TYPE,
            )
            mod.hook(setStatusBarColor).intercept { chain ->
                if (shouldTraceWindow(chain.thisObject as? Window)) {
                    val value = chain.args.getOrNull(0) as? Int ?: 0
                    traceSystemBarWrite("Window.setStatusBarColor", "0x${Integer.toHexString(value)}")
                }
                chain.proceed()
            }
            count++
        } catch (t: Throwable) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] trace Window.setStatusBarColor hook FAILED: ${t.message}")
        }
        try {
            val setAttributes = Window::class.java.getDeclaredMethod(
                "setAttributes",
                WindowManager.LayoutParams::class.java,
            )
            mod.hook(setAttributes).intercept { chain ->
                if (shouldTraceWindow(chain.thisObject as? Window)) {
                    val attrs = chain.args.getOrNull(0) as? WindowManager.LayoutParams
                    val cutoutMode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                        attrs?.layoutInDisplayCutoutMode
                    } else {
                        null
                    }
                    traceSystemBarWrite(
                        "Window.setAttributes",
                        "flags=0x${(attrs?.flags ?: 0).toString(16)}, cutout=$cutoutMode",
                    )
                }
                chain.proceed()
            }
            count++
        } catch (t: Throwable) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] trace Window.setAttributes hook FAILED: ${t.message}")
        }
        if (count > 0) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] system bar trace hooks INSTALLED: count=$count")
        }
        return count
    }

    private fun hookWindowBackgroundGuard(): Int {
        return try {
            val mod = XposedCompat.module ?: return 0
            val method = Window::class.java.getDeclaredMethod(
                "setBackgroundDrawableResource",
                Integer.TYPE,
            )
            mod.hook(method).intercept { chain ->
                val window = chain.thisObject as? Window
                val value = chain.args.getOrNull(0) as? Int ?: 0
                if (shouldTraceWindow(window)) {
                    traceSystemBarWrite(
                        "Window.setBackgroundDrawableResource",
                        "0x${value.toString(16)}",
                    )
                }
                if (shouldGuardTransparentWindowBackground(window, value)) {
                    val background = getHomePageStartupBackground(window?.context)
                    if (background != null) {
                        window?.setBackgroundDrawable(background)
                        traceSystemBarEvent("transparent window background guarded")
                        return@intercept null
                    }
                }
                chain.proceed()
            }
            XposedCompat.log("[SplashLifeHolderFastFinishHook] Window background guard hooked")
            1
        } catch (t: Throwable) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] Window background guard FAILED: ${t.message}")
            0
        }
    }

    private fun markSystemBarTraceTarget(activity: Activity, reason: String = "splash") {
        try {
            systemBarTraceUntil = android.os.SystemClock.uptimeMillis() + SYSTEM_BAR_TRACE_WINDOW_MS
            val activities = listOfNotNull(activity, getParentActivity(activity))
                .distinctBy { System.identityHashCode(it) }
            tracedWindows = activities.mapNotNull { targetActivity ->
                targetActivity.window?.let { WeakReference(it) }
            }
            tracedDecors = activities.mapNotNull { targetActivity ->
                targetActivity.window?.decorView?.let { WeakReference(it) }
            }
            tracedActivityName = activity.javaClass.name
            traceSystemBarEvent(
                "target=$tracedActivityName reason=$reason windows=${tracedWindows.size}",
            )
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] mark trace target failed: ${t.message}")
        }
    }

    private fun hookPhoneWindowStatusBarColorTransform(): Int {
        return try {
            val mod = XposedCompat.module ?: return 0
            val phoneWindowClass = Class.forName("com.android.internal.policy.PhoneWindow")
            val method = phoneWindowClass.getDeclaredMethod("setStatusBarColor", Integer.TYPE)
            mod.hook(method).intercept { chain ->
                val window = chain.thisObject as? Window
                val value = chain.args.getOrNull(0) as? Int ?: 0

                // Trace (only when detailed logging enabled)
                if (ConfigManager.shouldOutputDetailedLogs() && shouldTraceWindow(window)) {
                    traceSystemBarWrite("PhoneWindow.setStatusBarColor", "0x${Integer.toHexString(value)}")
                }

                // Transform black to transparent during protection window
                if (shouldTransformBlackStatusBar(window, value)) {
                    chain.args[0] = android.graphics.Color.TRANSPARENT
                    XposedCompat.logD("[SplashLifeHolderFastFinishHook] black status bar (0x${Integer.toHexString(value)}) -> transparent")
                    return@intercept chain.proceed()
                }

                chain.proceed()
            }
            XposedCompat.log("[SplashLifeHolderFastFinishHook] PhoneWindow.setStatusBarColor transform hook INSTALLED")
            1
        } catch (t: Throwable) {
            XposedCompat.log(
                "[SplashLifeHolderFastFinishHook] PhoneWindow.setStatusBarColor transform hook FAILED: ${t.message}",
            )
            0
        }
    }

    private fun shouldTraceWindow(window: Window?): Boolean {
        return ConfigManager.shouldOutputDetailedLogs() &&
            ConfigManager.isSplashInterstitialBlockEnabled &&
            window != null &&
            android.os.SystemClock.uptimeMillis() <= systemBarTraceUntil &&
            tracedWindows.any { it.get() === window }
    }

    private fun shouldTraceDecor(view: View?): Boolean {
        return ConfigManager.shouldOutputDetailedLogs() &&
            ConfigManager.isSplashInterstitialBlockEnabled &&
            view != null &&
            android.os.SystemClock.uptimeMillis() <= systemBarTraceUntil &&
            tracedDecors.any { it.get() === view }
    }

    private fun shouldGuardTransparentWindowBackground(window: Window?, resId: Int): Boolean {
        return ConfigManager.isSplashInterstitialBlockEnabled &&
            resId == android.R.color.transparent &&
            window != null &&
            android.os.SystemClock.uptimeMillis() <= windowBackgroundGuardUntil &&
            tracedWindows.any { it.get() === window }
    }

    private fun shouldTransformBlackStatusBar(window: Window?, color: Int): Boolean {
        return ConfigManager.isSplashInterstitialBlockEnabled &&
            (color == 0x0 || color == 0xFF000000.toInt()) &&
            window != null &&
            android.os.SystemClock.uptimeMillis() <= windowBackgroundGuardUntil &&
            tracedWindows.any { it.get() === window }
    }

    private fun traceSystemBarWrite(method: String, value: String) {
        traceSystemBarEvent("$method $value activity=$tracedActivityName caller=${findTraceCaller()}")
    }

    private fun traceSystemBarEvent(message: String) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        XposedCompat.logD("[SplashLifeHolderFastFinishHook][B-Trace] $message")
    }

    private fun findTraceCaller(): String {
        return Thread.currentThread().stackTrace
            .firstOrNull { element ->
                val name = element.className
                (name.startsWith("com.baidu.netdisk") ||
                    name.startsWith("com.netdisk") ||
                    name.startsWith("newhome")) &&
                    !name.contains("SplashLifeHolderFastFinishHook")
            }
            ?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
            ?: "unknown"
    }

    private fun findActivity(context: Context?): Activity? {
        var current = context
        repeat(8) {
            when (current) {
                is Activity -> return current
                is ContextWrapper -> current = current.baseContext
                else -> return null
            }
        }
        return null
    }

    private fun hookWindowFocusRestore(
        cl: ClassLoader,
        className: String,
        label: String,
        deferFirstRestore: Boolean,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        val activityClass = XposedCompat.findClassOrNull(className, cl)
        if (activityClass == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] $label class NOT FOUND")
            return 0
        }
        val focusMethod = XposedCompat.findMethodOrNull(
            activityClass,
            "onWindowFocusChanged",
            java.lang.Boolean.TYPE,
        )
        if (focusMethod == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] $label.onWindowFocusChanged NOT FOUND")
            return 0
        }
        mod.hook(focusMethod).intercept { chain ->
            val result = chain.proceed()
            val hasFocus = chain.args.getOrNull(0) as? Boolean ?: false
            if (hasFocus && shouldRestoreStatusBar()) {
                (chain.thisObject as? Activity)?.let { activity ->
                    markSystemBarTraceTarget(activity, "$label.onWindowFocusChanged")
                    if (deferFirstRestore) {
                        scheduleDeferredStatusBarRestore(activity, cl)
                    } else {
                        scheduleMainStatusBarRestore(activity, cl)
                    }
                }
            }
            result
        }
        XposedCompat.log("[SplashLifeHolderFastFinishHook] $label.onWindowFocusChanged hooked")
        return 1
    }

    private fun hookHomeSplashFinishRestore(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val homeActivityClass = XposedCompat.findClassOrNull(
            StableBaiduPanHookPoints.HOME_ACTIVITY,
            cl,
        )
        if (homeActivityClass == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] HomeActivity class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(homeActivityClass, "onSplashAdLoadFinish")
        if (method == null) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] HomeActivity.onSplashAdLoadFinish NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (shouldRestoreStatusBar()) {
                (chain.thisObject as? Activity)?.let { activity ->
                    markSystemBarTraceTarget(activity, "HomeActivity.onSplashAdLoadFinish")
                    scheduleMainStatusBarRestore(activity, cl)
                    activity.window?.decorView?.postDelayed(
                        { restoreHostStatusBar(activity, cl) },
                        520L,
                    )
                }
            }
            result
        }
        XposedCompat.log("[SplashLifeHolderFastFinishHook] HomeActivity.onSplashAdLoadFinish hooked")
        return 1
    }

    private fun scheduleMainStatusBarRestore(activity: Activity, cl: ClassLoader) {
        restoreHostStatusBar(activity, cl)
        val decor = activity.window?.decorView
        decor?.post { restoreHostStatusBar(activity, cl) }
        decor?.postDelayed({ restoreHostStatusBar(activity, cl) }, 80L)
        decor?.postDelayed({ restoreHostStatusBar(activity, cl) }, 240L)
    }

    private fun scheduleDeferredStatusBarRestore(activity: Activity, cl: ClassLoader) {
        val decor = activity.window?.decorView ?: return
        decor.postDelayed({ restoreHostStatusBar(activity, cl) }, 160L)
        decor.postDelayed({ restoreHostStatusBar(activity, cl) }, 360L)
        decor.postDelayed({ restoreHostStatusBar(activity, cl) }, 560L)
    }

    private fun schedulePostDestroyStatusBarRestore(activity: Activity, cl: ClassLoader) {
        val restoreActivity = findHomeActivity(activity) ?: activity
        markSystemBarTraceTarget(restoreActivity, "destroyContainer:after")
        restoreHostStatusBar(restoreActivity, cl)
        val decor = restoreActivity.window?.decorView ?: activity.window?.decorView ?: return
        decor.post { restoreHostStatusBar(restoreActivity, cl) }
        decor.postDelayed({ restoreHostStatusBar(restoreActivity, cl) }, 120L)
        decor.postDelayed({ restoreHostStatusBar(restoreActivity, cl) }, 320L)
    }

    private fun restoreHostStatusBar(activity: Activity, cl: ClassLoader) {
        if (activity.javaClass.name == StableBaiduPanHookPoints.HOME_ACTIVITY &&
            invokeHostCustomStatusBar(activity)
        ) {
            windowBackgroundGuardUntil = 0L
            removeStatusBarShieldNow(activity)
            scheduleStatusBarShieldFallbackRemoval(activity)
            return
        }
        restoreMainStatusBar(activity, cl)
        scheduleStatusBarShieldFallbackRemoval(activity)
    }

    private fun invokeHostCustomStatusBar(activity: Activity): Boolean {
        return try {
            XposedCompat.callMethod(activity, "customStatusBar")
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] HomeActivity.customStatusBar restored")
            true
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] HomeActivity.customStatusBar failed: ${t.message}")
            false
        }
    }

    private fun findHomeActivity(activity: Activity): Activity? {
        if (activity.javaClass.name == StableBaiduPanHookPoints.HOME_ACTIVITY) {
            return activity
        }
        return try {
            XposedCompat.callMethod(activity, "getHomeActivity") as? Activity
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] getHomeActivity failed: ${t.message}")
            null
        }
    }

    private fun restoreMainStatusBar(activity: Activity, cl: ClassLoader) {
        try {
            val statusBarClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.STATUS_BAR_UTILS, cl
            ) ?: return
            val method = XposedCompat.findMethodOrNull(
                statusBarClass,
                StableBaiduPanHookPoints.STATUS_BAR_UTILS_IMMERSE_STATUS_BAR_METHOD,
                Activity::class.java,
                java.lang.Boolean.TYPE,
                ViewGroup::class.java,
            ) ?: return
            method.invoke(null, activity, isDefaultSkin(activity, cl), null)
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] restore status bar failed: ${t.message}")
        }
    }

    private fun invokeCallback(callback: Any?, methodName: String) {
        if (callback == null) return
        try {
            XposedCompat.callMethod(callback, methodName)
        } catch (t: Throwable) {
            XposedCompat.log("[SplashLifeHolderFastFinishHook] callback $methodName FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun isDefaultSkin(activity: Activity, cl: ClassLoader): Boolean {
        return try {
            val skinConfigClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.SKIN_CONFIG_CLASS, cl
            ) ?: return true
            val method = XposedCompat.findMethodOrNull(
                skinConfigClass,
                "isDefaultSkin",
                android.content.Context::class.java,
            ) ?: return true
            method.invoke(null, activity) as? Boolean ?: true
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] isDefaultSkin failed: ${t.message}")
            true
        }
    }

    private fun installStatusBarShield(activity: Activity, splashView: View?) {
        try {
            val decor = activity.window?.decorView as? ViewGroup ?: return
            removeStatusBarShieldFrom(decor)
            val height = getStatusBarHeight(activity)
            if (height <= 0) return
            val background = getHomePageStartupBackground(activity)
                ?: cloneDrawable(splashView?.background)
                ?: cloneDrawable(getWelcomeWindowBackground(activity.classLoader))
                ?: cloneDrawable(activity.window?.decorView?.background)
                ?: return
            val shield = View(activity).apply {
                tag = STATUS_BAR_SHIELD_TAG
                this.background = background
                isClickable = false
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    elevation = 10000f
                }
            }
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
            ).apply {
                gravity = Gravity.TOP
            }
            decor.addView(shield, params)
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] status bar shield installed")
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] install status bar shield failed: ${t.message}")
        }
    }

    private fun applyTemporaryHomeWindowBackground(activity: Activity) {
        try {
            val background = getHomePageStartupBackground(activity) ?: return
            activity.window?.setBackgroundDrawable(background)
            traceSystemBarEvent("temporary home window background applied")
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] apply temporary window background failed: ${t.message}")
        }
    }

    private fun removeStatusBarShieldNow(activity: Activity) {
        removeStatusBarShield(activity)
        removeStatusBarShield(getParentActivity(activity))
    }

    private fun scheduleStatusBarShieldFallbackRemoval(activity: Activity) {
        try {
            val decor = activity.window?.decorView
            decor?.post { removeStatusBarShield(activity) }
            val parentActivity = getParentActivity(activity)
            parentActivity?.window?.decorView?.post { removeStatusBarShield(parentActivity) }
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] schedule shield removal failed: ${t.message}")
        }
    }

    private fun removeStatusBarShield(activity: Activity?) {
        val decor = activity?.window?.decorView as? ViewGroup ?: return
        val removed = removeStatusBarShieldFrom(decor)
        if (removed > 0) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] status bar shield removed: count=$removed")
        }
    }

    private fun getParentActivity(activity: Activity): Activity? {
        return try {
            Activity::class.java.getMethod("getParent").invoke(activity) as? Activity
        } catch (_: Throwable) {
            null
        }
    }

    private fun removeStatusBarShieldFrom(parent: ViewGroup): Int {
        var removed = 0
        for (index in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(index)
            if (child?.tag == STATUS_BAR_SHIELD_TAG) {
                parent.removeViewAt(index)
                removed++
            }
        }
        return removed
    }

    private fun getStatusBarHeight(activity: Activity): Int {
        return try {
            val id = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) activity.resources.getDimensionPixelSize(id) else 0
        } catch (_: Throwable) {
            0
        }
    }

    private fun getHomePageStartupBackground(
        context: Context?,
    ): android.graphics.drawable.Drawable? {
        if (context == null) return null
        return try {
            val resources = context.resources
            val packageName = context.packageName

            // 1. Try drawable resources
            val drawableId = findDrawableResource(resources, packageName, HOME_BACKGROUND_DRAWABLE_CANDIDATES)
            if (drawableId > 0) {
                val drawable = if (android.os.Build.VERSION.SDK_INT >= 21) {
                    resources.getDrawable(drawableId, context.theme)
                } else {
                    @Suppress("DEPRECATION")
                    resources.getDrawable(drawableId)
                }
                return cloneDrawable(drawable)
            }

            // 2. Fallback to color resources
            val colorId = findColorResource(resources, packageName, HOME_BACKGROUND_COLOR_CANDIDATES)
            if (colorId > 0) {
                return android.graphics.drawable.ColorDrawable(getColorCompat(context, colorId))
            }

            // 3. Final fallback: light gray
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] use fallback gray background")
            android.graphics.drawable.ColorDrawable(0xFFF5F5F5.toInt())
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] get home startup background failed: ${t.message}")
            null
        }
    }

    private fun findDrawableResource(
        resources: android.content.res.Resources,
        packageName: String,
        candidates: List<String>
    ): Int {
        for (name in candidates) {
            val resId = resources.getIdentifier(name, "drawable", packageName)
            if (resId != 0) {
                XposedCompat.logD("[SplashLifeHolderFastFinishHook] found drawable: $name")
                return resId
            }
        }
        return 0
    }

    private fun findColorResource(
        resources: android.content.res.Resources,
        packageName: String,
        candidates: List<String>
    ): Int {
        for (name in candidates) {
            val resId = resources.getIdentifier(name, "color", packageName)
            if (resId != 0) {
                XposedCompat.logD("[SplashLifeHolderFastFinishHook] found color: $name")
                return resId
            }
        }
        return 0
    }

    private fun getColorCompat(context: Context, resId: Int): Int {
        return if (android.os.Build.VERSION.SDK_INT >= 23) {
            context.resources.getColor(resId, context.theme)
        } else {
            @Suppress("DEPRECATION")
            context.resources.getColor(resId)
        }
    }

    private fun getWelcomeWindowBackground(cl: ClassLoader): android.graphics.drawable.Drawable? {
        return try {
            val cacheClass = XposedCompat.findClassOrNull(
                "com.baidu.netdisk.ui.youaguide.WelcomeWindowBgCache",
                cl,
            ) ?: return null
            val instance = cacheClass.getField("INSTANCE").get(null) ?: return null
            XposedCompat.callMethod(instance, "get") as? android.graphics.drawable.Drawable
        } catch (t: Throwable) {
            XposedCompat.logD("[SplashLifeHolderFastFinishHook] get welcome bg failed: ${t.message}")
            null
        }
    }

    private fun cloneDrawable(
        drawable: android.graphics.drawable.Drawable?,
    ): android.graphics.drawable.Drawable? {
        return try {
            drawable?.constantState?.newDrawable()?.mutate() ?: drawable?.mutate()
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() { synchronized(this) { hooked = false } }
}
