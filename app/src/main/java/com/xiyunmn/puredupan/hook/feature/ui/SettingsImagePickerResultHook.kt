package com.xiyunmn.puredupan.hook.feature.ui

import android.app.Activity
import android.content.Intent
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook

/**
 * Receives image picker results launched from the module settings panel in AboutMeActivity.
 */
object SettingsImagePickerResultHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val activityClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY,
                cl,
            ) ?: run {
                XposedCompat.log("[SettingsImagePickerResultHook] AboutMeActivity class NOT FOUND")
                hookState.reset()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                activityClass,
                "onActivityResult",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Intent::class.java,
            ) ?: run {
                XposedCompat.log("[SettingsImagePickerResultHook] onActivityResult NOT FOUND")
                hookState.reset()
                return
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

            XposedCompat.log("[SettingsImagePickerResultHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SettingsImagePickerResultHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

}
