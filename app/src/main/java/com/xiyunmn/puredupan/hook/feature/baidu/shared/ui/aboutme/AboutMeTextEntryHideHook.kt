package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAboutMeHookPoints
import java.lang.reflect.Method

/**
 * Hides the remaining about-me text entries at their render entries.
 *
 * The old God Mode path watched the whole Activity root and recursively scanned every text on
 * layout/pre-draw. This hook keeps the same user-visible behavior, but runs only when about-me rows,
 * welfare cards, or manage-space quota controls are rendered.
 */
object AboutMeTextEntryHideHook {
    private const val TAG = "AboutMeTextEntryHideHook"

    private const val TEXT_ACCOUNT_EXIT = "账号、退出"
    private const val TEXT_STAR_SKIN = "明星皮肤上线啦"
    private const val TEXT_FREE_DATA_CARD = "免流量卡、领无限空间"

    private const val MIDDLE_MANAGE_SPACE_ID = "manage_space"
    private const val MIDDLE_MANAGE_SPACE_ARROW_ID = "manage_space_arrow"
    private const val REWARD_SUBTITLE_ROOT_ID = "cl_subtitle"
    private const val REWARD_SUBTITLE_ARROW_ID = "iv_subtitle_arrow"
    private const val INIT_VIEWS_METHOD = "initViews"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isAnyEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            if (activeTextTargets().isNotEmpty()) {
                installed += hookMiddleRows(cl)
                installed += hookWelfareCards(cl)
            }
            if (isManageSpaceEnabled()) {
                installed += hookBottomManageSpace(cl)
            }
            if (isRewardEnabled()) {
                installed += hookCoinCenterRewardSubtitle(cl)
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[$TAG] hooks NOT INSTALLED")
                return
            }
            XposedCompat.log("[$TAG] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[$TAG] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookMiddleRows(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val holderClass = XposedCompat.findClassOrNull(
            BaiduAboutMeHookPoints.BASE_MIDDLE_VIEW_HOLDER,
            cl,
        ) ?: run {
            XposedCompat.logD("[$TAG] BaseMiddleViewHolder not found")
            return 0
        }

