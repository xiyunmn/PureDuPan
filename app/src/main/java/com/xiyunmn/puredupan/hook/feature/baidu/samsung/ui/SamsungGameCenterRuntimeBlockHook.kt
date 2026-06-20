package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

object SamsungGameCenterRuntimeBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungGameCenterRuntimeBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val viewModelClass = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.GAME_CENTER_VIEW_MODEL,
                cl,
            ) ?: run {
                XposedCompat.log("[SamsungGameCenterRuntimeBlockHook] GameCenterViewModel class NOT FOUND")
                hookState.reset()
                return
            }

            var installedCount = 0
            XposedCompat.findMethodOrNull(
                viewModelClass,
                BaiduSamsungHookPoints.GAME_CENTER_AMIS_OPEN_METHOD,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        XposedCompat.logD("[SamsungGameCenterRuntimeBlockHook] isAmisOpen blocked")
                        false
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[SamsungGameCenterRuntimeBlockHook] isAmisOpen NOT FOUND")

            val lifecycleOwnerClass = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.ANDROIDX_LIFECYCLE_OWNER,
                cl,
            )
            if (lifecycleOwnerClass == null) {
                XposedCompat.log("[SamsungGameCenterRuntimeBlockHook] LifecycleOwner class NOT FOUND")
            } else {
                XposedCompat.findMethodOrNull(
                    viewModelClass,
                    BaiduSamsungHookPoints.GAME_CENTER_FETCH_CONFIG_METHOD,
                    lifecycleOwnerClass,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            XposedCompat.logD("[SamsungGameCenterRuntimeBlockHook] fetchGameCenterConfig blocked")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[SamsungGameCenterRuntimeBlockHook] fetchGameCenterConfig NOT FOUND")
            }

            if (installedCount == 0) {
                XposedCompat.log("[SamsungGameCenterRuntimeBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[SamsungGameCenterRuntimeBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungGameCenterRuntimeBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isMyPageCustomizeEnabled && HookSettings.isGameCenterRemoved
}
