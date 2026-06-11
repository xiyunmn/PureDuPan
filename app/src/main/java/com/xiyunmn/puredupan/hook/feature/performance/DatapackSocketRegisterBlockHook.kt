package com.xiyunmn.puredupan.hook.feature.performance

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks only the DataPack module socket registration.
 */
object DatapackSocketRegisterBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DatapackSocketRegisterBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.DATAPACK_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log("[DatapackSocketRegisterBlockHook] DatapackContext Companion class NOT FOUND")
                resetHooked()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.DATAPACK_REGISTER_SOCKET_METHOD,
            ) ?: run {
                XposedCompat.log("[DatapackSocketRegisterBlockHook] registerSocket NOT FOUND")
                resetHooked()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[DatapackSocketRegisterBlockHook] registerSocket blocked")
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[DatapackSocketRegisterBlockHook] hook INSTALLED")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[DatapackSocketRegisterBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isDatapackSocketRegisterDisabled
}
