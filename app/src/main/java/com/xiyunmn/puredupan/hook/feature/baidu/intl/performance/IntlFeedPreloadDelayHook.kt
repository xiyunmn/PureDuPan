package com.xiyunmn.puredupan.hook.feature.baidu.intl.performance

import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object IntlFeedPreloadDelayHook {
    private const val TAG = "IntlFeedPreloadDelayHook"
    private const val NEW_FEED_HOME_CONTEXT_CLASS_NAME = BaiduIntlHookPoints.NEW_FEED_HOME_CONTEXT
    private const val NEW_FEED_HOME_COMPANION_CLASS_NAME = BaiduIntlHookPoints.NEW_FEED_HOME_COMPANION
    private const val PRELOAD_FEED_DATA_METHOD_NAME = "preloadFeedData"
    private const val HOME_STABLE_RESTORE_DELAY_MS = 2500L
    private const val TIMEOUT_RESTORE_DELAY_MS = 8000L

    private data class PreloadInvocation(
        val cursor: String,
        val serialNum: String,
    )

    private data class ResolvedPreloadMethod(
        val method: Method,
        val receiver: Any?,
    )

    private val hookState = HookState()
    private val lock = Any()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile private var preloadMethod: Method? = null
    @Volatile private var preloadReceiver: Any? = null
    @Volatile private var pendingInvocation: PreloadInvocation? = null
    @Volatile private var skipped = false
    @Volatile private var restored = false
    @Volatile private var restoring = false
    @Volatile private var skipCount = 0
    @Volatile private var restoreCount = 0
    @Volatile private var homeStableRestoreScheduled = false
    @Volatile private var timeoutRestoreScheduled = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IntlFeedPreloadDelayHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val resolved = resolvePreloadFeedDataMethod(cl)
            if (resolved == null) {
                hookState.reset()
                XposedCompat.log("[IntlFeedPreloadDelayHook] preloadFeedData method NOT FOUND")
                return
            }
            preloadMethod = resolved.method
            preloadReceiver = resolved.receiver

            mod.hook(resolved.method).intercept { chain ->
                val cursor = chain.args.getOrNull(0) as? String
                val serialNum = chain.args.getOrNull(1) as? String
                if (cursor == null || serialNum == null || !shouldSkipStartupPreload()) {
                    return@intercept chain.proceed()
                }

                synchronized(lock) {
                    pendingInvocation = PreloadInvocation(cursor, serialNum)
                    skipped = true
                    skipCount++
                }
                scheduleTimeoutRestore()
                XposedCompat.log(
                    "[IntlFeedPreloadDelayHook] skipped startup feed preload: " +
                        "${resolved.method.declaringClass.name}.${resolved.method.name}, skipCount=$skipCount",
                )
                null
            }

            val homeHooked = hookHomeStableRestoreSignal(cl)
            XposedCompat.log(
                "[IntlFeedPreloadDelayHook] hooks INSTALLED: " +
                    "preload=${resolved.method.declaringClass.name}.${resolved.method.name}, " +
                    "homeStable=$homeHooked",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlFeedPreloadDelayHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolvePreloadFeedDataMethod(cl: ClassLoader): ResolvedPreloadMethod? {
        // The intl host is heavily obfuscated. Keep the hook on Rubik's generated
        // @Keep context instead of the obfuscated feed manager implementation.
        val companionClass = XposedCompat.findClassOrNull(NEW_FEED_HOME_COMPANION_CLASS_NAME, cl)
        val contextClass = XposedCompat.findClassOrNull(NEW_FEED_HOME_CONTEXT_CLASS_NAME, cl)
        if (companionClass != null && contextClass != null) {
            val companionMethod = XposedCompat.findMethodOrNull(
                companionClass,
                PRELOAD_FEED_DATA_METHOD_NAME,
                String::class.java,
                String::class.java,
            )
            val companionReceiver = runCatching {
                XposedCompat.findField(contextClass, "INSTANCE").get(null)
            }.getOrNull()
            if (
                companionMethod != null &&
                companionReceiver != null &&
                companionMethod.declaringClass.isInstance(companionReceiver)
            ) {
                return ResolvedPreloadMethod(companionMethod, companionReceiver)
            } else if (companionMethod != null && companionReceiver != null) {
                XposedCompat.logW(
                    "[$TAG] preload companion receiver mismatch: " +
                        "receiver=${companionReceiver.javaClass.name}, " +
                        "methodOwner=${companionMethod.declaringClass.name}",
                )
            }
        }

        val staticMethod = contextClass?.let {
            XposedCompat.findMethodOrNull(
                it,
                PRELOAD_FEED_DATA_METHOD_NAME,
                String::class.java,
                String::class.java,
            )
        }
        return if (staticMethod != null && Modifier.isStatic(staticMethod.modifiers)) {
            ResolvedPreloadMethod(staticMethod, null)
        } else {
            null
        }
    }

    private fun shouldSkipStartupPreload(): Boolean {
        if (!isEnabled()) return false
        if (restoring || restored) return false
        return true
    }

    private fun hookHomeStableRestoreSignal(cl: ClassLoader): Boolean =
        IntlHomeStableRestoreSignal.hook(cl, TAG) {
            scheduleHomeStableRestore()
        }

    private fun scheduleHomeStableRestore() {
        if (!isEnabled()) return
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

    private fun scheduleTimeoutRestore() {
        if (!isEnabled()) return
        val shouldSchedule = synchronized(lock) {
            if (restored || timeoutRestoreScheduled) {
                false
            } else {
                timeoutRestoreScheduled = true
                true
            }
        }
        if (!shouldSchedule) return

        mainHandler.postDelayed({
            timeoutRestoreScheduled = false
            restoreIfPending("timeout")
        }, TIMEOUT_RESTORE_DELAY_MS)
        XposedCompat.logD("[IntlFeedPreloadDelayHook] timeout restore scheduled")
    }

    private fun restoreIfPending(reason: String) {
        if (!isEnabled()) return
        val method = preloadMethod ?: run {
            XposedCompat.logW("[IntlFeedPreloadDelayHook] restore skipped: preloadMethod missing, reason=$reason")
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
            method.invoke(preloadReceiver, invocation.cursor, invocation.serialNum)
            XposedCompat.log(
                "[IntlFeedPreloadDelayHook] restored feed preload: " +
                    "reason=$reason, restoreCount=$restoreCount",
            )
        } catch (t: Throwable) {
            synchronized(lock) {
                restored = false
                restoreCount--
            }
            XposedCompat.logW(
                "[IntlFeedPreloadDelayHook] restore FAILED: reason=$reason, msg=${t.message}",
            )
            XposedCompat.log(t)
        } finally {
            restoring = false
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isIntlFeedPreloadDelayed
}
