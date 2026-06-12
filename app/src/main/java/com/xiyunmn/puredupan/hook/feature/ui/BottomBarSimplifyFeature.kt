package com.xiyunmn.puredupan.hook.feature.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * 底栏定制 Feature。
 *
 * Hook [MainActivity.onCreate] 方法，在 Activity 视图构建完成后根据用户配置
 * 动态隐藏底部导航栏中指定的 Tab。
 *
 * 受 [ConfigManager.KEY_CUSTOM_BOTTOM_BAR] 主开关及 5 个子开关控制。
 */
object BottomBarSimplifyFeature {
    private val hookState = HookState()

    /** Tab 资源 ID 名称 → 对应的隐藏开关读取器 */
    private data class TabTarget(
        val idName: String,
        val label: String,
        val isHidden: () -> Boolean,
    )

    private val tabTargets = listOf(
        TabTarget("rb_home",        "首页") { ConfigManager.isBottomBarTabHomeHidden },
        TabTarget("rb_filelist",    "文件") { ConfigManager.isBottomBarTabFileHidden },
        TabTarget("rb_share",       "共享") { ConfigManager.isBottomBarTabShareHidden },
        TabTarget("rb_findresoure", "会员") { ConfigManager.isBottomBarTabVipHidden },
        TabTarget("rb_about_me",    "我的") { ConfigManager.isBottomBarTabMineHidden },
    )

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isBottomBarCustomEnabled) {
            XposedCompat.log("[BottomBarSimplifyFeature] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val activityClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.MAIN_ACTIVITY, cl
            ) ?: run {
                XposedCompat.log(
                    "[BottomBarSimplifyFeature] MainActivity class NOT FOUND: " +
                        StableBaiduPanHookPoints.MAIN_ACTIVITY
                )
                return
            }

            val onCreateMethod = XposedCompat.findMethodOrNull(
                activityClass, "onCreate", Bundle::class.java
            ) ?: run {
                XposedCompat.log("[BottomBarSimplifyFeature] MainActivity.onCreate NOT FOUND")
                return
            }

            mod.hook(onCreateMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    applyTabVisibility(chain.thisObject as? Activity)
                } catch (e: Exception) {
                    XposedCompat.logD {
                        "[BottomBarSimplifyFeature] applyTabVisibility failed (non-fatal): ${e.message}"
                    }
                }
                result
            }

            XposedCompat.log("[BottomBarSimplifyFeature] hook INSTALLED: ${StableBaiduPanHookPoints.MAIN_ACTIVITY}.onCreate")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[BottomBarSimplifyFeature] install FAILED: ${e.message}")
        }
    }

    /**
     * 遍历 5 个 Tab 目标，根据当前配置决定是否隐藏。
     * 使用动态资源 ID 查找，避免硬编码数字 ID。
     */
    private fun applyTabVisibility(activity: Activity?) {
        if (activity == null) return
        if (!ConfigManager.isBottomBarCustomEnabled) return

        var hiddenCount = 0
        for (tab in tabTargets) {
            try {
                if (!tab.isHidden()) continue

                val resId = activity.resources.getIdentifier(
                    tab.idName, "id", activity.packageName
                )
                if (resId == 0) {
                    XposedCompat.logD(
                        "[BottomBarSimplifyFeature] ${tab.label} tab resId not found: ${tab.idName}"
                    )
                    continue
                }

                val view = activity.findViewById<View>(resId)
                if (view != null) {
                    view.visibility = View.GONE
                    hiddenCount++
                    XposedCompat.logD(
                        "[BottomBarSimplifyFeature] ${tab.label} tab hidden (${tab.idName})"
                    )
                } else {
                    XposedCompat.logD(
                        "[BottomBarSimplifyFeature] ${tab.label} tab view not in hierarchy (resId=$resId)"
                    )
                }
            } catch (e: Exception) {
                XposedCompat.logD(
                    "[BottomBarSimplifyFeature] ${tab.label} tab hide failed: ${e.message}"
                )
            }
        }

        if (hiddenCount > 0) {
            XposedCompat.log(
                "[BottomBarSimplifyFeature] applied: $hiddenCount tab(s) hidden"
            )
        }
    }

}
