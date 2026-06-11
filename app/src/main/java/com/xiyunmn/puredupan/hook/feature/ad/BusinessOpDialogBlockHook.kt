package com.xiyunmn.puredupan.hook.feature.ad

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks in-app business operation dialogs.
 *
 * This is intentionally separate from SplashInterstitialBlockHook. Both this hook and
 * LuckyCouponBlockHook are controlled by ConfigManager.isInAppDialogBlocked.
 */
object BusinessOpDialogBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isInAppDialogBlocked) {
            XposedCompat.log("[BusinessOpDialogBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.BUSINESS_OP_DIALOG,
                cl,
            ) ?: run {
                XposedCompat.log("[BusinessOpDialogBlockHook] BusinessOPDialog class NOT FOUND")
                return
            }

            var installed = 0
            for (method in clazz.declaredMethods) {
                if (method.name != StableBaiduPanHookPoints.BUSINESS_OP_DIALOG_SHOW_DIALOG_METHOD) {
                    continue
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (ConfigManager.isInAppDialogBlocked) {
                        defaultReturnValue(method.returnType)
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }

            for (method in clazz.declaredMethods) {
                if (method.name != StableBaiduPanHookPoints.BUSINESS_OP_DIALOG_ON_CREATE_VIEW_METHOD) {
                    continue
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (ConfigManager.isInAppDialogBlocked) {
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[BusinessOpDialogBlockHook] BusinessOPDialog methods NOT FOUND")
                return
            }

            XposedCompat.log("[BusinessOpDialogBlockHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[BusinessOpDialogBlockHook] FAILED: ${t.message}")
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
}
