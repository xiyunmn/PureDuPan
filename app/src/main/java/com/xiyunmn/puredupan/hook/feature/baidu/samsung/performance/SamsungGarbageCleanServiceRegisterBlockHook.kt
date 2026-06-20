package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

/**
 * Blocks only Samsung host startup-time garbage clean service registration.
 */
internal object SamsungGarbageCleanServiceRegisterBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungGarbageCleanServiceRegisterBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.GRABAGECLEAN_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log(
                    "[SamsungGarbageCleanServiceRegisterBlockHook] GrabagecleanContext Companion NOT FOUND",
                )
                hookState.reset()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                BaiduSamsungHookPoints.GRABAGECLEAN_REGISTER_GARBAGE_CLEAN_SERVICE_METHOD,
                Context::class.java,
            ) ?: run {
                XposedCompat.log(
                    "[SamsungGarbageCleanServiceRegisterBlockHook] registerGarbageCleanService NOT FOUND",
                )
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD(
                        "[SamsungGarbageCleanServiceRegisterBlockHook] registerGarbageCleanService blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[SamsungGarbageCleanServiceRegisterBlockHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungGarbageCleanServiceRegisterBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled &&
            HookSettings.isGarbageCleanServiceRegisterDisabled
}
