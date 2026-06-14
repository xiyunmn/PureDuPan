package com.xiyunmn.puredupan.hook.feature.ui

import android.app.Activity
import android.content.Intent
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook

/**
 * Receives image picker results launched from module settings entry points.
 */
object SettingsImagePickerResultHook {
    private val hookState = HookState()
    private val resultHostClasses = listOf(
        StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY,
        StableBaiduPanHookPoints.NEW_ABOUT_ME_ACTIVITY,
        StableBaiduPanHookPoints.HOME_ACTIVITY,
        StableBaiduPanHookPoints.MAIN_ACTIVITY,
    )

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installedCount = 0

            for (className in resultHostClasses) {
                val activityClass = XposedCompat.findClassOrNull(className, cl)
                if (activityClass == null) {
                    XposedCompat.logD("[SettingsImagePickerResultHook] class NOT FOUND: $className")
                    continue
                }

                val method = XposedCompat.findMethodOrNull(
                    activityClass,
                    "onActivityResult",
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    Intent::class.java,
                )
                if (method == null) {
                    XposedCompat.logD("[SettingsImagePickerResultHook] onActivityResult NOT FOUND: $className")
                    continue
                }

                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        SettingsMenuHook.handleMemberCardBackgroundImageResult(
                            context = chain.thisObject as? Activity,
                            requestCode = chain.args.getOrNull(0) as? Int ?: -1,
                            resultCode = chain.args.getOrNull(1) as? Int ?: Activity.RESULT_CANCELED,
                            data = chain.args.getOrNull(2) as? Intent,
                        )
                    } catch (e: Exception) {
                        XposedCompat.logD("[SettingsImagePickerResultHook] handle result failed: ${e.message}")
                    }
                    result
                }
                installedCount++
                XposedCompat.logD("[SettingsImagePickerResultHook] onActivityResult hooked: $className")
            }

            if (installedCount == 0) {
                XposedCompat.log("[SettingsImagePickerResultHook] no onActivityResult hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[SettingsImagePickerResultHook] hooks INSTALLED: $installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SettingsImagePickerResultHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

}
