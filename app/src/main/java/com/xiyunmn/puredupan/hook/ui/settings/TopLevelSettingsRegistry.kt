package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal enum class TopLevelSettingsAction {
    NONE,
    HOME_CUSTOMIZE,
    FILE_PAGE_CUSTOMIZE,
    SEARCH_PAGE_CUSTOMIZE,
    SHARE_PAGE_CUSTOMIZE,
    MY_PAGE_CUSTOMIZE,
    MEMBER_CARD_CUSTOMIZE,
    BOTTOM_BAR_CUSTOMIZE,
    PERFORMANCE_OPTIMIZE,
}

internal data class TopLevelSwitchSpec(
    val key: String,
    val label: String,
    val description: String,
    val action: TopLevelSettingsAction = TopLevelSettingsAction.NONE,
    val restricted: Boolean = false,
)

internal object TopLevelSettingsRegistry {
    fun restrictedContentSpecs(primarySplashAdFeatureKey: String?): List<TopLevelSwitchSpec> {
        return buildList {
            add(
                TopLevelSwitchSpec(
                    SettingsUserState.KEY_ACCELERATE_INTL_SPLASH_STARTUP,
                    UiText.Settings.ACCELERATE_INTL_SPLASH_STARTUP_LABEL,
                    UiText.Settings.ACCELERATE_INTL_SPLASH_STARTUP_DESC,
                    restricted = true,
                )
            )
            when (primarySplashAdFeatureKey) {
                SettingsUserState.KEY_BLOCK_SPLASH_INTERSTITIAL -> add(
                    TopLevelSwitchSpec(
                        SettingsUserState.KEY_BLOCK_SPLASH_INTERSTITIAL,
                        UiText.Settings.BLOCK_SPLASH_INTERSTITIAL_LABEL,
                        UiText.Settings.BLOCK_SPLASH_INTERSTITIAL_DESC,
                        restricted = true,
                    )
                )
                SettingsUserState.KEY_REMOVE_HOT_START_SPLASH -> add(
                    TopLevelSwitchSpec(
                        SettingsUserState.KEY_REMOVE_HOT_START_SPLASH,
                        UiText.Settings.REMOVE_HOT_START_SPLASH_LABEL,
                        UiText.Settings.REMOVE_HOT_START_SPLASH_DESC,
                        restricted = true,
                    )
                )
            }
        }
    }

    val contentSpecs: List<TopLevelSwitchSpec> = listOf(
        TopLevelSwitchSpec(
            SettingsUserState.KEY_BLOCK_IN_APP_DIALOG,
            UiText.Settings.BLOCK_IN_APP_DIALOG_LABEL,
            UiText.Settings.BLOCK_IN_APP_DIALOG_DESC,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG,
            UiText.Settings.BLOCK_NON_WIFI_DOWNLOAD_DIALOG_LABEL,
            UiText.Settings.BLOCK_NON_WIFI_DOWNLOAD_DIALOG_DESC,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_BLOCK_NOTIFICATION_PROMPT,
            UiText.Settings.BLOCK_NOTIFICATION_PROMPT_LABEL,
            UiText.Settings.BLOCK_NOTIFICATION_PROMPT_DESC,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_BLOCK_UPDATE_DIALOG,
            UiText.Settings.BLOCK_UPDATE_DIALOG_LABEL,
            UiText.Settings.BLOCK_UPDATE_DIALOG_DESC,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_BLOCK_FULL_SCREEN_BACKUP,
            UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_LABEL,
            UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_DESC,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_BLOCK_APP_STORE_REVIEW,
            UiText.Settings.BLOCK_APP_STORE_REVIEW_LABEL,
            UiText.Settings.BLOCK_APP_STORE_REVIEW_DESC,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_BLOCK_SHARE_PUSH_GUIDE,
            UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_LABEL,
            UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_DESC,
        ),
    )

