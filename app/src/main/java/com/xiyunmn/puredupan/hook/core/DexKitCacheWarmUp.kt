package com.xiyunmn.puredupan.hook.core

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.feature.performance.intl.IntlAlbumAiInitBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.intl.IntlNonCoreDiffSocketDelayHook
import com.xiyunmn.puredupan.hook.feature.performance.intl.IntlStoryDouyinInitBlockHook
import com.xiyunmn.puredupan.hook.feature.startup.hotstart.intl.IntlHotStartSplashDexKitResolver
import com.xiyunmn.puredupan.hook.host.HostFlavor
import com.xiyunmn.puredupan.hook.host.HostProfile
import java.util.concurrent.atomic.AtomicBoolean

internal object DexKitCacheWarmUp {
    private val started = AtomicBoolean(false)
    private val scanStarted = AtomicBoolean(false)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private const val HOME_STABLE_SCAN_DELAY_MS = 4500L
    private const val FALLBACK_SCAN_DELAY_MS = 12000L

    private val stableActivityClassNames = listOf(
        "com.baidu.netdisk.ui.MainActivity",
        "com.baidu.netdisk.homepage.HomeActivity",
    )

    data class TargetDescriptor(
        val id: String,
        val target: String,
        val feature: String,
    )

    data class TargetStatusView(
        val descriptor: TargetDescriptor,
        val state: String,
        val detail: String?,
        val success: Boolean,
    )

    val targetDescriptors = listOf(
        TargetDescriptor(
            id = IntlHotStartSplashDexKitResolver.CACHE_ID,
            target = "intl hot-start splash resolver",
            feature = "block hot-start splash ad",
        ),
        TargetDescriptor(
            id = IntlStoryDouyinInitBlockHook.STORY_INIT_CACHE_ID,
            target = "intl story init method",
            feature = "delay Story/Douyin startup init",
        ),
        TargetDescriptor(
            id = IntlNonCoreDiffSocketDelayHook.SOCKET_REGISTER_CACHE_ID,
            target = "intl diff socket register method",
            feature = "delay non-core diff socket registration",
        ),
        TargetDescriptor(
            id = IntlAlbumAiInitBlockHook.DIRECT_ALBUM_AI_INIT_CACHE_ID,
            target = "intl album init method",
            feature = "delay album AI startup init",
        ),
    )

    fun startIfNeeded(
        host: HostProfile,
        processName: String,
        settings: SettingsSnapshot,
        classLoader: ClassLoader,
    ) {
        if (!settings.isExperimentalDexKitEnabled) return
        if (host.flavor != HostFlavor.BAIDU_INTL) return
        if (!host.isMainProcess(processName)) return

        val tasks = buildTasks(host, classLoader)
        if (tasks.isEmpty()) {
            XposedCompat.logD("[DexKitCacheWarmUp] skipped: no available intl DexKit task")
            return
        }
        if (!started.compareAndSet(false, true)) return

        val forceFullScan = DexKitCompat.consumeFullScanPending()
        val hooked = installStableActivitySignal(classLoader, tasks, forceFullScan)
        if (!hooked) {
            XposedCompat.logW("[DexKitCacheWarmUp] stable activity signal unavailable, fallback scheduled")
        }
        mainHandler.postDelayed(
            { startWarmUpThread(tasks, forceFullScan, "fallback") },
            FALLBACK_SCAN_DELAY_MS,
        )
    }

    fun statusViews(): List<TargetStatusView> {
        return targetDescriptors.map { descriptor ->
            val status = DexKitCompat.readTargetStatus(descriptor.id)
            TargetStatusView(
                descriptor = descriptor,
                state = status?.state ?: "pending",
                detail = status?.detail,
                success = status?.success == true,
            )
        }
    }

    fun summaryText(): String {
        val statuses = statusViews()
        val total = statuses.size
        val success = statuses.count { it.success }
        return "$success/$total"
    }

    private fun buildTasks(host: HostProfile, classLoader: ClassLoader): List<WarmUpTask> {
        val tasks = mutableListOf<WarmUpTask>()
        val capabilities = host.capabilities

        if (capabilities.supportsStandaloneHotStartSplashRemove && capabilities.supportsHotStartSplashAd) {
            tasks += WarmUpTask(IntlHotStartSplashDexKitResolver.CACHE_ID) {
                IntlHotStartSplashDexKitResolver.resolve(classLoader) != null
            }
        }
        if (capabilities.supportsIntlStoryDouyinInitBlock) {
            tasks += WarmUpTask(IntlStoryDouyinInitBlockHook.STORY_INIT_CACHE_ID) {
                IntlStoryDouyinInitBlockHook.warmUpDexKitCache(classLoader)
            }
        }
        if (capabilities.supportsIntlNonCoreDiffSocketDelay) {
            tasks += WarmUpTask(IntlNonCoreDiffSocketDelayHook.SOCKET_REGISTER_CACHE_ID) {
                IntlNonCoreDiffSocketDelayHook.warmUpDexKitCache(classLoader)
            }
        }
        if (capabilities.supportsIntlAlbumAiInitBlock) {
            tasks += WarmUpTask(IntlAlbumAiInitBlockHook.DIRECT_ALBUM_AI_INIT_CACHE_ID) {
                IntlAlbumAiInitBlockHook.warmUpDexKitCache(classLoader)
            }
        }

        return tasks
    }

    private fun installStableActivitySignal(
        classLoader: ClassLoader,
        tasks: List<WarmUpTask>,
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
                            startWarmUpThread(
                                tasks = tasks,
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

    private fun startWarmUpThread(
        tasks: List<WarmUpTask>,
        forceFullScan: Boolean,
        reason: String,
    ) {
        if (!scanStarted.compareAndSet(false, true)) return
        Thread({
            DexKitCompat.runWithScanningAllowed {
                warmUp(tasks, forceFullScan, reason)
            }
        }, "WPH-DexKit-WarmUp").apply {
            isDaemon = true
            start()
        }
    }

    private fun warmUp(tasks: List<WarmUpTask>, forceFullScan: Boolean, reason: String) {
        if (forceFullScan) {
            DexKitCompat.clearCachedMethods(
                "DexKitCacheWarmUp",
                targetDescriptors.map { it.id },
            )
        }
        XposedCompat.log(
            "[DexKitCacheWarmUp] warm-up START: " +
                "forceFullScan=$forceFullScan, reason=$reason, tasks=${tasks.joinToString { it.id }}",
        )
        var foundCount = 0
        tasks.forEach { task ->
            val beforeStatus = DexKitCompat.readTargetStatus(task.id)
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
                DexKitCompat.markTargetError("DexKitCacheWarmUp", task.id, t.message)
                XposedCompat.logW("[DexKitCacheWarmUp] task failed: ${task.id}, ${t.message}")
                XposedCompat.log(t)
            }
        }
        XposedCompat.log("[DexKitCacheWarmUp] warm-up END: found=$foundCount/${tasks.size}")
    }

    private data class WarmUpTask(
        val id: String,
        val resolve: () -> Boolean,
    )
}
