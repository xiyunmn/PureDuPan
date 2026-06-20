package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

internal object SamsungAudioCircleViewAutostartBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungAudioCircleViewAutostartBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.FLOAT_VIEW_STARTUP_TASK,
                cl,
            ) ?: run {
                XposedCompat.log("[SamsungAudioCircleViewAutostartBlockHook] FloatViewStartupTask NOT FOUND")
                hookState.reset()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                BaiduSamsungHookPoints.FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_METHOD,
            ) ?: run {
                XposedCompat.log(
                    "[SamsungAudioCircleViewAutostartBlockHook] initAudioCircleView NOT FOUND",
                )
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD(
                        "[SamsungAudioCircleViewAutostartBlockHook] initAudioCircleView blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[SamsungAudioCircleViewAutostartBlockHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungAudioCircleViewAutostartBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled &&
            HookSettings.isMediaBrowserServiceAutostartDisabled
}