    val restrictedUiSpecs: List<TopLevelSwitchSpec> = listOf(
        TopLevelSwitchSpec(
            SettingsUserState.KEY_HOME_CUSTOMIZE,
            UiText.Settings.HOME_CUSTOMIZE_LABEL,
            UiText.Settings.HOME_CUSTOMIZE_DESC,
            action = TopLevelSettingsAction.HOME_CUSTOMIZE,
            restricted = true,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_FILE_PAGE_CUSTOMIZE,
            UiText.Settings.FILE_PAGE_CUSTOMIZE_LABEL,
            UiText.Settings.FILE_PAGE_CUSTOMIZE_DESC,
            action = TopLevelSettingsAction.FILE_PAGE_CUSTOMIZE,
            restricted = true,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_SEARCH_PAGE_CUSTOMIZE,
            UiText.Settings.SEARCH_PAGE_CUSTOMIZE_LABEL,
            UiText.Settings.SEARCH_PAGE_CUSTOMIZE_DESC,
            action = TopLevelSettingsAction.SEARCH_PAGE_CUSTOMIZE,
            restricted = true,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_SHARE_PAGE_CUSTOMIZE,
            UiText.Settings.SHARE_PAGE_CUSTOMIZE_LABEL,
            UiText.Settings.SHARE_PAGE_CUSTOMIZE_DESC,
            action = TopLevelSettingsAction.SHARE_PAGE_CUSTOMIZE,
            restricted = true,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_MY_PAGE_CUSTOMIZE,
            UiText.Settings.MY_PAGE_CUSTOMIZE_LABEL,
            UiText.Settings.MY_PAGE_CUSTOMIZE_DESC,
            action = TopLevelSettingsAction.MY_PAGE_CUSTOMIZE,
            restricted = true,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_MEMBER_CARD_CUSTOMIZE,
            UiText.Settings.MEMBER_CARD_CUSTOMIZE_LABEL,
            UiText.Settings.MEMBER_CARD_CUSTOMIZE_DESC,
            action = TopLevelSettingsAction.MEMBER_CARD_CUSTOMIZE,
            restricted = true,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_CUSTOM_BOTTOM_BAR,
            UiText.Settings.CUSTOM_BOTTOM_BAR_LABEL,
            UiText.Settings.CUSTOM_BOTTOM_BAR_DESC,
            action = TopLevelSettingsAction.BOTTOM_BAR_CUSTOMIZE,
            restricted = true,
        ),
    )

    val uiSpecs: List<TopLevelSwitchSpec> = listOf(
        TopLevelSwitchSpec(
            SettingsUserState.KEY_BLOCK_ALBUM_BACKUP_BAR,
            UiText.Settings.BLOCK_ALBUM_BACKUP_BAR_LABEL,
            UiText.Settings.BLOCK_ALBUM_BACKUP_BAR_DESC,
        ),
    )

    fun themeSpecs(isIntlHost: Boolean): List<TopLevelSwitchSpec> = listOf(
        TopLevelSwitchSpec(
            SettingsUserState.KEY_ENABLE_NIGHT_MODE_SUPPORT,
            UiText.Settings.ENABLE_NIGHT_MODE_SUPPORT_LABEL,
            UiText.Settings.ENABLE_NIGHT_MODE_SUPPORT_DESC,
        ),
        TopLevelSwitchSpec(
            SettingsUserState.KEY_FOLLOW_SYSTEM_NIGHT_MODE,
            UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_LABEL,
            if (isIntlHost) {
                UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_INTL_DESC
            } else {
                UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_DESC
            },
        ),
    )

    val restrictedThemeSpecs: List<TopLevelSwitchSpec> = listOf(
        TopLevelSwitchSpec(
            SettingsUserState.KEY_PERFORMANCE_OPTIMIZE,
            UiText.Settings.PERFORMANCE_OPTIMIZE_LABEL,
            UiText.Settings.PERFORMANCE_OPTIMIZE_DESC,
            action = TopLevelSettingsAction.PERFORMANCE_OPTIMIZE,
            restricted = true,
        ),
    )
}
