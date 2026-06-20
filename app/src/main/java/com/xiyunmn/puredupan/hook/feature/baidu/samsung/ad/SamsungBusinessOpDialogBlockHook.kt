package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ad

import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

internal object SamsungBusinessOpDialogBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isInAppDialogBlocked) {
            XposedCompat.log("[SamsungBusinessOpDialogBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.BUSINESS_OP_DIALOG,
                cl,
            ) ?: run {
                XposedCompat.log("[SamsungBusinessOpDialogBlockHook] BusinessOPDialog class NOT FOUND")
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
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[SamsungBusinessOpDialogBlockHook] BusinessOPDialog methods NOT FOUND")
                return
            }

            XposedCompat.log("[SamsungBusinessOpDialogBlockHook] hooks INSTALLED: count=$installed")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log(
                "[SamsungBusinessOpDialogBlockHook] FAILED (reflection): " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungBusinessOpDialogBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun isShowDialogMethod(method: java.lang.reflect.Method): Boolean {
        if (method.name != BaiduSamsungHookPoints.BUSINESS_OP_DIALOG_SHOW_DIALOG_METHOD) return false
        val parameterTypes = method.parameterTypes
        return parameterTypes.size == 2 &&
            parameterTypes[0].name == BaiduSamsungHookPoints.ANDROIDX_FRAGMENT_MANAGER &&
            parameterTypes[1] == String::class.java
    }

    private fun isOnCreateViewMethod(method: java.lang.reflect.Method): Boolean {
        if (method.name != BaiduSamsungHookPoints.BUSINESS_OP_DIALOG_ON_CREATE_VIEW_METHOD) return false
        return method.parameterTypes.size == 3 &&
            View::class.java.isAssignableFrom(method.returnType)
    }
}
