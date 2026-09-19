package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import android.app.Activity
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method

/** Keep the complete native no-ad path, including routing, guides and delayed initialization. */
internal object DomesticColdStartNoAdHook {
    private const val TAG = "DomesticColdStartNoAdHook"
    private val policy = ColdStartAdPolicy()
    private val installed = mutableSetOf<Method>()
    private val coldDecision = ThreadLocal<Activity?>()

    @Synchronized
    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val mainClass = BaiduFeatureRuntime.currentMainActivityClassName() ?: return
        val entries = listOf(BaiduDomesticHookPoints.NAVIGATE_ACTIVITY, mainClass).map { name ->
            XposedCompat.findMethodOrNull(name, cl, "onCreate", Bundle::class.java) ?: return
        }
        // Install these even if resolution is deferred. A late warm-up cannot switch an
        // already-started process from parallel to serial halfway through its callbacks.
        entries.forEach { method ->
            if (method !in installed) {
                mod.hook(method).intercept { chain ->
                    useNativeNoAdMode()
                    chain.proceed()
                }
                installed += method
            }
        }

        val mode = DomesticColdStartModeDexKitResolver.resolve(cl) ?: return
        val gate = DomesticColdStartSplashDexKitResolver.resolve(cl) ?: return
        val entry = XposedCompat.findMethodOrNull(
            BaiduDomesticHookPoints.NAVIGATE_ACTIVITY, cl, BaiduDomesticHookPoints.NAVIGATE_SHOW_FLASH_SCREEN,
        ) ?: return

        if (entry !in installed) {
            mod.hook(entry).intercept { chain ->
                val previous = coldDecision.get()
                coldDecision.set(chain.thisObject as? Activity)
                try {
                    chain.proceed()
                } finally {
                    if (previous == null) coldDecision.remove() else coldDecision.set(previous)
                }
            }
            installed += entry
        }
        if (gate !in installed) {
            mod.hook(gate).intercept { chain ->
                val activity = coldDecision.get()
                if (activity != null && chain.args.firstOrNull() === activity && useNativeNoAdMode()) {
                    false
                } else {
                    chain.proceed()
                }
            }
            installed += gate
        }
        if (mode !in installed) {
            mod.hook(mode).intercept { chain ->
                if (useNativeNoAdMode()) false else chain.proceed()
            }
            installed += mode
        }
        // Publish only after both decisions and their scope are installed successfully.
        policy.markReady()
        XposedCompat.log("[$TAG] hooks INSTALLED: native non-parallel mode + scoped cold-ad gate")
    }

    private fun useNativeNoAdMode(): Boolean = policy.select(HookSettings.isSplashInterstitialBlockEnabled)
}
