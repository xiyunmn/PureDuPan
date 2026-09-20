package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime

/**
 * 首页及共享页广告悬浮窗的数据层拦截，共享页子类沿用此回调。
 *
 * 不修改 UI 布局（避免 ViewBinding 崩溃），而是直接切断数据下发通道：
 * 拦截 [NewHomePageFabFragment.onOperationActivitySuccess] 回调，
 * 阻止运营数据到达渲染层，从根源上杜绝悬浮球显示。
 *
 * 受“弹窗屏蔽”总开关及广告悬浮窗子项控制，由 [HookSettings.isHomeFabRemoved] 统一读取。
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
            val newHomeFabFragmentClassName = BaiduFeatureRuntime.currentNewHomeFabFragmentClassName()
                ?: run {
                    XposedCompat.log("[NewHomeFabRemoveHook] NewHomePageFabFragment host capability missing")
                    hookState.reset()
                    return
                }
            val popupResponseClassName = BaiduFeatureRuntime.currentPopupResponseClassName()
                ?: run {
                    XposedCompat.log("[NewHomeFabRemoveHook] PopupResponse host capability missing")
                    hookState.reset()
                    return
                }
            val targetClass = XposedCompat.findClassOrNull(
                newHomeFabFragmentClassName, cl
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[NewHomeFabRemoveHook] NewHomePageFabFragment class NOT FOUND")
                return
            }

            val paramClass = XposedCompat.findClassOrNull(
                popupResponseClassName, cl
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[NewHomeFabRemoveHook] PopupResponse class NOT FOUND")
                return
            }

            val method = XposedCompat.findMethodOrNull(
                targetClass, "onOperationActivitySuccess", paramClass
            ) ?: run {
                hookState.reset()
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
        HookSettings.isHomeFabRemoved
}
