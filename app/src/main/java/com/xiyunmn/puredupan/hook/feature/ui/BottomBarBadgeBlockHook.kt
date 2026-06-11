package com.xiyunmn.puredupan.hook.feature.ui

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks bottom-tab red-dot/text badges.
 *
 * Primary source: MainActivity.showOrHideNewTips().
 * Secondary source: MainActivityPresenter.drawUpdateIndicator().
 */
object BottomBarBadgeBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[BottomBarBadgeBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

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
                            defaultReturnValue(method.returnType)
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
                            defaultReturnValue(method.returnType)
                        } else {
                            chain.proceed()
                        }
                    }
                    installed++
                }
            } ?: XposedCompat.log("[BottomBarBadgeBlockHook] MainActivityPresenter class NOT FOUND")

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[BottomBarBadgeBlockHook] no hooks installed")
                return
            }

            XposedCompat.log("[BottomBarBadgeBlockHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[BottomBarBadgeBlockHook] FAILED: ${t.message}")
        }
    }

    private fun defaultReturnValue(type: Class<*>): Any? {
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

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isBottomBarCustomEnabled && ConfigManager.isBottomBarBadgeBlocked
}
