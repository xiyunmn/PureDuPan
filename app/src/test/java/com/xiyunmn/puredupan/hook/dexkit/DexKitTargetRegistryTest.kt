package com.xiyunmn.puredupan.hook.dexkit

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.dexkit.baidu.domestic.BaiduDomesticDexKitTargetRegistry
import com.xiyunmn.puredupan.hook.dexkit.baidu.intl.BaiduIntlDexKitTargetRegistry
import org.junit.Assert.assertEquals
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
