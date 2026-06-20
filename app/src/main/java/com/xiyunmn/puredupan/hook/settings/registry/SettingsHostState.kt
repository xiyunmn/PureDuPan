package com.xiyunmn.puredupan.hook.settings.registry

import android.content.Context
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityStatus
import com.xiyunmn.puredupan.hook.config.model.MemberCardLayoutMode
import com.xiyunmn.puredupan.hook.host.HostRuntimeState

internal object SettingsHostState {
    fun isSupportedHost(context: Context): Boolean {
        return HostRuntimeState.isSupportedPackage(context.packageName)
    }

    fun featureStatusMapFor(context: Context): Map<String, FeatureAvailabilityStatus> {
        return HostRuntimeState.featureStatusMapFor(context)
    }

    fun showDexKitStatusInSettings(context: Context): Boolean {
        return HostRuntimeState.showDexKitStatusInSettings(context)
    }

    fun memberCardLayoutMode(context: Context): MemberCardLayoutMode {
        return HostRuntimeState.memberCardLayoutModeFor(context)
    }

    fun primarySplashAdFeatureKeyFor(context: Context): String? {
        return HostRuntimeState.primarySplashAdFeatureKeyFor(context)
    }

    fun canonicalPackageName(context: Context): String {
        return HostRuntimeState.canonicalPackageNameOrSelf(context.packageName)
    }

    fun hostId(context: Context): String? {
        return HostRuntimeState.hostIdForPackage(context.packageName)
    }

    fun isFeatureVisibleForContext(context: Context, featureKey: String): Boolean {
        return featureStatusMapFor(context)[featureKey]?.isSupported() == true
    }
}
