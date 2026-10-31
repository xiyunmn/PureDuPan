package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import java.lang.reflect.Method

/** 在顶部按钮渲染后，按资源名隐藏续费入口。 */
internal object DomesticRenewButtonHideHook {
    private const val TAG = "DomesticRenewButtonHideHook"
    private const val SET_TOP_BUTTON_TEXT_METHOD = "setTopButtonText"
    private const val VIP_ENTRANCE_ID_NAME = "tv_toolbar_vip_entrance"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val aboutMeActivityClassName = BaiduFeatureRuntime.currentAboutMeActivityClassName()
                ?: run {
                    XposedCompat.log("[$TAG] AboutMeActivity host capability missing")
                    hookState.reset()
                    return
                }
            val activityClass = XposedCompat.findClassOrNull(
                aboutMeActivityClassName,
                cl,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] AboutMeActivity class NOT FOUND")
                return
            }

            val method = findSetTopButtonTextMethod(activityClass) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] AboutMeActivity.setTopButtonText NOT FOUND")
                return
            }

            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (isEnabled()) {
                    hideVipEntrance(chain.thisObject as? Activity)
                }
                result
            }

            XposedCompat.log("[$TAG] hook INSTALLED: AboutMeActivity.setTopButtonText")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[$TAG] FAILED: ${e.message}")
        }
    }

    private fun findSetTopButtonTextMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == SET_TOP_BUTTON_TEXT_METHOD &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1
        }?.apply { isAccessible = true }
    }

    private fun hideVipEntrance(activity: Activity?) {
        if (activity == null) return
        val resources = activity.resources ?: return
        val id = resources.getIdentifier(VIP_ENTRANCE_ID_NAME, "id", activity.packageName)
        if (id == 0) {
            XposedCompat.logD("[$TAG] $VIP_ENTRANCE_ID_NAME resource id not found")
            return
        }
        val view = activity.findViewById<View>(id) ?: return
        if (view !is TextView) {
            XposedCompat.logD("[$TAG] vip entrance is not a TextView: ${view.javaClass.name}")
            return
        }
        if (view.visibility != View.GONE) {
            view.visibility = View.GONE
            XposedCompat.logD("[$TAG] renew button hidden via render entry")
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isMyPageCustomizeEnabled && HookSettings.isRenewButtonHidden
}
