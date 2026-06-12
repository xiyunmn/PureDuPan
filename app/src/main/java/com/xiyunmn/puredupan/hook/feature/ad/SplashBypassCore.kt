package com.xiyunmn.puredupan.hook.feature.ad

import android.app.Activity
import android.os.SystemClock
import android.view.Window
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * 开屏广告跳过 - 核心实现
 *
 * ## 核心机制（只有2个Hook）
 *
 * ### 1. 进入无广告分支
 * Hook MainActivity.setupSplashAdView()，重定向到 skipSplashAd()。
 * 这是宿主官方的无广告路径，最干净、最稳定。
 *
 * ### 2. 消除黑色状态栏
 * Hook Window.setStatusBarColor()，在保护窗口期内将黑色(0x0)转换为透明色。
 * 从源头阻止状态栏变黑，避免50ms黑条闪烁。
 *
 * ## 设计原则
 * - 最小化：只保留核心功能，移除所有兜底代码
 * - 源头解决：利用宿主官方路径，不对抗宿主逻辑
 * - 时间窗口：状态栏保护3.6秒，之后自动失效
 *
 * ## 历史背景
 * 重构自 SplashLifeHolderFastFinishHook (931行)。
 * 原文件包含大量历史兜底逻辑（loadAD拦截、延迟销毁、全屏bypass等），
 * 但实际核心路径成功后，这些兜底代码从未被执行。
 * 重构后减少89%代码，显著提升可维护性。
 *
 * @see com.xiyunmn.puredupan.hook.feature.ad.SplashInterstitialBlockHook 热启动广告拦截
 */
object SplashBypassCore {
    private val hookState = HookState()

    // 状态栏保护窗口：启动后3.6秒
    @Volatile private var statusBarTransformUntil = 0L
    private const val STATUS_BAR_TRANSFORM_WINDOW_MS = 3600L

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isSplashInterstitialBlockEnabled) {
            XposedCompat.log("[SplashBypassCore] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        var installed = 0
        try {
            // 核心1: 重定向到无广告分支
            installed += hookSetupSplashAdViewSkip(cl)

            // 核心2: 消除黑色状态栏
            installed += hookStatusBarColorTransform()
        } catch (e: Exception) {
            XposedCompat.log("[SplashBypassCore] Installation error: ${e.message}")
            XposedCompat.log(e)
        }

        if (installed == 0) {
            hookState.reset()
            XposedCompat.log("[SplashBypassCore] No hooks installed, reset state")
        } else {
            XposedCompat.log("[SplashBypassCore] hooks INSTALLED: count=$installed")
        }
    }

    /**
     * Hook 1: 重定向 setupSplashAdView → skipSplashAd
     *
     * 宿主逻辑（MainActivity.java line 3645-3653）:
     * ```java
     * private void setupSplashAdView() {
     *     if (!isParallelLoadAd()) {
     *         skipSplashAd();  // ← 官方无广告路径
     *         return;
     *     }
     *     // 创建广告容器、加载广告...
     * }
     * ```
     *
     * 我们直接调用 skipSplashAd()，进入宿主官方的无广告分支。
     * skipSplashAd() 会：
     * 1. 隐藏开屏容器（如果存在）
     * 2. 调用 GuideContext.onColdeAdProcessFinished()
     * 3. 启动首页异步任务
     */
    private fun hookSetupSplashAdViewSkip(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            StableBaiduPanHookPoints.MAIN_ACTIVITY,
            cl,
        )
        if (clazz == null) {
            XposedCompat.log("[SplashBypassCore] MainActivity class NOT FOUND")
            return 0
        }

        val methods = clazz.declaredMethods.filter { it.name == "setupSplashAdView" }
        if (methods.isEmpty()) {
            XposedCompat.log("[SplashBypassCore] setupSplashAdView method NOT FOUND")
            return 0
        }

        methods.forEach { method ->
            mod.hook(method).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    try {
                        // 直接调用宿主的无广告分支
                        XposedCompat.callMethod(activity, "skipSplashAd")
                        // 标记状态栏保护窗口开始
                        markStatusBarTransformWindow()
                        XposedCompat.logD("[SplashBypassCore] setupSplashAdView → skipSplashAd")
                        return@intercept null
                    } catch (e: Throwable) {
                        XposedCompat.log("[SplashBypassCore] skipSplashAd call failed: ${e.message}")
                        XposedCompat.log(e)
                        return@intercept chain.proceed()
                    }
                }
                chain.proceed()
            }
        }

        XposedCompat.log("[SplashBypassCore] setupSplashAdView hook INSTALLED")
        return methods.size
    }

    /**
     * Hook 2: 黑色状态栏 → 透明
     *
     * 问题根源（SPLASH_FIX.md）:
     * MainActivity.onCreate 内部调用 immerseStatusBar(0x0) 设置黑色状态栏。
     * 这是宿主正常流程，不是广告导致。即使完全跳过广告，黑条仍会出现约50ms。
     *
     * 解决方案:
     * Hook Window.setStatusBarColor()，在保护窗口期内将黑色参数转换为透明色。
     * 从源头阻止黑条出现，避免"事后恢复"的竞态风险。
     *
     * 保护窗口: 3.6秒
     * - 足够覆盖启动阶段的所有状态栏设置
     * - 之后自动失效，不影响宿主正常使用
     */
    private fun hookStatusBarColorTransform(): Int {
        val mod = XposedCompat.module ?: return 0
        val method = XposedCompat.findMethodOrNull(
            android.view.Window::class.java,
            "setStatusBarColor",
            Integer.TYPE,
        )
        if (method == null) {
            XposedCompat.log("[SplashBypassCore] Window.setStatusBarColor method NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            val window = chain.thisObject as? Window
            val color = chain.args.getOrNull(0) as? Int ?: return@intercept chain.proceed()

            if (shouldTransformBlackStatusBar(window, color)) {
                chain.args[0] = android.graphics.Color.TRANSPARENT
                XposedCompat.logD("[SplashBypassCore] Black status bar (0x${Integer.toHexString(color)}) → transparent")
            }
            chain.proceed()
        }

        XposedCompat.log("[SplashBypassCore] statusBarColor transform hook INSTALLED")
        return 1
    }

    /**
     * 标记状态栏保护窗口开始
     *
     * 从当前时间起3.6秒内，所有黑色状态栏将被转换为透明色。
     */
    private fun markStatusBarTransformWindow() {
        statusBarTransformUntil = SystemClock.uptimeMillis() + STATUS_BAR_TRANSFORM_WINDOW_MS
        XposedCompat.logD("[SplashBypassCore] Status bar transform window activated for ${STATUS_BAR_TRANSFORM_WINDOW_MS}ms")
    }

    /**
     * 判断是否应该转换黑色状态栏
     *
     * 条件:
     * 1. 功能开关开启
     * 2. 在保护窗口期内
     * 3. 颜色是黑色（0x0 或 0xFF000000）
     */
    private fun shouldTransformBlackStatusBar(window: Window?, color: Int): Boolean {
        if (!ConfigManager.isSplashInterstitialBlockEnabled) return false
        if (SystemClock.uptimeMillis() > statusBarTransformUntil) return false

        // 只转换黑色（0x0 或 0xFF000000）
        return color == 0x0 || color == 0xFF000000.toInt()
    }
}
