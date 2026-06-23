package com.xiyunmn.puredupan.hook.config.runtime

import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityStatus
import com.xiyunmn.puredupan.hook.host.HostRuntimeState

internal object ConfigHostRuntime {
    private const val HOST_ID_SUFFIX_INTL = "_intl"

    fun supportedPackageNames(): Set<String> {
        return HostRuntimeState.supportedPackageNames()
    }

    fun featureStatusMapForPackage(packageName: String): Map<String, FeatureAvailabilityStatus> {
        return HostRuntimeState.featureStatusMapForPackage(packageName)
    }

    fun supportsExperimentalDexKit(packageName: String): Boolean {
        return HostRuntimeState.supportsExperimentalDexKit(packageName)
    }

    fun canonicalPackageNameOrSelf(packageName: String): String {
        return HostRuntimeState.canonicalPackageNameOrSelf(packageName)
    }

    fun isIntlHost(packageName: String): Boolean {
        return HostRuntimeState.hostIdForPackage(packageName)?.endsWith(HOST_ID_SUFFIX_INTL) == true
    }
}
