package com.xiyunmn.puredupan.hook.feature.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks only foreground-resume video front ad material preloading.
 */
object VideoAdPreloadBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[VideoAdPreloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ADVERTISE_SDK,
                cl,
            ) ?: run {
                XposedCompat.log("[VideoAdPreloadBlockHook] AdvertiseSDK class NOT FOUND")
                resetHooked()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.ADVERTISE_SDK_DOWNLOAD_VIDEO_FRONT_AD_METHOD,
                Context::class.java,
            ) ?: run {
                XposedCompat.log("[VideoAdPreloadBlockHook] downloadVideoFrontAd(Context) NOT FOUND")
                resetHooked()
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
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[VideoAdPreloadBlockHook] FAILED: ${t.message}")
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
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isVideoAdPreloadDisabled
}
