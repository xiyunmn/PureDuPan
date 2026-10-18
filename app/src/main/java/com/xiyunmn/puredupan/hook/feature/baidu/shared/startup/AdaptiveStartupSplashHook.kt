package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import android.app.Activity
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupSplashHookPoints
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object AdaptiveStartupSplashHook {
    private const val TAG = "AdaptiveStartupSplashHook"
    private val installed = mutableSetOf<Method>()
    private val failureLogged = AtomicBoolean(false)
    private fun enabled() = HookSettings.settingsSnapshot().isAdaptiveStartupSplashEnabled

    @Suppress("DEPRECATION")
    fun hook(cl: ClassLoader) {
        val module = XposedCompat.module ?: return
        val context = HookSettings.appContext() ?: return
        val contentId = context.resources.getIdentifier(
            BaiduStartupSplashHookPoints.NAVIGATE_CONTENT_ID, "id", context.packageName,
        )
        val classes = buildList {
            add(BaiduStartupSplashHookPoints.NAVIGATE_ACTIVITY)
            if (BaiduFeatureRuntime.isCurrentIntlHost()) add(BaiduStartupSplashHookPoints.DEFAULT_MAIN_ACTIVITY)
        }
        classes.forEach { name ->
            runCatching {
                val type = Class.forName(name, false, cl)
                val create = type.getDeclaredMethod("onCreate", Bundle::class.java)
                if (create in installed) return@runCatching
                // International Navigate's translucent theme has no welcome background.
                // Use its launcher's welcome until initView supplies the locale-specific content.
                val backgroundActivity = if (BaiduFeatureRuntime.isCurrentIntlHost() &&
                    name == BaiduStartupSplashHookPoints.NAVIGATE_ACTIVITY
                ) BaiduStartupSplashHookPoints.DEFAULT_MAIN_ACTIVITY else name
                val themeId = context.packageManager.getActivityInfo(ComponentName(context.packageName, backgroundActivity), 0).themeResource
                val theme = context.resources.newTheme().apply { applyStyle(themeId, true) }
                val attrs = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
                val background = try { attrs.getDrawable(0) } finally { attrs.recycle() }
                val prepared = background?.let { StartupSplashDrawable.wrap(it, context.resources, ::enabled) }
                checkNotNull(prepared) { "Unsupported launch background: $name" }
                val state = checkNotNull(prepared.constantState)

                module.hook(create).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept { chain ->
                    val activity = chain.thisObject as Activity
                    // Navigate caches its window background during onCreate for the MainActivity handoff.
                    if (enabled()) safely { activity.window.setBackgroundDrawable(state.newDrawable()) }
                    val result = chain.proceed()
                    if (enabled()) safely { updateWindow(activity) }
                    result
                }
                installed += create
                val init = type.getDeclaredMethod("initView")
                if (init !in installed) {
                    module.hook(init).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept { chain ->
                        val result = chain.proceed()
                        if (enabled() && name == BaiduStartupSplashHookPoints.NAVIGATE_ACTIVITY) safely {
                            updateLaunchContent(chain.thisObject as Activity, contentId)
                        }
                        if (enabled()) safely { updateWindow(chain.thisObject as Activity) }
                        result
                    }
                    installed += init
                }
                XposedCompat.log("[$TAG] launch background hooks INSTALLED: $name")
            }.onFailure { XposedCompat.logW("[$TAG] install failed for $name: ${it.message}") }
        }
        if (BaiduFeatureRuntime.isDomesticFamilyHost(context)) hookDomesticMainWindow(cl)
    }

    private fun updateLaunchContent(activity: Activity, contentId: Int) {
        val content = activity.findViewById<View>(contentId) ?: return
        val original = content.background ?: return
        // activity_navigate draws its own welcome above the window, on a white parent.
        content.background = checkNotNull(StartupSplashDrawable.wrap(original, activity.resources, ::enabled)) {
            "Unsupported launch content background: ${original.javaClass.name}"
        }
        val parent = content.parent as? View ?: return
        val parentBackground = parent.background as? ColorDrawable ?: return
        if (parentBackground.color == Color.WHITE) {
            parent.background = checkNotNull(StartupSplashDrawable.wrap(parentBackground, activity.resources, ::enabled))
        }
    }

    private fun hookDomesticMainWindow(cl: ClassLoader) {
        val module = XposedCompat.module ?: return
        val name = BaiduFeatureRuntime.currentMainActivityClassName() ?: return
        runCatching {
            val init = Class.forName(name, false, cl).getDeclaredMethod("initView")
            if (init in installed) return@runCatching
            module.hook(init).intercept { chain ->
                if (enabled()) safely {
                    val activity = chain.thisObject as Activity
                    val original = activity.window.decorView.background as? ColorDrawable
                    if (original?.color == Color.WHITE) {
                        // Main writes white immediately before initView. Its native focus callback
                        // later clears this temporary window background as usual.
                        activity.window.setBackgroundDrawable(checkNotNull(
                            StartupSplashDrawable.wrap(original, activity.resources, ::enabled),
                        ))
                    }
                }
                chain.proceed()
            }
            installed += init
            XposedCompat.log("[$TAG] main transition background hook INSTALLED")
        }.onFailure { XposedCompat.logW("[$TAG] main transition install failed: ${it.message}") }
    }

    @Suppress("DEPRECATION")
    private fun updateWindow(activity: Activity) {
        val window = activity.window
        val original = window.decorView.background ?: return
        // A host hook can restore the native window background during initialization.
        if (original !is StartupSplashDrawable) {
            val adaptive = StartupSplashDrawable.wrap(original, activity.resources, ::enabled)
            checkNotNull(adaptive) { "Unsupported launch background: ${original.javaClass.name}" }
            window.setBackgroundDrawable(adaptive)
        }
        if (StartupSplashDrawable.isSystemDark()) {
            window.statusBarColor = StartupSplashDrawable.DARK_BACKGROUND
            window.navigationBarColor = StartupSplashDrawable.DARK_BACKGROUND
            val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and mask.inv()
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            }
        }
    }

    private inline fun safely(action: () -> Unit) {
        runCatching(action).onFailure {
            if (failureLogged.compareAndSet(false, true)) XposedCompat.logW("[$TAG] window skipped: ${it.message}")
        }
    }
}
