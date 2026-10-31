package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui

import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.aboutme.AboutMeTopHeteromoResolver

/** 在会员卡渲染后隐藏 AI 硬币入口，受“我的页定制”独立开关控制。 */
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
            val fragmentClass = AboutMeTopHeteromoResolver.resolve(cl) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] AboutMeTopFragmentHeteromo NOT RESOLVED")
                return
            }
            val method = AboutMeTopHeteromoResolver.findSetCardUiMethod(fragmentClass) ?: run {
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
