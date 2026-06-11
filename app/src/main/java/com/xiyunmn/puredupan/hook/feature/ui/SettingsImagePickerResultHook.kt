package com.xiyunmn.puredupan.hook.feature.ui

import android.app.Activity
import android.content.Intent
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook

/**
 * Receives image picker results launched from the module settings panel in AboutMeActivity.
 */
object SettingsImagePickerResultHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val activityClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ABOUT_ME_ACTIVITY,
                cl,
            ) ?: run {
                XposedCompat.log("[SettingsImagePickerResultHook] AboutMeActivity class NOT FOUND")
                resetHooked()
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
                resetHooked()
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
                } catch (t: Throwable) {
                    XposedCompat.logD("[SettingsImagePickerResultHook] handle result failed: ${t.message}")
                }
                result
            }

            XposedCompat.log("[SettingsImagePickerResultHook] hook INSTALLED")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[SettingsImagePickerResultHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
