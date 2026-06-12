package com.xiyunmn.puredupan.hook.feature.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook

/**
 * 正式模块入口 Hook。
 *
 * 在"我的"页面 (AboutMeActivity) 的"扫一扫"按钮上绑定长按事件，
 * 长按呼出模块设置面板。
 *
 * 与旧的 [SettingsMenuHook] 不同，此入口不依赖动态符号扫描，
 * 使用已知固定类名和方法名直接 Hook。
 */
object FormalUiEntryHook {
    private const val SCAN_ICON_ID_NAME = "self_qrcode_scan_icon"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val activityClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY, cl
            )
            if (activityClass == null) {
                hookState.reset()
                XposedCompat.log(
                    "[FormalUiEntryHook] AboutMeActivity class NOT FOUND: " +
                        StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY
                )
                return
            }

            val onCreateMethod = XposedCompat.findMethodOrNull(
                activityClass, "onCreate", Bundle::class.java
            )
            if (onCreateMethod == null) {
                hookState.reset()
                XposedCompat.log("[FormalUiEntryHook] AboutMeActivity.onCreate NOT FOUND")
                return
            }

            mod.hook(onCreateMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    bindScanIconLongPress(chain.thisObject as? Activity)
                } catch (e: Exception) {
                    XposedCompat.logD {
                        "[FormalUiEntryHook] bind failed (non-fatal): ${e.message}"
                    }
                }
                result
            }
            XposedCompat.log("[FormalUiEntryHook] hook INSTALLED: ${StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY}.onCreate")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[FormalUiEntryHook] install FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    /**
     * 在给定 Activity 中查找"扫一扫"按钮并绑定长按事件。
     * 使用动态资源 ID 查找，避免硬编码资源 ID 因宿主版本更新而变化。
     */
    private fun bindScanIconLongPress(activity: Activity?) {
        if (activity == null) return

        val resId = activity.resources.getIdentifier(
            SCAN_ICON_ID_NAME, "id", activity.packageName
        )
        if (resId == 0) {
            XposedCompat.logD(
                "[FormalUiEntryHook] scan icon resId not found: $SCAN_ICON_ID_NAME"
            )
            return
        }

        val scanIconView = activity.findViewById<View>(resId)
        if (scanIconView == null) {
            XposedCompat.logD(
                "[FormalUiEntryHook] scan icon view not found in hierarchy (resId=$resId)"
            )
            return
        }

        scanIconView.setOnLongClickListener {
            try {
                SettingsMenuHook.showModuleSettingsDialog(activity, activity.classLoader)
            } catch (e: Exception) {
                XposedCompat.logW(
                    "[FormalUiEntryHook] show settings dialog failed: ${e.message}"
                )
            }
            true
        }
        XposedCompat.log(
            "[FormalUiEntryHook] long-press listener bound to $SCAN_ICON_ID_NAME (resId=$resId)"
        )
    }

}
