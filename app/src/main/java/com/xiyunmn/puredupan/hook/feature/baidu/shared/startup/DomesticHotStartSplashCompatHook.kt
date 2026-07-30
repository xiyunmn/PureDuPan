package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime

internal object DomesticHotStartSplashCompatHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isSplashInterstitialBlockEnabled) return
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val lifecycleManagerClassName =
                BaiduFeatureRuntime.currentHotStartSplashLifecycleManagerClassName()
            val backgroundResumeAdStartMethodName =
                BaiduFeatureRuntime.currentHotStartSplashBackgroundResumeAdStartMethodName()
            if (lifecycleManagerClassName.isNullOrBlank() ||
                backgroundResumeAdStartMethodName.isNullOrBlank()
            ) {
                XposedCompat.logD(
                    "[DomesticHotStartSplashCompatHook] legacy hot-start capability unavailable",
                )
                return
            }
            val lifecycleManagerClass = XposedCompat.findClassOrNull(
                lifecycleManagerClassName,
                cl,
            ) ?: run {
                XposedCompat.logD(
                    "[DomesticHotStartSplashCompatHook] legacy lifecycle manager unavailable",
                )
                return
            }
            val method = XposedCompat.findMethodOrNull(
                lifecycleManagerClass,
                backgroundResumeAdStartMethodName,
                java.lang.Long.TYPE,
            ) ?: run {
                XposedCompat.logD(
                    "[DomesticHotStartSplashCompatHook] legacy backgroundResumeAdStart method unavailable",
                )
                return
            }

            mod.hook(method).intercept { chain ->
                if (HookSettings.isSplashInterstitialBlockEnabled) {
                    XposedCompat.logD("[DomesticHotStartSplashCompatHook] hot resume splash blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
            XposedCompat.log("[DomesticHotStartSplashCompatHook] SplashLifecycleManager backgroundResumeAdStart hooked")
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[DomesticHotStartSplashCompatHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
