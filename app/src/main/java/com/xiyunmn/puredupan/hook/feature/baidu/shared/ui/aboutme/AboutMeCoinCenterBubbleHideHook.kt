package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme

import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAboutMeHookPoints

/**
 * 隐藏我的页金币中心气泡（v_bubble），三星版额外隐藏余额（iv_icon/tv_money/tv_yuan）。
 *
 * 迁移到渲染入口层：金币中心由运营模块 fragment NewAboutMeCoinCenterV2Fragment.initViews(CoinCenterTagData)
 * 填充（binding.vBubble 等）。hook 该渲染入口，proceed() 后在 fragment.getView() 内按明文资源名单次
 * findViewById 隐藏，宿主每次数据刷新重填即触发一次。
 *
 * 运营模块（Rubik）类名与 initViews 方法名三样本（国内 13.28.9、三星 13.27.8、国际 13.11.8）均明文，
 * 走稳定直连，不纳入 DexKit。资源名 v_bubble 明文；隐藏用 getIdentifier，抗 binding 字段混淆。
 *
 * 属共享「我的页定制」域（KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE，baiduSharedMyPageCustomize，三端可见）。
 * 已从 AboutMeGodModeHook 的 DecorView OnGlobalLayout/OnPreDraw + findCoinCenterRoot 全树递归中移除本项。
 */
object AboutMeCoinCenterBubbleHideHook {
    private const val TAG = "AboutMeCoinCenterBubbleHideHook"
    private const val INIT_VIEWS_METHOD = "initViews"

    private const val BUBBLE_ID = "v_bubble"
    private const val COIN_CENTER_ICON_ID = "iv_icon"
    private const val COIN_CENTER_MONEY_ID = "tv_money"
    private const val COIN_CENTER_YUAN_ID = "tv_yuan"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val fragmentClass = XposedCompat.findClassOrNull(BaiduAboutMeHookPoints.COIN_CENTER_V2_FRAGMENT, cl) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] NewAboutMeCoinCenterV2Fragment NOT FOUND")
                return
            }
            val tagDataClass = XposedCompat.findClassOrNull(BaiduAboutMeHookPoints.COIN_CENTER_TAG_DATA, cl) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] CoinCenterTagData NOT FOUND")
                return
            }
            val method = XposedCompat.findMethodOrNull(
                fragmentClass,
                INIT_VIEWS_METHOD,
                tagDataClass,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] initViews(CoinCenterTagData) NOT FOUND")
                return
            }

            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (isEnabled()) {
                    try {
                        val fragment = chain.thisObject
                        val root = fragment?.let {
                            runCatching { it.javaClass.getMethod("getView").invoke(it) as? View }.getOrNull()
                        }
                        if (root != null) hideCoinCenter(root)
                    } catch (e: Exception) {
                        XposedCompat.logD("[$TAG] apply failed: ${e.message}")
                    }
                }
                result
            }

            XposedCompat.log(
                "[$TAG] hook INSTALLED: ${method.declaringClass.name}.${method.name}",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[$TAG] FAILED: ${e.message}")
        }
    }

    private fun hideCoinCenter(root: View) {
        hideByEntryName(root, BUBBLE_ID, "coin center bubble")
        val context = root.context ?: return
        if (BaiduFeatureRuntime.isSamsungHost(context)) {
            hideByEntryName(root, COIN_CENTER_ICON_ID, "coin center icon")
            hideByEntryName(root, COIN_CENTER_MONEY_ID, "coin center money")
            hideByEntryName(root, COIN_CENTER_YUAN_ID, "coin center yuan")
        }
    }

    private fun hideByEntryName(root: View, idName: String, label: String) {
        val resources = root.resources ?: return
        val packageName = root.context?.packageName ?: return
        val id = resources.getIdentifier(idName, "id", packageName)
        if (id == 0) return
        val view = root.findViewById<View>(id) ?: return
        if (view.visibility != View.GONE) {
            view.visibility = View.GONE
            XposedCompat.logD("[$TAG] $label hidden via render entry")
        }
    }

    private fun isEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeCoinCenterBubbleHidden
    }
}
