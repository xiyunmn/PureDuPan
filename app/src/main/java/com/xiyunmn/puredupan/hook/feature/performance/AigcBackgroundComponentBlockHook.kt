package com.xiyunmn.puredupan.hook.feature.performance

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.reflect.Method

/**
 * Blocks only AIGC widget background refresh and startup resource unzip routes.
 */
object AigcBackgroundComponentBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[AigcBackgroundComponentBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.AIGC_CLOUD_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log("[AigcBackgroundComponentBlockHook] AigcCloudContext Companion class NOT FOUND")
                resetHooked()
                return
            }

            val methods = listOfNotNull(
                findNoArgMethod(clazz, StableBaiduPanHookPoints.AIGC_UPDATE_WIDGET_FROM_CACHE_METHOD),
                findStringArgMethod(clazz, StableBaiduPanHookPoints.AIGC_UPDATE_WIDGET_BY_DATA_METHOD),
                findNoArgMethod(clazz, StableBaiduPanHookPoints.AIGC_UNZIP_CLOUD_ZIP_METHOD),
            )

            if (methods.isEmpty()) {
                XposedCompat.log("[AigcBackgroundComponentBlockHook] no AIGC background methods found")
                resetHooked()
                return
            }

            for (method in methods) {
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        XposedCompat.logD(
                            "[AigcBackgroundComponentBlockHook] ${method.name} blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }

            XposedCompat.log("[AigcBackgroundComponentBlockHook] hooks INSTALLED: count=${methods.size}")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[AigcBackgroundComponentBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun findNoArgMethod(clazz: Class<*>, methodName: String): Method? {
        return XposedCompat.findMethodOrNull(clazz, methodName).also { method ->
            if (method == null) {
                XposedCompat.log("[AigcBackgroundComponentBlockHook] $methodName NOT FOUND")
            }
        }
    }

    private fun findStringArgMethod(clazz: Class<*>, methodName: String): Method? {
        return XposedCompat.findMethodOrNull(clazz, methodName, String::class.java).also { method ->
            if (method == null) {
                XposedCompat.log("[AigcBackgroundComponentBlockHook] $methodName(String) NOT FOUND")
            }
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isAigcBackgroundComponentDisabled
}
