package com.xiyunmn.puredupan.hook.feature.performance

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks auto download/install decisions for selected edge dynamic plugin types only.
 */
object DynamicPluginAutoDownloadBlockHook {
    @Volatile private var hooked = false

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
        if (!tryMarkHooked()) return

        try {
            val pluginClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.DYNAMIC_PLUGIN_MODEL,
                cl,
            ) ?: run {
                XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] DynamicPlugin class NOT FOUND")
                resetHooked()
                return
            }

            var installedCount = 0
            installedCount += hookDecisionClasses(
                cl = cl,
                pluginClass = pluginClass,
                classNames = StableBaiduPanHookPoints.DYNAMIC_PLUGIN_AUTO_DOWNLOAD_DOWNLOADER_CLASSES,
                methodName = StableBaiduPanHookPoints.DYNAMIC_PLUGIN_IS_AUTO_DOWNLOAD_METHOD,
                decisionName = "autoDownload",
            )
            installedCount += hookDecisionClasses(
                cl = cl,
                pluginClass = pluginClass,
                classNames = StableBaiduPanHookPoints.DYNAMIC_PLUGIN_AUTO_INSTALL_EXECUTOR_CLASSES,
                methodName = StableBaiduPanHookPoints.DYNAMIC_PLUGIN_IS_AUTO_INSTALL_METHOD,
                decisionName = "autoInstall",
            )

            if (installedCount == 0) {
                XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] no hooks installed")
                resetHooked()
                return
            }

            XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[DynamicPluginAutoDownloadBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
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

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isDynamicPluginAutoDownloadDisabled
}
