package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import android.app.Activity
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupHookPoints
import java.util.concurrent.ConcurrentHashMap

internal object DomesticHotStartSplashBlocker {
    private val hotStartHookStates = ConcurrentHashMap<String, HookState>()
    private val coldStartHookStates = ConcurrentHashMap<String, HookState>()
    private val splashFallbackHookStates = ConcurrentHashMap<String, HookState>()

    internal fun hookColdStartSplashManager(cl: ClassLoader, ownerTag: String): Int {
        val mod = XposedCompat.module ?: return 0
        val hookState = coldStartHookStates.getOrPut(ownerTag) { HookState() }
        if (!hookState.markInstalled()) return 1

        val resolved = DomesticColdStartSplashDexKitResolver.resolve(cl) ?: run {
            hookState.reset()
            XposedCompat.logD("[$ownerTag] domestic cold start splash manager NOT FOUND")
            return 0
        }
        val method = resolveColdStartMethod(cl, resolved) ?: run {
            hookState.reset()
            XposedCompat.logD(
                "[$ownerTag] domestic cold start method invalid: " +
                    "${resolved.className}.${resolved.methodName}",
            )
            return 0
        }

        try {
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSplashInterstitialBlockEnabled) {
                    XposedCompat.logD("[$ownerTag] cold start splash blocked")
                    false
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            hookState.reset()
            throw t
        }
        XposedCompat.log(
            "[$ownerTag] cold start splash manager hooked: ${method.declaringClass.name}.${method.name}",
        )
        return 1
    }

    internal fun hookHotStartManager(cl: ClassLoader, ownerTag: String): Int {
        val mod = XposedCompat.module ?: return 0
        val hookState = hotStartHookStates.getOrPut(ownerTag) { HookState() }
        if (!hookState.markInstalled()) return 1

        val resolved = DomesticHotStartSplashDexKitResolver.resolve(cl) ?: run {
            hookState.reset()
            XposedCompat.logD("[$ownerTag] domestic hot start manager NOT FOUND")
            return 0
        }
        val method = resolveHotStartMethod(cl, resolved) ?: run {
            hookState.reset()
            XposedCompat.logD(
                "[$ownerTag] domestic hot start method invalid: " +
                    "${resolved.className}.${resolved.methodName}",
            )
            return 0
        }

        try {
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSplashInterstitialBlockEnabled) {
                    XposedCompat.logD("[$ownerTag] hot start splash blocked")
                    false
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            hookState.reset()
            throw t
        }
        XposedCompat.log(
            "[$ownerTag] hot start manager hooked: ${method.declaringClass.name}.${method.name}",
        )
        return 1
    }

    internal fun hookSplashAdActivityFallback(cl: ClassLoader, ownerTag: String): Int {
        val mod = XposedCompat.module ?: return 0
        val hookState = splashFallbackHookStates.getOrPut(ownerTag) { HookState() }
        if (!hookState.markInstalled()) return 1

        val splashActivityClass = XposedCompat.findClassOrNull(
            BaiduStartupHookPoints.SPLASH_AD_ACTIVITY,
            cl,
        ) ?: run {
            hookState.reset()
            XposedCompat.logD("[$ownerTag] SplashAdActivity class NOT FOUND")
            return 0
        }

        var installed = 0
        try {
            XposedCompat.findMethodOrNull(splashActivityClass, "initView")?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isSplashInterstitialBlockEnabled) {
                        finishSplashActivity(chain.thisObject, ownerTag, "initView")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }
            XposedCompat.findMethodOrNull(
                splashActivityClass,
                "onCreate",
                Bundle::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (HookSettings.isSplashInterstitialBlockEnabled) {
                        finishSplashActivity(chain.thisObject, ownerTag, "onCreate")
                    }
                    result
                }
                installed++
            }
            XposedCompat.findMethodOrNull(splashActivityClass, "onResume")?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (HookSettings.isSplashInterstitialBlockEnabled) {
                        finishSplashActivity(chain.thisObject, ownerTag, "onResume")
                    }
                    result
                }
                installed++
            }
        } catch (t: Throwable) {
            hookState.reset()
            throw t
        }

        if (installed == 0) {
            hookState.reset()
            XposedCompat.logD("[$ownerTag] SplashAdActivity fallback methods NOT FOUND")
        } else {
            XposedCompat.log("[$ownerTag] SplashAdActivity fallback hooked: count=$installed")
        }
        return installed
    }

    private fun finishSplashActivity(target: Any?, ownerTag: String, source: String) {
        val activity = target as? Activity ?: return
        XposedCompat.logD("[$ownerTag] finishing SplashAdActivity fallback from $source")
        activity.finish()
        runCatching { activity.overridePendingTransition(0, 0) }
    }

    private fun resolveHotStartMethod(
        cl: ClassLoader,
        result: DomesticHotStartSplashDexKitResolver.ResolveResult,
    ): java.lang.reflect.Method? {
        val clazz = XposedCompat.findClassOrNull(result.className, cl) ?: return null
        val method = XposedCompat.findMethodOrNull(
            clazz,
            result.methodName,
            Activity::class.java,
        ) ?: return null
        if (!java.lang.reflect.Modifier.isStatic(method.modifiers)) return null
        if (method.returnType != java.lang.Boolean.TYPE) return null
        return method
    }

    private fun resolveColdStartMethod(
        cl: ClassLoader,
        result: DomesticColdStartSplashDexKitResolver.ResolveResult,
    ): java.lang.reflect.Method? {
        val clazz = XposedCompat.findClassOrNull(result.className, cl) ?: return null
        val method = XposedCompat.findMethodOrNull(
            clazz,
            result.methodName,
            Activity::class.java,
        ) ?: return null
        if (java.lang.reflect.Modifier.isStatic(method.modifiers)) return null
        if (method.returnType != java.lang.Boolean.TYPE) return null
        return method
    }
}
