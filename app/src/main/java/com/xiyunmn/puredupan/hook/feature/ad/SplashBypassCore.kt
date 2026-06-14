package com.xiyunmn.puredupan.hook.feature.ad

import android.app.Activity
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat


object SplashBypassCore {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isSplashInterstitialBlockEnabled) {
            XposedCompat.log("[SplashBypassCore] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        var installed = 0
        try {
            // 核心: 重定向到无广告分支
            installed += hookSetupSplashAdViewSkip(cl)
        } catch (e: Exception) {
            XposedCompat.log("[SplashBypassCore] Installation error: ${e.message}")
            XposedCompat.log(e)
        }

        if (installed == 0) {
            hookState.reset()
            XposedCompat.log("[SplashBypassCore] No hooks installed, reset state")
        } else {
            XposedCompat.log("[SplashBypassCore] hooks INSTALLED: count=$installed")
        }
    }


    private fun hookSetupSplashAdViewSkip(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            StableBaiduPanHookPoints.MAIN_ACTIVITY,
            cl,
        )
        if (clazz == null) {
            XposedCompat.log("[SplashBypassCore] MainActivity class NOT FOUND")
            return 0
        }

        val methods = clazz.declaredMethods.filter { it.name == "setupSplashAdView" }
        if (methods.isEmpty()) {
            XposedCompat.log("[SplashBypassCore] setupSplashAdView method NOT FOUND")
            return 0
        }

        methods.forEach { method ->
            mod.hook(method).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    try {
                        // 直接调用宿主的无广告分支
                        XposedCompat.callMethod(activity, "skipSplashAd")
                        XposedCompat.logD("[SplashBypassCore] setupSplashAdView → skipSplashAd")
                        return@intercept null
                    } catch (e: Throwable) {
                        XposedCompat.log("[SplashBypassCore] skipSplashAd call failed: ${e.message}")
                        XposedCompat.log(e)
                        return@intercept chain.proceed()
                    }
                }
                chain.proceed()
            }
        }

        XposedCompat.log("[SplashBypassCore] setupSplashAdView hook INSTALLED")
        return methods.size
    }
}
