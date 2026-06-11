package com.xiyunmn.puredupan.hook.config.model

object FeatureAvailabilityState {
    const val FULL = "full"
    const val PARTIAL = "partial"
    const val DISABLED = "disabled"
    const val HARD_CODED = "hardcoded"
}

data class FeatureAvailabilityStatus(
    val state: String = FeatureAvailabilityState.DISABLED,
    val missingCritical: List<String> = emptyList(),
    val missingOptional: List<String> = emptyList(),
) {
    fun isSupported(): Boolean = state != FeatureAvailabilityState.DISABLED
    fun isPartial(): Boolean = state == FeatureAvailabilityState.PARTIAL
}
