package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints


internal object DomesticAudioCircleViewAutostartBlockHook {
    private const val TAG = "DomesticAudioCircleViewAutostartBlockHook"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val dexKitInstalled = hookDexKitFloatViewStartupTask(cl)
            val installed = if (dexKitInstalled > 0) dexKitInstalled else hookFloatViewStartupTask(cl)
            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[$TAG] No hooks installed")
            } else {
                XposedCompat.log("[$TAG] hook INSTALLED")
            }
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[$TAG] Installation error: ${e.message}")
            XposedCompat.log(e)
        }
    }

   
    private fun hookFloatViewStartupTask(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.FLOAT_VIEW_STARTUP_TASK,
            cl,
        )
        if (clazz == null) {
            XposedCompat.log("[$TAG] FloatViewStartupTask class NOT FOUND")
            return 0
        }

        val method = XposedCompat.findMethodOrNull(
            clazz,
            BaiduDomesticHookPoints.FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_METHOD,
        )
        if (method == null) {
            XposedCompat.log("[$TAG] initAudioCircleView method NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[$TAG] initAudioCircleView blocked")
                null
            } else {
                chain.proceed()
            }
        }

        XposedCompat.log("[$TAG] FloatViewStartupTask.initAudioCircleView hooked")
        return 1
    }

    private fun hookDexKitFloatViewStartupTask(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val method = DomesticFloatViewStartupDexKitResolver.resolveInitAudioCircleView(cl) ?: run {
            XposedCompat.log("[$TAG] DexKit initAudioCircleView NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[$TAG] DexKit initAudioCircleView blocked")
                null
            } else {
                chain.proceed()
            }
        }

        XposedCompat.log(
            "[$TAG] DexKit FloatViewStartupTask.initAudioCircleView hooked",
        )
        return 1
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isMediaBrowserServiceAutostartDisabled
}
