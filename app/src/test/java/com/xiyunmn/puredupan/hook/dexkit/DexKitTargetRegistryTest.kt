package com.xiyunmn.puredupan.hook.dexkit

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.dexkit.baidu.domestic.BaiduDomesticDexKitTargetRegistry
import com.xiyunmn.puredupan.hook.dexkit.baidu.intl.BaiduIntlDexKitTargetRegistry
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.HomeRecentItemLimitDexKitResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DexKitTargetRegistryTest {
    @Test
    fun everyVisibleDomesticDescriptorHasWarmUpTask() {
        assertDescriptorTaskCoverage(BaiduDomesticDexKitTargetRegistry)
    }

    @Test
    fun everyVisibleIntlDescriptorHasWarmUpTask() {
        assertDescriptorTaskCoverage(BaiduIntlDexKitTargetRegistry)
    }

    @Test
    fun homeRecentUsesOneAggregateStatusAndClearsInternalCaches() {
        listOf(BaiduDomesticDexKitTargetRegistry, BaiduIntlDexKitTargetRegistry).forEach { registry ->
            val descriptorIds = registry.descriptors.map { it.id }
            assertTrue(descriptorIds.contains(HomeRecentItemLimitDexKitResolver.STATUS_CACHE_ID))
            assertFalse(descriptorIds.any(HomeRecentItemLimitDexKitResolver.cacheIds::contains))

            val host = DexKitHostContext(
                hostId = "test",
                mainProcessName = "test",
                targetRegistryId = "test",
                availableFeatureKeys = registry.descriptors.mapNotNullTo(linkedSetOf()) { it.featureKey },
                stableActivityClassNames = emptyList(),
            )
            val task = registry.buildTasks(
                host = host,
                settings = SettingsSnapshot(),
                classLoader = javaClass.classLoader!!,
            ).single { it.id == HomeRecentItemLimitDexKitResolver.STATUS_CACHE_ID }
            assertTrue(task.cacheIds.containsAll(HomeRecentItemLimitDexKitResolver.cacheIds))
        }
    }

    private fun assertDescriptorTaskCoverage(registry: DexKitTargetRegistry) {
        val featureKeys = registry.descriptors.mapNotNullTo(linkedSetOf()) { it.featureKey }
        val host = DexKitHostContext(
            hostId = "test",
            mainProcessName = "test",
            targetRegistryId = "test",
            availableFeatureKeys = featureKeys,
            stableActivityClassNames = emptyList(),
        )
        val taskIds = registry.buildTasks(
            host = host,
            settings = SettingsSnapshot(),
            classLoader = javaClass.classLoader!!,
        ).mapTo(linkedSetOf()) { it.id }
        val descriptorIds = registry.descriptors.mapTo(linkedSetOf()) { it.id }

        assertEquals(descriptorIds, taskIds)
    }
}
