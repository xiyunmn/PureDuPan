package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme

import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAboutMeHookPoints
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Hides about-me entries only from stable render entries and fixed resource ids.
 */
object AboutMeTextEntryHideHook {
    private const val TAG = "AboutMeTextEntryHideHook"

    private const val TEXT_ACCOUNT_EXIT = "账号、退出"
    private const val TEXT_STAR_SKIN = "明星皮肤上线啦"
    private const val TEXT_FREE_DATA_CARD = "免流量卡、领无限空间"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_PERSONAL_THEME_SETTING = "personal_theme_setting"

    private const val MIDDLE_MANAGE_SPACE_ID = "manage_space"
    private const val MIDDLE_MANAGE_SPACE_ARROW_ID = "manage_space_arrow"
    private const val REWARD_SUBTITLE_ROOT_ID = "cl_subtitle"
    private const val REWARD_SUBTITLE_ARROW_ID = "iv_subtitle_arrow"
    private const val INIT_VIEWS_METHOD = "initViews"

    private val hookState = HookState()
    private val stringFieldCache = ConcurrentHashMap<Class<*>, List<Field>>()

    internal fun hook(cl: ClassLoader) {
        if (!isAnyEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            if (isAccountExitEnabled() || isStarSkinEnabled() || isFreeDataCardEnabled()) {
                installed += hookMiddleRows(cl)
            }
            if (isFreeDataCardEnabled()) {
                installed += hookWelfareItems(cl)
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
        val method = AboutMeMiddleViewHolderDexKitResolver.resolve(cl) ?: run {
            XposedCompat.logD("[$TAG] BaseMiddleViewHolder.bind not found")
            return 0
        }

        method.isAccessible = true
        mod.hook(method).intercept { chain ->
            val node = chain.args.firstOrNull()
            val accountExitNode = isAccountExitEnabled() &&
                hasStringValue(node, KEY_SETTINGS) &&
                hasStringValue(node, TEXT_ACCOUNT_EXIT)
            val starNode = isStarSkinEnabled() && hasStringValue(node, KEY_PERSONAL_THEME_SETTING)
            if (isAccountExitEnabled()) {
                clearStringValues(node, setOf(TEXT_ACCOUNT_EXIT), "account/exit hint")
            }
            if (isStarSkinEnabled()) {
                clearStringValues(node, setOf(TEXT_STAR_SKIN), "star-skin hint")
            }
            if (isFreeDataCardEnabled()) {
                clearStringValues(node, setOf(TEXT_FREE_DATA_CARD), "free-data middle row hint")
            }
            val result = chain.proceed()
            if (accountExitNode) {
                clearHolderHint(chain.thisObject, "account/exit")
            }
            if (starNode) {
                clearHolderHint(chain.thisObject, "star-skin")
            }
            result
        }
        XposedCompat.logD(
            "[$TAG] middle model hook installed: ${method.declaringClass.name}.${method.name}",
        )
        return 1
    }

    private fun hookWelfareItems(cl: ClassLoader): Int {
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
            if (!isWelfareSetItemsMethod(method)) continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (isFreeDataCardEnabled()) {
                    (chain.args.firstOrNull() as? List<*>)?.forEach { item ->
                        clearStringValues(item, setOf(TEXT_FREE_DATA_CARD), "free-data welfare item")
                    }
                }
                chain.proceed()
            }
            count++
            XposedCompat.logD("[$TAG] welfare model hook installed: ${method.name}")
        }
        return count
    }

    private fun isWelfareSetItemsMethod(method: Method): Boolean {
        val params = method.parameterTypes
        return method.returnType == Void.TYPE &&
            params.size == 1 &&
            List::class.java.isAssignableFrom(params[0])
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

    private fun hasStringValue(target: Any?, value: String): Boolean {
        target ?: return false
        return stringFields(target.javaClass).any { field ->
            runCatching { field.get(target) as? String }.getOrNull() == value
        }
    }

    private fun clearStringValues(target: Any?, values: Set<String>, label: String): Boolean {
        target ?: return false
        var changed = false
        for (field in stringFields(target.javaClass)) {
            val current = runCatching { field.get(target) as? String }.getOrNull() ?: continue
            if (values.none { current == it || current.contains(it) }) continue
            runCatching {
                field.set(target, "")
                changed = true
            }.onFailure {
                XposedCompat.logD("[$TAG] $label field clear failed: ${field.name}, ${it.message}")
            }
        }
        if (changed) {
            XposedCompat.logD("[$TAG] $label cleared from model: ${target.javaClass.name}")
        }
        return changed
    }

    private fun stringFields(clazz: Class<*>): List<Field> {
        return stringFieldCache.getOrPut(clazz) {
            buildList {
                var current: Class<*>? = clazz
                while (current != null && current != Any::class.java) {
                    for (field in current.declaredFields) {
                        if (Modifier.isStatic(field.modifiers)) continue
                        if (field.type != String::class.java) continue
                        field.isAccessible = true
                        add(field)
                    }
                    current = current.superclass
                }
            }
        }
    }

    private fun clearHolderHint(holder: Any?, label: String) {
        holder ?: return
        var current: Class<*>? = holder.javaClass
        while (current != null && current != Any::class.java) {
            val method = current.declaredMethods.firstOrNull {
                it.name == "setHint" &&
                    it.returnType == Void.TYPE &&
                    it.parameterTypes.contentEquals(arrayOf(String::class.java))
            }
            if (method != null) {
                runCatching {
                    method.isAccessible = true
                    method.invoke(holder, "")
                    XposedCompat.logD("[$TAG] $label render hint cleared")
                }
                return
            }
            current = current.superclass
        }
    }

    private fun isAnyEnabled(): Boolean =
        isAccountExitEnabled() ||
            isStarSkinEnabled() ||
            isFreeDataCardEnabled() ||
            isManageSpaceEnabled() ||
            isRewardEnabled()

    private fun isAccountExitEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeAccountExitTextHidden
    }

    private fun isStarSkinEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeStarSkinTextHidden
    }

    private fun isFreeDataCardEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeFreeDataCardTextHidden
    }

    private fun isManageSpaceEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeManageSpaceTextHidden
    }

    private fun isRewardEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeRewardTextHidden
    }
}
