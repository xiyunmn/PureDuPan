package com.xiyunmn.puredupan.hook.feature.performance

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks only the startup async icon resource background download.
 */
object IconResourceDownloadBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IconResourceDownloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ICON_DOWNLOAD_MANAGER,
                cl,
            ) ?: run {
                XposedCompat.log("[IconResourceDownloadBlockHook] IconDownloadManager class NOT FOUND")
                resetHooked()
                return
            }

            val function2Class = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.KOTLIN_FUNCTION2,
                cl,
            ) ?: run {
                XposedCompat.log("[IconResourceDownloadBlockHook] kotlin Function2 class NOT FOUND")
                resetHooked()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.ICON_DOWNLOAD_MANAGER_START_DOWNLOAD_METHOD,
                function2Class,
                function2Class,
            ) ?: run {
                XposedCompat.log("[IconResourceDownloadBlockHook] startDownload(Function2, Function2) NOT FOUND")
                resetHooked()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[IconResourceDownloadBlockHook] startDownload blocked")
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[IconResourceDownloadBlockHook] hook INSTALLED")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[IconResourceDownloadBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isIconResourceDownloadDisabled
}
