package com.xiyunmn.puredupan.hook.feature.ad

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.reflect.Method

/**
 * Blocks the share tab push notification guide DialogFragment.
 *
 * The hook lets FragmentManager finish the legal lifecycle transition first, then immediately
 * dismisses the target dialog. This avoids breaking tab-switch Handler logic at show(...).
 */
object SharePushGuideBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isSharePushGuideBlocked) {
            XposedCompat.log("[SharePushGuideBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val targetClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.SHARE_TAB_PUSH_GUIDE_NORMAL_DIALOG,
                cl,
            ) ?: run {
                XposedCompat.log("[SharePushGuideBlockHook] ShareTabPushGuideNormalDialog class NOT FOUND")
                return
            }

            val method = findNoArgMethodInHierarchy(
                targetClass,
                StableBaiduPanHookPoints.SHARE_TAB_PUSH_GUIDE_ON_START_METHOD,
            ) ?: run {
                resetHooked()
                XposedCompat.log("[SharePushGuideBlockHook] onStart method NOT FOUND")
                return
            }

            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (
                    ConfigManager.isSharePushGuideBlocked &&
                    targetClass.isInstance(chain.thisObject)
                ) {
                    dismissDialogFragment(chain.thisObject)
                }
                result
            }

            XposedCompat.log(
                "[SharePushGuideBlockHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.${method.name}"
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[SharePushGuideBlockHook] FAILED: ${t.message}")
        }
    }

    private fun dismissDialogFragment(fragment: Any?) {
        if (fragment == null) return
        try {
            val dismissMethod = findNoArgMethodInHierarchy(fragment.javaClass, "dismissAllowingStateLoss")
                ?: findNoArgMethodInHierarchy(fragment.javaClass, "dismiss")
            if (dismissMethod == null) {
                XposedCompat.logW("[SharePushGuideBlockHook] dismiss method NOT FOUND")
                return
            }
            dismissMethod.invoke(fragment)
            XposedCompat.logD("[SharePushGuideBlockHook] ShareTabPushGuideNormalDialog dismissed")
        } catch (t: Throwable) {
            XposedCompat.logW("[SharePushGuideBlockHook] dismiss failed: ${t.message}")
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

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
