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
        // Each capability owns its installation state and can retry after DexKit warm-up.
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
