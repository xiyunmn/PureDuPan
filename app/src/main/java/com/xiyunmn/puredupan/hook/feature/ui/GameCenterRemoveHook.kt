package com.xiyunmn.puredupan.hook.feature.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * 游戏中心移除 Hook。
 *
 * 受 [ConfigManager.KEY_REMOVE_GAME_CENTER] 控制，默认开启。
 */
object GameCenterRemoveHook {
    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[GameCenterRemoveHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ABOUT_ME_GAME_CENTER_FRAGMENT, cl
            ) ?: run {
                XposedCompat.log("[GameCenterRemoveHook] AboutMeGameCenterFragment class NOT FOUND")
                return
            }
            val method = XposedCompat.findMethodOrNull(
                clazz, "onCreateView",
                LayoutInflater::class.java,
                ViewGroup::class.java,
                Bundle::class.java
            ) ?: run {
                XposedCompat.log("[GameCenterRemoveHook] onCreateView NOT FOUND")
                return
            }
            mod.hook(method).intercept {
                if (isEnabled()) null else it.proceed()
            }
            XposedCompat.log("[GameCenterRemoveHook] hook INSTALLED")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[GameCenterRemoveHook] FAILED: ${t.message}")
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }
    private fun isEnabled(): Boolean =
        ConfigManager.isMyPageCustomizeEnabled && ConfigManager.isGameCenterRemoved

    private fun resetHooked() { synchronized(this) { hooked = false } }
}
