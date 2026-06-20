package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

/**
 * Blocks only Samsung host DataPack module socket registration.
 */
internal object SamsungDatapackSocketRegisterBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungDatapackSocketRegisterBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.DATAPACK_CONTEXT_COMPANION,
                cl,
            ) ?: run {
                XposedCompat.log(
                    "[SamsungDatapackSocketRegisterBlockHook] DatapackContext Companion NOT FOUND",
                )
                hookState.reset()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                BaiduSamsungHookPoints.DATAPACK_REGISTER_SOCKET_METHOD,
            ) ?: run {
                XposedCompat.log("[SamsungDatapackSocketRegisterBlockHook] registerSocket NOT FOUND")
                hookState.reset()
                return
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[SamsungDatapackSocketRegisterBlockHook] registerSocket blocked")
                    null
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log("[SamsungDatapackSocketRegisterBlockHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungDatapackSocketRegisterBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isDatapackSocketRegisterDisabled
}
