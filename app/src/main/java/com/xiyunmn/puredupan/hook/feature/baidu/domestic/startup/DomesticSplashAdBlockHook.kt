package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.DomesticHotStartSplashBlocker

internal object DomesticSplashAdBlockHook {
    private const val TAG = "DomesticSplashAdBlockHook"

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isSplashInterstitialBlockEnabled) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        // 各功能独立记录安装状态，失败后可单独重试。
        install("cold startup") { DomesticColdStartNoAdHook.hook(cl) }
        install("hot startup") { DomesticHotStartSplashBlocker.hookHotStartManager(cl, TAG) }
        install("ad activity fallback") { DomesticHotStartSplashBlocker.hookSplashAdActivityFallback(cl, TAG) }
    }

    private inline fun install(capability: String, block: () -> Unit) {
        runCatching(block).onFailure {
            XposedCompat.logW("[$TAG] $capability install FAILED: ${it.message}")
        }
    }
}
