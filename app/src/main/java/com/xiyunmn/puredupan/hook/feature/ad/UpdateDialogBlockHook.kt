package com.xiyunmn.puredupan.hook.feature.ad

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks the in-app software update dialog.
 *
 * The narrow business entry is VersionUpdateHelper.showLCVersionDialog(...). Blocking this
 * avoids touching BaseDialogBuilder, which is shared by many unrelated host dialogs.
 */
object UpdateDialogBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isUpdateDialogBlocked) {
            XposedCompat.log("[UpdateDialogBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.VERSION_UPDATE_HELPER,
                cl,
            ) ?: run {
                XposedCompat.log("[UpdateDialogBlockHook] VersionUpdateHelper class NOT FOUND")
                return
            }

            var installed = 0
            for (method in clazz.declaredMethods) {
                if (method.name != StableBaiduPanHookPoints.VERSION_UPDATE_HELPER_SHOW_LC_VERSION_DIALOG_METHOD) {
                    continue
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (ConfigManager.isUpdateDialogBlocked) {
                        defaultReturnValue(method.returnType)
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[UpdateDialogBlockHook] showLCVersionDialog NOT FOUND")
                return
            }

            XposedCompat.log("[UpdateDialogBlockHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[UpdateDialogBlockHook] FAILED: ${t.message}")
        }
    }

    private fun defaultReturnValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
