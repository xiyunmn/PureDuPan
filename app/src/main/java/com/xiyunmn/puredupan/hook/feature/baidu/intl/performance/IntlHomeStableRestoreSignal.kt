package com.xiyunmn.puredupan.hook.feature.baidu.intl.performance

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import java.lang.reflect.Method

internal object IntlHomeStableRestoreSignal {
    private const val HOME_STABLE_REASON = "home_stable"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun hook(cl: ClassLoader, tag: String, onHomeStable: () -> Unit): Boolean {
        val mod = XposedCompat.module ?: return false
        val activityClassNames = stableActivityClassNames()
        if (activityClassNames.isEmpty()) {
            XposedCompat.log("[$tag] stable activity host capability missing")
            return false
        }

        var hookedCount = 0
        for (className in activityClassNames) {
            val activityClass = XposedCompat.findClassOrNull(className, cl)
            if (activityClass == null) {
                XposedCompat.logD("[$tag] stable activity class NOT FOUND: $className")
                continue
            }
            val focusMethod = findOnWindowFocusChangedMethod(activityClass)
            if (focusMethod == null) {
                XposedCompat.logD("[$tag] ${activityClass.name}.onWindowFocusChanged NOT FOUND")
                continue
            }

            mod.hook(focusMethod).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                val hasFocus = chain.args.firstOrNull() as? Boolean ?: false
                if (hasFocus && activity?.javaClass?.name in activityClassNames) {
                    onHomeStable()
                }
                result
            }
            hookedCount++
        }

        if (hookedCount == 0) {
            XposedCompat.log("[$tag] stable activity focus hooks NOT FOUND")
            return false
        }

        XposedCompat.logD("[$tag] home stable focus hooks installed: count=$hookedCount")
        return true
    }

    fun scheduleDelayedRestore(
        tag: String,
        delayMs: Long,
        tryMarkScheduled: () -> Boolean,
        clearScheduled: () -> Unit,
        restore: (String) -> Unit,
    ) {
        if (!tryMarkScheduled()) return
        mainHandler.postDelayed({
            clearScheduled()
            restore(HOME_STABLE_REASON)
        }, delayMs)
        XposedCompat.logD("[$tag] home stable restore scheduled")
    }

    private fun stableActivityClassNames(): List<String> =
        (
            BaiduFeatureRuntime.currentStableActivityClassNames() +
                listOfNotNull(BaiduFeatureRuntime.currentMainActivityClassName())
        ).distinct()

    private fun findOnWindowFocusChangedMethod(clazz: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(
                    "onWindowFocusChanged",
                    Boolean::class.javaPrimitiveType!!,
                ).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }
}
