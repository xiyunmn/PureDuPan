package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme

import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAboutMeHookPoints

/** 阻止“我的”页底部横幅请求，并在渲染入口隐藏缓存或外部推送内容。 */
object AboutMeBannerHideHook {
    private const val TAG = "AboutMeBannerHideHook"
    private const val BANNER_ID = "aboutme_banner"
    private const val UPDATE_ITEMS_PREFIX = "updateItems\$"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val fragmentClass = XposedCompat.findClassOrNull(
                BaiduAboutMeHookPoints.ABOUT_ME_BOTTOM_FRAGMENT,
                cl,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] AboutMeBottomFragment NOT FOUND")
                return
            }

            var installed = 0
            installed += hookInitBanner(fragmentClass)
            installed += hookBlockMethod(fragmentClass, "loadBusinessAD")
            installed += hookBlockMethod(fragmentClass, "loadOperationActive")
            installed += hookBlockMethod(fragmentClass, "businessAdUpdate")
            installed += hookUpdateItems(fragmentClass)

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

    private fun hookInitBanner(fragmentClass: Class<*>): Int {
        val mod = XposedCompat.module ?: return 0
        val method = fragmentClass.declaredMethods.firstOrNull {
            it.name == "initBanner" && it.parameterTypes.isEmpty()
        } ?: return 0
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (isEnabled()) hideBanner(fragmentRoot(chain.thisObject), "initBanner")
            result
        }
        XposedCompat.logD("[$TAG] render hook installed: ${fragmentClass.simpleName}.initBanner")
        return 1
    }

    private fun hookBlockMethod(fragmentClass: Class<*>, methodName: String): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (method in fragmentClass.declaredMethods) {
            if (method.name != methodName) continue
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    hideBanner(fragmentRoot(chain.thisObject), methodName)
                    null
                } else {
                    chain.proceed()
                }
            }
            count++
            XposedCompat.logD("[$TAG] block hook installed: ${fragmentClass.simpleName}.${method.name}")
        }
        return count
    }

    private fun hookUpdateItems(fragmentClass: Class<*>): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (method in fragmentClass.declaredMethods) {
            if (!method.name.startsWith(UPDATE_ITEMS_PREFIX)) continue
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    hideBanner(fragmentRoot(chain.thisObject), method.name)
                    null
                } else {
                    chain.proceed()
                }
            }
            count++
            XposedCompat.logD("[$TAG] updateItems hook installed: ${fragmentClass.simpleName}.${method.name}")
        }
        return count
    }

    private fun fragmentRoot(fragment: Any?): View? {
        return fragment?.let {
            runCatching { it.javaClass.getMethod("getView").invoke(it) as? View }.getOrNull()
        }
    }

    private fun hideBanner(root: View?, source: String) {
        if (root == null) return
        val resources = root.resources ?: return
        val packageName = root.context?.packageName ?: return
        val id = resources.getIdentifier(BANNER_ID, "id", packageName)
        if (id == 0) return
        val view = root.findViewById<View>(id) ?: return
        val shouldLog = view.visibility != View.GONE || view.alpha != 0f || view.isEnabled || view.isClickable
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
        if (shouldLog) {
            XposedCompat.logD("[$TAG] banner hidden via $source")
        }
    }

    private fun isEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeBannerRemoved
    }
}
