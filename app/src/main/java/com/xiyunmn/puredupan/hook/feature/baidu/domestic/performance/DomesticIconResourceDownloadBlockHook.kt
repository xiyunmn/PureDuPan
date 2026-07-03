package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks only the startup async icon resource background download.
 */
object DomesticIconResourceDownloadBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticIconResourceDownloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = DomesticIconResourceDownloadDexKitResolver.resolve(cl) ?: run {
                XposedCompat.log("[DomesticIconResourceDownloadBlockHook] startDownload equivalent NOT FOUND")
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[DomesticIconResourceDownloadBlockHook] startDownload blocked")
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log(
                "[DomesticIconResourceDownloadBlockHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticIconResourceDownloadBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }



    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isIconResourceDownloadDisabled
}
