package com.xiyunmn.puredupan.hook.feature.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

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
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ADVERTISE_SDK,
                cl,
            ) ?: run {
                XposedCompat.log("[VideoAdPreloadBlockHook] AdvertiseSDK class NOT FOUND")
                hookState.reset()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.ADVERTISE_SDK_DOWNLOAD_VIDEO_FRONT_AD_METHOD,
                Context::class.java,
            ) ?: run {
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
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isVideoAdPreloadDisabled
}
