package com.xiyunmn.puredupan.hook.dexkit

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.baidu.domestic.BaiduDomesticDexKitTargetRegistry
import com.xiyunmn.puredupan.hook.dexkit.baidu.intl.BaiduIntlDexKitTargetRegistry
import com.xiyunmn.puredupan.hook.host.HostIds
import com.xiyunmn.puredupan.hook.host.HostRegistry

internal object HostDexKitTargetRegistries {
    private val registryEntries: List<Pair<String, DexKitTargetRegistry>> = listOf(
        HostIds.BAIDU_CN to BaiduDomesticDexKitTargetRegistry,
        HostIds.BAIDU_INTL to BaiduIntlDexKitTargetRegistry,
        HostIds.BAIDU_SAMSUNG to BaiduDomesticDexKitTargetRegistry,
    )
    private val registriesById: Map<String, DexKitTargetRegistry> = registryEntries.toMap()

    init {
        val registeredRegistryIds = registryEntries.map { it.first }
        val duplicateRegistryIds = duplicateValues(registeredRegistryIds)
        require(duplicateRegistryIds.isEmpty()) {
            "Duplicate DexKit target registry registrations: ${duplicateRegistryIds.joinToString()}"
        }
        val registeredRegistryIdSet = registeredRegistryIds.toSet()
        val missingIds = HostRegistry.requiredDexKitTargetRegistryIds - registeredRegistryIdSet
        require(missingIds.isEmpty()) {
            "Missing DexKit target registry registration: ${missingIds.joinToString()}"
        }
        val unusedIds = registeredRegistryIdSet - HostRegistry.requiredDexKitTargetRegistryIds
        require(unusedIds.isEmpty()) {
            "Unused DexKit target registry registrations: ${unusedIds.joinToString()}"
        }
        registriesById.forEach { (registryId, registry) ->
            val descriptorIds = registry.descriptors.map { it.id }
            require(descriptorIds.none { it.isBlank() }) {
                "Blank DexKit descriptor id in registry $registryId"
            }
            val duplicateDescriptorIds = duplicateValues(descriptorIds)
            require(duplicateDescriptorIds.isEmpty()) {
                "Duplicate DexKit descriptor ids in registry $registryId: " +
                    duplicateDescriptorIds.joinToString()
            }
        }
    }

    fun forHost(host: DexKitHostContext): DexKitTargetRegistry {
        val registryId = host.targetRegistryId ?: return EmptyDexKitTargetRegistry
        return registriesById[registryId] ?: run {
            XposedCompat.logW(
                "[HostDexKitTargetRegistries] missing DexKit registry: " +
                    "host=${host.hostId}, registryId=$registryId",
            )
            EmptyDexKitTargetRegistry
        }
    }
}

private fun duplicateValues(values: List<String>): Set<String> {
    return values
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
}
