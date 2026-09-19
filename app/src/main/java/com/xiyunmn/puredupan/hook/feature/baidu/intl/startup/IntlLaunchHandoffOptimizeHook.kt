package com.xiyunmn.puredupan.hook.feature.baidu.intl.startup

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints

/** Cold-ad bypass and window continuity for the two international launcher shells. */
internal object IntlLaunchHandoffOptimizeHook {
    private const val TAG = "IntlLaunchHandoffOptimizeHook"
    private val windowState = HookState()
    private val coldGateState = HookState()
    private val shellInit = ThreadLocal<Activity?>()
    private val coldDecision = ThreadLocal<Activity?>()
    private val shellNames = setOf(BaiduIntlHookPoints.DEFAULT_MAIN_ACTIVITY, BaiduIntlHookPoints.NAVIGATE_ACTIVITY)

    fun hook(cl: ClassLoader) {
        if (!HookSettings.isIntlSplashStartupAccelerateEnabled) return
        // Independent installation: a cold cache miss must not prevent the window repair.
        runCatching { hookColdGate(cl) }.onFailure {
            XposedCompat.logW("[$TAG] cold gate unavailable: ${it.message}")
        }
        runCatching { hookWindows(cl) }.onFailure {
            XposedCompat.logW("[$TAG] window repair unavailable: ${it.message}")
        }
    }

    private fun hookColdGate(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val gate = IntlColdStartSplashDexKitResolver.resolve(cl) ?: run {
            XposedCompat.logD("[$TAG] cold gate pending DexKit warm-up; native ad routing retained")
            return
        }
        val entry = XposedCompat.findMethodOrNull(
            BaiduIntlHookPoints.NAVIGATE_ACTIVITY, cl, BaiduIntlHookPoints.NAVIGATE_SHOW_FLASH_SCREEN,
        ) ?: return
        if (!coldGateState.markInstalled()) return
        mod.hook(entry).intercept { chain ->
            val previous = coldDecision.get()
            coldDecision.set(chain.thisObject as? Activity)
            try {
                chain.proceed()
            } finally {
                if (previous == null) coldDecision.remove() else coldDecision.set(previous)
            }
        }
        mod.hook(gate).intercept { chain ->
            val activity = coldDecision.get()
            if (HookSettings.isIntlSplashStartupAccelerateEnabled && activity != null &&
                activity.javaClass.name == BaiduIntlHookPoints.NAVIGATE_ACTIVITY &&
                chain.args.firstOrNull() === activity
            ) {
                // Native no-ad dispatch still owns login, guides and external/teen/enterprise
                // routes. A synchronous fake ad callback would race the caller's setSplash(true).
                false
            } else {
                chain.proceed()
            }
        }
        XposedCompat.logD("[$TAG] cold splash gate installed")
    }

