package com.xiyunmn.puredupan.hook.feature.baidu.intl.performance

import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Method

internal object IntlAudioCircleStartupShowBlockHook {
    private const val TAG = "IntlAudioCircleStartupShowBlockHook"
    private const val AUDIO_API_CLASS_NAME = BaiduIntlHookPoints.AUDIO_API
    private const val AUDIO_PLAYER_ACTIVITY_CLASS_NAME = BaiduIntlHookPoints.AUDIO_PLAYER_ACTIVITY
    private const val HOME_STABLE_DELAY_MS = 2500L
    private const val MAX_STARTUP_WINDOW_MS = 30_000L

    private data class AudioState(
        val sourceKnown: Boolean,
        val hasPlayingSource: Boolean,
        val activeKnown: Boolean,
        val isAudioCircleActive: Boolean,
    )

    private val hookState = HookState()
    private val lock = Any()
    private val processStartElapsed = SystemClock.elapsedRealtime()

    @Volatile private var getPlayingAudiosMethod: Method? = null
    @Volatile private var isAudioCircleActiveMethod: Method? = null
    @Volatile private var homeStableReached = false
    @Volatile private var audioEntryReached = false
    @Volatile private var homeStableScheduled = false
    @Volatile private var blockedCount = 0
    @Volatile private var allowedCount = 0

