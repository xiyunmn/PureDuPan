package com.xiyunmn.puredupan.hook.dexkit

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.util.concurrent.atomic.AtomicBoolean

internal object DexKitCacheWarmUp {
    private val started = AtomicBoolean(false)
    private val automaticScanStarted = AtomicBoolean(false)
    private val scanRunning = AtomicBoolean(false)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private const val HOME_STABLE_SCAN_DELAY_MS = 4500L
    private const val FALLBACK_SCAN_DELAY_MS = 12000L

    data class TargetStatusView(
        val descriptor: DexKitTargetDescriptor,
        val state: String,
        val detail: String?,
        val updatedAt: Long,
        val success: Boolean,
    )

    fun startIfNeeded(
        host: DexKitHostContext,
        processName: String,
        settings: SettingsSnapshot,
        classLoader: ClassLoader,
    ) {
        if (!host.isMainProcess(processName)) return

        val registry = HostDexKitTargetRegistries.forHost(host)
        val tasks = registry.buildTasks(host, classLoader)
        if (tasks.isEmpty()) {
            XposedCompat.logD("[DexKitCacheWarmUp] skipped: no available DexKit task for host=${host.hostId}")
            return
        }
        val forceFullScan = DexKitCompat.consumeFullScanPending()
        if (!forceFullScan && tasks.all { task -> DexKitCompat.readTargetStatus(task.id)?.isTerminal() == true }) {
            XposedCompat.logD("[DexKitCacheWarmUp] skipped: current DexKit cache is already terminal")
            return
        }
        if (!started.compareAndSet(false, true)) return

        val hooked = installStableActivitySignal(
            classLoader = classLoader,
            stableActivityClassNames = host.stableActivityClassNames,
            tasks = tasks,
            descriptors = registry.descriptors,
            forceFullScan = forceFullScan,
        )
        if (!hooked) {
            XposedCompat.logW("[DexKitCacheWarmUp] stable activity signal unavailable, fallback scheduled")
        }
        mainHandler.postDelayed(
            { startAutomaticWarmUpThread(tasks, registry.descriptors, forceFullScan, "fallback") },
            FALLBACK_SCAN_DELAY_MS,
        )
    }

    fun scanNow(
        host: DexKitHostContext,
        classLoader: ClassLoader,
        forceFullScan: Boolean,
        reason: String,
    ): Boolean {
        val registry = HostDexKitTargetRegistries.forHost(host)
        val tasks = registry.buildTasks(host, classLoader)
        if (tasks.isEmpty()) {
            XposedCompat.logD("[DexKitCacheWarmUp] scan-now skipped: no available DexKit task for host=${host.hostId}")
            return false
        }
        return startWarmUpThread(
            tasks = tasks,
            descriptors = registry.descriptors,
            forceFullScan = forceFullScan,
            reason = reason,
            consumePendingFullScan = true,
        )
    }

    fun statusViews(host: DexKitHostContext): List<TargetStatusView> {
        return HostDexKitTargetRegistries.forHost(host).descriptors.map { descriptor ->
            val status = DexKitCompat.readTargetStatus(descriptor.id)
            TargetStatusView(
                descriptor = descriptor,
                state = status?.state ?: "pending",
                detail = status?.detail,
                updatedAt = status?.updatedAt ?: 0L,
                success = status?.success == true,
            )
        }
    }

    fun summaryText(host: DexKitHostContext): String {
        val statuses = statusViews(host)
        val total = statuses.size
        val success = statuses.count { it.success }
        return "$success/$total"
    }

