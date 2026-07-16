package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import java.util.Collections
import java.util.WeakHashMap

/**
 * DecorView-level cleanup for "About Me" page views that are rebuilt by host UI.
 */
object AboutMeGodModeHook {
    private const val BANNER_ID = "aboutme_banner"
    private const val MY_SERVICE_ID = "cl_my_service"
    private const val COIN_CENTER_BUBBLE_ID = "v_bubble"
    private const val COIN_CENTER_LAYOUT_TAG = "fragment_new_aboutme_coincenter_v2"
    private const val COIN_CENTER_ICON_ID = "iv_icon"
    private const val COIN_CENTER_MONEY_ID = "tv_money"
    private const val COIN_CENTER_YUAN_ID = "tv_yuan"
    private const val SIGN_IN_DOT_ID = "f1_entry_dot"
    private val SIGN_IN_DOT_FALLBACK_IDS = listOf(
        "fl_entry_dot",
        "activity_entry_dot",
        "entry_dot_view",
    )
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
            val activityClassNames = BaiduFeatureRuntime.currentAboutMeActivityClassNames()
                .ifEmpty {
                    BaiduFeatureRuntime.currentAboutMeActivityClassName()
                        ?.let(::listOf)
                        .orEmpty()
                }
            if (activityClassNames.isEmpty()) {
                XposedCompat.log("[AboutMeGodModeHook] AboutMeActivity host capability missing")
                hookState.reset()
                return
            }

            var installed = 0
            for (activityClassName in activityClassNames) {
                installed += hookActivityClass(
                    cl = cl,
                    activityClassName = activityClassName,
                )
            }

            if (installed == 0) {
                XposedCompat.log("[AboutMeGodModeHook] AboutMeActivity hooks NOT INSTALLED")
                hookState.reset()
                return
            }

