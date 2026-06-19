package com.xiyunmn.puredupan.hook.feature.performance.intl

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.reflect.Method

internal object IntlTaskScoreRefreshDelayHook {
    private const val TASK_SCORE_MANAGER_CLASS_NAME = "com.baidu.netdisk.task.TaskScoreManager"
    private const val VIP_CHANNEL_ACTIVITY_CLASS_NAME = "com.baidu.netdisk.ui.vipchannel.VipChannelActivity"
    private const val MAIN_ACTIVITY_CLASS_NAME = "com.baidu.netdisk.ui.MainActivity"
    private const val HOME_STABLE_RESTORE_DELAY_MS = 2500L

    private data class RefreshInvocation(
        val receiver: Any,
        val context: Context,
    )

    private val hookState = HookState()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val lock = Any()

    @Volatile private var refreshMethod: Method? = null
    @Volatile private var pendingInvocation: RefreshInvocation? = null
    @Volatile private var skipped = false
    @Volatile private var restored = false
    @Volatile private var restoring = false
    @Volatile private var skipCount = 0
    @Volatile private var restoreCount = 0
    @Volatile private var homeStableRestoreScheduled = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IntlTaskScoreRefreshDelayHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = resolveTaskScoreRefreshMethod(cl)
            if (method == null) {
                hookState.reset()
                XposedCompat.log("[IntlTaskScoreRefreshDelayHook] task score refresh method NOT FOUND")
                return
            }
            refreshMethod = method

            mod.hook(method).intercept { chain ->
                val context = chain.args.getOrNull(0) as? Context
                val force = chain.args.getOrNull(1) as? Boolean ?: false
                if (context == null || force || !shouldSkipStartupRefresh()) {
                    if (force) {
                        XposedCompat.logD("[IntlTaskScoreRefreshDelayHook] allow forced task score refresh")
                    }
                    return@intercept chain.proceed()
                }

                synchronized(lock) {
                    pendingInvocation = RefreshInvocation(chain.thisObject, context.applicationContext ?: context)
                    skipped = true
                    skipCount++
                }
                XposedCompat.log(
                    "[IntlTaskScoreRefreshDelayHook] skipped startup task score refresh: " +
                        "${method.declaringClass.name}.${method.name}, force=false, skipCount=$skipCount",
                )
                null
            }

            val homeHooked = hookHomeStableRestoreSignal(cl)
            val businessHooked = hookBusinessEntryRestoreSignals(cl)
            XposedCompat.log(
                "[IntlTaskScoreRefreshDelayHook] hooks INSTALLED: " +
                    "refresh=${method.declaringClass.name}.${method.name}, " +
                    "homeStable=$homeHooked, businessSignals=$businessHooked",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlTaskScoreRefreshDelayHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolveTaskScoreRefreshMethod(cl: ClassLoader): Method? {
        val taskScoreManagerClass = XposedCompat.findClassOrNull(TASK_SCORE_MANAGER_CLASS_NAME, cl) ?: return null
        val contextClass = XposedCompat.findClassOrNull("android.content.Context", cl) ?: Context::class.java
        val candidates = taskScoreManagerClass.declaredMethods.filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                contextClass.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }
        if (candidates.size != 1) {
            XposedCompat.logW(
                "[IntlTaskScoreRefreshDelayHook] ambiguous task score refresh method: " +
                    candidates.joinToString { it.name },
            )
            return null
        }
        return candidates.single().apply { isAccessible = true }
    }

    private fun shouldSkipStartupRefresh(): Boolean {
        if (!isEnabled()) return false
        if (restoring || restored) return false
        return true
    }

    private fun hookHomeStableRestoreSignal(cl: ClassLoader): Boolean {
        val mod = XposedCompat.module ?: return false
        val mainActivityClass = XposedCompat.findClassOrNull(MAIN_ACTIVITY_CLASS_NAME, cl) ?: run {
            XposedCompat.log("[IntlTaskScoreRefreshDelayHook] MainActivity class NOT FOUND")
            return false
        }
        val focusMethod = XposedCompat.findMethodOrNull(
            mainActivityClass,
            "onWindowFocusChanged",
            Boolean::class.javaPrimitiveType!!,
        ) ?: run {
            XposedCompat.log("[IntlTaskScoreRefreshDelayHook] MainActivity.onWindowFocusChanged NOT FOUND")
            return false
        }

        mod.hook(focusMethod).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            val hasFocus = chain.args.firstOrNull() as? Boolean ?: false
            if (hasFocus && activity?.javaClass?.name == MAIN_ACTIVITY_CLASS_NAME) {
                scheduleHomeStableRestore()
            }
            result
        }
        return true
    }

    private fun hookBusinessEntryRestoreSignals(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val vipActivityClass = XposedCompat.findClassOrNull(VIP_CHANNEL_ACTIVITY_CLASS_NAME, cl) ?: run {
            XposedCompat.log("[IntlTaskScoreRefreshDelayHook] VipChannelActivity class NOT FOUND")
            return 0
        }
        var installed = 0

        XposedCompat.findMethodOrNull(vipActivityClass, "onCreate", Bundle::class.java)?.let { method ->
            mod.hook(method).intercept { chain ->
                restoreIfPending("vip_channel_activity:onCreate")
                chain.proceed()
            }
            installed++
        }

        XposedCompat.findMethodOrNull(vipActivityClass, "onResume")?.let { method ->
            mod.hook(method).intercept { chain ->
                restoreIfPending("vip_channel_activity:onResume")
                chain.proceed()
            }
            installed++
        }

        return installed
    }

    private fun scheduleHomeStableRestore() {
        if (!isEnabled() || restored || homeStableRestoreScheduled) return
        synchronized(lock) {
            if (restored || homeStableRestoreScheduled) return
            homeStableRestoreScheduled = true
        }
        mainHandler.postDelayed({
            homeStableRestoreScheduled = false
            restoreIfPending("home_stable")
        }, HOME_STABLE_RESTORE_DELAY_MS)
        XposedCompat.logD("[IntlTaskScoreRefreshDelayHook] home stable restore scheduled")
    }

    private fun restoreIfPending(reason: String) {
        if (!isEnabled()) return
        val method = refreshMethod ?: run {
            XposedCompat.logW("[IntlTaskScoreRefreshDelayHook] restore skipped: refreshMethod missing, reason=$reason")
            return
        }
        val invocation = synchronized(lock) {
            if (!skipped || restored) return
            val pending = pendingInvocation ?: return
            restored = true
            restoreCount++
            pending
        }

        try {
            restoring = true
            method.invoke(invocation.receiver, invocation.context, false)
            XposedCompat.log(
                "[IntlTaskScoreRefreshDelayHook] restored task score refresh: " +
                    "reason=$reason, restoreCount=$restoreCount",
            )
        } catch (t: Throwable) {
            synchronized(lock) {
                restored = false
                restoreCount--
            }
            XposedCompat.logW(
                "[IntlTaskScoreRefreshDelayHook] restore FAILED: reason=$reason, msg=${t.message}",
            )
            XposedCompat.log(t)
        } finally {
            restoring = false
        }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isIntlTaskScoreRefreshDelayed
}
