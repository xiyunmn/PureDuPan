package com.xiyunmn.puredupan.hook.feature.baidu.shared.automation

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

internal object AutoDailySignInScheduler {
    private const val MIN_AUTO_DELAY_MS = 20_000L
    private const val MAX_AUTO_DELAY_MS = 120_000L
    private val AUTO_DELAY_RANGE_MS = MIN_AUTO_DELAY_MS..MAX_AUTO_DELAY_MS
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val hookStates = ConcurrentHashMap<String, HookState>()
    private val pendingRuns = ConcurrentHashMap<String, AtomicBoolean>()

    fun install(
        cl: ClassLoader,
        tag: String,
        delayRangeMs: LongRange = AUTO_DELAY_RANGE_MS,
        onRun: (Activity) -> Unit,
    ) {
        val hookState = hookStates.getOrPut(tag) { HookState() }
        if (!hookState.markInstalled()) return

        try {
            val mod = XposedCompat.module ?: run {
                hookState.reset()
                XposedCompat.log("[$tag] module unavailable")
                return
            }
            val mainActivityClassName = BaiduFeatureRuntime.currentMainActivityClassName() ?: run {
                hookState.reset()
                XposedCompat.log("[$tag] MainActivity host capability missing")
                return
            }
            val mainActivityClass = XposedCompat.findClassOrNull(mainActivityClassName, cl) ?: run {
                hookState.reset()
                XposedCompat.log("[$tag] MainActivity class NOT FOUND")
                return
            }

            var installedCount = 0
            XposedCompat.findMethodOrNull(mainActivityClass, "onResume")?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    scheduleIfNeeded(chain.thisObject as? Activity, tag, delayRangeMs, onRun)
                    result
                }
                installedCount++
            }

            XposedCompat.findMethodOrNull(
                mainActivityClass,
                "onWindowFocusChanged",
                Boolean::class.javaPrimitiveType!!,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val hasFocus = chain.args.firstOrNull() as? Boolean ?: false
                    if (hasFocus) {
                        scheduleIfNeeded(chain.thisObject as? Activity, tag, delayRangeMs, onRun)
                    }
                    result
                }
                installedCount++
            }

            if (installedCount == 0) {
                hookState.reset()
                XposedCompat.log("[$tag] MainActivity lifecycle methods NOT FOUND")
                return
            }

            XposedCompat.log("[$tag] hook INSTALLED: MainActivity lifecycle auto sign-in signal")
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[$tag] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun scheduleIfNeeded(
        activity: Activity?,
        tag: String,
        delayRangeMs: LongRange,
        onRun: (Activity) -> Unit,
    ) {
        if (activity == null) return
        if (!HookSettings.isAutoDailySignInEnabled) return
        if (activity.isFinishing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed) return

        val pending = pendingRuns.getOrPut(tag) { AtomicBoolean(false) }
        if (!pending.compareAndSet(false, true)) return

        val delayMs = randomDelayMs(delayRangeMs)
        val activityRef = WeakReference(activity)
        mainHandler.postDelayed({
            pending.set(false)
            val current = activityRef.get() ?: return@postDelayed
            if (!HookSettings.isAutoDailySignInEnabled) return@postDelayed
            if (current.isFinishing) return@postDelayed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && current.isDestroyed) {
                return@postDelayed
            }
            runCatching { onRun(current) }
                .onFailure { t ->
                    XposedCompat.log("[$tag] scheduled run failed: ${t.message}")
                    XposedCompat.log(t)
                }
        }, delayMs)
        XposedCompat.logD("[$tag] auto sign-in scheduled after ${delayMs}ms")
    }

    private fun randomDelayMs(rangeMs: LongRange): Long {
        val first = rangeMs.first.coerceAtLeast(0L)
        val last = rangeMs.last.coerceAtLeast(first)
        if (first == last) return first
        return Random.nextLong(first, last + 1)
    }
}
