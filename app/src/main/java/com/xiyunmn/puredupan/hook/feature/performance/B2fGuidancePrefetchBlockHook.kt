package com.xiyunmn.puredupan.hook.feature.performance

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks only startup-time B2F guidance dialog data prefetch.
 */
object B2fGuidancePrefetchBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[B2fGuidancePrefetchBlockHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!hookState.markInstalled()) return

        try {
            var installedCount = 0
            installedCount += hookNoArgMethod(
                cl = cl,
                className = StableBaiduPanHookPoints.GUIDE_CONTEXT_COMPANION,
                methodName = StableBaiduPanHookPoints.GUIDE_CONTEXT_REQUIRE_B2F_GUIDANCE_DIALOG_DATA_METHOD,
                logName = "GuideContext.Companion.requireB2FGuidanceDialogData",
            )
            installedCount += hookNoArgMethod(
                cl = cl,
                className = StableBaiduPanHookPoints.GUIDE_APIS_KT,
                methodName = StableBaiduPanHookPoints.GUIDE_APIS_REQUIRE_B2F_GUIDANCE_DIALOG_DATA_METHOD,
                logName = "GuideApisKt.requireB2FGuidanceDialogData",
            )

            if (installedCount == 0) {
                XposedCompat.log("[B2fGuidancePrefetchBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[B2fGuidancePrefetchBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[B2fGuidancePrefetchBlockHook] FAILED: ${e.message}")
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
            XposedCompat.log("[B2fGuidancePrefetchBlockHook] $className NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, methodName)
        if (method == null) {
            XposedCompat.log("[B2fGuidancePrefetchBlockHook] $className.$methodName NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[B2fGuidancePrefetchBlockHook] $logName blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }



    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isB2fGuidancePrefetchDisabled
}
