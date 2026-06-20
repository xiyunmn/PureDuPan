package com.xiyunmn.puredupan.hook.feature.baidu.samsung.startup

import android.app.Activity
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

internal object SamsungSplashAdBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isSplashInterstitialBlockEnabled) {
            XposedCompat.log("[SamsungSplashAdBlockHook] skipped: config disabled")
            return
        }
        if (!hookState.markInstalled()) return

        var installed = 0
        try {
            installed += hookColdSplash(cl)
            installed += hookHotSplash(cl)
        } catch (t: Throwable) {
            XposedCompat.log("[SamsungSplashAdBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }

        if (installed == 0) {
            hookState.reset()
            XposedCompat.log("[SamsungSplashAdBlockHook] no hooks installed")
        } else {
            XposedCompat.log("[SamsungSplashAdBlockHook] hooks INSTALLED: count=$installed")
        }
    }

    private fun hookColdSplash(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val splashManagerClass = XposedCompat.findClassOrNull(
            BaiduSamsungHookPoints.SPLASH_MANAGER,
            cl,
        ) ?: run {
            XposedCompat.log("[SamsungSplashAdBlockHook] SplashManager class NOT FOUND")
            return 0
        }
        val fragmentActivityClass = XposedCompat.findClassOrNull(
            "androidx.fragment.app.FragmentActivity",
            cl,
        ) ?: run {
            XposedCompat.log("[SamsungSplashAdBlockHook] FragmentActivity class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            splashManagerClass,
            "isShowSplash",
            fragmentActivityClass,
        ) ?: run {
            XposedCompat.log("[SamsungSplashAdBlockHook] SplashManager.isShowSplash NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (HookSettings.isSplashInterstitialBlockEnabled) {
                XposedCompat.logD("[SamsungSplashAdBlockHook] cold splash blocked")
                false
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookHotSplash(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val hotStartClass = XposedCompat.findClassOrNull(
            BaiduSamsungHookPoints.ADVERTISE_HOT_START_MANAGER,
            cl,
        ) ?: run {
            XposedCompat.log("[SamsungSplashAdBlockHook] AdvertiseHotStartManager class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            hotStartClass,
            "onResume",
            Activity::class.java,
        ) ?: run {
            XposedCompat.log("[SamsungSplashAdBlockHook] AdvertiseHotStartManager.onResume NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (HookSettings.isSplashInterstitialBlockEnabled) {
                XposedCompat.logD("[SamsungSplashAdBlockHook] hot splash blocked")
                false
            } else {
                chain.proceed()
            }
        }
        return 1
    }
}
