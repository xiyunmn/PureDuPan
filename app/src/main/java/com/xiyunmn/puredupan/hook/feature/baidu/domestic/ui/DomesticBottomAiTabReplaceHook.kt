package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * 底栏 AI Tab 替换为会员 Hook。
 *
 * 受 [HookSettings.isBottomAiReplaced] 控制，默认开启。
 */
internal object DomesticBottomAiTabReplaceHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticBottomAiTabReplaceHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = resolveAiCloudTabModeMethod(cl) ?: run {
                XposedCompat.log("[DomesticBottomAiTabReplaceHook] getAiCloudTabMode equivalent NOT FOUND")
                hookState.reset()
                return
            }
            mod.hook(method).intercept {
                if (isEnabled()) 0L else it.proceed()
            }
            XposedCompat.log(
                "[DomesticBottomAiTabReplaceHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticBottomAiTabReplaceHook] FAILED: ${e.message}")
        }
    }

    private fun resolveAiCloudTabModeMethod(cl: ClassLoader) =
        BottomAiTabDexKitResolver.resolve(cl)

    private fun isEnabled(): Boolean =
        HookSettings.isBottomBarCustomEnabled && HookSettings.isBottomAiReplaced
}
