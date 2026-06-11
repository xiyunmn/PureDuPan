package com.xiyunmn.puredupan.hook.feature.ad

import android.app.Activity
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Closes Baidu Netdisk's full-screen backup guide Activity after the host lifecycle is registered.
 *
 * NewQuickSettingsActivity can be part of a startActivityForResult route during first login.
 * Therefore we must let the host onCreate complete before calling finish(), otherwise the previous
 * page may never receive the route callback that continues into MainActivity.
 */
object FullScreenBackupBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isFullScreenBackupBlocked) {
            XposedCompat.log("[FullScreenBackupBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.NEW_QUICK_SETTINGS_ACTIVITY,
                cl,
            ) ?: run {
                XposedCompat.log("[FullScreenBackupBlockHook] NewQuickSettingsActivity class NOT FOUND")
                return
            }

            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.NEW_QUICK_SETTINGS_ACTIVITY_ON_CREATE_METHOD,
                Bundle::class.java,
            ) ?: run {
                XposedCompat.log("[FullScreenBackupBlockHook] NewQuickSettingsActivity.onCreate NOT FOUND")
                return
            }

            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (!ConfigManager.isFullScreenBackupBlocked) {
                    return@intercept result
                }

                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    hideBeforeNextFrame(activity)
                    activity.finish()
                    suppressTransition(activity)
                    XposedCompat.logD("[FullScreenBackupBlockHook] NewQuickSettingsActivity closed")
                }
                result
            }

            XposedCompat.log("[FullScreenBackupBlockHook] hook INSTALLED: NewQuickSettingsActivity.onCreate")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[FullScreenBackupBlockHook] FAILED: ${t.message}")
        }
    }

    private fun hideBeforeNextFrame(activity: Activity) {
        try {
            activity.window?.decorView?.alpha = 0f
        } catch (t: Throwable) {
            XposedCompat.logD("[FullScreenBackupBlockHook] hide failed: ${t.message}")
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

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
