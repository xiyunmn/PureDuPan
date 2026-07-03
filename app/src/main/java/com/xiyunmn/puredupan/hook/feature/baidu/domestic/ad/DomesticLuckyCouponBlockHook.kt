package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method

/**
 * Blocks the lucky coupon package dialog.
 *
 * ReceiveCouponDialogV3 may inherit android.app.Dialog.show() instead of declaring its own show().
 * If so, this hook attaches to the inherited show method and filters by targetClass.isInstance().
 */
internal object DomesticLuckyCouponBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isInAppDialogBlocked) {
            XposedCompat.log("[DomesticLuckyCouponBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val targetClass = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.RECEIVE_COUPON_DIALOG_V3,
                cl,
            ) ?: run {
                XposedCompat.log("[DomesticLuckyCouponBlockHook] ReceiveCouponDialogV3 class NOT FOUND")
                return
            }

            val method = findNoArgMethodInHierarchy(
                targetClass,
                BaiduDomesticHookPoints.DIALOG_SHOW_METHOD,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[DomesticLuckyCouponBlockHook] show method NOT FOUND")
                return
            }

            mod.hook(method).intercept { chain ->
                if (
                    HookSettings.isInAppDialogBlocked &&
                    targetClass.isInstance(chain.thisObject)
                ) {
                    XposedCompat.logD("[DomesticLuckyCouponBlockHook] ReceiveCouponDialogV3.show blocked")
                    return@intercept HookUtils.getDefaultReturnValue(method.returnType)
                }
                chain.proceed()
            }

            XposedCompat.log(
                "[DomesticLuckyCouponBlockHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.${method.name}"
            )
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log("[DomesticLuckyCouponBlockHook] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticLuckyCouponBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
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
}
