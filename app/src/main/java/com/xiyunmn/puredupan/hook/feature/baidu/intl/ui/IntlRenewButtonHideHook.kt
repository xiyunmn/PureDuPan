package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAboutMeHookPoints

/** 隐藏我的页工具栏中的升级、解锁入口。 */
internal object IntlRenewButtonHideHook {
    private const val TAG = "IntlRenewButtonHideHook"
    private val hookState = HookState()

    fun hook(cl: ClassLoader) {
        if (!isEnabled() || hookState.isInstalled()) return
        val mod = XposedCompat.module ?: return
        val newPage = XposedCompat.findMethodOrNull(
            BaiduAboutMeHookPoints.NEW_ABOUT_ME_ACTIVITY, cl, "onCreate", Bundle::class.java,
        )
        val centerConfig = XposedCompat.findClassOrNull(BaiduAboutMeHookPoints.CENTER_CONFIG, cl)
        val standardPage = centerConfig?.let {
            XposedCompat.findMethodOrNull(
                BaiduAboutMeHookPoints.ABOUT_ME_ACTIVITY, cl, "setTopButtonText", it,
            )
        }
        val methods = listOfNotNull(newPage, standardPage).filter { it.returnType == Void.TYPE }
        if (methods.isEmpty()) return
        if (!hookState.markInstalled()) return
        try {
            methods.forEach { method ->
                mod.hook(method).setId("IntlRenewButtonHideHook:hook:method").intercept { chain ->
                    val result = chain.proceed()
                    if (isEnabled()) {
                        val activity = chain.thisObject as? Activity
                        if (activity != null) {
                            val id = activity.resources.getIdentifier("tv_toolbar_vip_entrance", "id", activity.packageName)
                            if (id != 0) activity.findViewById<View>(id)?.visibility = View.GONE
                        }
                    }
                    result
                }
                XposedCompat.logD("[$TAG] hook INSTALLED: ${method.declaringClass.simpleName}.${method.name}")
            }
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.logW("[$TAG] hook failed: ${e.message}")
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isMyPageCustomizeEnabled && HookSettings.isRenewButtonHidden
}
