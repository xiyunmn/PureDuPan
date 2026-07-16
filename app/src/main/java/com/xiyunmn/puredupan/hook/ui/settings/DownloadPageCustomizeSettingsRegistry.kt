package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal data class DownloadPageCustomizeSwitchSpec(
    val key: String,
    val label: String,
    val description: String,
)

internal object DownloadPageCustomizeSettingsRegistry {
    val specs: List<DownloadPageCustomizeSwitchSpec> = listOf(
        DownloadPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_DOWNLOAD_PAGE_GAME_GUIDE,
            UiText.Settings.HIDE_DOWNLOAD_PAGE_GAME_GUIDE_LABEL,
            UiText.Settings.HIDE_DOWNLOAD_PAGE_GAME_GUIDE_DESC,
        ),
        DownloadPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_DOWNLOAD_PAGE_PROMOTION_AD,
            UiText.Settings.HIDE_DOWNLOAD_PAGE_PROMOTION_AD_LABEL,
            UiText.Settings.HIDE_DOWNLOAD_PAGE_PROMOTION_AD_DESC,
        ),
        DownloadPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_DOWNLOAD_PAGE_MEMBER_PROMOTION,
            UiText.Settings.HIDE_DOWNLOAD_PAGE_MEMBER_PROMOTION_LABEL,
            UiText.Settings.HIDE_DOWNLOAD_PAGE_MEMBER_PROMOTION_DESC,
        ),
    )
}
