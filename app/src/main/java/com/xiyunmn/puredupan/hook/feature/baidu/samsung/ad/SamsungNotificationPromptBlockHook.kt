package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ad

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints
import java.lang.reflect.Method

internal object SamsungNotificationPromptBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isNotificationPromptBlocked) {
            XposedCompat.log("[SamsungNotificationPromptBlockHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!hookState.markInstalled()) return

        try {
            val installed = hookPushGuideDialog(cl)

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[SamsungNotificationPromptBlockHook] no hooks installed")
                return
            }

            XposedCompat.log("[SamsungNotificationPromptBlockHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[SamsungNotificationPromptBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookPushGuideDialog(cl: ClassLoader): Int {
        val targetClass = XposedCompat.findClassOrNull(
            BaiduSamsungHookPoints.PUSH_GUIDE_NORMAL_DIALOG,
            cl,
        ) ?: run {
            XposedCompat.log("[SamsungNotificationPromptBlockHook] PushGuideNormalDialog class NOT FOUND")
            return 0
        }

        var installed = 0
        XposedCompat.findMethodOrNull(
            targetClass,
            BaiduSamsungHookPoints.PUSH_GUIDE_ON_CREATE_DIALOG_METHOD,
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

        for (methodName in listOf(
            BaiduSamsungHookPoints.PUSH_GUIDE_ON_START_METHOD,
            BaiduSamsungHookPoints.PUSH_GUIDE_ON_RESUME_METHOD,
        )) {
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
            XposedCompat.log("[SamsungNotificationPromptBlockHook] PushGuideNormalDialog methods NOT FOUND")
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
            XposedCompat.logD("[SamsungNotificationPromptBlockHook] context lookup failed: ${t.message}")
            null
        }
    }

    private fun dismissDialogFragment(fragment: Any?) {
        if (fragment == null) return
        try {
            findNoArgMethodInHierarchy(fragment.javaClass, "dismissAllowingStateLoss")
                ?.invoke(fragment)
                ?: findNoArgMethodInHierarchy(fragment.javaClass, "dismiss")?.invoke(fragment)
            XposedCompat.logD("[SamsungNotificationPromptBlockHook] PushGuideNormalDialog dismissed")
        } catch (t: Throwable) {
            XposedCompat.logD("[SamsungNotificationPromptBlockHook] dismiss ignored: ${t.message}")
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