        var count = 0
        for (method in holderClass.declaredMethods) {
            if (!isMiddleBindMethod(method)) continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                hideConfiguredTexts(itemView(chain.thisObject), "middle row bind")
                result
            }
            count++
            XposedCompat.logD("[$TAG] middle row render hook installed: ${method.name}")
        }
        return count
    }

    private fun isMiddleBindMethod(method: Method): Boolean {
        val params = method.parameterTypes
        return method.returnType == Void.TYPE &&
            params.size == 2 &&
            params[1] == Boolean::class.javaPrimitiveType
    }

    private fun hookWelfareCards(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val adapterClass = XposedCompat.findClassOrNull(
            BaiduAboutMeHookPoints.ABOUT_MY_WELFARE_ADAPTER,
            cl,
        ) ?: run {
            XposedCompat.logD("[$TAG] AboutMyWelfareAdapter not found")
            return 0
        }

        var count = 0
        for (method in adapterClass.declaredMethods) {
            if (!isWelfareBindMethod(method)) continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                hideConfiguredTexts(itemView(chain.args.firstOrNull()), "welfare card bind")
                result
            }
            count++
            XposedCompat.logD("[$TAG] welfare render hook installed: ${method.name}")
        }
        return count
    }

    private fun isWelfareBindMethod(method: Method): Boolean {
        val params = method.parameterTypes
        return method.returnType == Void.TYPE &&
            params.size == 2 &&
            params[1] == Int::class.javaPrimitiveType &&
            params[0].name.contains("WelfareViewHolder")
    }

    private fun hookBottomManageSpace(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val fragmentClass = XposedCompat.findClassOrNull(
            BaiduAboutMeHookPoints.ABOUT_ME_BOTTOM_FRAGMENT,
            cl,
        ) ?: run {
            XposedCompat.logD("[$TAG] AboutMeBottomFragment not found")
            return 0
        }

        var count = 0
        for (method in fragmentClass.declaredMethods) {
            if (!isManageSpaceRenderMethod(method)) continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (isManageSpaceEnabled()) hideMiddleManageSpace(chain.thisObject, method.name)
                result
            }
            count++
            XposedCompat.logD("[$TAG] bottom manage-space hook installed: ${method.name}")
        }
        return count
    }

    private fun isManageSpaceRenderMethod(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            (
                method.name == "refreshManageSpace" && method.parameterTypes.isEmpty() ||
                    method.name == "showManageSpace" && method.parameterTypes.size == 1
                )
    }

    private fun hideMiddleManageSpace(fragment: Any?, source: String) {
        val root = fragmentRoot(fragment)
        hideByEntryName(
            root,
            MIDDLE_MANAGE_SPACE_ID,
            "manage space via $source",
        )
        hideByEntryName(
            root,
            MIDDLE_MANAGE_SPACE_ARROW_ID,
            "manage space arrow via $source",
        )
    }

    private fun hideConfiguredTexts(root: View?, source: String) {
        val targets = activeTextTargets()
        if (root == null || targets.isEmpty()) return
        hideConfiguredTextInTree(root, targets, source)
    }

    private fun hideConfiguredTextInTree(root: View, targets: List<TextTarget>, source: String): Boolean {
        if (root is TextView) {
            val text = root.text?.toString()
            val target = targets.firstOrNull { it.text == text }
            if (target != null) {
                hideView(root)
                XposedCompat.logD("[$TAG] ${target.label} hidden via $source")
                return true
            }
        }
        if (root !is ViewGroup) return false
        var hidden = false
        for (index in 0 until root.childCount) {
            hidden = hideConfiguredTextInTree(root.getChildAt(index), targets, source) || hidden
        }
        return hidden
    }

    private fun hookCoinCenterRewardSubtitle(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val fragmentClass = XposedCompat.findClassOrNull(
            BaiduAboutMeHookPoints.COIN_CENTER_V2_FRAGMENT,
            cl,
        ) ?: run {
            XposedCompat.logD("[$TAG] NewAboutMeCoinCenterV2Fragment not found")
            return 0
        }
        val tagDataClass = XposedCompat.findClassOrNull(
            BaiduAboutMeHookPoints.COIN_CENTER_TAG_DATA,
            cl,
        ) ?: run {
            XposedCompat.logD("[$TAG] CoinCenterTagData not found")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(fragmentClass, INIT_VIEWS_METHOD, tagDataClass) ?: run {
            XposedCompat.logD("[$TAG] coin center initViews(CoinCenterTagData) not found")
            return 0
        }

        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (isRewardEnabled()) {
                val root = fragmentRoot(chain.thisObject)
                hideByEntryName(root, REWARD_SUBTITLE_ROOT_ID, "reward subtitle")
                hideByEntryName(root, REWARD_SUBTITLE_ARROW_ID, "reward subtitle arrow")
            }
            result
        }
        XposedCompat.logD("[$TAG] reward subtitle hook installed: ${method.name}")
        return 1
    }

    private fun hideByEntryName(root: View?, idName: String, label: String): Boolean {
        if (root == null) return false
        val resources = root.resources ?: return false
        val packageName = root.context?.packageName ?: return false
        val id = resources.getIdentifier(idName, "id", packageName)
        if (id == 0) return false
        val view = root.findViewById<View>(id) ?: return false
        hideView(view)
        XposedCompat.logD("[$TAG] $label hidden by id: $idName")
        return true
    }

    private fun hideView(view: View) {
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
    }

    private fun fragmentRoot(fragment: Any?): View? {
        return fragment?.let {
            runCatching { it.javaClass.getMethod("getView").invoke(it) as? View }.getOrNull()
        }
    }

    private fun itemView(holder: Any?): View? {
        var current = holder?.javaClass
        while (current != null) {
            try {
                val field = current.getDeclaredField("itemView")
                field.isAccessible = true
                return field.get(holder) as? View
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun activeTextTargets(): List<TextTarget> {
        val options = HookSettings.aboutMeOptions()
        if (!options.isMyPageCustomizeEnabled) return emptyList()
        return buildList {
            if (options.isAboutMeAccountExitTextHidden) {
                add(TextTarget(TEXT_ACCOUNT_EXIT, "account_exit_text"))
            }
            if (options.isAboutMeStarSkinTextHidden) {
                add(TextTarget(TEXT_STAR_SKIN, "star_skin_text"))
            }
            if (options.isAboutMeFreeDataCardTextHidden) {
                add(TextTarget(TEXT_FREE_DATA_CARD, "free_data_card_text"))
            }
        }
    }

    private fun isAnyEnabled(): Boolean =
        activeTextTargets().isNotEmpty() || isManageSpaceEnabled() || isRewardEnabled()

    private fun isManageSpaceEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeManageSpaceTextHidden
    }

    private fun isRewardEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeRewardTextHidden
    }

    private data class TextTarget(val text: String, val label: String)
}
