package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks only game center display decisions and config requests from About Me.
 */
internal object DomesticGameCenterRuntimeBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[GameCenterRuntimeBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val viewModelClass = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.GAME_CENTER_VIEW_MODEL,
                cl,
            ) ?: run {
                XposedCompat.log("[GameCenterRuntimeBlockHook] GameCenterViewModel class NOT FOUND")
                hookState.reset()
                return
            }

            var installedCount = 0
            XposedCompat.findMethodOrNull(
                viewModelClass,
                BaiduDomesticHookPoints.GAME_CENTER_CAN_SHOW_METHOD,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        XposedCompat.logD("[GameCenterRuntimeBlockHook] gameCenterCanShow blocked")
                        false
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[GameCenterRuntimeBlockHook] gameCenterCanShow NOT FOUND")

            val lifecycleOwnerClass = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.ANDROIDX_LIFECYCLE_OWNER,
                cl,
            )
            if (lifecycleOwnerClass == null) {
                XposedCompat.log("[GameCenterRuntimeBlockHook] LifecycleOwner class NOT FOUND")
            } else {
                XposedCompat.findMethodOrNull(
                    viewModelClass,
                    BaiduDomesticHookPoints.GAME_CENTER_FETCH_CONFIG_METHOD,
                    lifecycleOwnerClass,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            XposedCompat.logD("[GameCenterRuntimeBlockHook] fetchGameCenterConfig blocked")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[GameCenterRuntimeBlockHook] fetchGameCenterConfig(LifecycleOwner) NOT FOUND")
            }

            if (installedCount == 0) {
                XposedCompat.log("[GameCenterRuntimeBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[GameCenterRuntimeBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[GameCenterRuntimeBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }


    private fun isEnabled(): Boolean =
        HookSettings.isMyPageCustomizeEnabled && HookSettings.isGameCenterRemoved
}