            XposedCompat.log("[AboutMeGodModeHook] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[AboutMeGodModeHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookActivityClass(cl: ClassLoader, activityClassName: String): Int {
        val mod = XposedCompat.module ?: return 0
        val targetClass = XposedCompat.findClassOrNull(
            activityClassName,
            cl,
        ) ?: run {
            XposedCompat.log("[AboutMeGodModeHook] AboutMeActivity class NOT FOUND: $activityClassName")
            return 0
        }

        val onCreate = XposedCompat.findMethodOrNull(
            targetClass,
            "onCreate",
            Bundle::class.java,
        ) ?: run {
            XposedCompat.log("[AboutMeGodModeHook] $activityClassName.onCreate NOT FOUND")
            return 0
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

        XposedCompat.log("[AboutMeGodModeHook] hook INSTALLED: $activityClassName")
        return 1
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
        val popupResponseClassName = BaiduFeatureRuntime.currentPopupResponseClassName() ?: run {
            XposedCompat.logD("[AboutMeGodModeHook] PopupResponse host capability missing, setActivityEntry skipped")
            return
        }
        val popupResponseClass = XposedCompat.findClassOrNull(
            popupResponseClassName,
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
        val config = HookSettings.aboutMeOptions()
        if (!hasEnabledOption()) return

        if (config.isAboutMeBannerRemoved) {
            hideViewByEntryName(activity, root, BANNER_ID, "banner")
        }
        if (config.isMyServiceRemoved) {
            hideViewByEntryName(activity, root, MY_SERVICE_ID, "my_service")
        }
        if (config.isAboutMeCoinCenterBubbleHidden) {
            if (isSamsungHost(activity)) {
                hideCoinCenterViews(activity, root)
            } else {
                hideViewByEntryName(activity, root, COIN_CENTER_BUBBLE_ID, "coin_center_bubble")
            }
        }
        if (config.isAboutMeSignInDotHidden) {
            hideViewByEntryName(activity, root, SIGN_IN_DOT_ID, "sign_in_dot")
            for (entryName in SIGN_IN_DOT_FALLBACK_IDS) {
                hideViewByEntryName(activity, root, entryName, "sign_in_dot")
            }
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

    private fun hideCoinCenterViews(activity: Activity, root: View) {
        val coinCenterRoot = findCoinCenterRoot(activity, root)
        if (coinCenterRoot == null) {
            hideViewByEntryName(activity, root, COIN_CENTER_BUBBLE_ID, "coin_center_bubble")
            return
        }

        hideViewByEntryNameInRoot(activity, coinCenterRoot, COIN_CENTER_BUBBLE_ID, "coin_center_bubble")
        hideViewByEntryNameInRoot(activity, coinCenterRoot, COIN_CENTER_ICON_ID, "coin_center_icon")

        val balanceContainer = findCoinCenterBalanceContainer(activity, coinCenterRoot)
        if (balanceContainer != null) {
            hideView(balanceContainer, "coin_center_balance_container", "$COIN_CENTER_MONEY_ID/$COIN_CENTER_YUAN_ID")
        } else {
            hideViewByEntryNameInRoot(activity, coinCenterRoot, COIN_CENTER_MONEY_ID, "coin_center_money")
            hideViewByEntryNameInRoot(activity, coinCenterRoot, COIN_CENTER_YUAN_ID, "coin_center_yuan")
        }
    }

    private fun isSamsungHost(activity: Activity): Boolean {
        return BaiduFeatureRuntime.isSamsungHost(activity)
    }

    private fun findCoinCenterRoot(activity: Activity, root: View): ViewGroup? {
        val bubble = findViewByEntryNameInRoot(activity, root, COIN_CENTER_BUBBLE_ID)
        val bubbleParent = bubble?.parent as? ViewGroup
        if (bubbleParent != null && isCoinCenterRoot(activity, bubbleParent)) {
            return bubbleParent
        }

        val taggedRoot = findViewGroupByTag(root, COIN_CENTER_LAYOUT_TAG)
        if (taggedRoot != null) return taggedRoot

        val balanceRoot = findCoinCenterRootByBalanceIds(activity, root)
        if (balanceRoot != null) return balanceRoot

        return bubbleParent
    }

    private fun isCoinCenterRoot(activity: Activity, root: View): Boolean {
        return findViewByEntryNameInRoot(activity, root, COIN_CENTER_BUBBLE_ID) != null ||
            findViewByEntryNameInRoot(activity, root, COIN_CENTER_MONEY_ID) != null ||
            findViewByEntryNameInRoot(activity, root, COIN_CENTER_YUAN_ID) != null
    }

    private fun findViewGroupByTag(root: View, tagText: String): ViewGroup? {
        if (root is ViewGroup && root.tag?.toString()?.contains(tagText) == true) {
            return root
        }
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val found = findViewGroupByTag(root.getChildAt(index), tagText)
            if (found != null) return found
        }
        return null
    }

    private fun findCoinCenterRootByBalanceIds(activity: Activity, root: View): ViewGroup? {
        val money = findViewByEntryNameInRoot(activity, root, COIN_CENTER_MONEY_ID)
        val yuan = findViewByEntryNameInRoot(activity, root, COIN_CENTER_YUAN_ID)
        val balanceChild = money ?: yuan ?: return null
        var current = balanceChild.parent
        while (current is ViewGroup) {
            val hasBalanceText = findViewByEntryNameInRoot(activity, current, COIN_CENTER_MONEY_ID) != null ||
                findViewByEntryNameInRoot(activity, current, COIN_CENTER_YUAN_ID) != null
            val hasIcon = findViewByEntryNameInRoot(activity, current, COIN_CENTER_ICON_ID) != null
            if (hasBalanceText && hasIcon) return current
            current = current.parent
        }
        return null
    }

    private fun findCoinCenterBalanceContainer(activity: Activity, root: View): View? {
        val money = findViewByEntryNameInRoot(activity, root, COIN_CENTER_MONEY_ID)
        val yuan = findViewByEntryNameInRoot(activity, root, COIN_CENTER_YUAN_ID)
        val moneyParent = money?.parent as? View
        val yuanParent = yuan?.parent as? View
        return when {
            moneyParent != null && moneyParent == yuanParent -> moneyParent
            moneyParent != null -> moneyParent
            yuanParent != null -> yuanParent
            else -> null
        }
    }

    private fun hideViewByEntryName(activity: Activity, root: View, entryName: String, label: String) {
        val id = activity.resources.getIdentifier(entryName, "id", activity.packageName)
        if (id == 0) return
        val view = activity.findViewById<View>(id) ?: root.findViewById(id) ?: return
        hideView(view, label, entryName)
    }

    private fun hideViewByEntryNameInRoot(activity: Activity, root: View, entryName: String, label: String) {
        val view = findViewByEntryNameInRoot(activity, root, entryName) ?: return
        hideView(view, label, entryName)
    }

    private fun findViewByEntryNameInRoot(activity: Activity, root: View, entryName: String): View? {
        val id = activity.resources.getIdentifier(entryName, "id", activity.packageName)
        if (id == 0) return null
        return root.findViewById(id)
    }

    private fun hideView(view: View, label: String, source: String) {
        val shouldLog = view.visibility != View.GONE || view.alpha != 0f || view.isEnabled || view.isClickable
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
        if (shouldLog) {
            XposedCompat.log("[AboutMeGodModeHook] $label hidden ($source)")
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
        val snapshot = HookSettings.aboutMeOptions()
        return snapshot.isMyPageCustomizeEnabled &&
            (
                snapshot.isAboutMeBannerRemoved ||
                    snapshot.isMyServiceRemoved ||
                    snapshot.isAboutMeCoinCenterBubbleHidden ||
                    snapshot.isAboutMeSignInDotHidden ||
                    snapshot.isAboutMeManageSpaceTextHidden ||
                    snapshot.isAboutMeRewardTextHidden ||
                    snapshot.isAboutMeAccountExitTextHidden ||
                    snapshot.isAboutMeStarSkinTextHidden ||
                    snapshot.isAboutMeFreeDataCardTextHidden
            )
    }

}
