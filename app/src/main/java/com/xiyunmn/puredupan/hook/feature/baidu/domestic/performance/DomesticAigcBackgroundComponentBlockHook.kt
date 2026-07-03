package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import java.lang.reflect.Method

/**
 * Blocks only AIGC widget background refresh and startup resource unzip routes.
 */
object DomesticAigcBackgroundComponentBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticAigcBackgroundComponentBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.AIGC_CLOUD_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log("[DomesticAigcBackgroundComponentBlockHook] AigcCloudContext Companion class NOT FOUND")
                hookState.reset()
                return
            }

            val methods = listOfNotNull(
                findNoArgMethod(clazz, BaiduDomesticHookPoints.AIGC_UPDATE_WIDGET_FROM_CACHE_METHOD),
                findStringArgMethod(clazz, BaiduDomesticHookPoints.AIGC_UPDATE_WIDGET_BY_DATA_METHOD),
                findNoArgMethod(clazz, BaiduDomesticHookPoints.AIGC_UNZIP_CLOUD_ZIP_METHOD),
            )

            if (methods.isEmpty()) {
                XposedCompat.log("[DomesticAigcBackgroundComponentBlockHook] no AIGC background methods found")
                hookState.reset()
                return
            }

            for (method in methods) {
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        XposedCompat.logD(
                            "[DomesticAigcBackgroundComponentBlockHook] ${method.name} blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }

            XposedCompat.log("[DomesticAigcBackgroundComponentBlockHook] hooks INSTALLED: count=${methods.size}")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticAigcBackgroundComponentBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun findNoArgMethod(clazz: Class<*>, methodName: String): Method? {
        return XposedCompat.findMethodOrNull(clazz, methodName).also { method ->
            if (method == null) {
                XposedCompat.log("[DomesticAigcBackgroundComponentBlockHook] $methodName NOT FOUND")
            }
        }
    }

    private fun findStringArgMethod(clazz: Class<*>, methodName: String): Method? {
        return XposedCompat.findMethodOrNull(clazz, methodName, String::class.java).also { method ->
            if (method == null) {
                XposedCompat.log("[DomesticAigcBackgroundComponentBlockHook] $methodName(String) NOT FOUND")
            }
        }
    }



    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isAigcBackgroundComponentDisabled
}
