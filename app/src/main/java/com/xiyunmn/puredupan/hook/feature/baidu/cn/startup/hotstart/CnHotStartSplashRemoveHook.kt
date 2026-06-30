package com.xiyunmn.puredupan.hook.feature.baidu.cn.startup.hotstart

import android.app.Activity
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.symbols.baidu.cn.BaiduCnHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.DomesticHotStartSplashBlocker

internal object CnHotStartSplashRemoveHook {
    private const val TAG = "CnHotStartSplashRemoveHook"
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isSplashInterstitialBlockEnabled) return
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        var installed = 0
        try {
            installed += hookLegacyHotStartManager(cl)
            installed += DomesticHotStartSplashBlocker.hookHotStartManager13278(cl, TAG)
            installed += DomesticHotStartSplashBlocker.hookSplashAdActivityFallback(cl, TAG)
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[$TAG] install FAILED: ${t.message}")
            XposedCompat.log(t)
            return
        }

        if (installed == 0) {
            hookState.reset()
            XposedCompat.log("[$TAG] no hooks installed")
        } else {
            XposedCompat.log("[$TAG] hooks INSTALLED: count=$installed")
        }
    }

    private fun hookLegacyHotStartManager(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val hotStartClass = XposedCompat.findClassOrNull(
            BaiduCnHookPoints.ADVERTISE_HOT_START_MANAGER,
            cl,
        ) ?: run {
            XposedCompat.logD("[$TAG] legacy AdvertiseHotStartManager class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            hotStartClass,
            "onResume",
            Activity::class.java,
        ) ?: run {
            XposedCompat.logD("[$TAG] legacy AdvertiseHotStartManager.onResume NOT FOUND")
            return 0
        }

        mod.hook(method).intercept {
            if (HookSettings.isSplashInterstitialBlockEnabled) false else it.proceed()
        }
        XposedCompat.log("[$TAG] legacy AdvertiseHotStartManager.onResume hooked")
        return 1
    }
}
