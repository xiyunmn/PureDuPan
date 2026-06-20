package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

object SamsungGameCenterRemoveHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungGameCenterRemoveHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.ABOUT_ME_GAME_CENTER_FRAGMENT,
                cl,
            ) ?: run {
                XposedCompat.log("[SamsungGameCenterRemoveHook] AboutMeGameCenterFragment class NOT FOUND")
                hookState.reset()
                return
            }
            val method = XposedCompat.findMethodOrNull(
                clazz,
                "onCreateView",
                LayoutInflater::class.java,
                ViewGroup::class.java,
                Bundle::class.java,
            ) ?: run {
                XposedCompat.log("[SamsungGameCenterRemoveHook] onCreateView NOT FOUND")
                hookState.reset()
                return
            }
            mod.hook(method).intercept { chain ->
                if (isEnabled()) null else chain.proceed()
            }
            XposedCompat.log("[SamsungGameCenterRemoveHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungGameCenterRemoveHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isMyPageCustomizeEnabled && HookSettings.isGameCenterRemoved
}
