package com.xiyunmn.puredupan.hook.feature.baidu.cn.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticVideoAdPreloadDexKitResolver

/**
 * Blocks only foreground-resume video front ad material preloading.
 */
object VideoAdPreloadBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[VideoAdPreloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = DomesticVideoAdPreloadDexKitResolver.resolve(cl) ?: run {
                XposedCompat.log("[VideoAdPreloadBlockHook] downloadVideoFrontAd(Context) NOT FOUND")
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[VideoAdPreloadBlockHook] downloadVideoFrontAd blocked")
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[VideoAdPreloadBlockHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[VideoAdPreloadBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }



    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isVideoAdPreloadDisabled
}
