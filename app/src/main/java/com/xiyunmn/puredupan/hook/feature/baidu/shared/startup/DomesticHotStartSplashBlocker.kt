package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import android.app.Activity
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupHookPoints

internal object DomesticHotStartSplashBlocker {
    internal fun hookHotStartManager13278(cl: ClassLoader, ownerTag: String): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            BaiduStartupHookPoints.DOMESTIC_HOT_START_MANAGER_13_27_8,
            cl,
        ) ?: run {
            XposedCompat.logD("[$ownerTag] 13.27.8 hot start manager class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            BaiduStartupHookPoints.DOMESTIC_HOT_START_MANAGER_ON_RESUME_13_27_8_METHOD,
            Activity::class.java,
        ) ?: run {
            XposedCompat.logD("[$ownerTag] 13.27.8 hot start onResume method NOT FOUND")
            return 0
        }
        if (method.returnType != java.lang.Boolean.TYPE) {
            XposedCompat.logD("[$ownerTag] 13.27.8 hot start onResume return type mismatch")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (HookSettings.isSplashInterstitialBlockEnabled) {
                XposedCompat.logD("[$ownerTag] 13.27.8 hot start splash blocked")
                false
            } else {
                chain.proceed()
            }
        }
        XposedCompat.log("[$ownerTag] 13.27.8 hot start manager hooked: ${clazz.name}.${method.name}")
        return 1
    }

    internal fun hookSplashAdActivityFallback(cl: ClassLoader, ownerTag: String): Int {
        val mod = XposedCompat.module ?: return 0
        val splashActivityClass = XposedCompat.findClassOrNull(
            BaiduStartupHookPoints.SPLASH_AD_ACTIVITY,
            cl,
        ) ?: run {
            XposedCompat.logD("[$ownerTag] SplashAdActivity class NOT FOUND")
            return 0
        }

        var installed = 0
        XposedCompat.findMethodOrNull(splashActivityClass, "initView")?.let { method ->
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSplashInterstitialBlockEnabled) {
                    finishSplashActivity(chain.thisObject, ownerTag, "initView")
                    null
                } else {
                    chain.proceed()
                }
            }
            installed++
        }
        XposedCompat.findMethodOrNull(
            splashActivityClass,
            "onCreate",
            Bundle::class.java,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (HookSettings.isSplashInterstitialBlockEnabled) {
                    finishSplashActivity(chain.thisObject, ownerTag, "onCreate")
                }
                result
            }
            installed++
        }
        XposedCompat.findMethodOrNull(splashActivityClass, "onResume")?.let { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (HookSettings.isSplashInterstitialBlockEnabled) {
                    finishSplashActivity(chain.thisObject, ownerTag, "onResume")
                }
                result
            }
            installed++
        }

        if (installed == 0) {
            XposedCompat.logD("[$ownerTag] SplashAdActivity fallback methods NOT FOUND")
        } else {
            XposedCompat.log("[$ownerTag] SplashAdActivity fallback hooked: count=$installed")
        }
        return installed
    }

    private fun finishSplashActivity(target: Any?, ownerTag: String, source: String) {
        val activity = target as? Activity ?: return
        XposedCompat.logD("[$ownerTag] finishing SplashAdActivity fallback from $source")
        activity.finish()
        runCatching { activity.overridePendingTransition(0, 0) }
    }
}