    internal fun hook(cl: ClassLoader) {
        // 旧代已交还待恢复调用，热重载不重新进入冷启动抑制阶段。
        if (ModuleLifetime.hotReloaded) return
        if (!isEnabled()) {
            XposedCompat.log("[IntlAudioCircleStartupShowBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val apiClass = XposedCompat.findClassOrNull(AUDIO_API_CLASS_NAME, cl)
            if (apiClass == null) {
                hookState.reset()
                XposedCompat.log("[IntlAudioCircleStartupShowBlockHook] MAudioApi class NOT FOUND")
                return
            }
            getPlayingAudiosMethod = XposedCompat.findMethodOrNull(apiClass, "getPlayingAudios")
            isAudioCircleActiveMethod =
                XposedCompat.findMethodOrNull(apiClass, "isAudioCircleViewManagerAudioActive")
            if (getPlayingAudiosMethod == null || isAudioCircleActiveMethod == null) {
                hookState.reset()
                XposedCompat.log("[IntlAudioCircleStartupShowBlockHook] audio state methods NOT FOUND")
                return
            }

            val showHooks = hookShowAudioCircleMethods(apiClass)
            val entryHooks = hookAudioEntrySignals(cl, apiClass)
            val homeHooked = hookHomeStableSignal(cl)
            if (showHooks == 0) {
                hookState.reset()
                XposedCompat.log("[IntlAudioCircleStartupShowBlockHook] show methods NOT FOUND")
                return
            }

            XposedCompat.log(
                "[IntlAudioCircleStartupShowBlockHook] hooks INSTALLED: " +
                    "showHooks=$showHooks, entryHooks=$entryHooks, homeStable=$homeHooked",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlAudioCircleStartupShowBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookShowAudioCircleMethods(apiClass: Class<*>): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0
        for (method in apiClass.declaredMethods) {
            if (method.name != "showAudioCircleViewManagerAudio") continue
            method.isAccessible = true
            mod.hook(method).setId("IntlAudioCircleStartupShowBlockHook:hookShowAudioCircleMethods:method").intercept { chain ->
                if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
                val receiver = chain.thisObject
                val activity = chain.args.firstOrNull() as? Activity
                val methodLabel = "${method.name}/${method.parameterTypes.size}"
                if (receiver == null || !shouldBlockStartupShow(receiver, activity, methodLabel)) {
                    return@intercept chain.proceed()
                }

                synchronized(lock) {
                    blockedCount++
                }
                XposedCompat.log(
                    "[IntlAudioCircleStartupShowBlockHook] blocked startup audio circle show: " +
                        "method=$methodLabel, activity=${activity?.javaClass?.name}, " +
                        "blockedCount=$blockedCount",
                )
                null
            }
            installed++
        }
        return installed
    }

    private fun hookAudioEntrySignals(cl: ClassLoader, apiClass: Class<*>): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0

        for (method in apiClass.declaredMethods) {
            if (method.name != "startAudioPlayerActivity") continue
            method.isAccessible = true
            mod.hook(method).setId("IntlAudioCircleStartupShowBlockHook:hookAudioEntrySignals:method").intercept { chain ->
                if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
                markAudioEntryReached("api:${method.name}/${method.parameterTypes.size}")
                chain.proceed()
            }
            installed++
        }

        val playerClass = XposedCompat.findClassOrNull(AUDIO_PLAYER_ACTIVITY_CLASS_NAME, cl)
        if (playerClass != null) {
            XposedCompat.findMethodOrNull(playerClass, "onCreate", Bundle::class.java)?.let { method ->
                mod.hook(method).setId("IntlAudioCircleStartupShowBlockHook:hookAudioEntrySignals:method:2").intercept { chain ->
                    if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
                    markAudioEntryReached("activity:onCreate")
                    chain.proceed()
                }
                installed++
            }
            XposedCompat.findMethodOrNull(playerClass, "onResume")?.let { method ->
                mod.hook(method).setId("IntlAudioCircleStartupShowBlockHook:hookAudioEntrySignals:method:3").intercept { chain ->
                    if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
                    markAudioEntryReached("activity:onResume")
                    chain.proceed()
                }
                installed++
            }
        } else {
            XposedCompat.log("[IntlAudioCircleStartupShowBlockHook] AudioPlayerActivity class NOT FOUND")
        }

        return installed
    }

    private fun hookHomeStableSignal(cl: ClassLoader): Boolean =
        IntlHomeStableRestoreSignal.hook(cl, TAG) {
            scheduleHomeStable()
        }

    private fun shouldBlockStartupShow(receiver: Any, activity: Activity?, methodLabel: String): Boolean {
        if (!isEnabled()) return false
        if (!isInStartupWindow()) return false
        if (audioEntryReached) return allowShow(methodLabel, "audio_entry_reached")
        if (activity?.javaClass?.name == AUDIO_PLAYER_ACTIVITY_CLASS_NAME) {
            return allowShow(methodLabel, "audio_player_activity")
        }

        val state = readAudioState(receiver)
        if (!state.sourceKnown || !state.activeKnown) {
            return allowShow(methodLabel, "audio_state_unknown")
        }
        if (state.hasPlayingSource || state.isAudioCircleActive) {
            return allowShow(
                methodLabel,
                "active_or_has_source:source=${state.hasPlayingSource},active=${state.isAudioCircleActive}",
            )
        }
        return true
    }

    private fun readAudioState(receiver: Any): AudioState {
        val sourceMethod = getPlayingAudiosMethod
        val activeMethod = isAudioCircleActiveMethod
        val playingSource = runCatching {
            sourceMethod?.invoke(receiver)
        }.getOrElse { t ->
            XposedCompat.logW("[IntlAudioCircleStartupShowBlockHook] getPlayingAudios failed: ${t.message}")
            return AudioState(
                sourceKnown = false,
                hasPlayingSource = false,
                activeKnown = false,
                isAudioCircleActive = false,
            )
        }
        val active = runCatching {
            activeMethod?.invoke(receiver) as? Boolean
        }.getOrElse { t ->
            XposedCompat.logW(
                "[IntlAudioCircleStartupShowBlockHook] isAudioCircleViewManagerAudioActive failed: ${t.message}",
            )
            return AudioState(
                sourceKnown = true,
                hasPlayingSource = playingSource != null,
                activeKnown = false,
                isAudioCircleActive = false,
            )
        }
        return AudioState(
            sourceKnown = sourceMethod != null,
            hasPlayingSource = playingSource != null,
            activeKnown = active != null,
            isAudioCircleActive = active == true,
        )
    }

    private fun allowShow(methodLabel: String, reason: String): Boolean {
        synchronized(lock) {
            allowedCount++
        }
        XposedCompat.logD(
            "[IntlAudioCircleStartupShowBlockHook] allow audio circle show: " +
                "method=$methodLabel, reason=$reason, allowedCount=$allowedCount",
        )
        return false
    }

    private fun isInStartupWindow(): Boolean {
        if (homeStableReached) return false
        return SystemClock.elapsedRealtime() - processStartElapsed <= MAX_STARTUP_WINDOW_MS
    }

    private fun markAudioEntryReached(reason: String) {
        if (audioEntryReached) return
        audioEntryReached = true
        XposedCompat.logD("[IntlAudioCircleStartupShowBlockHook] audio entry reached: reason=$reason")
    }

    private fun scheduleHomeStable() {
        IntlHomeStableRestoreSignal.scheduleDelayedRestore(
            tag = TAG,
            delayMs = HOME_STABLE_DELAY_MS,
            tryMarkScheduled = {
                synchronized(lock) {
                    if (homeStableReached || homeStableScheduled) {
                        false
                    } else {
                        homeStableScheduled = true
                        true
                    }
                }
            },
            clearScheduled = { homeStableScheduled = false },
            restore = {
                homeStableReached = true
                XposedCompat.logD("[$TAG] home stable reached")
            },
        )
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isIntlAudioCircleStartupShowBlocked
}
