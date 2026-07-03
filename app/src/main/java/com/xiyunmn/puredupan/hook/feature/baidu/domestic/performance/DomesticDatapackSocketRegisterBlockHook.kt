package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks only the DataPack module socket registration.
 */
object DomesticDatapackSocketRegisterBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticDatapackSocketRegisterBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.DATAPACK_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log("[DomesticDatapackSocketRegisterBlockHook] DatapackContext Companion class NOT FOUND")
                hookState.reset()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                BaiduDomesticHookPoints.DATAPACK_REGISTER_SOCKET_METHOD,
            ) ?: run {
                XposedCompat.log("[DomesticDatapackSocketRegisterBlockHook] registerSocket NOT FOUND")
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[DomesticDatapackSocketRegisterBlockHook] registerSocket blocked")
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[DomesticDatapackSocketRegisterBlockHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticDatapackSocketRegisterBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }



    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isDatapackSocketRegisterDisabled
}
