package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.DomesticHotStartSplashBlocker

internal object DomesticSplashAdBlockHook {
    private const val TAG = "DomesticSplashAdBlockHook"
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isSplashInterstitialBlockEnabled) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!hookState.markInstalled()) return

        var installed = 0
        var hotStartInstalled = 0
        try {
            installed += DomesticHotStartSplashBlocker.hookColdStartSplashManager(cl, TAG)
            hotStartInstalled = DomesticHotStartSplashBlocker.hookHotStartManager(cl, TAG)
            installed += hotStartInstalled
            installed += DomesticHotStartSplashBlocker.hookSplashAdActivityFallback(cl, TAG)
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[$TAG] install FAILED: ${t.message}")
            XposedCompat.log(t)
            return
        }

        if (hotStartInstalled == 0) {
            hookState.reset()
        }
        if (installed == 0) {
            hookState.reset()
            XposedCompat.log("[$TAG] no hooks installed")
        } else {
            XposedCompat.log("[$TAG] hooks INSTALLED: count=$installed")
        }
    }
}
