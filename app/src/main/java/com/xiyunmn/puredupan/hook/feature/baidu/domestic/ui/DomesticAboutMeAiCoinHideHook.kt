package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui

import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.membercard.AboutMeTopHeteromoDexKitResolver

/**
 * 隐藏我的页顶部会员卡上的 AI 硬币入口（layout_ai_coin）。
 *
 * 迁移到渲染入口层：layout_ai_coin 由 AboutMeTopFragmentHeteromo.setCardUi→initAiPoint() 渲染，
 * 复用会员卡的 AboutMeTopHeteromoDexKitResolver 定位 setCardUi，proceed() 后按稳定资源名
 * layout_ai_coin 单次 findViewById 定位并设为 GONE。宿主每次数据变化重渲染即触发一次。
 *
 * 属「我的页定制」域（KEY_HIDE_ABOUT_ME_AI_COIN_ASSET，仅国内/三星可见），故独立于会员卡 hook
 * 安装、独立设置门；与会员卡共用同一 setCardUi 渲染入口与 resolver。
 *
 * 已从 AboutMeGodModeHook 的 DecorView OnGlobalLayout/OnPreDraw + postDelayed 级联中移除本项。
 */
internal object DomesticAboutMeAiCoinHideHook {
    private const val TAG = "DomesticAboutMeAiCoinHideHook"
    private const val AI_COIN_ASSET_ID_NAME = "layout_ai_coin"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val fragmentClass = AboutMeTopHeteromoDexKitResolver.resolve(cl) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] AboutMeTopFragmentHeteromo NOT RESOLVED")
                return
            }
            val method = AboutMeTopHeteromoDexKitResolver.findSetCardUiMethod(fragmentClass) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] setCardUi NOT FOUND")
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
                        if (root != null) hideAiCoin(root)
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

    private fun hideAiCoin(root: View) {
        val resources = root.resources ?: return
        val packageName = root.context?.packageName ?: return
        val id = resources.getIdentifier(AI_COIN_ASSET_ID_NAME, "id", packageName)
        if (id == 0) {
            XposedCompat.logD("[$TAG] $AI_COIN_ASSET_ID_NAME resource id not found")
            return
        }
        val view = root.findViewById<View>(id) ?: return
        if (view.visibility != View.GONE) {
            view.visibility = View.GONE
            XposedCompat.logD("[$TAG] ai coin asset hidden via render entry")
        }
    }

    private fun isEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeAiCoinAssetHidden
    }
}
