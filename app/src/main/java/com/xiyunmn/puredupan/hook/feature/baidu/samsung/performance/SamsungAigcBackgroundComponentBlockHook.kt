package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints
import java.lang.reflect.Method

/**
 * Blocks only Samsung host AIGC widget background refresh and startup resource unzip routes.
 */
internal object SamsungAigcBackgroundComponentBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungAigcBackgroundComponentBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.AIGC_CLOUD_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log(
                    "[SamsungAigcBackgroundComponentBlockHook] AigcCloudContext Companion NOT FOUND",
                )
                hookState.reset()
                return
            }

            val methods = listOfNotNull(
                findNoArgMethod(clazz, BaiduSamsungHookPoints.AIGC_UPDATE_WIDGET_FROM_CACHE_METHOD),
                findStringArgMethod(clazz, BaiduSamsungHookPoints.AIGC_UPDATE_WIDGET_BY_DATA_METHOD),
                findNoArgMethod(clazz, BaiduSamsungHookPoints.AIGC_UNZIP_CLOUD_ZIP_METHOD),
            )

            if (methods.isEmpty()) {
                XposedCompat.log("[SamsungAigcBackgroundComponentBlockHook] no AIGC background methods found")
                hookState.reset()
                return
            }

            for (method in methods) {
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        XposedCompat.logD(
                            "[SamsungAigcBackgroundComponentBlockHook] ${method.name} blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }

            XposedCompat.log(
                "[SamsungAigcBackgroundComponentBlockHook] hooks INSTALLED: count=${methods.size}",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungAigcBackgroundComponentBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun findNoArgMethod(clazz: Class<*>, methodName: String): Method? {
        return XposedCompat.findMethodOrNull(clazz, methodName).also { method ->
            if (method == null) {
                XposedCompat.log("[SamsungAigcBackgroundComponentBlockHook] $methodName NOT FOUND")
            }
        }
    }

    private fun findStringArgMethod(clazz: Class<*>, methodName: String): Method? {
        return XposedCompat.findMethodOrNull(clazz, methodName, String::class.java).also { method ->
            if (method == null) {
                XposedCompat.log("[SamsungAigcBackgroundComponentBlockHook] $methodName(String) NOT FOUND")
            }
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isAigcBackgroundComponentDisabled
}
