package com.xiyunmn.puredupan.hook.feature.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks only the startup-time garbage clean component service registration.
 */
object GarbageCleanServiceRegisterBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[GarbageCleanServiceRegisterBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.GRABAGECLEAN_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log(
                    "[GarbageCleanServiceRegisterBlockHook] GrabagecleanContext Companion class NOT FOUND",
                )
                hookState.reset()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.GRABAGECLEAN_REGISTER_GARBAGE_CLEAN_SERVICE_METHOD,
                Context::class.java,
            ) ?: run {
                XposedCompat.log(
                    "[GarbageCleanServiceRegisterBlockHook] registerGarbageCleanService NOT FOUND",
                )
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD(
                        "[GarbageCleanServiceRegisterBlockHook] registerGarbageCleanService blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[GarbageCleanServiceRegisterBlockHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[GarbageCleanServiceRegisterBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }



    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isGarbageCleanServiceRegisterDisabled
}
