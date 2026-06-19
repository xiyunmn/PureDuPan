package com.xiyunmn.puredupan.hook.feature.startup.intl

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier

internal object IntlLaunchHandoffOptimizeHook {
    private const val DEFAULT_MAIN_ACTIVITY_CLASS_NAME = "com.baidu.netdisk.ui.DefaultMainActivity"
    private const val NAVIGATE_CLASS_NAME = "com.baidu.netdisk.ui.Navigate"
    private const val MAIN_ACTIVITY_CLASS_NAME = "com.baidu.netdisk.ui.MainActivity"
    private const val SKIN_CONFIG_CLASS_NAME = "com.netdisk.themeskin.SkinConfig"
    private const val WINDOW_INSETS_CONTROLLER_COMPAT_CLASS_NAME = "androidx.core.view.WindowInsetsControllerCompat"
    private const val STARTUP_STATUS_BAR_STABILIZE_MS = 2500L

    private val hookState = HookState()
    private val shellInitViewDepth = ThreadLocal<Int>()
    @Volatile private var pendingNavigateRef: WeakReference<Activity>? = null
    @Volatile private var stabilizeMainWindowUntilMs: Long = 0L

    @Suppress("DEPRECATION")
    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isIntlSplashStartupAccelerateEnabled) {
            XposedCompat.log("[IntlLaunchHandoffOptimizeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val startActivityMethod = Activity::class.java.getDeclaredMethod(
                "startActivity",
                Intent::class.java,
            ).apply { isAccessible = true }
            val windowAddFlagsMethod = Window::class.java.getDeclaredMethod(
                "addFlags",
                Int::class.javaPrimitiveType!!,
            ).apply { isAccessible = true }
            val setSystemUiVisibilityMethod = View::class.java.getDeclaredMethod(
                "setSystemUiVisibility",
                Int::class.javaPrimitiveType!!,
            ).apply { isAccessible = true }
            val defaultMainActivityClass = XposedCompat.findClassOrNull(DEFAULT_MAIN_ACTIVITY_CLASS_NAME, cl)
                ?: throw ClassNotFoundException(DEFAULT_MAIN_ACTIVITY_CLASS_NAME)
            val navigateClass = XposedCompat.findClassOrNull(NAVIGATE_CLASS_NAME, cl)
                ?: throw ClassNotFoundException(NAVIGATE_CLASS_NAME)
            val mainActivityClass = XposedCompat.findClassOrNull(MAIN_ACTIVITY_CLASS_NAME, cl)
                ?: throw ClassNotFoundException(MAIN_ACTIVITY_CLASS_NAME)
            val defaultOnCreateMethod = XposedCompat.findMethodOrNull(
                defaultMainActivityClass,
                "onCreate",
                Bundle::class.java,
            ) ?: throw NoSuchMethodException("$DEFAULT_MAIN_ACTIVITY_CLASS_NAME.onCreate")
            val navigateOnCreateMethod = XposedCompat.findMethodOrNull(
                navigateClass,
                "onCreate",
                Bundle::class.java,
            ) ?: throw NoSuchMethodException("$NAVIGATE_CLASS_NAME.onCreate")
            val defaultInitViewMethod = XposedCompat.findMethodOrNull(
                defaultMainActivityClass,
                "initView",
            ) ?: throw NoSuchMethodException("$DEFAULT_MAIN_ACTIVITY_CLASS_NAME.initView")
            val navigateInitViewMethod = XposedCompat.findMethodOrNull(
                navigateClass,
                "initView",
            ) ?: throw NoSuchMethodException("$NAVIGATE_CLASS_NAME.initView")
            val mainOnCreateMethod = XposedCompat.findMethodOrNull(
                mainActivityClass,
                "onCreate",
                Bundle::class.java,
            ) ?: throw NoSuchMethodException("$MAIN_ACTIVITY_CLASS_NAME.onCreate")
            val mainOnResumeMethod = XposedCompat.findMethodOrNull(
                mainActivityClass,
                "onResume",
            ) ?: throw NoSuchMethodException("$MAIN_ACTIVITY_CLASS_NAME.onResume")
            val onWindowFocusChangedMethod = XposedCompat.findMethodOrNull(
                mainActivityClass,
                "onWindowFocusChanged",
                Boolean::class.javaPrimitiveType!!,
            ) ?: throw NoSuchMethodException("$MAIN_ACTIVITY_CLASS_NAME.onWindowFocusChanged")

            listOf(defaultOnCreateMethod, navigateOnCreateMethod).forEach { method ->
                mod.hook(method).intercept { chain ->
                    val activity = chain.thisObject as? Activity
                    stabilizeShellWindow(activity, "${activity.shortClassName()}.onCreate/before")
                    val result = chain.proceed()
                    stabilizeShellWindow(activity, "${activity.shortClassName()}.onCreate/after")
                    result
                }
            }
            listOf(defaultInitViewMethod, navigateInitViewMethod).forEach { method ->
                mod.hook(method).intercept { chain ->
                    val activity = chain.thisObject as? Activity
                    enterShellInitView()
                    try {
                        stabilizeShellWindow(activity, "${activity.shortClassName()}.initView/before")
                        val result = chain.proceed()
                        stabilizeShellWindow(activity, "${activity.shortClassName()}.initView/after")
                        result
                    } finally {
                        exitShellInitView()
                    }
                }
            }
            mod.hook(setSystemUiVisibilityMethod).intercept { chain ->
                val visibility = chain.args.firstOrNull() as? Int ?: return@intercept chain.proceed()
                if (!isShellInitViewActive()) return@intercept chain.proceed()

                val sanitizedVisibility = sanitizeShellSystemUiVisibility(visibility)
                if (sanitizedVisibility != visibility) {
                    XposedCompat.logD(
                        "[IntlLaunchHandoffOptimizeHook] sanitized shell View.setSystemUiVisibility: " +
                            "0x${visibility.toString(16)} -> 0x${sanitizedVisibility.toString(16)}",
                    )
                }
                chain.proceed(arrayOf(sanitizedVisibility))
            }
            mod.hook(windowAddFlagsMethod).intercept { chain ->
                val flags = chain.args.firstOrNull() as? Int ?: return@intercept chain.proceed()
                if (!isShellInitViewActive()) return@intercept chain.proceed()

                val sanitizedFlags = flags and
                    (WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).inv()
                if (sanitizedFlags != flags) {
                    XposedCompat.logD(
                        "[IntlLaunchHandoffOptimizeHook] sanitized shell Window.addFlags: " +
                            "0x${flags.toString(16)} -> 0x${sanitizedFlags.toString(16)}",
                    )
                }
                if (sanitizedFlags == 0) null else chain.proceed(arrayOf(sanitizedFlags))
            }
            hookOptionalInsetsHideMethods(cl)
            mod.hook(startActivityMethod).intercept { chain ->
                val activity = chain.thisObject as? Activity
                val intent = chain.args.firstOrNull() as? Intent
                val shouldAccelerate = shouldAccelerate(activity, intent)
                val result = chain.proceed()

                if (shouldAccelerate && activity != null) {
                    pendingNavigateRef = WeakReference(activity)
                    markMainWindowStabilization()
                    suppressTransition(activity)
                    XposedCompat.logD(
                        "[IntlLaunchHandoffOptimizeHook] captured Navigate handoff, waiting for MainActivity focus",
                    )
                }
                result
            }
            mod.hook(mainOnCreateMethod).intercept { chain ->
                val result = chain.proceed()
                stabilizeMainWindow(chain.thisObject as? Activity, "onCreate")
                result
            }
            mod.hook(mainOnResumeMethod).intercept { chain ->
                val result = chain.proceed()
                stabilizeMainWindow(chain.thisObject as? Activity, "onResume")
                result
            }
            mod.hook(onWindowFocusChangedMethod).intercept { chain ->
                val result = chain.proceed()
                val hasFocus = chain.args.firstOrNull() as? Boolean ?: false
                if (hasFocus && ConfigManager.isIntlSplashStartupAccelerateEnabled) {
                    stabilizeMainWindow(chain.thisObject as? Activity, "onWindowFocusChanged")
                    finishPendingNavigate()
                }
                result
            }

            XposedCompat.log(
                "[IntlLaunchHandoffOptimizeHook] hooks INSTALLED: " +
                    "startup shell system-bar guard + Activity.startActivity(Intent) + " +
                    "MainActivity.onCreate/onResume/onWindowFocusChanged",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlLaunchHandoffOptimizeHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun enterShellInitView() {
        shellInitViewDepth.set(shellInitViewDepth.getDepth() + 1)
    }

    private fun exitShellInitView() {
        val depth = shellInitViewDepth.getDepth() - 1
        if (depth <= 0) {
            shellInitViewDepth.remove()
        } else {
            shellInitViewDepth.set(depth)
        }
    }

    private fun isShellInitViewActive(): Boolean {
        return shellInitViewDepth.getDepth() > 0
    }

    private fun ThreadLocal<Int>.getDepth(): Int = get() ?: 0

    @Suppress("DEPRECATION")
    private fun sanitizeShellSystemUiVisibility(visibility: Int): Int {
        val hiddenBarFlags = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LOW_PROFILE
        return visibility and hiddenBarFlags.inv()
    }

    private fun hookOptionalInsetsHideMethods(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController::class.java.getDeclaredMethod(
                    "hide",
                    Int::class.javaPrimitiveType!!,
                ).apply { isAccessible = true }.also { method ->
                    if (Modifier.isAbstract(method.modifiers)) {
                        XposedCompat.logD("[IntlLaunchHandoffOptimizeHook] platform insets hide hook skipped: abstract method")
                        return@runCatching
                    }
                    mod.hook(method).intercept { chain ->
                        val types = chain.args.firstOrNull() as? Int ?: return@intercept chain.proceed()
                        if (isShellInitViewActive() && shouldBlockShellInsetsHide(types)) {
                            XposedCompat.logD(
                                "[IntlLaunchHandoffOptimizeHook] skipped shell WindowInsetsController.hide: " +
                                    "0x${types.toString(16)}",
                            )
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }
            }
        }.onFailure {
            XposedCompat.logD("[IntlLaunchHandoffOptimizeHook] platform insets hide hook skipped: ${it.message}")
        }

        runCatching {
            val compatClass = XposedCompat.findClassOrNull(WINDOW_INSETS_CONTROLLER_COMPAT_CLASS_NAME, cl)
                ?: return@runCatching
            val hideMethod = XposedCompat.findMethodOrNull(
                compatClass,
                "hide",
                Int::class.javaPrimitiveType!!,
            ) ?: return@runCatching
            mod.hook(hideMethod).intercept { chain ->
                val types = chain.args.firstOrNull() as? Int ?: return@intercept chain.proceed()
                if (isShellInitViewActive() && shouldBlockShellInsetsHide(types)) {
                    XposedCompat.logD(
                        "[IntlLaunchHandoffOptimizeHook] skipped shell WindowInsetsControllerCompat.hide: " +
                            "0x${types.toString(16)}",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
        }.onFailure {
            XposedCompat.logD("[IntlLaunchHandoffOptimizeHook] compat insets hide hook skipped: ${it.message}")
        }
    }

    private fun shouldBlockShellInsetsHide(types: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val statusOrSystemBars = WindowInsets.Type.statusBars() or WindowInsets.Type.systemBars()
        return types and statusOrSystemBars != 0
    }

    private fun shouldAccelerate(activity: Activity?, intent: Intent?): Boolean {
        if (!ConfigManager.isIntlSplashStartupAccelerateEnabled) return false
        if (activity == null || intent == null) return false
        if (activity.javaClass.name != NAVIGATE_CLASS_NAME) return false
        return intent.component?.className == MAIN_ACTIVITY_CLASS_NAME
    }

    private fun markMainWindowStabilization() {
        stabilizeMainWindowUntilMs = SystemClock.uptimeMillis() + STARTUP_STATUS_BAR_STABILIZE_MS
    }

    private fun finishPendingNavigate() {
        val activity = pendingNavigateRef?.get() ?: return
        pendingNavigateRef = null
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching {
            suppressTransition(activity)
            activity.finish()
            suppressTransition(activity)
            XposedCompat.logD("[IntlLaunchHandoffOptimizeHook] Navigate finished after MainActivity gained focus")
        }.onFailure {
            XposedCompat.logD("[IntlLaunchHandoffOptimizeHook] delayed finish failed: ${it.message}")
        }
    }

    private fun stabilizeShellWindow(activity: Activity?, reason: String) {
        if (!shouldStabilizeShellWindow(activity)) return
        val shellActivity = activity ?: return
        applyStartupStatusBar(shellActivity, reason)
        shellActivity.window?.decorView?.let { decorView ->
            decorView.post { applyStartupStatusBar(shellActivity, "$reason/post") }
            decorView.postDelayed({ applyStartupStatusBar(shellActivity, "$reason/post24") }, 24L)
            decorView.postDelayed({ applyStartupStatusBar(shellActivity, "$reason/post80") }, 80L)
        }
    }

    private fun shouldStabilizeShellWindow(activity: Activity?): Boolean {
        if (!ConfigManager.isIntlSplashStartupAccelerateEnabled) return false
        val className = activity?.javaClass?.name ?: return false
        return className == DEFAULT_MAIN_ACTIVITY_CLASS_NAME || className == NAVIGATE_CLASS_NAME
    }

    private fun stabilizeMainWindow(activity: Activity?, reason: String) {
        if (!shouldStabilizeMainWindow(activity)) return
        val mainActivity = activity ?: return
        applyStartupStatusBar(mainActivity, reason)
        mainActivity.window?.decorView?.let { decorView ->
            decorView.post { applyStartupStatusBar(mainActivity, "$reason/post") }
            decorView.postDelayed({ applyStartupStatusBar(mainActivity, "$reason/post48") }, 48L)
            decorView.postDelayed({ applyStartupStatusBar(mainActivity, "$reason/post160") }, 160L)
        }
    }

    private fun shouldStabilizeMainWindow(activity: Activity?): Boolean {
        if (!ConfigManager.isIntlSplashStartupAccelerateEnabled) return false
        if (activity?.javaClass?.name != MAIN_ACTIVITY_CLASS_NAME) return false
        return pendingNavigateRef?.get() != null ||
            SystemClock.uptimeMillis() <= stabilizeMainWindowUntilMs
    }

    @Suppress("DEPRECATION")
    private fun applyStartupStatusBar(activity: Activity, reason: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching {
            val window = activity.window ?: return
            val oldFlags = window.attributes.flags
            val oldVisibility = window.decorView.systemUiVisibility
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = Color.TRANSPARENT
            showSystemBars(window)
            val newVisibility = startupStatusBarVisibility(activity)
            window.decorView.systemUiVisibility = newVisibility
            XposedCompat.logD(
                "[IntlLaunchHandoffOptimizeHook] startup status bar stabilized: " +
                    "$reason flags=0x${oldFlags.toString(16)}->0x${window.attributes.flags.toString(16)} " +
                    "ui=0x${oldVisibility.toString(16)}->0x${newVisibility.toString(16)}",
            )
        }.onFailure {
            XposedCompat.logD("[IntlLaunchHandoffOptimizeHook] status bar stabilize failed: ${it.message}")
        }
    }

    private fun showSystemBars(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        }
    }

    @Suppress("DEPRECATION")
    private fun startupStatusBarVisibility(activity: Activity): Int {
        var visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isDefaultSkin(activity)) {
            visibility = visibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        return visibility
    }

    private fun isDefaultSkin(context: Context): Boolean {
        return runCatching {
            val skinConfigClass = XposedCompat.findClassOrNull(SKIN_CONFIG_CLASS_NAME, context.classLoader)
                ?: return@runCatching true
            val method = skinConfigClass.getDeclaredMethod("isDefaultSkin", Context::class.java)
                .apply { isAccessible = true }
            method.invoke(null, context) as? Boolean ?: true
        }.getOrDefault(true)
    }

    private fun suppressTransition(activity: Activity) {
        runCatching {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }.onFailure {
            XposedCompat.logD("[IntlLaunchHandoffOptimizeHook] overridePendingTransition failed: ${it.message}")
        }
    }

    private fun Activity?.shortClassName(): String {
        return this?.javaClass?.simpleName ?: "Activity"
    }
}
