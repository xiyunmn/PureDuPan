package com.xiyunmn.puredupan.hook.plan

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot

internal data class DerivedSettings(
    val hasMemberCardCustomizeOption: Boolean,
    val hasHomeCustomizeOption: Boolean,
    val hasFilePageCustomizeOption: Boolean,
    val hasSearchPageCustomizeOption: Boolean,
    val hasMyPageCustomizeOption: Boolean,
    val hasBottomBarTabOption: Boolean,
)

internal data class HookSpec(
    val id: String,
    val enabled: (PlanContext, SettingsSnapshot, DerivedSettings) -> Boolean,
    val featureKey: String? = null,
    val install: (ClassLoader) -> Unit,
) {
    fun isFeatureAvailableFor(context: PlanContext): Boolean {
        val key = featureKey ?: return true
        return context.isFeatureAvailable(key)
    }
}

internal class PlanContext(
    private val host: HookPlanHostContext,
) {
    val isMain: Boolean = host.isMain
    val isPushService: Boolean = host.isPushService
    val supportsOemPushHook: Boolean = host.supportsOemPushHook

    fun isFeatureAvailable(featureKey: String): Boolean {
        return host.isFeatureAvailable(featureKey)
    }
}
