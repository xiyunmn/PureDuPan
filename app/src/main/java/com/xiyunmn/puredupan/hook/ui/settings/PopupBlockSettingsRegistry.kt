package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal data class PopupBlockSwitchSpec(val key: String, val label: String, val description: String)

internal object PopupBlockSettingsRegistry {
    val specs: List<PopupBlockSwitchSpec> = listOf(
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_REMOVE_HOME_FAB,
            UiText.Settings.REMOVE_HOME_FAB_LABEL,
            UiText.Settings.REMOVE_HOME_FAB_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_IN_APP_DIALOG,
            UiText.Settings.BLOCK_IN_APP_DIALOG_LABEL,
            UiText.Settings.BLOCK_IN_APP_DIALOG_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG,
            UiText.Settings.BLOCK_NON_WIFI_DOWNLOAD_DIALOG_LABEL,
            UiText.Settings.BLOCK_NON_WIFI_DOWNLOAD_DIALOG_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_NOTIFICATION_PROMPT,
            UiText.Settings.BLOCK_NOTIFICATION_PROMPT_LABEL,
            UiText.Settings.BLOCK_NOTIFICATION_PROMPT_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_UPDATE_DIALOG,
            UiText.Settings.BLOCK_UPDATE_DIALOG_LABEL,
            UiText.Settings.BLOCK_UPDATE_DIALOG_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_FULL_SCREEN_BACKUP,
            UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_LABEL,
            UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_APP_STORE_REVIEW,
            UiText.Settings.BLOCK_APP_STORE_REVIEW_LABEL,
            UiText.Settings.BLOCK_APP_STORE_REVIEW_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_SHARE_PUSH_GUIDE,
            UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_LABEL,
            UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_DESC,
        ),
    )

}
