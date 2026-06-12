package com.xiyunmn.puredupan.hook.feature.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import java.util.Collections
import java.util.WeakHashMap

/**
 * Hides the "renew" action that appears at the top-right of AboutMeActivity while scrolling.
 *
 * The view is created from ActivityAboutMeBinding and is not stable enough to address by field.
 * This hook therefore attaches a narrow DecorView traversal to AboutMeActivity only.
 */
object RenewButtonHideHook {
    private val renewTexts = setOf("去续费", "续费")
    private val attachedActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[RenewButtonHideHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val activityClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY,
                cl,
            ) ?: run {
                XposedCompat.log("[RenewButtonHideHook] AboutMeActivity class NOT FOUND")
                return
            }

            val method = XposedCompat.findMethodOrNull(
                activityClass,
                StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY_ON_CREATE_METHOD,
                Bundle::class.java,
            ) ?: run {
                XposedCompat.log("[RenewButtonHideHook] AboutMeActivity.onCreate NOT FOUND")
                return
            }

            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    attachRenewButtonWatcher(chain.thisObject as? Activity)
                } catch (e: Exception) {
                    XposedCompat.logD("[RenewButtonHideHook] attach failed: ${e.message}")
                }
                result
            }

            XposedCompat.log("[RenewButtonHideHook] hook INSTALLED: AboutMeActivity.onCreate")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[RenewButtonHideHook] FAILED: ${e.message}")
        }
    }

    private fun attachRenewButtonWatcher(activity: Activity?) {
        if (activity == null) return
        if (!attachedActivities.add(activity)) return

        val decorView = activity.window?.decorView ?: run {
            XposedCompat.logD("[RenewButtonHideHook] decorView unavailable")
            return
        }

        decorView.post {
            hideRenewButtons(decorView)
        }
        decorView.viewTreeObserver.addOnGlobalLayoutListener {
            try {
                hideRenewButtons(decorView)
            } catch (_: Throwable) {
                // Keep host layout callbacks stable.
            }
        }

        XposedCompat.log("[RenewButtonHideHook] watcher attached to AboutMeActivity DecorView")
    }

    private fun hideRenewButtons(root: View): Int {
        if (!isEnabled()) return 0

        var hidden = 0
        if (isRenewButton(root)) {
            if (root.visibility != View.GONE) {
                root.visibility = View.GONE
                XposedCompat.logD("[RenewButtonHideHook] renew button hidden: ${root.javaClass.name}")
            }
            hidden++
        }

        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                hidden += hideRenewButtons(root.getChildAt(index))
            }
        }
        return hidden
    }

    private fun isRenewButton(view: View): Boolean {
        if (view !is TextView) return false
        if (view.visibility == View.GONE) return false

        val text = view.text?.toString()?.trim().orEmpty()
        if (text !in renewTexts) return false

        val className = view.javaClass.name
        if (!className.contains("UITextView")) return false

        return true
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isMyPageCustomizeEnabled && ConfigManager.isRenewButtonHidden

}
