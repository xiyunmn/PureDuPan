package com.xiyunmn.puredupan.hook.ui.settings

import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal object BottomBarSettingsItemsBuilder {
    fun bottomBarCustomizeGroups(
        prefs: SharedPreferences,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): BottomBarCustomizeGroups {
        val itemsByKey = bottomBarCustomizeItems(
            prefs = prefs,
            texts = texts,
            isFeatureVisible = isFeatureVisible,
        ).associateBy { it.key }
        val directItems = BottomBarCustomizeSettingsRegistry
            .specsIn(BottomBarCustomizeSettingsSection.DIRECT)
            .mapNotNull { itemsByKey[it.key] }
        val tabItems = BottomBarCustomizeSettingsRegistry
            .specsIn(BottomBarCustomizeSettingsSection.TAB)
            .mapNotNull { itemsByKey[it.key] }
        return BottomBarCustomizeGroups(
            directItems = directItems,
            tabSection = TitledKeyedSwitchSection(
                title = UiText.Settings.BOTTOM_BAR_TAB_SECTION_TITLE,
                items = tabItems,
            ),
        )
    }

    fun bottomBarCustomizeSaveValues(
        isFeatureVisible: (String) -> Boolean,
        isChecked: (String) -> Boolean,
    ): BottomBarCustomizeSaveValues {
        val rawSelection = BottomBarCustomizeSettingsRegistry.tabSelectionFor { key ->
            isFeatureVisible(key) && isChecked(key)
        }
        if (!rawSelection.hasVisibleTab()) {
            return BottomBarCustomizeSaveValues(
                hasVisibleTab = false,
                hasEnabledOption = false,
                directValues = emptyMap(),
                tabValues = emptyMap(),
            )
        }

        val normalized = SettingsUserState.normalizeBottomBarSelection(rawSelection)
        val directValues = BottomBarCustomizeSettingsRegistry
            .specsIn(BottomBarCustomizeSettingsSection.DIRECT)
            .filter { spec -> isFeatureVisible(spec.key) }
            .associate { spec -> spec.key to isChecked(spec.key) }
        val hasEnabledDirectOption = BottomBarCustomizeSettingsRegistry
            .specsIn(BottomBarCustomizeSettingsSection.DIRECT)
            .any { spec -> isFeatureVisible(spec.key) && isChecked(spec.key) }
        return BottomBarCustomizeSaveValues(
            hasVisibleTab = true,
            hasEnabledOption = hasEnabledDirectOption ||
                BottomBarCustomizeSettingsRegistry.hasHiddenTab(normalized),
            directValues = directValues,
            tabValues = BottomBarCustomizeSettingsRegistry.tabValues(normalized)
                .filterKeys { key -> isFeatureVisible(key) },
        )
    }

    fun putBottomBarCustomizeValues(
        editor: SharedPreferences.Editor,
        values: BottomBarCustomizeSaveValues,
    ): SharedPreferences.Editor {
        editor.putBoolean(SettingsUserState.KEY_CUSTOM_BOTTOM_BAR, values.hasEnabledOption)
        values.directValues.forEach { (key, value) ->
            editor.putBoolean(key, value)
        }
        values.tabValues.forEach { (key, value) ->
            editor.putBoolean(key, value)
        }
        return editor
    }

    fun hasEnabledBottomBarCustomizeOption(
        prefs: SharedPreferences,
        isFeatureVisible: (String) -> Boolean,
    ): Boolean {
        val hasEnabledDirectOption = BottomBarCustomizeSettingsRegistry
            .specsIn(BottomBarCustomizeSettingsSection.DIRECT)
            .any { spec -> isFeatureVisible(spec.key) && prefs.getBoolean(spec.key, false) }
        if (hasEnabledDirectOption) return true

        val tabSelection = BottomBarCustomizeSettingsRegistry.readTabSelection(prefs)
        return BottomBarCustomizeSettingsRegistry
            .specsIn(BottomBarCustomizeSettingsSection.TAB)
            .any { spec ->
                isFeatureVisible(spec.key) &&
                    BottomBarCustomizeSettingsRegistry.tabValue(tabSelection, spec.key)
            }
    }

    private fun bottomBarCustomizeItems(
        prefs: SharedPreferences,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): List<KeyedSwitchItem> {
        val initialSelection = BottomBarCustomizeSettingsRegistry.readTabSelection(prefs)
        return BottomBarCustomizeSettingsRegistry.specs.map { spec ->
            val visible = isFeatureVisible(spec.key)
            val text = texts.text(spec.key, spec.label, spec.description)
            val defaultValue = when (spec.section) {
                BottomBarCustomizeSettingsSection.DIRECT -> prefs.getBoolean(spec.key, false)
                BottomBarCustomizeSettingsSection.TAB ->
                    BottomBarCustomizeSettingsRegistry.tabValue(initialSelection, spec.key)
            }
            KeyedSwitchItem(
                key = spec.key,
                item = SwitchItem(
                    label = text.label,
                    description = text.description,
                    prefKey = null,
                    supported = visible,
                    defaultValue = defaultValue,
                    visible = visible,
                ),
            )
        }
    }
}
