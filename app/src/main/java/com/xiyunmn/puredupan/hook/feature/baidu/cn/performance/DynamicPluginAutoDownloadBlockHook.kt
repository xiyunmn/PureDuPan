package com.xiyunmn.puredupan.hook.feature.baidu.cn.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticDynamicPluginAutoDecisionDexKitResolver
import com.xiyunmn.puredupan.hook.symbols.baidu.cn.BaiduCnHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks auto download/install decisions for selected edge dynamic plugin types only.
 */
object DynamicPluginAutoDownloadBlockHook {
    private val hookState = HookState()

    private val blockedPluginTypes = setOf(
        24, // OCR_SCAN_MODEL_V5
        32, // OCR_ENHANCE_MODEL_V5
        33, // OCR_SO_SDK_V5
        34, // IMAGE_TO_OFFICE
        36, // SHOUBAI_IMAGE_BODY_IDENTIFY
        37, // IMAGE_RECOG_SDK
        38, // FACE_DETECT
    )

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val pluginClass = XposedCompat.findClassOrNull(
                BaiduCnHookPoints.DYNAMIC_PLUGIN_MODEL,
                cl,
            ) ?: run {
                XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] DynamicPlugin class NOT FOUND")
                hookState.reset()
                return
            }

            var installedCount = 0
            installedCount += DomesticDynamicPluginAutoDecisionDexKitResolver.hookResolvedDecisionFactories(
                cl = cl,
                blockedPluginTypes = blockedPluginTypes,
                logTag = "DynamicPluginAutoDownloadBlockHook",
                isEnabled = ::isEnabled,
            )
            if (installedCount == 0) {
                installedCount += hookDecisionClasses(
                    cl = cl,
                    pluginClass = pluginClass,
                    classNames = BaiduCnHookPoints.DYNAMIC_PLUGIN_AUTO_DOWNLOAD_DOWNLOADER_CLASSES,
                    methodName = BaiduCnHookPoints.DYNAMIC_PLUGIN_IS_AUTO_DOWNLOAD_METHOD,
                    decisionName = "autoDownload",
                )
                installedCount += hookDecisionClasses(
                    cl = cl,
                    pluginClass = pluginClass,
                    classNames = BaiduCnHookPoints.DYNAMIC_PLUGIN_AUTO_INSTALL_EXECUTOR_CLASSES,
                    methodName = BaiduCnHookPoints.DYNAMIC_PLUGIN_IS_AUTO_INSTALL_METHOD,
                    decisionName = "autoInstall",
                )
            }

            if (installedCount == 0) {
                XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookDecisionClasses(
        cl: ClassLoader,
        pluginClass: Class<*>,
        classNames: List<String>,
        methodName: String,
        decisionName: String,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        var installedCount = 0
        for (className in classNames) {
            val clazz = XposedCompat.findClassOrNull(className, cl)
            if (clazz == null) {
                XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] $className NOT FOUND")
                continue
            }
            val method = XposedCompat.findMethodOrNull(clazz, methodName, pluginClass)
            if (method == null) {
                XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] $className.$methodName NOT FOUND")
                continue
            }
            mod.hook(method).intercept { chain ->
                val plugin = chain.args.firstOrNull()
                if (
                    isEnabled() &&
                    shouldBlockPlugin(plugin)
                ) {
                    XposedCompat.logD(
                        "[DynamicPluginAutoDownloadBlockHook] $decisionName blocked: " +
                            "type=${pluginTypeOf(plugin)}, id=${pluginIdOf(plugin)}",
                    )
                    false
                } else {
                    chain.proceed()
                }
            }
            installedCount += 1
        }
        return installedCount
    }

    private fun shouldBlockPlugin(plugin: Any?): Boolean {
        val type = pluginTypeOf(plugin) ?: return false
        return type in blockedPluginTypes
    }

    private fun pluginTypeOf(plugin: Any?): Int? {
        if (plugin == null) return null
        return runCatching {
            XposedCompat.getObjectField(plugin, "type") as? Int
        }.getOrNull()
    }

    private fun pluginIdOf(plugin: Any?): String? {
        if (plugin == null) return null
        return runCatching {
            XposedCompat.getObjectField(plugin, "id") as? String
        }.getOrNull()
    }



    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isDynamicPluginAutoDownloadDisabled
}
