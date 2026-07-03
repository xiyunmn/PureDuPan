package com.xiyunmn.puredupan.hook.dexkit

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot

internal data class DexKitTargetDescriptor(
    val id: String,
    val target: String,
    val feature: String,
    val featureKey: String? = null,
)

internal data class DexKitWarmUpTask(
    val id: String,
    val resolve: () -> Boolean,
)

internal interface DexKitTargetRegistry {
    val descriptors: List<DexKitTargetDescriptor>

    fun buildTasks(
        host: DexKitHostContext,
        settings: SettingsSnapshot,
        classLoader: ClassLoader,
    ): List<DexKitWarmUpTask>
}

internal object EmptyDexKitTargetRegistry : DexKitTargetRegistry {
    override val descriptors: List<DexKitTargetDescriptor> = emptyList()

    override fun buildTasks(
        host: DexKitHostContext,
        settings: SettingsSnapshot,
        classLoader: ClassLoader,
    ): List<DexKitWarmUpTask> {
        return emptyList()
    }
}
