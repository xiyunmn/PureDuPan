package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.core.XposedCompat

object BottomBarSimplifyFeature {
    private const val TAB_CONTAINER_ID = "rg_tabs"
    private const val TAB_AIGC_CLOUD_ID = "aigc_cloud"
    private const val TAB_AIGC_AFX_ID = "aigc_afx"
    private const val TAB_AIGC_AFX_CLICK_AREA_ID = "aigc_afx_click_area"
    private const val TAB_AIGC_HI_LOTTIE_ID = "aigc_hi_lottie"
    private const val TAB_AIGC_RAISED_BACKGROUND_ID = "main_tab_raised_bg"
    private const val TAB_DISCOVERY_ID = "rb_findresoure"

    private val hookState = HookState()

    private data class TabTarget(
        val idName: String,
        val label: String,
        val isHidden: () -> Boolean,
    )

    private val tabTargets = listOf(
        TabTarget("rb_home", "首页") { HookSettings.isBottomBarTabHomeHidden },
        TabTarget("rb_filelist", "文件") { HookSettings.isBottomBarTabFileHidden },
        TabTarget(TAB_AIGC_CLOUD_ID, "AIGC") { HookSettings.isBottomBarTabAigcHidden },
        TabTarget("rb_share", "共享") { HookSettings.isBottomBarTabShareHidden },
        TabTarget("rb_findresoure", "会员") { HookSettings.isBottomBarTabVipHidden },
        TabTarget("rb_about_me", "我的") { HookSettings.isBottomBarTabMineHidden },
    )

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isBottomBarCustomEnabled) {
            XposedCompat.log("[BottomBarSimplifyFeature] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val mainActivityClassName = BaiduFeatureRuntime.currentMainActivityClassName()
                ?: run {
                    XposedCompat.log("[BottomBarSimplifyFeature] MainActivity host capability missing")
                    hookState.reset()
                    return
                }
            val activityClass = XposedCompat.findClassOrNull(
                mainActivityClassName,
                cl,
            ) ?: run {
                XposedCompat.log(
                    "[BottomBarSimplifyFeature] MainActivity class NOT FOUND: " +
                        mainActivityClassName,
                )
                return
            }

            var installed = 0

            XposedCompat.findMethodOrNull(activityClass, "initView")?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    applyTabVisibilitySafely(chain.thisObject as? Activity, "initView")
                    result
                }
                installed++
            }

            XposedCompat.findMethodOrNull(activityClass, "initTabsSkin")?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    applyTabVisibilitySafely(chain.thisObject as? Activity, "initTabsSkin")
                    result
                }
                installed++
            }

            if (installed == 0) {
                val onCreateMethod = XposedCompat.findMethodOrNull(
                    activityClass,
                    "onCreate",
                    Bundle::class.java,
                ) ?: run {
                    XposedCompat.log("[BottomBarSimplifyFeature] MainActivity tab init methods NOT FOUND")
                    return
                }
                mod.hook(onCreateMethod).intercept { chain ->
                    val result = chain.proceed()
                    applyTabVisibilitySafely(chain.thisObject as? Activity, "onCreate")
                    result
                }
                installed++
            }

            XposedCompat.log(
                "[BottomBarSimplifyFeature] hooks INSTALLED: $mainActivityClassName, count=$installed",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[BottomBarSimplifyFeature] install FAILED: ${e.message}")
        }
    }

    private fun applyTabVisibilitySafely(activity: Activity?, reason: String) {
        runCatching { applyTabVisibility(activity) }
            .onFailure {
                XposedCompat.logD(
                    "[BottomBarSimplifyFeature] apply failed ($reason): ${it.message}",
                )
            }
    }

    private fun applyTabVisibility(activity: Activity?) {
        if (activity == null) return
        if (!HookSettings.isBottomBarCustomEnabled) return

        if (HookSettings.isBottomBarTabAigcHidden) {
            hideAigcFeatureSlot(activity)
        }

        var hiddenCount = 0
        for (tab in tabTargets) {
            try {
                if (!tab.isHidden()) continue

                val resId = activity.resources.getIdentifier(
                    tab.idName,
                    "id",
                    activity.packageName,
                )
                if (resId == 0) {
                    XposedCompat.logD(
                        "[BottomBarSimplifyFeature] ${tab.label} tab resId not found: ${tab.idName}",
                    )
                    continue
                }

                val view = activity.findViewById<View>(resId)
                if (view != null) {
                    hideTabView(view)
                    hiddenCount++
                    XposedCompat.logD(
                        "[BottomBarSimplifyFeature] ${tab.label} tab hidden (${tab.idName})",
                    )
                } else {
                    XposedCompat.logD(
                        "[BottomBarSimplifyFeature] ${tab.label} tab view not in hierarchy (resId=$resId)",
                    )
                }
            } catch (e: Exception) {
                XposedCompat.logD(
                    "[BottomBarSimplifyFeature] ${tab.label} tab hide failed: ${e.message}",
                )
            }
        }

        if (hiddenCount > 0) {
            XposedCompat.log("[BottomBarSimplifyFeature] applied: $hiddenCount tab(s) hidden")
        }
    }

    private fun hideAigcFeatureSlot(activity: Activity) {
        runCatching {
            val aigcCloudTab = findViewByEntryName(activity, TAB_AIGC_CLOUD_ID)
            val aigcAfxTab = findViewByEntryName(activity, TAB_AIGC_AFX_ID)
            val raisedBackground = findViewByEntryName(activity, TAB_AIGC_RAISED_BACKGROUND_ID)
            val tabContainer = findViewByEntryName(activity, TAB_CONTAINER_ID) as? LinearLayout
            val hasDedicatedAigcSlot = aigcAfxTab != null || raisedBackground != null
            val aigcSlotContainer = if (hasDedicatedAigcSlot) {
                ((aigcAfxTab?.parent as? View) ?: (aigcCloudTab?.parent as? View))
                    ?.takeIf { it !== tabContainer }
            } else {
                null
            }
            val hadVisibleDedicatedAigcSlot = hasDedicatedAigcSlot &&
                (aigcCloudTab.isShownOrVisible() ||
                    aigcAfxTab.isShownOrVisible() ||
                    raisedBackground.isShownOrVisible())

            hideTabView(aigcCloudTab)
            hideTabView(aigcAfxTab)
            hideTabView(aigcSlotContainer)
            hideTabView(findViewByEntryName(activity, TAB_AIGC_AFX_CLICK_AREA_ID))
            hideTabView(findViewByEntryName(activity, TAB_AIGC_HI_LOTTIE_ID))
            hideTabView(raisedBackground)

            if (hadVisibleDedicatedAigcSlot) {
                val resoureTab = findViewByEntryName(activity, TAB_DISCOVERY_ID)
                hideTabView(resoureTab)
            }

            if (tabContainer != null) {
                reflowLinearLayoutChildren(tabContainer)
            }
        }.onFailure {
            XposedCompat.logD("[BottomBarSimplifyFeature] hideAigcFeatureSlot failed: ${it.message}")
        }
    }

    private fun findViewByEntryName(activity: Activity, idName: String): View? {
        val resId = activity.resources.getIdentifier(idName, "id", activity.packageName)
        if (resId == 0) return null
        return activity.findViewById(resId)
    }

    private fun hideTabView(view: View?) {
        if (view == null) return
        view.visibility = View.GONE
        val params = view.layoutParams
        if (params is LinearLayout.LayoutParams) {
            params.width = 0
            params.weight = 0f
            view.layoutParams = params
        }
        (view.parent as? ViewGroup)?.requestLayout()
    }

    private fun reflowLinearLayoutChildren(container: LinearLayout) {
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
            if (child.visibility == View.VISIBLE && child.id != View.NO_ID) {
                val idName = runCatching { child.resources.getResourceEntryName(child.id) }.getOrNull()
                if (idName != TAB_AIGC_CLOUD_ID && params.width == 0 && params.weight == 0f) {
                    params.weight = 1f
                    child.layoutParams = params
                }
            }
        }
        container.requestLayout()
    }

    private fun View?.isShownOrVisible(): Boolean {
        return this != null && (visibility == View.VISIBLE || isShown)
    }
}