    private fun hookWindows(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val shells = shellNames.map { XposedCompat.findClassOrNull(it, cl) ?: return }
        val creates = shells.map { XposedCompat.findMethodOrNull(it, "onCreate", Bundle::class.java) ?: return }
        val inits = shells.map { XposedCompat.findMethodOrNull(it, "initView") ?: return }
        val finishes = shells.map { XposedCompat.findMethodOrNull(it, "finish") ?: return }.distinct()
        val start = Activity::class.java.getDeclaredMethod(
            "startActivityForResult", Intent::class.java, Int::class.javaPrimitiveType, Bundle::class.java,
        )
        if (!windowState.markInstalled()) return

        creates.forEach { method ->
            mod.hook(method).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (isEnabledShell(activity)) {
                    val shell = activity!!
                    safely {
                        // Before decor creation: painting an opaque View does not change the
                        // system's translucent-window occlusion state.
                        if (shell.javaClass.name == BaiduIntlHookPoints.NAVIGATE_ACTIVITY) makeOpaque(shell)
                        prepareShellWindow(shell)
                        suppressTransition(shell)
                    }
                }
                chain.proceed()
            }
        }
        inits.forEach { method ->
            mod.hook(method).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (!isEnabledShell(activity)) return@intercept chain.proceed()
                val previous = shellInit.get()
                shellInit.set(activity)
                try {
                    chain.proceed()
                } finally {
                    if (previous == null) shellInit.remove() else shellInit.set(previous)
                    safely { prepareShellWindow(activity!!) }
                }
            }
        }
        finishes.forEach { method ->
            mod.hook(method).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (isEnabledShell(activity)) safely { suppressTransition(activity!!) }
                val result = chain.proceed()
                if (isEnabledShell(activity)) safely { suppressTransition(activity!!) }
                result
            }
        }
        // Both startActivity overloads delegate here. Keep the native lifetime of each shell;
        // no global pending activity, focus-based finish or delayed main-window rewrites.
        mod.hook(start).intercept { chain ->
            val activity = chain.thisObject as? Activity
            val intent = chain.args.firstOrNull() as? Intent
            val ordinaryHandoff = isEnabledShell(activity) && chain.args.getOrNull(1) == -1 &&
                isShellHandoff(activity!!, intent)
            val result = chain.proceed()
            if (ordinaryHandoff) safely { suppressTransition(activity!!) }
            result
        }
        hookShellBarRequests(cl)
        XposedCompat.logD("[$TAG] opaque startup shells and transition boundaries installed")
    }

    @Suppress("DEPRECATION")
    private fun makeOpaque(activity: Activity) {
        // Reuse the host's locale-aware welcome theme, without numeric resource IDs.
        val themeId = activity.packageManager.getActivityInfo(
            ComponentName(activity.packageName, BaiduIntlHookPoints.DEFAULT_MAIN_ACTIVITY), 0,
        ).theme
        if (themeId == 0) return
        val theme = activity.resources.newTheme().apply { applyStyle(themeId, true) }
        val attrs = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent, android.R.attr.windowIsFloating))
        val opaque = try { !attrs.getBoolean(0, false) && !attrs.getBoolean(1, false) } finally { attrs.recycle() }
        if (!opaque) return
        activity.setTheme(themeId)
        // setTheme applies an overlay and may retain windowIsTranslucent from Navigate.
        // Replace the theme contents as well, so PhoneWindow sees the opaque base theme.
        activity.theme.setTo(theme)
        // Manifest translucency is also held by system_server. Update it through the public
        // API; on older releases keep the host's conservative delayed cleanup.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.setTranslucent(false)
    }

    @Suppress("DEPRECATION")
    private fun prepareShellWindow(activity: Activity) {
        val window = activity.window
        window.setWindowAnimations(0)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        val decor = window.decorView
        val lightBackground = isDefaultSkin(activity)
        var visibility = visibleBars(decor.systemUiVisibility)
        visibility = if (lightBackground) visibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            else visibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        decor.systemUiVisibility = visibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                show(WindowInsets.Type.systemBars())
                setSystemBarsAppearance(
                    if (lightBackground) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hookShellBarRequests(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        mod.hook(View::class.java.getDeclaredMethod("setSystemUiVisibility", Int::class.javaPrimitiveType)).intercept { chain ->
            val shell = shellInit.get()
            if (isEnabledShell(shell) && chain.thisObject === shell!!.window.peekDecorView()) {
                chain.proceed(arrayOf(visibleBars(chain.args[0] as Int)))
            } else chain.proceed()
        }
        mod.hook(Window::class.java.getDeclaredMethod("addFlags", Int::class.javaPrimitiveType)).intercept { chain ->
            val shell = shellInit.get()
            if (isEnabledShell(shell) && chain.thisObject === shell!!.window) {
                val flags = (chain.args[0] as Int) and
                    (WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).inv()
                chain.proceed(arrayOf(flags))
            } else chain.proceed()
        }
        val hide = XposedCompat.findMethodOrNull(
            "androidx.core.view.WindowInsetsControllerCompat", cl, "hide", Int::class.javaPrimitiveType!!,
        ) ?: return
        mod.hook(hide).intercept { chain ->
            if (isEnabledShell(shellInit.get())) {
                // Compat systemBars mask is status/navigation/caption on supported releases.
                val types = (chain.args[0] as Int) and 7.inv()
                if (types == 0) null else chain.proceed(arrayOf(types))
            } else chain.proceed()
        }
    }

    private fun isEnabledShell(activity: Activity?): Boolean =
        HookSettings.isIntlSplashStartupAccelerateEnabled && activity?.javaClass?.name in shellNames

    private fun isDefaultSkin(context: Context): Boolean = runCatching {
        val name = BaiduFeatureRuntime.skinConfigClassNameFor(context) ?: return@runCatching true
        val method = XposedCompat.findMethodOrNull(name, context.classLoader, "isDefaultSkin", Context::class.java)
        method?.invoke(null, context) as? Boolean ?: true
    }.getOrDefault(true)

    private fun isShellHandoff(activity: Activity, intent: Intent?): Boolean {
        val component = intent?.component ?: return false
        if (component.packageName != activity.packageName || intent.flags and
            (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0
        ) return false
        return when (activity.javaClass.name) {
            BaiduIntlHookPoints.DEFAULT_MAIN_ACTIVITY -> component.className == BaiduIntlHookPoints.NAVIGATE_ACTIVITY
            BaiduIntlHookPoints.NAVIGATE_ACTIVITY -> component.className == BaiduFeatureRuntime.currentMainActivityClassName()
            else -> false
        }
    }

    @Suppress("DEPRECATION")
    private fun visibleBars(flags: Int): Int = flags and (View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LOW_PROFILE).inv()

    @Suppress("DEPRECATION")
    private fun suppressTransition(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        activity.overridePendingTransition(0, 0)
    }

    private inline fun safely(block: () -> Unit) {
        runCatching(block).onFailure { XposedCompat.logD("[$TAG] window operation skipped: ${it.message}") }
    }
}
