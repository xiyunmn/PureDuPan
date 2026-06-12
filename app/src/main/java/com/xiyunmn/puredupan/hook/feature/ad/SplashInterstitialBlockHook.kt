package com.xiyunmn.puredupan.hook.feature.ad

import android.app.Activity
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * 开屏 / 切屏广告拦截 Hook。
 *
 * 受 [ConfigManager.KEY_BLOCK_SPLASH_INTERSTITIAL] 控制，默认开启。
 *
 * 冷启动开屏由 [SplashLifeHolderFastFinishHook] 在容器加载入口快退。
 * 这里仅保留热重载广告入口拦截，避免强行改写 MainActivity 并行开屏判断后
 * 破坏宿主自身的状态栏/沉浸式初始化。
 */
object SplashInterstitialBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isSplashInterstitialBlockEnabled) {
            XposedCompat.log("[SplashInterstitialBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0

            // AdvertiseHotStartManager.onResume(Activity) -> false
            val hotStartClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ADVERTISE_HOT_START_MANAGER, cl
            )
            if (hotStartClass != null) {
                val method = XposedCompat.findMethodOrNull(
                    hotStartClass, "onResume", Activity::class.java
                )
                if (method != null) {
                    mod.hook(method).intercept {
                        if (ConfigManager.isSplashInterstitialBlockEnabled) false else it.proceed()
                    }
                    installed++
                    XposedCompat.log("[SplashInterstitialBlockHook] AdvertiseHotStartManager.onResume hooked")
                } else {
                    XposedCompat.log("[SplashInterstitialBlockHook] AdvertiseHotStartManager.onResume NOT FOUND")
                }
            } else {
                XposedCompat.log("[SplashInterstitialBlockHook] AdvertiseHotStartManager class NOT FOUND")
            }

            if (installed == 0) { hookState.reset(); return }
            XposedCompat.log("[SplashInterstitialBlockHook] hooks INSTALLED: count=$installed")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log("[SplashInterstitialBlockHook] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SplashInterstitialBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }
}
