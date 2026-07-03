package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method

/**
 * Blocks the share tab push notification guide DialogFragment.
 *
 * The hook lets FragmentManager finish the legal lifecycle transition first, then immediately
 * dismisses the target dialog. This avoids breaking tab-switch Handler logic at show(...).
 */
internal object DomesticSharePushGuideBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isSharePushGuideBlocked) {
            XposedCompat.log("[DomesticSharePushGuideBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val targetClass = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.SHARE_TAB_PUSH_GUIDE_NORMAL_DIALOG,
                cl,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[DomesticSharePushGuideBlockHook] ShareTabPushGuideNormalDialog class NOT FOUND")
                return
            }

            var installed = 0
            XposedCompat.findMethodOrNull(
                targetClass,
                BaiduDomesticHookPoints.SHARE_TAB_PUSH_GUIDE_ON_CREATE_DIALOG_METHOD,
                Bundle::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (!HookSettings.isSharePushGuideBlocked) {
                        return@intercept chain.proceed()
                    }
                    createDismissedDialog(chain.thisObject) ?: chain.proceed()
                }
                installed += 1
            }

            findNoArgMethodInHierarchy(
                targetClass,
                BaiduDomesticHookPoints.SHARE_TAB_PUSH_GUIDE_ON_RESUME_METHOD,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (
                        HookSettings.isSharePushGuideBlocked &&
                        targetClass.isInstance(chain.thisObject)
                    ) {
                        dismissDialogFragment(chain.thisObject)
                    }
                    result
                }
                installed += 1
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[DomesticSharePushGuideBlockHook] ShareTabPushGuideNormalDialog methods NOT FOUND")
                return
            }

            XposedCompat.log(
                "[DomesticSharePushGuideBlockHook] hooks INSTALLED: count=$installed"
            )
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log("[DomesticSharePushGuideBlockHook] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticSharePushGuideBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
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
            XposedCompat.logD("[DomesticSharePushGuideBlockHook] context lookup failed: ${t.message}")
            null
        }
    }

    private fun dismissDialogFragment(fragment: Any?) {
        if (fragment == null) return
        try {
            val dismissMethod = findNoArgMethodInHierarchy(fragment.javaClass, "dismissAllowingStateLoss")
                ?: findNoArgMethodInHierarchy(fragment.javaClass, "dismiss")
            if (dismissMethod == null) {
                XposedCompat.logW("[DomesticSharePushGuideBlockHook] dismiss method NOT FOUND")
                return
            }
            dismissMethod.invoke(fragment)
            XposedCompat.logD("[DomesticSharePushGuideBlockHook] ShareTabPushGuideNormalDialog dismissed")
        } catch (t: Throwable) {
            XposedCompat.logW("[DomesticSharePushGuideBlockHook] dismiss failed: ${t.message}")
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
