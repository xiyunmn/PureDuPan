package com.xiyunmn.puredupan.hook.feature.performance

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * 阻止音频悬浮球自动启动
 *
 * ## 核心机制（只有1个Hook）
 *
 * Hook FloatViewStartupTask.initAudioCircleView()，从启动期源头阻止
 * 音频悬浮球组件的自动初始化。
 *
 * ## 宿主逻辑
 *
 * 启动任务中自动初始化音频悬浮球:
 * ```java
 * // FloatViewStartupTask.java
 * public void run() {
 *     // ...
 *     initAudioCircleView();  // 自动初始化音频悬浮球
 *     // ...
 * }
 *
 * public final void initAudioCircleView() {
 *     MAudioApiCompManager.audioCircleViewInit();
 * }
 * ```
 *
 * ## 用户播放不受影响
 *
 * 用户主动播放音频文件时，播放器会按需初始化MediaBrowser服务，
 * 不依赖启动期的自动初始化。
 *
 * ## 设计原则
 *
 * - 最小化：只Hook启动任务，从源头解决
 * - 源头拦截：不在Service层拦截，避免影响用户播放
 * - 按需加载：播放器会在需要时初始化服务
 *
 * ## 历史背景
 *
 * 重构自 MediaBrowserServiceAutostartBlockHook (294行，6个Hook)。
 * 原文件Hook了完整链路（StartupTask→Helper→Manager→Service），
 * 但实际启动任务成功后，后续Hook从未被执行。
 * 重构后减少83%代码，显著提升可维护性。
 */
object AudioCircleViewAutostartBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[AudioCircleViewAutostartBlock] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val installed = hookFloatViewStartupTask(cl)
            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[AudioCircleViewAutostartBlock] No hooks installed")
            } else {
                XposedCompat.log("[AudioCircleViewAutostartBlock] hook INSTALLED")
            }
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[AudioCircleViewAutostartBlock] Installation error: ${e.message}")
            XposedCompat.log(e)
        }
    }

    /**
     * Hook FloatViewStartupTask.initAudioCircleView()
     *
     * 这是音频悬浮球自动启动的唯一入口。
     * 阻止此方法后，启动期不会自动初始化音频悬浮球组件。
     *
     * 用户主动播放音频时，播放器会按需初始化MediaBrowser服务和AudioPlayService，
     * 不依赖启动期的自动初始化，因此用户播放不受影响。
     */
    private fun hookFloatViewStartupTask(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            StableBaiduPanHookPoints.FLOAT_VIEW_STARTUP_TASK,
            cl,
        )
        if (clazz == null) {
            XposedCompat.log("[AudioCircleViewAutostartBlock] FloatViewStartupTask class NOT FOUND")
            return 0
        }

        val method = XposedCompat.findMethodOrNull(
            clazz,
            StableBaiduPanHookPoints.FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_METHOD,
        )
        if (method == null) {
            XposedCompat.log("[AudioCircleViewAutostartBlock] initAudioCircleView method NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[AudioCircleViewAutostartBlock] initAudioCircleView blocked")
                null
            } else {
                chain.proceed()
            }
        }

        XposedCompat.log("[AudioCircleViewAutostartBlock] FloatViewStartupTask.initAudioCircleView hooked")
        return 1
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isMediaBrowserServiceAutostartDisabled
}
