package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks only the startup-time garbage clean component service registration.
 */
object DomesticGarbageCleanServiceRegisterBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticGarbageCleanServiceRegisterBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.GRABAGECLEAN_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log(
                    "[DomesticGarbageCleanServiceRegisterBlockHook] GrabagecleanContext Companion class NOT FOUND",
                )
                hookState.reset()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                BaiduDomesticHookPoints.GRABAGECLEAN_REGISTER_GARBAGE_CLEAN_SERVICE_METHOD,
                Context::class.java,
            ) ?: run {
                XposedCompat.log(
                    "[DomesticGarbageCleanServiceRegisterBlockHook] registerGarbageCleanService NOT FOUND",
                )
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD(
                        "[DomesticGarbageCleanServiceRegisterBlockHook] registerGarbageCleanService blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[DomesticGarbageCleanServiceRegisterBlockHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticGarbageCleanServiceRegisterBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }



    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isGarbageCleanServiceRegisterDisabled
}
