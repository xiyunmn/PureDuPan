package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal enum class DebugSettingsAction {
    NONE,
    DEXKIT_STATUS,
    CLEAR_LOGS,
    RESET_MODULE_SETTINGS,
}

internal data class DebugSwitchSpec(
    val key: String?,
    val label: String,
    val description: String,
    val action: DebugSettingsAction = DebugSettingsAction.NONE,
    val showSwitch: Boolean = true,
)

internal object DebugSettingsRegistry {
    fun specs(hostPackageName: String): List<DebugSwitchSpec> {
        return listOf(
            DebugSwitchSpec(
                SettingsUserState.KEY_ENABLE_EXPERIMENTAL_DEXKIT,
                UiText.Settings.EXPERIMENTAL_DEXKIT_LABEL,
                UiText.Settings.EXPERIMENTAL_DEXKIT_DESC,
                action = DebugSettingsAction.DEXKIT_STATUS,
            ),
            DebugSwitchSpec(
                SettingsUserState.KEY_ENABLE_DETAILED_LOGGING,
                UiText.Settings.DETAILED_LOGGING_LABEL,
                UiText.Settings.detailedLoggingDesc(hostPackageName),
            ),
            DebugSwitchSpec(
                key = null,
                label = UiText.Settings.CLEAR_LOGS_LABEL,
                description = UiText.Settings.CLEAR_LOGS_DESC,
                action = DebugSettingsAction.CLEAR_LOGS,
                showSwitch = false,
            ),
            DebugSwitchSpec(
                key = null,
                label = UiText.Settings.RESET_MODULE_SETTINGS_LABEL,
                description = UiText.Settings.RESET_MODULE_SETTINGS_DESC,
                action = DebugSettingsAction.RESET_MODULE_SETTINGS,
                showSwitch = false,
            ),
        )
    }
}
