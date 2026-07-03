package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method

internal object DomesticNotificationPromptBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isNotificationPromptBlocked) {
            XposedCompat.log("[DomesticNotificationPromptBlockHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!hookState.markInstalled()) return

        try {
            val installed = hookPushGuideDialog(cl)

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[DomesticNotificationPromptBlockHook] no hooks installed")
                return
            }

            XposedCompat.log("[DomesticNotificationPromptBlockHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[DomesticNotificationPromptBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookPushGuideDialog(cl: ClassLoader): Int {
        val targetClass = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.PUSH_GUIDE_NORMAL_DIALOG,
            cl,
        ) ?: run {
            XposedCompat.log("[DomesticNotificationPromptBlockHook] PushGuideNormalDialog class NOT FOUND")
            return 0
        }

        var installed = 0
        XposedCompat.findMethodOrNull(
            targetClass,
            BaiduDomesticHookPoints.PUSH_GUIDE_ON_CREATE_DIALOG_METHOD,
            Bundle::class.java,
        )?.let { method ->
            method.isAccessible = true
            XposedCompat.module?.hook(method)?.intercept { chain ->
                if (!HookSettings.isNotificationPromptBlocked) {
                    return@intercept chain.proceed()
                }
                createDismissedDialog(chain.thisObject) ?: chain.proceed()
            }
            installed++
        }

        for (methodName in listOf(BaiduDomesticHookPoints.PUSH_GUIDE_ON_RESUME_METHOD)) {
            findNoArgMethodInHierarchy(targetClass, methodName)?.let { method ->
                XposedCompat.module?.hook(method)?.intercept { chain ->
                    val result = chain.proceed()
                    if (HookSettings.isNotificationPromptBlocked) {
                        dismissDialogFragment(chain.thisObject)
                    }
                    result
                }
                installed++
            }
        }

        if (installed == 0) {
            XposedCompat.log("[DomesticNotificationPromptBlockHook] PushGuideNormalDialog methods NOT FOUND")
        }
        return installed
    }

    private fun createDismissedDialog(fragment: Any?): Dialog? {
        val context = findContext(fragment) ?: return null
        return Dialog(context).apply {
            setOnShowListener { dialog -> dialog.dismiss() }
        }
    }

    private fun findContext(fragment: Any?): Context? {
        if (fragment == null) return null
        return try {
            findNoArgMethodInHierarchy(fragment.javaClass, "getActivity")?.invoke(fragment) as? Context
                ?: findNoArgMethodInHierarchy(fragment.javaClass, "getContext")?.invoke(fragment) as? Context
        } catch (t: Throwable) {
            XposedCompat.logD("[DomesticNotificationPromptBlockHook] context lookup failed: ${t.message}")
            null
        }
    }

    private fun dismissDialogFragment(fragment: Any?) {
        if (fragment == null) return
        try {
            findNoArgMethodInHierarchy(fragment.javaClass, "dismissAllowingStateLoss")
                ?.invoke(fragment)
                ?: findNoArgMethodInHierarchy(fragment.javaClass, "dismiss")?.invoke(fragment)
            XposedCompat.logD("[DomesticNotificationPromptBlockHook] PushGuideNormalDialog dismissed")
        } catch (t: Throwable) {
            XposedCompat.logD("[DomesticNotificationPromptBlockHook] dismiss ignored: ${t.message}")
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
