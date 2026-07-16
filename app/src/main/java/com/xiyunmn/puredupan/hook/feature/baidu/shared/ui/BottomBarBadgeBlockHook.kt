package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduBottomBarHookPoints

/**
 * Blocks bottom-tab red-dot/text badges.
 *
 * Primary source: MainActivity.showOrHideNewTips().
 * Secondary source: MainActivityPresenter.drawUpdateIndicator().
 */
object BottomBarBadgeBlockHook {
    private const val MAIN_ACTIVITY_SHOW_OR_HIDE_NEW_TIPS_METHOD = "showOrHideNewTips"
    private const val MAIN_ACTIVITY_PRESENTER_DRAW_UPDATE_INDICATOR_METHOD = "drawUpdateIndicator"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[BottomBarBadgeBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0

            BaiduFeatureRuntime.currentMainActivityClassName()?.let { className ->
                XposedCompat.findClassOrNull(className, cl)?.let { clazz ->
                    val methods = clazz.declaredMethods.filter {
                        it.name == MAIN_ACTIVITY_SHOW_OR_HIDE_NEW_TIPS_METHOD
                    }
                    for (method in methods) {
                        method.isAccessible = true
                        mod.hook(method).intercept { chain ->
                            if (isEnabled()) {
                                HookUtils.getDefaultReturnValue(method.returnType)
                            } else {
                                chain.proceed()
                            }
                        }
                        installed++
                    }
                } ?: XposedCompat.log("[BottomBarBadgeBlockHook] MainActivity class NOT FOUND")
            } ?: XposedCompat.log("[BottomBarBadgeBlockHook] MainActivity host capability missing")

            BaiduFeatureRuntime.currentMainActivityPresenterClassName()?.let { className ->
                XposedCompat.findClassOrNull(className, cl)?.let { clazz ->
                    val methods = clazz.declaredMethods.filter {
                        it.name == MAIN_ACTIVITY_PRESENTER_DRAW_UPDATE_INDICATOR_METHOD ||
                            isDrawUpdateIndicatorShape(it)
                    }
                    for (method in methods) {
                        method.isAccessible = true
                        mod.hook(method).intercept { chain ->
                            if (isEnabled()) {
                                HookUtils.getDefaultReturnValue(method.returnType)
                            } else {
                                chain.proceed()
                            }
                        }
                        installed++
                    }
                } ?: XposedCompat.log("[BottomBarBadgeBlockHook] MainActivityPresenter class NOT FOUND")
            } ?: XposedCompat.log("[BottomBarBadgeBlockHook] MainActivityPresenter host capability missing")

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[BottomBarBadgeBlockHook] no hooks installed")
                return
            }

            XposedCompat.log("[BottomBarBadgeBlockHook] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[BottomBarBadgeBlockHook] FAILED: ${e.message}")
        }
    }
    private fun isEnabled(): Boolean =
        HookSettings.isBottomBarCustomEnabled && HookSettings.isBottomBarBadgeBlocked

    private fun isDrawUpdateIndicatorShape(method: java.lang.reflect.Method): Boolean {
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        return params.size == 7 &&
            params[0].name == BaiduBottomBarHookPoints.LOTTIE_RADIO_BUTTON &&
            params[1] == Boolean::class.javaPrimitiveType &&
            params[2] == Boolean::class.javaPrimitiveType &&
            params[3] == String::class.java &&
            params[4] == Int::class.javaPrimitiveType &&
            params[5] == Int::class.javaPrimitiveType &&
            params[6] == Int::class.javaPrimitiveType
    }
}
