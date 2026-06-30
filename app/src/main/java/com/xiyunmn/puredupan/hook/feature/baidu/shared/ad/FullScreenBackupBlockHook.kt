package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import android.app.Activity
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduDialogHookPoints
import java.lang.reflect.Modifier

/**
 * Closes Baidu Netdisk's full-screen backup guide Activity after the host lifecycle is registered.
 */
internal object FullScreenBackupBlockHook {
    private const val TAG = "FullScreenBackupBlockHook"
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isFullScreenBackupBlocked) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            installed += hookActivityOnCreate(cl, BaiduDialogHookPoints.NEW_QUICK_SETTINGS_ACTIVITY)
            installed += hookActivityOnCreate(cl, BaiduDialogHookPoints.REPEATED_NEW_QUICK_SETTINGS_ACTIVITY)
            installed += hookNewQuickSettingsCanShow(cl)
            installed += hookRepeatedQuickSettingsCanShow(cl)

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[$TAG] no hooks installed")
            } else {
                XposedCompat.log("[$TAG] hooks INSTALLED: count=$installed")
            }
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log("[$TAG] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[$TAG] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookActivityOnCreate(cl: ClassLoader, className: String): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.logD("[$TAG] class NOT FOUND: $className")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            BaiduDialogHookPoints.NEW_QUICK_SETTINGS_ACTIVITY_ON_CREATE_METHOD,
            Bundle::class.java,
        ) ?: run {
            XposedCompat.logD("[$TAG] $className.onCreate NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (HookSettings.isFullScreenBackupBlocked) {
                closeActivity(chain.thisObject as? Activity, className)
            }
            result
        }
        XposedCompat.log("[$TAG] hook INSTALLED: $className.onCreate")
        return 1
    }

    private fun hookNewQuickSettingsCanShow(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            BaiduDialogHookPoints.NEW_QUICK_SETTINGS_ACTIVITY,
            cl,
        ) ?: return 0
        val method = clazz.declaredMethods.firstOrNull { method ->
            method.name == BaiduDialogHookPoints.NEW_QUICK_SETTINGS_CAN_SHOW_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == java.lang.Boolean.TYPE
        } ?: run {
            XposedCompat.logD("[$TAG] canNewQuickSettingsActivityShow NOT FOUND")
            return 0
        }
        method.isAccessible = true
        mod.hook(method).intercept { chain ->
            if (HookSettings.isFullScreenBackupBlocked) {
                XposedCompat.logD("[$TAG] NewQuickSettingsActivity can-show blocked")
                false
            } else {
                chain.proceed()
            }
        }
        XposedCompat.log("[$TAG] hook INSTALLED: NewQuickSettingsActivity.canNewQuickSettingsActivityShow")
        return 1
    }

    private fun hookRepeatedQuickSettingsCanShow(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            BaiduDialogHookPoints.REPEATED_NEW_QUICK_SETTINGS_ACTIVITY,
            cl,
        ) ?: return 0
        val method = clazz.declaredMethods.firstOrNull { method ->
            method.name == BaiduDialogHookPoints.REPEATED_NEW_QUICK_SETTINGS_CAN_SHOW_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == java.lang.Boolean.TYPE
        } ?: run {
            XposedCompat.logD("[$TAG] checkIsShowRepeateNewQuickSettingsActivity NOT FOUND")
            return 0
        }
        method.isAccessible = true
        mod.hook(method).intercept { chain ->
            if (HookSettings.isFullScreenBackupBlocked) false else chain.proceed()
        }
        XposedCompat.log("[$TAG] hook INSTALLED: RepeatedNewQuickSettingsActivity.checkIsShow")
        return 1
    }

    private fun closeActivity(activity: Activity?, source: String) {
        if (activity == null) return
        hideBeforeNextFrame(activity)
        activity.finish()
        suppressTransition(activity)
        XposedCompat.logD("[$TAG] closed: $source")
    }

    private fun hideBeforeNextFrame(activity: Activity) {
        try {
            activity.window?.decorView?.alpha = 0f
        } catch (t: Throwable) {
            XposedCompat.logD("[$TAG] hide failed: ${t.message}")
        }
    }

    private fun suppressTransition(activity: Activity) {
        try {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        } catch (_: Throwable) {
            // Optional visual polish only.
        }
    }
}
