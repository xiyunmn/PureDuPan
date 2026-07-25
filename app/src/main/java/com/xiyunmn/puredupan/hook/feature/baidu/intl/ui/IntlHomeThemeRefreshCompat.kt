package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

internal object IntlHomeThemeRefreshCompat {
    private const val TAG = "IntlHomeThemeRefreshCompat"

    private val hookState = HookState()
    @Volatile private var homeFragmentRef: WeakReference<Any>? = null
    @Volatile private var feedFragmentRef: WeakReference<Any>? = null
    @Volatile private var titleBarFragmentRef: WeakReference<Any>? = null

    internal fun hook(
        cl: ClassLoader,
        homeFragmentClassName: String,
        feedFragmentClassName: String,
        titleBarFragmentClassName: String,
    ) {
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        val hookedTargets = mutableListOf<String>()
        hookOnResume(mod, cl, homeFragmentClassName, "home") { instance ->
            homeFragmentRef = WeakReference(instance)
        }?.let(hookedTargets::add)
        hookOnResume(mod, cl, feedFragmentClassName, "feed") { instance ->
            feedFragmentRef = WeakReference(instance)
        }?.let(hookedTargets::add)
        hookOnResume(mod, cl, titleBarFragmentClassName, "titleBar") { instance ->
            titleBarFragmentRef = WeakReference(instance)
        }?.let(hookedTargets::add)

        if (hookedTargets.isEmpty()) {
            XposedCompat.log("[$TAG] no compatible home fragments found")
        } else {
            XposedCompat.log("[$TAG] hooks INSTALLED: ${hookedTargets.joinToString()}")
        }
    }

    internal fun refresh(reason: String) {
        refreshTarget("home", homeFragmentRef?.get(), reason)
        refreshTarget("feed", feedFragmentRef?.get(), reason)
        replayCameraAnimation(titleBarFragmentRef?.get(), reason)
    }

    private fun hookOnResume(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
        className: String,
        label: String,
        capture: (Any) -> Unit,
    ): String? {
        return try {
            val clazz = XposedCompat.findClassOrNull(className, cl)
                ?: run {
                    XposedCompat.log("[$TAG] $label fragment class NOT FOUND")
                    return null
                }
            val onResume = XposedCompat.findMethodOrNull(clazz, "onResume")
                ?: run {
                    XposedCompat.log("[$TAG] $label fragment onResume NOT FOUND")
                    return null
                }
            mod.hook(onResume).intercept { chain ->
                val result = chain.proceed()
                chain.thisObject?.let(capture)
                result
            }
            "$label=${clazz.name}.onResume"
        } catch (t: Throwable) {
            XposedCompat.log("[$TAG] $label fragment hook FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun refreshTarget(label: String, target: Any?, reason: String) {
        if (target == null) {
            XposedCompat.logD("[$TAG] $label refresh skipped: instance unavailable ($reason)")
            return
        }
        try {
            if (!isViewReady(target)) {
                XposedCompat.logD("[$TAG] $label refresh skipped: view unavailable ($reason)")
                return
            }
            val onSkinChanged = findNoArgMethod(target.javaClass, "onSkinChanged")
                ?: run {
                    XposedCompat.log("[$TAG] $label onSkinChanged NOT FOUND")
                    return
                }
            onSkinChanged.invoke(target)
            XposedCompat.logD("[$TAG] $label refreshed: $reason")
        } catch (t: Throwable) {
            XposedCompat.log("[$TAG] $label refresh FAILED ($reason): ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun replayCameraAnimation(titleBar: Any?, reason: String) {
        if (titleBar == null) {
            XposedCompat.logD("[$TAG] camera replay skipped: title bar unavailable ($reason)")
            return
        }
        try {
            if (!isViewReady(titleBar)) {
                XposedCompat.logD("[$TAG] camera replay skipped: view unavailable ($reason)")
                return
            }
            val cameraView = findField(titleBar.javaClass, "cameraLottieView")?.get(titleBar)
                ?: run {
                    XposedCompat.log("[$TAG] camera Lottie view NOT FOUND")
                    return
                }
            val playAnimation = findNoArgMethod(cameraView.javaClass, "playAnimation")
                ?: run {
                    XposedCompat.log("[$TAG] camera playAnimation NOT FOUND")
                    return
                }
            playAnimation.invoke(cameraView)
            XposedCompat.logD("[$TAG] camera animation replayed: $reason")
        } catch (t: Throwable) {
            XposedCompat.log("[$TAG] camera replay FAILED ($reason): ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun isViewReady(target: Any): Boolean {
        val isAdded = findNoArgMethod(target.javaClass, "isAdded")?.invoke(target) as? Boolean
        if (isAdded == false) return false
        return findNoArgMethod(target.javaClass, "getView")?.invoke(target) != null
    }

    private fun findNoArgMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(name).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
