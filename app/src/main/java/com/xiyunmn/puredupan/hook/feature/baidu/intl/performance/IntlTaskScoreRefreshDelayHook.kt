package com.xiyunmn.puredupan.hook.feature.baidu.intl.performance

import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import android.content.Context
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Method

internal object IntlTaskScoreRefreshDelayHook {
    fun disposeForReload() {
        try {
            if (pendingInvocation != null) restoreIfPending("reload")
        } finally {
            pendingInvocation = null
            refreshMethod = null
            homeStableRestoreScheduled = false
        }
    }

    private const val TAG = "IntlTaskScoreRefreshDelayHook"
    private const val TASK_SCORE_MANAGER_CLASS_NAME = BaiduIntlHookPoints.TASK_SCORE_MANAGER
    private const val VIP_CHANNEL_ACTIVITY_CLASS_NAME = BaiduIntlHookPoints.VIP_CHANNEL_ACTIVITY
    private const val HOME_STABLE_RESTORE_DELAY_MS = 2500L

    private data class RefreshInvocation(
        val receiver: Any,
        val context: Context,
    )

    private val hookState = HookState()
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
        // 旧代已交还待恢复调用，热重载不重新进入冷启动抑制阶段。
        if (ModuleLifetime.hotReloaded) return
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

            mod.hook(method).setId("IntlTaskScoreRefreshDelayHook:hook:method").intercept { chain ->
                if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
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
        if (ModuleLifetime.isRetiring || !isEnabled()) return false
        if (restoring || restored) return false
        return true
    }

    private fun hookHomeStableRestoreSignal(cl: ClassLoader): Boolean =
        IntlHomeStableRestoreSignal.hook(cl, TAG) {
            scheduleHomeStableRestore()
        }

    private fun hookBusinessEntryRestoreSignals(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val vipActivityClass = XposedCompat.findClassOrNull(VIP_CHANNEL_ACTIVITY_CLASS_NAME, cl) ?: run {
            XposedCompat.log("[IntlTaskScoreRefreshDelayHook] VipChannelActivity class NOT FOUND")
            return 0
        }
        var installed = 0

        XposedCompat.findMethodOrNull(vipActivityClass, "onCreate", Bundle::class.java)?.let { method ->
            mod.hook(method).setId("IntlTaskScoreRefreshDelayHook:hookBusinessEntryRestoreSignals:method").intercept { chain ->
                if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
                restoreIfPending("vip_channel_activity:onCreate")
                chain.proceed()
            }
            installed++
        }

        XposedCompat.findMethodOrNull(vipActivityClass, "onResume")?.let { method ->
            mod.hook(method).setId("IntlTaskScoreRefreshDelayHook:hookBusinessEntryRestoreSignals:method:2").intercept { chain ->
                if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
                restoreIfPending("vip_channel_activity:onResume")
                chain.proceed()
            }
            installed++
        }

        return installed
    }

    private fun scheduleHomeStableRestore() {
        if (ModuleLifetime.isRetiring || !isEnabled()) return
        IntlHomeStableRestoreSignal.scheduleDelayedRestore(
            tag = TAG,
            delayMs = HOME_STABLE_RESTORE_DELAY_MS,
            tryMarkScheduled = {
                synchronized(lock) {
                    if (restored || homeStableRestoreScheduled) {
                        false
                    } else {
                        homeStableRestoreScheduled = true
                        true
                    }
                }
            },
            clearScheduled = { homeStableRestoreScheduled = false },
            restore = ::restoreIfPending,
        )
    }

    private fun restoreIfPending(reason: String) {
        if (reason != "reload" && (ModuleLifetime.isRetiring || !isEnabled())) return
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
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isIntlTaskScoreRefreshDelayed
}
