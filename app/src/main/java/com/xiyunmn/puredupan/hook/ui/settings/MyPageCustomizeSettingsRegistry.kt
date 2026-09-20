package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal enum class MyPageCustomizeSettingsSection {
    POSITION,
    WIDGET,
    TEXT_WIDGET,
}

internal data class MyPageCustomizeSwitchSpec(
    val key: String,
    val label: String,
    val description: String,
    val section: MyPageCustomizeSettingsSection,
)

internal object MyPageCustomizeSettingsRegistry {
    val specs: List<MyPageCustomizeSwitchSpec> = listOf(
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_MY_PAGE_CONTENT_AUTO_FOLLOW_MEMBER_CARD,
            UiText.Settings.MY_PAGE_CONTENT_AUTO_FOLLOW_MEMBER_CARD_LABEL,
            UiText.Settings.MY_PAGE_CONTENT_AUTO_FOLLOW_MEMBER_CARD_DESC,
            MyPageCustomizeSettingsSection.POSITION,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_MY_PAGE_CONTENT_MANUAL_OFFSET,
            UiText.Settings.MY_PAGE_CONTENT_MANUAL_OFFSET_LABEL,
            UiText.Settings.MY_PAGE_CONTENT_MANUAL_OFFSET_DESC,
            MyPageCustomizeSettingsSection.POSITION,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_RENEW_BUTTON,
            UiText.Settings.HIDE_RENEW_BUTTON_LABEL,
            UiText.Settings.HIDE_RENEW_BUTTON_DESC,
            MyPageCustomizeSettingsSection.WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_REMOVE_GAME_CENTER,
            UiText.Settings.REMOVE_GAME_CENTER_LABEL,
            UiText.Settings.REMOVE_GAME_CENTER_DESC,
            MyPageCustomizeSettingsSection.WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_REMOVE_ABOUT_ME_BANNER,
            UiText.Settings.REMOVE_ABOUT_ME_BANNER_LABEL,
            UiText.Settings.REMOVE_ABOUT_ME_BANNER_DESC,
            MyPageCustomizeSettingsSection.WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_REMOVE_MY_SERVICE,
            UiText.Settings.REMOVE_MY_SERVICE_LABEL,
            UiText.Settings.REMOVE_MY_SERVICE_DESC,
            MyPageCustomizeSettingsSection.WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE,
            UiText.Settings.HIDE_ABOUT_ME_COIN_CENTER_BUBBLE_LABEL,
            UiText.Settings.HIDE_ABOUT_ME_COIN_CENTER_BUBBLE_DESC,
            MyPageCustomizeSettingsSection.WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT,
            UiText.Settings.HIDE_ABOUT_ME_SIGN_IN_DOT_LABEL,
            UiText.Settings.HIDE_ABOUT_ME_SIGN_IN_DOT_DESC,
            MyPageCustomizeSettingsSection.WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET,
            UiText.Settings.HIDE_ABOUT_ME_AI_COIN_ASSET_LABEL,
            UiText.Settings.HIDE_ABOUT_ME_AI_COIN_ASSET_DESC,
            MyPageCustomizeSettingsSection.WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT,
            UiText.Settings.HIDE_ABOUT_ME_MANAGE_SPACE_TEXT_LABEL,
            UiText.Settings.HIDE_ABOUT_ME_MANAGE_SPACE_TEXT_DESC,
            MyPageCustomizeSettingsSection.TEXT_WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_ABOUT_ME_REWARD_TEXT,
            UiText.Settings.HIDE_ABOUT_ME_REWARD_TEXT_LABEL,
            UiText.Settings.HIDE_ABOUT_ME_REWARD_TEXT_DESC,
            MyPageCustomizeSettingsSection.TEXT_WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT,
            UiText.Settings.HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT_LABEL,
            UiText.Settings.HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT_DESC,
            MyPageCustomizeSettingsSection.TEXT_WIDGET,
        ),
        MyPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT,
            UiText.Settings.HIDE_ABOUT_ME_STAR_SKIN_TEXT_LABEL,
            UiText.Settings.HIDE_ABOUT_ME_STAR_SKIN_TEXT_DESC,
            MyPageCustomizeSettingsSection.TEXT_WIDGET,
        ),
    )

    fun specsIn(section: MyPageCustomizeSettingsSection): List<MyPageCustomizeSwitchSpec> {
        return specs.filter { it.section == section }
    }
}
