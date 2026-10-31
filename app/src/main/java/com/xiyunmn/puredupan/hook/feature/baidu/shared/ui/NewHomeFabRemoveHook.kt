package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime

/** 拦截首页及共享页的运营数据回调，阻止创建广告悬浮窗；由弹窗屏蔽总开关和子项共同控制。 */
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
                    // 阻止运营数据进入渲染流程。
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
