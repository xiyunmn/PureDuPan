package com.xiyunmn.puredupan.hook.feature.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks only the startup-time garbage clean component service registration.
 */
object GarbageCleanServiceRegisterBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[GarbageCleanServiceRegisterBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.GRABAGECLEAN_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log(
                    "[GarbageCleanServiceRegisterBlockHook] GrabagecleanContext Companion class NOT FOUND",
                )
                resetHooked()
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
                resetHooked()
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
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[GarbageCleanServiceRegisterBlockHook] FAILED: ${t.message}")
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
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isGarbageCleanServiceRegisterDisabled
}
