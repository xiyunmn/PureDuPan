package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme

import android.app.Activity
import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAboutMeHookPoints
import java.lang.reflect.Modifier

/**
 * Hides "My service" and the sign-in entry red dot from their render entries.
 *
 * My service is rendered by about-me middle/bottom fragments after service node updates. The synthetic
 * lambda suffix changes between host builds, so install by stable prefix and hide the resource from
 * the fragment root after the host callback finishes.
 *
 * The sign-in red dot belongs to the about-me activity top bar, so it stays at activity render
 * points with finite scheduling only; it no longer needs activity-wide global/pre-draw listeners.
 */
object AboutMeServiceAndSignDotHideHook {
    private const val TAG = "AboutMeServiceAndSignDotHideHook"
    private const val INIT_MY_SERVICE_LAMBDA_PREFIX = "initMyService\$lambda"
    private const val INIT_MORE_SERVICE_LAMBDA_PREFIX = "initMoreService\$lambda"

    private const val MY_SERVICE_ID = "cl_my_service"
    private val SIGN_DOT_IDS = listOf(
        "f1_entry_dot",
        "fl_entry_dot",
        "activity_entry_dot",
        "entry_dot_view",
    )

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isAnyEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            if (isMyServiceEnabled()) {
                installed += hookServiceLambda(
                    cl = cl,
                    className = BaiduAboutMeHookPoints.NEW_MIDDLE_FRAGMENT,
                    methodPrefix = INIT_MY_SERVICE_LAMBDA_PREFIX,
                )
                installed += hookServiceLambda(
                    cl = cl,
                    className = BaiduAboutMeHookPoints.ABOUT_ME_MIDDLE_FRAGMENT,
                    methodPrefix = INIT_MY_SERVICE_LAMBDA_PREFIX,
                )
                installed += hookServiceLambda(
                    cl = cl,
                    className = BaiduAboutMeHookPoints.ABOUT_ME_BOTTOM_FRAGMENT,
                    methodPrefix = INIT_MORE_SERVICE_LAMBDA_PREFIX,
                )
            }
            if (isSignDotEnabled()) {
                for (activityClassName in BaiduFeatureRuntime.currentAboutMeActivityClassNames()) {
                    installed += hookActivityRenderPoints(cl, activityClassName)
                }
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[$TAG] hooks NOT INSTALLED")
                return
            }
            XposedCompat.log("[$TAG] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[$TAG] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookServiceLambda(
        cl: ClassLoader,
        className: String,
        methodPrefix: String,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        val fragmentClass = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.logD("[$TAG] fragment not found: $className")
            return 0
        }
        var count = 0
        for (method in fragmentClass.declaredMethods) {
            if (!method.name.startsWith(methodPrefix)) continue
            if (!Modifier.isStatic(method.modifiers)) continue
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (isMyServiceEnabled()) {
                    val fragment = chain.args.firstOrNull { fragmentClass.isInstance(it) }
                    hideMyService(fragment)
                }
                result
            }
            count++
            XposedCompat.logD("[$TAG] service render hook installed: ${fragmentClass.simpleName}.${method.name}")
        }
        return count
    }

    private fun hookActivityRenderPoints(cl: ClassLoader, activityClassName: String): Int {
        val mod = XposedCompat.module ?: return 0
        val activityClass = XposedCompat.findClassOrNull(activityClassName, cl) ?: run {
            XposedCompat.logD("[$TAG] activity not found: $activityClassName")
            return 0
        }
        var count = 0
        for (methodName in listOf("initView", "onResume", "onPostResume", "onSkinChanged")) {
            val method = XposedCompat.findMethodOrNull(activityClass, methodName) ?: continue
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                scheduleHideSignDot(chain.thisObject as? Activity, methodName)
                result
            }
            count++
            XposedCompat.logD("[$TAG] sign-dot render hook installed: $activityClassName.$methodName")
        }
        count += hookSetActivityEntry(activityClass, cl)
        return count
    }

    private fun hookSetActivityEntry(activityClass: Class<*>, cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val popupResponseClassName = BaiduFeatureRuntime.currentPopupResponseClassName() ?: return 0
        val popupResponseClass = XposedCompat.findClassOrNull(popupResponseClassName, cl) ?: return 0
        val method = XposedCompat.findMethodOrNull(activityClass, "setActivityEntry", popupResponseClass) ?: return 0
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            scheduleHideSignDot(chain.thisObject as? Activity, "setActivityEntry")
            result
        }
        XposedCompat.logD("[$TAG] sign-dot render hook installed: ${activityClass.name}.setActivityEntry")
        return 1
    }

    private fun hideMyService(fragment: Any?) {
        val root = fragmentRoot(fragment) ?: return
        hideByEntryName(root, MY_SERVICE_ID, "my service")
    }

    private fun scheduleHideSignDot(activity: Activity?, source: String) {
        if (activity == null || !isSignDotEnabled()) return
        val root = activity.window?.decorView ?: return
        hideSignDot(root, source)
        root.post { hideSignDot(root, source) }
        for (delay in listOf(80L, 240L, 600L)) {
            root.postDelayed({ hideSignDot(root, source) }, delay)
        }
    }

    private fun hideSignDot(root: View, source: String) {
        for (entryName in SIGN_DOT_IDS) {
            hideByEntryName(root, entryName, "sign-in dot", source)
        }
    }

    private fun fragmentRoot(fragment: Any?): View? {
        return fragment?.let {
            runCatching { it.javaClass.getMethod("getView").invoke(it) as? View }.getOrNull()
        }
    }

    private fun hideByEntryName(root: View, idName: String, label: String, source: String = "render entry") {
        val resources = root.resources ?: return
        val packageName = root.context?.packageName ?: return
        val id = resources.getIdentifier(idName, "id", packageName)
        if (id == 0) return
        val view = root.findViewById<View>(id) ?: return
        val shouldLog = view.visibility != View.GONE || view.alpha != 0f || view.isEnabled || view.isClickable
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
        if (shouldLog) {
            XposedCompat.logD("[$TAG] $label hidden via $source ($idName)")
        }
    }

    private fun isAnyEnabled(): Boolean {
        return isMyServiceEnabled() || isSignDotEnabled()
    }

    private fun isMyServiceEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isMyServiceRemoved
    }

    private fun isSignDotEnabled(): Boolean {
        val options = HookSettings.aboutMeOptions()
        return options.isMyPageCustomizeEnabled && options.isAboutMeSignInDotHidden
    }
}
