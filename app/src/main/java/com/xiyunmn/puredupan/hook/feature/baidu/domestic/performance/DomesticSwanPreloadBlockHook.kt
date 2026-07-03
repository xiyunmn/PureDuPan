package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Context
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticSwanPreloadResolver

/**
 * Blocks Swan mini-program runtime preloading without disabling user-initiated Swan launches.
 */
object DomesticSwanPreloadBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticSwanPreloadBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val preloadHelperClass = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.SWAN_APP_PRELOAD_HELPER,
                cl,
            ) ?: run {
                XposedCompat.log("[DomesticSwanPreloadBlockHook] SwanAppPreloadHelper NOT FOUND")
                val installedCount = hookDomesticPrefetchManager(cl)
                if (installedCount == 0) {
                    XposedCompat.log("[DomesticSwanPreloadBlockHook] no compat hooks installed")
                    hookState.reset()
                    return
                }
                XposedCompat.log("[DomesticSwanPreloadBlockHook] compat hooks INSTALLED: count=$installedCount")
                return
            }
            val swanClientPuppetClass = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.SWAN_CLIENT_PUPPET,
                cl,
            ) ?: run {
                XposedCompat.log("[DomesticSwanPreloadBlockHook] SwanClientPuppet NOT FOUND")
                hookState.reset()
                return
            }

            var installedCount = 0
            installedCount += hookVoidPreloadMethod(
                preloadHelperClass,
                BaiduDomesticHookPoints.SWAN_PRELOAD_TRY_PRELOAD_METHOD,
                Context::class.java,
                Bundle::class.java,
            )
            installedCount += hookVoidPreloadMethod(
                preloadHelperClass,
                BaiduDomesticHookPoints.SWAN_PRELOAD_TRY_PRELOAD_IF_KEEP_ALIVE_METHOD,
                Context::class.java,
                Bundle::class.java,
            )
            installedCount += hookVoidPreloadMethod(
                preloadHelperClass,
                BaiduDomesticHookPoints.SWAN_PRELOAD_TRY_PRELOAD_METHOD,
                Context::class.java,
                swanClientPuppetClass,
                Bundle::class.java,
            )
            installedCount += hookVoidPreloadMethod(
                preloadHelperClass,
                BaiduDomesticHookPoints.SWAN_PRELOAD_START_SERVICE_FOR_PRELOAD_NEXT_METHOD,
                Context::class.java,
                Bundle::class.java,
            )

            XposedCompat.findMethodOrNull(
                swanClientPuppetClass,
                BaiduDomesticHookPoints.SWAN_PRELOAD_TRY_PRELOAD_METHOD,
                Context::class.java,
                Bundle::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        XposedCompat.logD("[DomesticSwanPreloadBlockHook] SwanClientPuppet.tryPreload blocked")
                        chain.thisObject
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[DomesticSwanPreloadBlockHook] SwanClientPuppet.tryPreload NOT FOUND")

            installedCount += hookDomesticPrefetchManager(cl)

            if (installedCount == 0) {
                XposedCompat.log("[DomesticSwanPreloadBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[DomesticSwanPreloadBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticSwanPreloadBlockHook] FAILED: ${e.message}")
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
            XposedCompat.log("[DomesticSwanPreloadBlockHook] ${clazz.name}.$methodName NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[DomesticSwanPreloadBlockHook] ${clazz.name}.$methodName blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookDomesticPrefetchManager(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        DomesticSwanPreloadResolver.resolvePrefetchEventMethod(cl)?.let { method ->
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[DomesticSwanPreloadBlockHook] Swan prefetch event blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
            return 1
        }
        return 0
    }



    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isSwanPreloadDisabled
}
