package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticVideoAdPreloadDexKitResolver

/**
 * Blocks only Samsung host video front ad material preloading.
 */
internal object SamsungVideoAdPreloadBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungVideoAdPreloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = DomesticVideoAdPreloadDexKitResolver.resolve(cl) ?: run {
                XposedCompat.log(
                    "[SamsungVideoAdPreloadBlockHook] downloadVideoFrontAd(Context) NOT FOUND",
                )
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD(
                        "[SamsungVideoAdPreloadBlockHook] downloadVideoFrontAd blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[SamsungVideoAdPreloadBlockHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungVideoAdPreloadBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isVideoAdPreloadDisabled
}
