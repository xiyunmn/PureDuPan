package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

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
            val clazz = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.ADVERTISE_SDK,
                cl,
            ) ?: run {
                XposedCompat.log("[SamsungVideoAdPreloadBlockHook] AdvertiseSDK class NOT FOUND")
                hookState.reset()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                BaiduSamsungHookPoints.ADVERTISE_SDK_DOWNLOAD_VIDEO_FRONT_AD_METHOD,
                Context::class.java,
            ) ?: run {
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
