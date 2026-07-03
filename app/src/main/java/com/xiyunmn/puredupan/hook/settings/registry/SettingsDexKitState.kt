package com.xiyunmn.puredupan.hook.settings.registry

import android.content.Context
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.config.runtime.DexKitSettingsRuntime
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCacheWarmUp
import com.xiyunmn.puredupan.hook.dexkit.DexKitHostContext
import com.xiyunmn.puredupan.hook.host.HostRuntimeState

internal object SettingsDexKitState {
    data class TargetStatusView(
        val id: String,
        val target: String,
        val feature: String,
        val featureKey: String?,
        val state: String,
        val detail: String?,
        val updatedAt: Long,
    )

    fun summaryText(context: Context): String {
        val host = hostFor(context) ?: return "0/0"
        return DexKitCacheWarmUp.summaryText(host)
    }

    fun shouldContinueStatusRefresh(context: Context): Boolean {
        return DexKitCacheWarmUp.isScanRunning() ||
            statusViews(context).any { status ->
                status.state == "pending" || status.state == "scanning"
            }
    }

    fun statusViews(context: Context): List<TargetStatusView> {
        val host = hostFor(context) ?: return emptyList()
        return DexKitCacheWarmUp.statusViews(host).map { status ->
            TargetStatusView(
                id = status.descriptor.id,
                target = status.descriptor.target,
                feature = status.descriptor.feature,
                featureKey = status.descriptor.featureKey,
                state = status.state,
                detail = status.detail,
                updatedAt = status.updatedAt,
            )
        }
    }

    fun markFullScanPendingFromSettings(context: Context) {
        if (!SettingsHostState.isFeatureVisibleForContext(
                context,
                SettingsUserState.KEY_DEXKIT_STATUS,
            )
        ) {
            XposedCompat.logW("[SettingsDexKitState] full scan pending skipped: unsupported host")
            return
        }
        DexKitSettingsRuntime.markFullScanPendingFromSettings()
    }

    fun triggerFullScanFromSettings(context: Context): Boolean {
        if (!SettingsHostState.isFeatureVisibleForContext(
                context,
                SettingsUserState.KEY_DEXKIT_STATUS,
            )
        ) {
            XposedCompat.logW("[SettingsDexKitState] full scan trigger skipped: unsupported host")
            return false
        }
        val host = hostFor(context) ?: run {
            DexKitSettingsRuntime.markFullScanPendingFromSettings()
            return false
        }
        val started = DexKitCacheWarmUp.scanNow(
            host = host,
            settings = ConfigManager.snapshot(),
            classLoader = context.classLoader,
            forceFullScan = true,
            reason = "settings manual scan",
        )
        if (!started) {
            DexKitSettingsRuntime.markFullScanPendingFromSettings()
        }
        return started
    }

    private fun hostFor(context: Context) =
        HostRuntimeState.dexKitRuntimeInfoForPackage(context.packageName)
            ?.let(DexKitHostContext::from)
}
