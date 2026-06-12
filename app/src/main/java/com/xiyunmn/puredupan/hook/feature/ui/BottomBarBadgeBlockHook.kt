package com.xiyunmn.puredupan.hook.feature.ui

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks bottom-tab red-dot/text badges.
 *
 * Primary source: MainActivity.showOrHideNewTips().
 * Secondary source: MainActivityPresenter.drawUpdateIndicator().
 */
object BottomBarBadgeBlockHook {
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

            XposedCompat.findClassOrNull(StableBaiduPanHookPoints.MAIN_ACTIVITY, cl)?.let { clazz ->
                val methods = clazz.declaredMethods.filter {
                    it.name == StableBaiduPanHookPoints.MAIN_ACTIVITY_SHOW_OR_HIDE_NEW_TIPS_METHOD
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

            XposedCompat.findClassOrNull(StableBaiduPanHookPoints.MAIN_ACTIVITY_PRESENTER, cl)?.let { clazz ->
                val methods = clazz.declaredMethods.filter {
                    it.name == StableBaiduPanHookPoints.MAIN_ACTIVITY_PRESENTER_DRAW_UPDATE_INDICATOR_METHOD
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

    private fun HookUtils.getDefaultReturnValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }


    private fun isEnabled(): Boolean =
        ConfigManager.isBottomBarCustomEnabled && ConfigManager.isBottomBarBadgeBlocked
}
