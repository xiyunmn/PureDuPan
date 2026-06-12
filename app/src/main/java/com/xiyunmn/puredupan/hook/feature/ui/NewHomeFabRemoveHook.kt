package com.xiyunmn.puredupan.hook.feature.ui

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * 首页悬浮球逻辑层拦截 Hook。
 *
 * 不修改 UI 布局（避免 ViewBinding 崩溃），而是直接切断数据下发通道：
 * 拦截 [NewHomePageFabFragment.onOperationActivitySuccess] 回调，
 * 阻止运营数据到达渲染层，从根源上杜绝悬浮球显示。
 *
 * 受 [ConfigManager.KEY_REMOVE_HOME_FAB] 控制，默认开启。
 */
object NewHomeFabRemoveHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[NewHomeFabRemoveHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val targetClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.NEW_HOME_FAB_FRAGMENT, cl
            ) ?: run {
                XposedCompat.log("[NewHomeFabRemoveHook] NewHomePageFabFragment class NOT FOUND")
                return
            }

            val paramClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.POPUP_RESPONSE, cl
            ) ?: run {
                XposedCompat.log("[NewHomeFabRemoveHook] PopupResponse class NOT FOUND")
                return
            }

            val method = XposedCompat.findMethodOrNull(
                targetClass, "onOperationActivitySuccess", paramClass
            ) ?: run {
                XposedCompat.log("[NewHomeFabRemoveHook] onOperationActivitySuccess NOT FOUND")
                return
            }

            mod.hook(method).intercept {
                if (isEnabled()) {
                    // 拦截原方法执行，切断数据渲染链路
                    return@intercept null
                }
                it.proceed()
            }

            XposedCompat.log("[NewHomeFabRemoveHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[NewHomeFabRemoveHook] FAILED: ${e.message}")
        }
    }


    private fun isEnabled(): Boolean =
        ConfigManager.isSharePageCustomizeEnabled && ConfigManager.isHomeFabRemoved
}
