package com.xiyunmn.puredupan.hook.feature.ui

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * 底栏 AI Tab 替换为会员 Hook。
 *
 * 受 [ConfigManager.KEY_REPLACE_BOTTOM_AI] 控制，默认开启。
 */
object BottomAiTabReplaceHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[BottomAiTabReplaceHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.AI_CLOUD_TAB_AMIS_KT, cl
            ) ?: run {
                XposedCompat.log("[BottomAiTabReplaceHook] AiCloudTabAmisKt class NOT FOUND")
                return
            }
            val method = XposedCompat.findMethodOrNull(clazz, "getAiCloudTabMode")
                ?: run {
                    XposedCompat.log("[BottomAiTabReplaceHook] getAiCloudTabMode NOT FOUND")
                    return
            }
            mod.hook(method).intercept {
                if (isEnabled()) 0L else it.proceed()
            }
            XposedCompat.log("[BottomAiTabReplaceHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[BottomAiTabReplaceHook] FAILED: ${e.message}")
        }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isBottomBarCustomEnabled && ConfigManager.isBottomAiReplaced
}
