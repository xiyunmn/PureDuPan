package com.xiyunmn.puredupan.hook.feature.ad

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks the full-screen SVIP exclusive icon guide shown from AboutMeActivity.
 */
object SvipIconGuideBlockHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isFullScreenBackupBlocked) {
            XposedCompat.log("[SvipIconGuideBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.SVIP_ICON_GUIDE,
                cl,
            ) ?: run {
                XposedCompat.log("[SvipIconGuideBlockHook] SvipIconGuide class NOT FOUND")
                return
            }

            var installed = 0
            for (method in clazz.declaredMethods) {
                if (method.name != StableBaiduPanHookPoints.SVIP_ICON_GUIDE_SHOW_GUIDE_METHOD) {
                    continue
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (ConfigManager.isFullScreenBackupBlocked) {
                        defaultReturnValue(method.returnType)
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[SvipIconGuideBlockHook] showGuide NOT FOUND")
                return
            }

            XposedCompat.log("[SvipIconGuideBlockHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[SvipIconGuideBlockHook] FAILED: ${t.message}")
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