    private fun installStableActivitySignal(
        classLoader: ClassLoader,
        stableActivityClassNames: List<String>,
        tasks: List<DexKitWarmUpTask>,
        descriptors: List<DexKitTargetDescriptor>,
        forceFullScan: Boolean,
    ): Boolean {
        val mod = XposedCompat.module ?: return false
        var installed = false
        for (className in stableActivityClassNames) {
            val clazz = XposedCompat.findClassOrNull(className, classLoader) ?: continue
            if (!Activity::class.java.isAssignableFrom(clazz)) continue
            val method = XposedCompat.findMethodOrNull(
                clazz,
                "onWindowFocusChanged",
                Boolean::class.javaPrimitiveType!!,
            ) ?: continue
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val hasFocus = chain.args.firstOrNull() as? Boolean ?: false
                if (hasFocus) {
                    mainHandler.postDelayed(
                        {
                            startAutomaticWarmUpThread(
                                tasks = tasks,
                                descriptors = descriptors,
                                forceFullScan = forceFullScan,
                                reason = "${clazz.name}.onWindowFocusChanged",
                            )
                        },
                        HOME_STABLE_SCAN_DELAY_MS,
                    )
                }
                result
            }
            installed = true
            XposedCompat.logD("[DexKitCacheWarmUp] stable activity signal installed: $className")
        }
        return installed
    }

    private fun startAutomaticWarmUpThread(
        tasks: List<DexKitWarmUpTask>,
        descriptors: List<DexKitTargetDescriptor>,
        forceFullScan: Boolean,
        reason: String,
    ): Boolean {
        if (!automaticScanStarted.compareAndSet(false, true)) return false
        return startWarmUpThread(
            tasks = tasks,
            descriptors = descriptors,
            forceFullScan = forceFullScan,
            reason = reason,
            consumePendingFullScan = false,
        )
    }

    private fun startWarmUpThread(
        tasks: List<DexKitWarmUpTask>,
        descriptors: List<DexKitTargetDescriptor>,
        forceFullScan: Boolean,
        reason: String,
        consumePendingFullScan: Boolean,
    ): Boolean {
        if (!scanRunning.compareAndSet(false, true)) {
            XposedCompat.logD("[DexKitCacheWarmUp] scan skipped: already running, reason=$reason")
            return false
        }
        Thread({
            try {
                val pendingForceFullScan = if (consumePendingFullScan) {
                    DexKitCompat.consumeFullScanPending()
                } else {
                    false
                }
                val effectiveForceFullScan = forceFullScan || pendingForceFullScan
                DexKitCompat.runWithScanningAllowed {
                    warmUp(tasks, descriptors, effectiveForceFullScan, reason)
                }
            } finally {
                scanRunning.set(false)
            }
        }, "WPH-DexKit-WarmUp").apply {
            isDaemon = true
            start()
        }
        return true
    }

    private fun warmUp(
        tasks: List<DexKitWarmUpTask>,
        descriptors: List<DexKitTargetDescriptor>,
        forceFullScan: Boolean,
        reason: String,
    ) {
        if (forceFullScan) {
            DexKitCompat.clearCachedMethods(
                "DexKitCacheWarmUp",
                descriptors.map { it.id },
            )
        }
        XposedCompat.log(
            "[DexKitCacheWarmUp] warm-up START: " +
                "forceFullScan=$forceFullScan, reason=$reason, tasks=${tasks.joinToString { it.id }}",
        )
        var foundCount = 0
        var skippedCount = 0
        tasks.forEach { task ->
            val beforeStatus = DexKitCompat.readTargetStatus(task.id)
            if (!forceFullScan && beforeStatus?.success == true) {
                foundCount++
                skippedCount++
                XposedCompat.logD("[DexKitCacheWarmUp] task skipped: cached success, id=${task.id}")
                return@forEach
            }
            if (DexKitCompat.shouldSkipScan("DexKitCacheWarmUp", task.id, forceFullScan)) {
                skippedCount++
                return@forEach
            }
            if (forceFullScan || beforeStatus == null) {
                DexKitCompat.markTargetScanning("DexKitCacheWarmUp", task.id)
            }
            runCatching {
                if (task.resolve()) {
                    foundCount++
                    if (DexKitCompat.readTargetStatus(task.id)?.success != true) {
                        DexKitCompat.markTargetSuccess("DexKitCacheWarmUp", task.id, null)
                    }
                } else if (
                    DexKitCompat.readTargetStatus(task.id)?.state.let { it == null || it == "scanning" }
                ) {
                    DexKitCompat.markTargetError(
                        "DexKitCacheWarmUp",
                        task.id,
                        "no resolver result",
                    )
                }
            }.onFailure { t ->
                DexKitCompat.markTargetError(
                    "DexKitCacheWarmUp",
                    task.id,
                    buildFailureDetail(task.id, t),
                )
                XposedCompat.logW("[DexKitCacheWarmUp] task failed: ${task.id}, ${t.message}")
                XposedCompat.log(t)
            }
        }
        XposedCompat.log(
            "[DexKitCacheWarmUp] warm-up END: found=$foundCount/${tasks.size}, skipped=$skippedCount",
        )
    }

    private fun buildFailureDetail(taskId: String, t: Throwable): String {
        val lines = t.stackTraceToString()
            .lineSequence()
            .take(10)
            .joinToString("\n")
        return buildString {
            append("task=").append(taskId).append('\n')
            append("throwable=").append(t::class.java.name).append('\n')
            append("message=").append(t.message ?: "-").append('\n')
            append("stacktrace=\n").append(lines)
        }
    }

    private fun DexKitCompat.TargetStatus.isTerminal(): Boolean {
        return state != "pending" && state != "scanning"
    }
}
