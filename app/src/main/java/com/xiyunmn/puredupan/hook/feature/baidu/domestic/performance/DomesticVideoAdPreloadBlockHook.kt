package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticVideoAdPreloadDexKitResolver
import java.lang.reflect.Method

/**
 * Blocks only foreground-resume video front ad material preloading.
 */
object DomesticVideoAdPreloadBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticVideoAdPreloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = DomesticVideoAdPreloadDexKitResolver.resolve(cl) ?: run {
                XposedCompat.log("[DomesticVideoAdPreloadBlockHook] downloadVideoFrontAd(Context) NOT FOUND")
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[DomesticVideoAdPreloadBlockHook] downloadVideoFrontAd blocked")
                    blockedReturnValue(method)
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[DomesticVideoAdPreloadBlockHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticVideoAdPreloadBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }



    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isVideoAdPreloadDisabled

    private fun blockedReturnValue(method: Method): Any? {
        if (method.returnType == Void.TYPE) return null
        return runCatching {
            method.returnType.getField("INSTANCE").get(null)
        }.getOrNull()
    }
}
