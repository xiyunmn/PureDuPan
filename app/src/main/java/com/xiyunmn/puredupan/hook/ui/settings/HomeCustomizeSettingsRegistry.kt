package com.xiyunmn.puredupan.hook.ui.settings

import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal enum class HomeCustomizeSettingsSection {
    TOP_WIDGET,
    CONTENT_SECTION,
    CUSTOM_SECTION_DISPLAY,
}

internal data class HomeCustomizeSwitchSpec(
    val key: String,
    val label: String,
    val description: String,
    val section: HomeCustomizeSettingsSection,
    val readChecked: (SharedPreferences) -> Boolean,
) {
    fun isChecked(prefs: SharedPreferences): Boolean {
        return readChecked(prefs)
    }
}

internal object HomeCustomizeSettingsRegistry {
    val specs: List<HomeCustomizeSwitchSpec> = listOf(
        HomeCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_HOME_TOP_PROMOTION,
            UiText.Settings.HIDE_HOME_TOP_PROMOTION_LABEL,
            UiText.Settings.HIDE_HOME_TOP_PROMOTION_DESC,
            HomeCustomizeSettingsSection.TOP_WIDGET,
            SettingsUserState::readHomeTopPromotionHidden,
        ),
        booleanSpec(
            SettingsUserState.KEY_HIDE_HOME_SEARCH_PLACEHOLDER,
            UiText.Settings.HIDE_HOME_SEARCH_PLACEHOLDER_LABEL,
            UiText.Settings.HIDE_HOME_SEARCH_PLACEHOLDER_DESC,
            HomeCustomizeSettingsSection.TOP_WIDGET,
        ),
        booleanSpec(
            SettingsUserState.KEY_HIDE_HOME_SEARCH_AIGC_ICON,
            UiText.Settings.HIDE_HOME_SEARCH_AIGC_ICON_LABEL,
            UiText.Settings.HIDE_HOME_SEARCH_AIGC_ICON_DESC,
            HomeCustomizeSettingsSection.TOP_WIDGET,
        ),
        booleanSpec(
            SettingsUserState.KEY_HIDE_HOME_TOOLBAR,
            UiText.Settings.HIDE_HOME_TOOLBAR_LABEL,
            UiText.Settings.HIDE_HOME_TOOLBAR_DESC,
            HomeCustomizeSettingsSection.TOP_WIDGET,
        ),
        booleanSpec(
            SettingsUserState.KEY_HIDE_HOME_FEED_TIP,
            UiText.Settings.HIDE_HOME_FEED_TIP_LABEL,
            UiText.Settings.HIDE_HOME_FEED_TIP_DESC,
            HomeCustomizeSettingsSection.TOP_WIDGET,
        ),
        booleanSpec(
            SettingsUserState.KEY_HIDE_HOME_BANNER,
            UiText.Settings.HIDE_HOME_BANNER_LABEL,
            UiText.Settings.HIDE_HOME_BANNER_DESC,
            HomeCustomizeSettingsSection.TOP_WIDGET,
        ),
        booleanSpec(
            SettingsUserState.KEY_HIDE_HOME_RECENT_SECTION,
            UiText.Settings.HIDE_HOME_RECENT_SECTION_LABEL,
            UiText.Settings.HIDE_HOME_RECENT_SECTION_DESC,
            HomeCustomizeSettingsSection.CONTENT_SECTION,
        ),
        booleanSpec(
            SettingsUserState.KEY_HIDE_HOME_SAVE_SECTION,
            UiText.Settings.HIDE_HOME_SAVE_SECTION_LABEL,
            UiText.Settings.HIDE_HOME_SAVE_SECTION_DESC,
            HomeCustomizeSettingsSection.CONTENT_SECTION,
        ),
        booleanSpec(
            SettingsUserState.KEY_HIDE_HOME_MEMORIES_SECTION,
            UiText.Settings.HIDE_HOME_MEMORIES_SECTION_LABEL,
            UiText.Settings.HIDE_HOME_MEMORIES_SECTION_DESC,
            HomeCustomizeSettingsSection.CONTENT_SECTION,
        ),
        booleanSpec(
            SettingsUserState.KEY_HOME_RECENT_ITEM_LIMIT_ENABLED,
            UiText.Settings.HOME_RECENT_ITEM_LIMIT_LABEL,
            UiText.Settings.HOME_RECENT_ITEM_LIMIT_DESC,
            HomeCustomizeSettingsSection.CUSTOM_SECTION_DISPLAY,
        ),
        booleanSpec(
            SettingsUserState.KEY_HOME_SAVE_VERTICAL_LAYOUT,
            UiText.Settings.HOME_SAVE_VERTICAL_LAYOUT_LABEL,
            UiText.Settings.HOME_SAVE_VERTICAL_LAYOUT_DESC,
            HomeCustomizeSettingsSection.CUSTOM_SECTION_DISPLAY,
        ),
    )

    fun specsIn(section: HomeCustomizeSettingsSection): List<HomeCustomizeSwitchSpec> {
        return specs.filter { it.section == section }
    }

    private fun booleanSpec(
        key: String,
        label: String,
        description: String,
        section: HomeCustomizeSettingsSection,
    ): HomeCustomizeSwitchSpec {
        return HomeCustomizeSwitchSpec(
            key = key,
            label = label,
            description = description,
            section = section,
            readChecked = { prefs -> prefs.getBoolean(key, false) },
        )
    }
}
