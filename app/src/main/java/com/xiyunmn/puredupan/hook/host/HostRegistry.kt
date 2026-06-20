package com.xiyunmn.puredupan.hook.host

import com.xiyunmn.puredupan.hook.host.profiles.baidu.BaiduCnHostProfile
import com.xiyunmn.puredupan.hook.host.profiles.baidu.BaiduIntlHostProfile
import com.xiyunmn.puredupan.hook.host.profiles.baidu.BaiduSamsungHostProfile

internal object HostRegistry {
    private val profiles = listOf(
        BaiduCnHostProfile,
        BaiduIntlHostProfile,
        BaiduSamsungHostProfile,
    )

    init {
        HostProfileValidator.validate(profiles)
    }

    val supportedPackageNames: Set<String> = profiles.mapTo(linkedSetOf()) { it.packageName }
    val supportedFeatureKeys: Set<String> =
        profiles.flatMapTo(linkedSetOf()) { it.capabilities.features.availableKeys }
    val hookCatalogRequirements: List<HostHookCatalogRequirement> =
        profiles.mapNotNull { profile ->
            val catalogId = profile.capabilities.hooks.catalogId ?: return@mapNotNull null
            HostHookCatalogRequirement(
                hostId = profile.id,
                catalogId = catalogId,
                availableFeatureKeys = profile.capabilities.features.availableKeys,
            )
        }
    val requiredHookCatalogIds: Set<String> =
        hookCatalogRequirements.mapTo(linkedSetOf()) { it.catalogId }
    val requiredDexKitTargetRegistryIds: Set<String> =
        profiles.mapNotNullTo(linkedSetOf()) { it.capabilities.dexKit.targetRegistryId }

    fun resolveByPackageName(packageName: String): HostProfile? {
        return profiles.firstOrNull { it.handlesPackage(packageName) }
    }
}

internal data class HostHookCatalogRequirement(
    val hostId: String,
    val catalogId: String,
    val availableFeatureKeys: Set<String>,
)
