package com.xiyunmn.puredupan.hook.plan

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.host.HostIds
import com.xiyunmn.puredupan.hook.host.HostRegistry
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.cn.BaiduCnHookCatalog
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.intl.BaiduIntlHookCatalog
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.samsung.BaiduSamsungHookCatalog

internal object HostHookCatalogs {
    private val catalogEntries: List<Pair<String, HookCatalog>> = listOf(
        HostIds.BAIDU_CN to BaiduCnHookCatalog,
        HostIds.BAIDU_INTL to BaiduIntlHookCatalog,
        HostIds.BAIDU_SAMSUNG to BaiduSamsungHookCatalog,
    )
    private val catalogsById: Map<String, HookCatalog> = catalogEntries.toMap()

    init {
        val registeredCatalogIds = catalogEntries.map { it.first }
        val duplicateCatalogIds = duplicateValues(registeredCatalogIds)
        require(duplicateCatalogIds.isEmpty()) {
            "Duplicate hook catalog registrations: ${duplicateCatalogIds.joinToString()}"
        }
        val registeredCatalogIdSet = registeredCatalogIds.toSet()
        val missingIds = HostRegistry.requiredHookCatalogIds - registeredCatalogIdSet
        require(missingIds.isEmpty()) {
            "Missing hook catalog registration: ${missingIds.joinToString()}"
        }
        val unusedIds = registeredCatalogIdSet - HostRegistry.requiredHookCatalogIds
        require(unusedIds.isEmpty()) {
            "Unused hook catalog registrations: ${unusedIds.joinToString()}"
        }
        catalogsById.forEach { (catalogId, catalog) ->
            val specs = catalog.postAttachSpecs()
            val blankSpecIds = specs.map { it.id }.filter { it.isBlank() }
            require(blankSpecIds.isEmpty()) {
                "Blank hook spec id in catalog $catalogId"
            }
            val duplicateSpecIds = duplicateValues(specs.map { it.id })
            require(duplicateSpecIds.isEmpty()) {
                "Duplicate hook specs in catalog $catalogId: ${duplicateSpecIds.joinToString()}"
            }
        }
        HostRegistry.hookCatalogRequirements.forEach { requirement ->
            val catalogId = requirement.catalogId
            val catalog = catalogsById[catalogId] ?: return@forEach
            val unavailableFeatureKeys = catalog.postAttachSpecs()
                .mapNotNull { it.featureKey }
                .filterNot { it in requirement.availableFeatureKeys }
                .toSet()
            require(unavailableFeatureKeys.isEmpty()) {
                "Hook catalog $catalogId contains unavailable feature keys for host ${requirement.hostId}: " +
                    unavailableFeatureKeys.joinToString()
            }
        }
    }

    fun forHost(host: HookPlanHostContext): HookCatalog {
        val catalogId = host.catalogId ?: return EmptyHookCatalog
        return catalogsById[catalogId] ?: run {
            XposedCompat.logW(
                "[HostHookCatalogs] missing hook catalog: host=${host.hostId}, catalogId=$catalogId",
            )
            EmptyHookCatalog
        }
    }
}

private object EmptyHookCatalog : HookCatalog {
    override fun postAttachSpecs(): List<HookSpec> = emptyList()
}

private fun duplicateValues(values: List<String>): Set<String> {
    return values
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
}
