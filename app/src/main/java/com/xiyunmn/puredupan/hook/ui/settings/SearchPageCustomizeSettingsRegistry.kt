package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal data class SearchPageCustomizeSwitchSpec(
    val key: String,
    val label: String,
    val description: String,
)

internal object SearchPageCustomizeSettingsRegistry {
    val specs: List<SearchPageCustomizeSwitchSpec> = listOf(
        SearchPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_SEARCH_PAGE_AI_ENTRY,
            UiText.Settings.HIDE_SEARCH_PAGE_AI_ENTRY_LABEL,
            UiText.Settings.HIDE_SEARCH_PAGE_AI_ENTRY_DESC,
        ),
        SearchPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_SEARCH_PAGE_PLACEHOLDER,
            UiText.Settings.HIDE_SEARCH_PAGE_PLACEHOLDER_LABEL,
            UiText.Settings.HIDE_SEARCH_PAGE_PLACEHOLDER_DESC,
        ),
        SearchPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_SEARCH_PAGE_RECOMMEND,
            UiText.Settings.HIDE_SEARCH_PAGE_RECOMMEND_LABEL,
            UiText.Settings.HIDE_SEARCH_PAGE_RECOMMEND_DESC,
        ),
        SearchPageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_SEARCH_PAGE_VOICE_SEARCH,
            UiText.Settings.HIDE_SEARCH_PAGE_VOICE_SEARCH_LABEL,
            UiText.Settings.HIDE_SEARCH_PAGE_VOICE_SEARCH_DESC,
        ),
    )
}
