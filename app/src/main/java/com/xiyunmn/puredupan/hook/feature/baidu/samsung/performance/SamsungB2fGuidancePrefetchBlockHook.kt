package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

/**
 * Blocks only Samsung host startup-time B2F guidance dialog data prefetch.
 */
internal object SamsungB2fGuidancePrefetchBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungB2fGuidancePrefetchBlockHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!hookState.markInstalled()) return

        try {
            var installedCount = 0
            installedCount += hookNoArgMethod(
                cl = cl,
                className = BaiduSamsungHookPoints.GUIDE_CONTEXT_COMPANION,
                methodName = BaiduSamsungHookPoints.GUIDE_REQUIRE_B2F_GUIDANCE_DIALOG_DATA_METHOD,
                logName = "GuideContext.Companion.requireB2FGuidanceDialogData",
            )
            installedCount += hookNoArgMethod(
                cl = cl,
                className = BaiduSamsungHookPoints.GUIDE_APIS_KT,
                methodName = BaiduSamsungHookPoints.GUIDE_REQUIRE_B2F_GUIDANCE_DIALOG_DATA_METHOD,
                logName = "GuideApisKt.requireB2FGuidanceDialogData",
            )

            if (installedCount == 0) {
                XposedCompat.log("[SamsungB2fGuidancePrefetchBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log(
                "[SamsungB2fGuidancePrefetchBlockHook] hooks INSTALLED: count=$installedCount",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungB2fGuidancePrefetchBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookNoArgMethod(
        cl: ClassLoader,
        className: String,
        methodName: String,
        logName: String,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(className, cl)
        if (clazz == null) {
            XposedCompat.log("[SamsungB2fGuidancePrefetchBlockHook] $className NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, methodName)
        if (method == null) {
            XposedCompat.log("[SamsungB2fGuidancePrefetchBlockHook] $className.$methodName NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[SamsungB2fGuidancePrefetchBlockHook] $logName blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isB2fGuidancePrefetchDisabled
}
