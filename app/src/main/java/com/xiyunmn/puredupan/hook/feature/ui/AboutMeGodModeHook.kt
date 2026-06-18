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
 * DecorView-level cleanup for "About Me" page views that are rebuilt by host UI.
 */
object AboutMeGodModeHook {
    private const val BANNER_ID = "aboutme_banner"
    private const val MY_SERVICE_ID = "cl_my_service"
    private const val COIN_CENTER_BUBBLE_ID = "v_bubble"
    private const val SIGN_IN_DOT_ID = "f1_entry_dot"
    private val SIGN_IN_DOT_FALLBACK_IDS = listOf(
        "fl_entry_dot",
        "activity_entry_dot",
        "entry_dot_view",
    )
    private const val AI_COIN_ASSET_ID = "layout_ai_coin"
    private const val TEXT_MANAGE_SPACE = "管理空间"
    private const val TEXT_REWARD = "领奖励"
    private const val TEXT_ACCOUNT_EXIT = "账号、退出"
    private const val TEXT_STAR_SKIN = "明星皮肤上线啦"
    private const val TEXT_FREE_DATA_CARD = "免流量卡、领无限空间"

    private val attachedDecorViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val targetClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY,
                cl,
            ) ?: run {
                XposedCompat.log("[AboutMeGodModeHook] AboutMeActivity class NOT FOUND")
                hookState.reset()
                return
            }

            val onCreate = XposedCompat.findMethodOrNull(
                targetClass,
                "onCreate",
                Bundle::class.java,
            ) ?: run {
                XposedCompat.log("[AboutMeGodModeHook] onCreate NOT FOUND")
                hookState.reset()
                return
            }

            mod.hook(onCreate).intercept { chain ->
                val result = chain.proceed()
                scheduleFromLifecycle(chain.thisObject, "onCreate")
                result
            }

            hookNoArgLifecycleMethod(targetClass, "initView")
            hookNoArgLifecycleMethod(targetClass, "onResume")
            hookNoArgLifecycleMethod(targetClass, "onPostResume")
            hookSetActivityEntry(targetClass, cl)

            XposedCompat.log("[AboutMeGodModeHook] hook INSTALLED: ${StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY}")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[AboutMeGodModeHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookNoArgLifecycleMethod(targetClass: Class<*>, methodName: String) {
        val mod = XposedCompat.module ?: return
        val method = XposedCompat.findMethodOrNull(targetClass, methodName) ?: run {
            XposedCompat.logD("[AboutMeGodModeHook] $methodName not found, skipped")
            return
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            scheduleFromLifecycle(chain.thisObject, methodName)
            result
        }
        XposedCompat.logD("[AboutMeGodModeHook] lifecycle hook installed: $methodName")
    }

    private fun hookSetActivityEntry(targetClass: Class<*>, cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val popupResponseClass = XposedCompat.findClassOrNull(
            "com.baidu.netdisk.operation.io.PopupResponse",
            cl,
        ) ?: run {
            XposedCompat.logD("[AboutMeGodModeHook] PopupResponse not found, setActivityEntry skipped")
            return
        }
        val method = XposedCompat.findMethodOrNull(
            targetClass,
            "setActivityEntry",
            popupResponseClass,
        ) ?: run {
            XposedCompat.logD("[AboutMeGodModeHook] setActivityEntry(PopupResponse) not found, skipped")
            return
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            scheduleFromLifecycle(chain.thisObject, "setActivityEntry")
            result
        }
        XposedCompat.logD("[AboutMeGodModeHook] lifecycle hook installed: setActivityEntry")
    }

    private fun scheduleFromLifecycle(thisObject: Any?, source: String) {
        try {
            val activity = thisObject as? Activity ?: return
            attachGodModeListener(activity)
            XposedCompat.logD("[AboutMeGodModeHook] scheduled from $source")
        } catch (e: Exception) {
            XposedCompat.logD("[AboutMeGodModeHook] schedule failed: ${e.message}")
        }
    }

    private fun attachGodModeListener(activity: Activity?) {
        if (activity == null) return
        if (!hasEnabledOption()) return

        val decorView = activity.window?.decorView ?: run {
            XposedCompat.logD("[AboutMeGodModeHook] decorView unavailable")
            return
        }

        scheduleApply(activity, decorView)

        if (!attachedDecorViews.add(decorView)) return
        decorView.viewTreeObserver.addOnGlobalLayoutListener {
            runCatching { applyGodMode(activity, decorView) }
        }
        decorView.viewTreeObserver.addOnPreDrawListener {
            runCatching { applyGodMode(activity, decorView) }
            true
        }
        XposedCompat.log("[AboutMeGodModeHook] GodMode listener attached to AboutMeActivity DecorView")
    }

    private fun scheduleApply(activity: Activity, decorView: View) {
        runCatching { applyGodMode(activity, decorView) }
        decorView.post { runCatching { applyGodMode(activity, decorView) } }
        for (delay in listOf(80L, 240L, 600L, 1200L)) {
            decorView.postDelayed(
                { runCatching { applyGodMode(activity, decorView) } },
                delay,
            )
        }
    }

    private fun applyGodMode(activity: Activity, root: View) {
        val config = ConfigManager.snapshot()
        if (!hasEnabledOption()) return

        if (config.isAboutMeBannerRemoved) {
            hideViewByEntryName(activity, root, BANNER_ID, "banner")
        }
        if (config.isMyServiceRemoved) {
            hideViewByEntryName(activity, root, MY_SERVICE_ID, "my_service")
        }
        if (config.isAboutMeCoinCenterBubbleHidden) {
            hideViewByEntryName(activity, root, COIN_CENTER_BUBBLE_ID, "coin_center_bubble")
        }
        if (config.isAboutMeSignInDotHidden) {
            hideViewByEntryName(activity, root, SIGN_IN_DOT_ID, "sign_in_dot")
            for (entryName in SIGN_IN_DOT_FALLBACK_IDS) {
                hideViewByEntryName(activity, root, entryName, "sign_in_dot")
            }
        }
        if (config.isAboutMeAiCoinAssetHidden) {
            hideViewByEntryName(activity, root, AI_COIN_ASSET_ID, "ai_coin_asset")
        }
        if (config.isAboutMeManageSpaceTextHidden) {
            hideTextView(root, TEXT_MANAGE_SPACE, "manage_space_text")
        }
        if (config.isAboutMeRewardTextHidden) {
            hideTextView(root, TEXT_REWARD, "reward_text")
        }
        if (config.isAboutMeAccountExitTextHidden) {
            hideTextView(root, TEXT_ACCOUNT_EXIT, "account_exit_text")
        }
        if (config.isAboutMeStarSkinTextHidden) {
            hideTextView(root, TEXT_STAR_SKIN, "star_skin_text")
        }
        if (config.isAboutMeFreeDataCardTextHidden) {
            hideTextView(root, TEXT_FREE_DATA_CARD, "free_data_card_text")
        }
    }

    private fun hideViewByEntryName(activity: Activity, root: View, entryName: String, label: String) {
        val id = activity.resources.getIdentifier(entryName, "id", activity.packageName)
        if (id == 0) return
        val view = activity.findViewById<View>(id) ?: root.findViewById(id) ?: return
        val shouldLog = view.visibility != View.GONE || view.alpha != 0f || view.isEnabled || view.isClickable
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
        if (shouldLog) {
            XposedCompat.log("[AboutMeGodModeHook] $label hidden ($entryName)")
        }
    }

    private fun hideTextView(root: View, text: String, label: String): Boolean {
        if (root is TextView && root.text?.toString() == text) {
            val shouldLog = root.visibility != View.GONE || root.alpha != 0f || root.isEnabled || root.isClickable
            root.visibility = View.GONE
            root.alpha = 0f
            root.isEnabled = false
            root.isClickable = false
            if (shouldLog) {
                XposedCompat.log("[AboutMeGodModeHook] $label hidden by text: $text")
            }
            return true
        }
        if (root !is ViewGroup) return false
        var hidden = false
        for (index in 0 until root.childCount) {
            hidden = hideTextView(root.getChildAt(index), text, label) || hidden
        }
        return hidden
    }

    private fun hasEnabledOption(): Boolean {
        val snapshot = ConfigManager.snapshot()
        return snapshot.isMyPageCustomizeEnabled &&
            (
                snapshot.isAboutMeBannerRemoved ||
                    snapshot.isMyServiceRemoved ||
                    snapshot.isAboutMeCoinCenterBubbleHidden ||
                    snapshot.isAboutMeSignInDotHidden ||
                    snapshot.isAboutMeAiCoinAssetHidden ||
                    snapshot.isAboutMeManageSpaceTextHidden ||
                    snapshot.isAboutMeRewardTextHidden ||
                    snapshot.isAboutMeAccountExitTextHidden ||
                    snapshot.isAboutMeStarSkinTextHidden ||
                    snapshot.isAboutMeFreeDataCardTextHidden
            )
    }

}
