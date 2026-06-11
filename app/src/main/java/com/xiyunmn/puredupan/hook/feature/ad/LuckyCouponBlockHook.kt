package com.xiyunmn.puredupan.hook.feature.ad

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.reflect.Method

/**
 * Blocks the lucky coupon package dialog.
 *
 * ReceiveCouponDialogV3 may inherit android.app.Dialog.show() instead of declaring its own show().
 * If so, this hook attaches to the inherited show method and filters by targetClass.isInstance().
 */
object LuckyCouponBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isInAppDialogBlocked) {
            XposedCompat.log("[LuckyCouponBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val targetClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.RECEIVE_COUPON_DIALOG_V3,
                cl,
            ) ?: run {
                XposedCompat.log("[LuckyCouponBlockHook] ReceiveCouponDialogV3 class NOT FOUND")
                return
            }

            val method = findNoArgMethodInHierarchy(
                targetClass,
                StableBaiduPanHookPoints.DIALOG_SHOW_METHOD,
            ) ?: run {
                resetHooked()
                XposedCompat.log("[LuckyCouponBlockHook] show method NOT FOUND")
                return
            }

            mod.hook(method).intercept { chain ->
                if (
                    ConfigManager.isInAppDialogBlocked &&
                    targetClass.isInstance(chain.thisObject)
                ) {
                    XposedCompat.logD("[LuckyCouponBlockHook] ReceiveCouponDialogV3.show blocked")
                    return@intercept defaultReturnValue(method.returnType)
                }
                chain.proceed()
            }

            XposedCompat.log(
                "[LuckyCouponBlockHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.${method.name}"
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[LuckyCouponBlockHook] FAILED: ${t.message}")
        }
    }

    private fun findNoArgMethodInHierarchy(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(name).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
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
