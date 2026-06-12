package com.xiyunmn.puredupan.hook.feature.performance

import android.content.Context
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks Swan mini-program runtime preloading without disabling user-initiated Swan launches.
 */
object SwanPreloadBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SwanPreloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val preloadHelperClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.SWAN_APP_PRELOAD_HELPER,
                cl,
            ) ?: run {
                XposedCompat.log("[SwanPreloadBlockHook] SwanAppPreloadHelper NOT FOUND")
                hookState.reset()
                return
            }
            val swanClientPuppetClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.SWAN_CLIENT_PUPPET,
                cl,
            ) ?: run {
                XposedCompat.log("[SwanPreloadBlockHook] SwanClientPuppet NOT FOUND")
                hookState.reset()
                return
            }

            var installedCount = 0
            installedCount += hookVoidPreloadMethod(
                preloadHelperClass,
                StableBaiduPanHookPoints.SWAN_PRELOAD_TRY_PRELOAD_METHOD,
                Context::class.java,
                Bundle::class.java,
            )
            installedCount += hookVoidPreloadMethod(
                preloadHelperClass,
                StableBaiduPanHookPoints.SWAN_PRELOAD_TRY_PRELOAD_IF_KEEP_ALIVE_METHOD,
                Context::class.java,
                Bundle::class.java,
            )
            installedCount += hookVoidPreloadMethod(
                preloadHelperClass,
                StableBaiduPanHookPoints.SWAN_PRELOAD_TRY_PRELOAD_METHOD,
                Context::class.java,
                swanClientPuppetClass,
                Bundle::class.java,
            )
            installedCount += hookVoidPreloadMethod(
                preloadHelperClass,
                StableBaiduPanHookPoints.SWAN_PRELOAD_START_SERVICE_FOR_PRELOAD_NEXT_METHOD,
                Context::class.java,
                Bundle::class.java,
            )

            XposedCompat.findMethodOrNull(
                swanClientPuppetClass,
                StableBaiduPanHookPoints.SWAN_PRELOAD_TRY_PRELOAD_METHOD,
                Context::class.java,
                Bundle::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        XposedCompat.logD("[SwanPreloadBlockHook] SwanClientPuppet.tryPreload blocked")
                        chain.thisObject
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[SwanPreloadBlockHook] SwanClientPuppet.tryPreload NOT FOUND")

            if (installedCount == 0) {
                XposedCompat.log("[SwanPreloadBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[SwanPreloadBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SwanPreloadBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookVoidPreloadMethod(
        clazz: Class<*>,
        methodName: String,
        vararg paramTypes: Class<*>,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        val method = XposedCompat.findMethodOrNull(clazz, methodName, *paramTypes)
        if (method == null) {
            XposedCompat.log("[SwanPreloadBlockHook] ${clazz.name}.$methodName NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[SwanPreloadBlockHook] ${clazz.name}.$methodName blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }



    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isSwanPreloadDisabled
}
