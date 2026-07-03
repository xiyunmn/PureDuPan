package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints

object NightModeSupportHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val settingsActivityClass = XposedCompat.findClassOrNull(BaiduIntlHookPoints.SETTINGS_ACTIVITY, cl)
                ?: run {
                    hookState.reset()
                    XposedCompat.log("[NightModeSupportHook] SettingsActivity class NOT FOUND")
                    return
                }

            var hookCount = 0
            XposedCompat.findMethodOrNull(settingsActivityClass, "onCreate", Bundle::class.java)?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? Activity)?.let { restoreNightModeSwitch(cl, it) }
                    result
                }
                hookCount++
            }
            XposedCompat.findMethodOrNull(settingsActivityClass, "onResume")?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? Activity)?.let { restoreNightModeSwitch(cl, it) }
                    result
                }
                hookCount++
            }

            if (hookCount == 0) {
                hookState.reset()
                XposedCompat.log("[NightModeSupportHook] SettingsActivity lifecycle methods NOT FOUND")
                return
            }
            XposedCompat.log("[NightModeSupportHook] hooks INSTALLED: SettingsActivity lifecycle")
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[NightModeSupportHook] install FAILED: ${t.message}")
        }
    }

    private fun restoreNightModeSwitch(cl: ClassLoader, activity: Activity) {
        if (!HookSettings.isNightModeSupportEnabled) return
        if (activity.javaClass.name != BaiduIntlHookPoints.SETTINGS_ACTIVITY) return
        IntlNightModeSkinAssetInstaller.ensureDarkSkinAvailable(activity)
        val item = findDarkSettingsItem(activity)
            ?: run {
                XposedCompat.logD("[NightModeSupportHook] dark_settings view unavailable")
                return
            }

        item.visibility = View.VISIBLE
        item.isEnabled = true
        currentHostNightMode(cl, activity)?.let { isNight ->
            invokeNoArgMethod(item, "switchCheckboxNormalMode")
            invokeBooleanMethod(item, "setChecked", isNight)
        }
        item.requestLayout()
        item.invalidate()
        XposedCompat.logD("[NightModeSupportHook] dark_settings restored")
    }

    private fun findDarkSettingsItem(activity: Activity): View? {
        val id = runCatching {
            activity.resources.getIdentifier(
                BaiduIntlHookPoints.DARK_SETTINGS_ID_NAME,
                "id",
                activity.packageName,
            )
        }.getOrDefault(0)
        if (id == 0) return null
        return runCatching { activity.findViewById<View>(id) }.getOrNull()
    }

    private fun currentHostNightMode(cl: ClassLoader, context: Context): Boolean? {
        val skinConfigClass = XposedCompat.findClassOrNull(BaiduIntlHookPoints.SKIN_CONFIG, cl) ?: return null
        val isDefaultSkinMethod = XposedCompat.findMethodOrNull(
            skinConfigClass,
            "isDefaultSkin",
            Context::class.java,
        ) ?: return null
        return runCatching { !(isDefaultSkinMethod.invoke(null, context) as Boolean) }
            .getOrNull()
    }

    private fun invokeNoArgMethod(target: Any, name: String): Any? {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            try {
                return current.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun invokeBooleanMethod(target: Any, name: String, value: Boolean): Any? {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, java.lang.Boolean.TYPE)
                    .apply { isAccessible = true }
                    .invoke(target, value)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }
}
