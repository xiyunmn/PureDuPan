package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method

/**
 * Blocks in-app business operation dialogs.
 *
 * This hook and LuckyCouponBlockHook are controlled by HookSettings.isInAppDialogBlocked.
 */
internal object DomesticBusinessOpDialogBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isInAppDialogBlocked) {
            XposedCompat.log("[DomesticBusinessOpDialogBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.BUSINESS_OP_DIALOG,
                cl,
            ) ?: run {
                XposedCompat.log("[DomesticBusinessOpDialogBlockHook] BusinessOPDialog class NOT FOUND")
                return
            }

            var installed = 0
            for (method in clazz.declaredMethods) {
                if (!isShowDialogMethod(method)) {
                    continue
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isInAppDialogBlocked) {
                        dismissDialogFragment(chain.thisObject)
                        HookUtils.getDefaultReturnValue(method.returnType)
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }

            for (method in clazz.declaredMethods) {
                if (!isOnCreateViewMethod(method)) {
                    continue
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isInAppDialogBlocked) {
                        dismissDialogFragment(chain.thisObject)
                        createBlockedDialogView(chain.args.firstOrNull())
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[DomesticBusinessOpDialogBlockHook] BusinessOPDialog methods NOT FOUND")
                return
            }

            XposedCompat.log("[DomesticBusinessOpDialogBlockHook] hooks INSTALLED: count=$installed")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log("[DomesticBusinessOpDialogBlockHook] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticBusinessOpDialogBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun isShowDialogMethod(method: Method): Boolean {
        if (method.name != BaiduDomesticHookPoints.BUSINESS_OP_DIALOG_SHOW_DIALOG_METHOD) return false
        val parameterTypes = method.parameterTypes
        return parameterTypes.size == 2 &&
            parameterTypes[0].name == BaiduDomesticHookPoints.ANDROIDX_FRAGMENT_MANAGER &&
            parameterTypes[1] == String::class.java
    }

    private fun isOnCreateViewMethod(method: Method): Boolean {
        if (method.name != BaiduDomesticHookPoints.BUSINESS_OP_DIALOG_ON_CREATE_VIEW_METHOD) return false
        return method.parameterTypes.size == 3 &&
            View::class.java.isAssignableFrom(method.returnType)
    }

    private fun createBlockedDialogView(inflaterArg: Any?): View? {
        val context = (inflaterArg as? android.view.LayoutInflater)?.context ?: return null
        return FrameLayout(context).apply {
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(0, 0)
        }
    }

    private fun dismissDialogFragment(fragment: Any?) {
        if (fragment == null) return
        try {
            val dismissMethod = findNoArgMethodInHierarchy(fragment.javaClass, "dismissAllowingStateLoss")
                ?: findNoArgMethodInHierarchy(fragment.javaClass, "dismiss")
                ?: return
            dismissMethod.invoke(fragment)
            XposedCompat.logD("[DomesticBusinessOpDialogBlockHook] BusinessOPDialog dismissed")
        } catch (t: Throwable) {
            XposedCompat.logD("[DomesticBusinessOpDialogBlockHook] dismiss ignored: ${t.message}")
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
