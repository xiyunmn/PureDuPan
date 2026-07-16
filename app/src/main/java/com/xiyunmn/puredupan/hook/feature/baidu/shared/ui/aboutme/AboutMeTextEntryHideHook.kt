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

    private const val TEXT_MANAGE_SPACE = "管理空间"
    private const val TEXT_REWARD = "领奖励"
    private const val TEXT_ACCOUNT_EXIT = "账号、退出"
    private const val TEXT_STAR_SKIN = "明星皮肤上线啦"
    private const val TEXT_FREE_DATA_CARD = "免流量卡、领无限空间"

    private const val TOP_MANAGE_SPACE_ID = "tv_quota_guide"
    private const val MIDDLE_MANAGE_SPACE_ID = "manage_space"

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
            installed += hookMiddleRows(cl)
            installed += hookWelfareCards(cl)
            installed += hookTopManageSpace(cl)
            installed += hookFragmentManageSpace(cl, BaiduAboutMeHookPoints.ABOUT_ME_MIDDLE_FRAGMENT)
            installed += hookFragmentManageSpace(cl, BaiduAboutMeHookPoints.ABOUT_ME_BOTTOM_FRAGMENT)

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

    private fun hookTopManageSpace(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val fragmentClass = XposedCompat.findClassOrNull(
            BaiduAboutMeHookPoints.USER_TOP_FRAGMENT,
            cl,
        ) ?: run {
            XposedCompat.logD("[$TAG] UserTopFragment not found")
            return 0
        }

        var count = 0
        for (method in fragmentClass.declaredMethods) {
            if (method.name != "showManageSpace") continue
            if (method.returnType != Void.TYPE || method.parameterTypes.size != 3) continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (isManageSpaceEnabled()) {
                    hideTextByEntryName(
                        fragmentRoot(chain.thisObject),
                        TOP_MANAGE_SPACE_ID,
                        TEXT_MANAGE_SPACE,
                        "top manage space",
                    )
                }
                result
            }
            count++
            XposedCompat.logD("[$TAG] top manage-space hook installed: ${method.name}")
        }
        return count
    }

    private fun hookFragmentManageSpace(cl: ClassLoader, className: String): Int {
        val mod = XposedCompat.module ?: return 0
        val fragmentClass = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.logD("[$TAG] fragment not found: $className")
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
            XposedCompat.logD("[$TAG] fragment manage-space hook installed: ${fragmentClass.simpleName}.${method.name}")
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
        hideTextByEntryName(
            fragmentRoot(fragment),
            MIDDLE_MANAGE_SPACE_ID,
            TEXT_MANAGE_SPACE,
            "manage space via $source",
        )
    }

    private fun hideConfiguredTexts(root: View?, source: String) {
        val targets = activeTextTargets().filterNot { it.text == TEXT_MANAGE_SPACE }
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

    private fun hideTextByEntryName(root: View?, idName: String, expectedText: String, label: String): Boolean {
        if (root == null) return false
        val resources = root.resources ?: return false
        val packageName = root.context?.packageName ?: return false
        val id = resources.getIdentifier(idName, "id", packageName)
        if (id == 0) return false
        val view = root.findViewById<TextView>(id) ?: return false
        if (view.text?.toString() != expectedText) return false
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
            if (options.isAboutMeManageSpaceTextHidden) {
                add(TextTarget(TEXT_MANAGE_SPACE, "manage_space_text"))
            }
            if (options.isAboutMeRewardTextHidden) {
                add(TextTarget(TEXT_REWARD, "reward_text"))
            }
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

    private fun isAnyEnabled(): Boolean = activeTextTargets().isNotEmpty()

    private fun isManageSpaceEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeManageSpaceTextHidden
    }

    private data class TextTarget(val text: String, val label: String)
}
