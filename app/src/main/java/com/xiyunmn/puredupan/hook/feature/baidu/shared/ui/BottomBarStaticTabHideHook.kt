package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime

/**
 * Hides static bottom tabs at MainActivity's fixed render entry.
 */
internal object BottomBarStaticTabHideHook {
    private const val INIT_VIEW_METHOD = "initView"
    private const val INIT_TABS_METHOD = "initTabs"
    private const val INIT_TABS_SKIN_METHOD = "initTabsSkin"
    private const val REFRESH_TAB_VIEW_TEXT_METHOD = "refreshTabViewText"
    private const val SET_NORMAL_TAB_IMG_METHOD = "setNormalTabImg"
    private const val SET_SKIN_TAB_IMG_METHOD = "setSkinTabImg"
    private const val SET_TAB_RAISED_BG_VISIBLE_METHOD = "setTabRaisedBgVisible"
    private const val ON_CLICK_METHOD = "onClick"

    private val hookState = HookState()
    private val refreshVisualDepth = ThreadLocal<Int>()
    private val selectedTabDuringRefresh = ThreadLocal<View>()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[BottomBarStaticTabHideHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val className = BaiduFeatureRuntime.currentMainActivityClassName()
            val clazz = className?.let { XposedCompat.findClassOrNull(it, cl) }
            val method = clazz?.declaredMethods?.firstOrNull {
                it.name == INIT_VIEW_METHOD && it.parameterTypes.isEmpty()
            }

            if (method == null) {
                hookState.reset()
                XposedCompat.log("[BottomBarStaticTabHideHook] MainActivity.initView NOT FOUND")
                return
            }

            val initTabsMethod = clazz.declaredMethods.firstOrNull {
                it.name == INIT_TABS_METHOD &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Intent::class.java
            }
            if (initTabsMethod != null) {
                initTabsMethod.isAccessible = true
                mod.hook(initTabsMethod).intercept { chain ->
                    if (isEnabled()) {
                        applyStaticTabVisibility(chain.thisObject as Activity)
                        applyAigcFallbackVisibility(chain.thisObject as Activity)
                    }
                    chain.proceed()
                }
                XposedCompat.log(
                    "[BottomBarStaticTabHideHook] hook INSTALLED: " +
                        "${clazz.name}.$INIT_TABS_METHOD (pre-selection)",
                )
            } else {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (isEnabled()) {
                        applyStaticTabVisibility(chain.thisObject as Activity)
                    }
                    result
                }
                XposedCompat.log(
                    "[BottomBarStaticTabHideHook] hook INSTALLED: " +
                        "${clazz.name}.$INIT_VIEW_METHOD (post-init fallback)",
                )
            }
            hookAigcRefreshMethods(clazz)
            hookRefreshSelectedTabProtection(clazz)
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[BottomBarStaticTabHideHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookAigcRefreshMethods(clazz: Class<*>) {
        if (!HookSettings.isBottomBarTabAigcHidden) return
        if (XposedCompat.module == null) return
        hookSetTabRaisedBgVisible(clazz)
        hookInitTabsSkin(clazz)
        hookAigcClick(clazz)
    }

    private fun hookInitTabsSkin(clazz: Class<*>) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == INIT_TABS_SKIN_METHOD && it.parameterTypes.isEmpty()
        } ?: return
        method.isAccessible = true
        XposedCompat.module?.hook(method)?.intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            if (activity != null && shouldHideAigcTab(activity)) {
                collapseViewById(activity, "main_tab_raised_bg")
            }
            result
        }
    }

    private fun hookSetTabRaisedBgVisible(clazz: Class<*>) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == SET_TAB_RAISED_BG_VISIBLE_METHOD &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        } ?: return
        method.isAccessible = true
        XposedCompat.module?.hook(method)?.intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && shouldHideAigcTab(activity)) {
                chain.args[0] = false
            }
            chain.proceed()
        }
    }

    private fun hookRefreshSelectedTabProtection(clazz: Class<*>) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == REFRESH_TAB_VIEW_TEXT_METHOD && it.parameterTypes.size == 1
        } ?: return
        method.isAccessible = true
        XposedCompat.module?.hook(method)?.intercept { chain ->
            val depth = refreshVisualDepth.get() ?: 0
            val previousSelectedTab = selectedTabDuringRefresh.get()
            val selectedTab = chain.args.firstOrNull() as? View
            refreshVisualDepth.set(depth + 1)
            if (selectedTab != null) selectedTabDuringRefresh.set(selectedTab) else selectedTabDuringRefresh.remove()
            try {
                chain.proceed()
            } finally {
                if (depth == 0) refreshVisualDepth.remove() else refreshVisualDepth.set(depth)
                if (previousSelectedTab == null) {
                    selectedTabDuringRefresh.remove()
                } else {
                    selectedTabDuringRefresh.set(previousSelectedTab)
                }
            }
        }
        hookTabImageReset(clazz, SET_NORMAL_TAB_IMG_METHOD, tabArgumentIndex = 3)
        hookTabImageReset(clazz, SET_SKIN_TAB_IMG_METHOD, tabArgumentIndex = 2)
    }

    private fun hookTabImageReset(
        clazz: Class<*>,
        name: String,
        tabArgumentIndex: Int,
    ) {
        val method = clazz.declaredMethods.firstOrNull { it.name == name } ?: return
        method.isAccessible = true
        XposedCompat.module?.hook(method)?.intercept { chain ->
            val tab = chain.args.getOrNull(tabArgumentIndex) as? View
            val selectedTab = selectedTabDuringRefresh.get()
            if ((refreshVisualDepth.get() ?: 0) > 0 && tab != null && tab.id == selectedTab?.id) {
                return@intercept null
            }
            chain.proceed()
        }
    }

    private fun hookAigcClick(clazz: Class<*>) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == ON_CLICK_METHOD &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == View::class.java
        } ?: return
        method.isAccessible = true
        XposedCompat.module?.hook(method)?.intercept { chain ->
            val activity = chain.thisObject as? Activity
            val view = chain.args.firstOrNull() as? View
            if (activity != null && view != null && shouldHideAigcTab(activity) && isAigcTabView(activity, view)) {
                return@intercept null
            }
            chain.proceed()
        }
    }

    private fun applyStaticTabVisibility(activity: Activity) {
        hideTab(activity, "rb_home", HookSettings.isBottomBarTabHomeHidden)
        hideTab(activity, "rb_filelist", HookSettings.isBottomBarTabFileHidden)
        hideTab(activity, "rb_share", HookSettings.isBottomBarTabShareHidden)
        hideTab(activity, "rb_findresoure", HookSettings.isBottomBarTabVipHidden)
        hideTab(activity, "rb_about_me", HookSettings.isBottomBarTabMineHidden)
    }

    private fun applyAigcFallbackVisibility(activity: Activity) {
        if (!shouldHideAigcTab(activity)) return
        hideAigcContainerByChildId(activity, "aigc_cloud")
        hideAigcContainerByChildId(activity, "aigc_afx")
        collapseViewById(activity, "aigc_hi_lottie")
        collapseViewById(activity, "aigc_afx_click_area")
        collapseViewById(activity, "main_tab_raised_bg")
    }

    private fun hideAigcContainerByChildId(activity: Activity, childIdName: String) {
        val id = activity.resources.getIdentifier(childIdName, "id", activity.packageName)
        if (id == 0) return
        val child = activity.findViewById<View>(id) ?: return
        (child.parent as? View)?.let(::collapseView)
        collapseView(child)
    }

    private fun collapseViewById(activity: Activity, idName: String) {
        val id = activity.resources.getIdentifier(idName, "id", activity.packageName)
        if (id == 0) return
        activity.findViewById<View>(id)?.let(::collapseView)
    }

    private fun collapseView(view: View) {
        view.visibility = View.GONE
        view.isEnabled = false
        view.isClickable = false
        view.setOnClickListener(null)
    }

    private fun hideTab(activity: Activity, idName: String, hidden: Boolean) {
        if (!hidden) return
        val id = activity.resources.getIdentifier(idName, "id", activity.packageName)
        if (id == 0) return
        activity.findViewById<View>(id)?.visibility = View.GONE
    }

    private fun shouldHideAigcTab(activity: Activity): Boolean =
        HookSettings.isBottomBarCustomEnabled &&
            HookSettings.isBottomBarTabAigcHidden &&
            !BaiduFeatureRuntime.isIntlHost(activity)

    private fun isAigcTabView(activity: Activity, view: View): Boolean =
        view.id == activity.resources.getIdentifier("aigc_cloud", "id", activity.packageName) ||
            view.id == activity.resources.getIdentifier("aigc_afx_click_area", "id", activity.packageName)

    private fun isEnabled(): Boolean =
        HookSettings.isBottomBarCustomEnabled &&
            (
                HookSettings.isBottomBarTabHomeHidden ||
                    HookSettings.isBottomBarTabFileHidden ||
                    HookSettings.isBottomBarTabShareHidden ||
                    HookSettings.isBottomBarTabVipHidden ||
                    HookSettings.isBottomBarTabMineHidden ||
                    HookSettings.isBottomBarTabAigcHidden
                )
}
