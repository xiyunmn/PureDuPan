package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme

import android.view.View
import android.widget.TextView
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

    private const val KEY_SETTINGS = "settings"
    private const val KEY_PERSONAL_THEME_SETTING = "personal_theme_setting"
    // The misspelling is part of the host's persisted node-key contract.
    private const val KEY_MORE_SERVICE = "more_servce"

    private const val MIDDLE_HINT_ID = "item_hint"
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
            val accountExitNode = isAccountExitEnabled() && hasStringValue(node, KEY_SETTINGS)
            val starNode = isStarSkinEnabled() && hasStringValue(node, KEY_PERSONAL_THEME_SETTING)
            val freeDataCardNode = isFreeDataCardEnabled() && hasStringValue(node, KEY_MORE_SERVICE)
            val result = chain.proceed()
            if (accountExitNode) {
                clearHolderHintById(chain.thisObject, "account/exit")
            }
            if (starNode) {
                clearHolderHintById(chain.thisObject, "star-skin")
            }
            if (freeDataCardNode) {
                clearHolderHintById(chain.thisObject, "free-data card")
            }
            result
        }
        XposedCompat.logD(
            "[$TAG] middle model hook installed: ${method.declaringClass.name}.${method.name}",
        )
        return 1
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

    private fun clearHolderHintById(holder: Any?, label: String) {
        val itemView = holderItemView(holder) ?: return
        val resources = itemView.resources ?: return
        val packageName = itemView.context?.packageName ?: return
        val hintId = resources.getIdentifier(MIDDLE_HINT_ID, "id", packageName)
        if (hintId == 0) return
        val hintView = itemView.findViewById<View>(hintId) as? TextView ?: return
        hintView.text = ""
        XposedCompat.logD("[$TAG] $label render hint cleared by id: $MIDDLE_HINT_ID")
    }

    private fun holderItemView(holder: Any?): View? {
        holder ?: return null
        return runCatching {
            holder.javaClass.getField("itemView").get(holder) as? View
        }.getOrNull()
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
