package com.xiyunmn.puredupan.hook.ui.settings

import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState

internal object PageCustomizeSettingsItemsBuilder {
    fun sharePageCustomizeItems(
        prefs: SharedPreferences,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): List<KeyedSwitchItem> {
        return SharePageCustomizeSettingsRegistry.specs.map { spec ->
            val visible = isFeatureVisible(spec.key)
            val text = texts.text(spec.key, spec.label, spec.description)
            KeyedSwitchItem(
                key = spec.key,
                item = SwitchItem(
                    label = text.label,
                    description = text.description,
                    prefKey = null,
                    supported = visible,
                    defaultValue = prefs.getBoolean(spec.key, false),
                    visible = visible,
                ),
            )
        }
    }

    fun hasEnabledSharePageCustomizeOption(
        isFeatureVisible: (String) -> Boolean,
        isChecked: (String) -> Boolean,
    ): Boolean {
        return SharePageCustomizeSettingsRegistry.specs.any { spec ->
            isFeatureVisible(spec.key) && isChecked(spec.key)
        }
    }

    fun putSharePageCustomizeValues(
        editor: SharedPreferences.Editor,
        isFeatureVisible: (String) -> Boolean,
        isChecked: (String) -> Boolean,
    ): SharedPreferences.Editor {
        val hasEnabledOption = hasEnabledSharePageCustomizeOption(
            isFeatureVisible = isFeatureVisible,
            isChecked = isChecked,
        )
        editor.putBoolean(SettingsUserState.KEY_SHARE_PAGE_CUSTOMIZE, hasEnabledOption)
        SharePageCustomizeSettingsRegistry.specs.filter { spec ->
            isFeatureVisible(spec.key)
        }.forEach { spec ->
            editor.putBoolean(spec.key, isChecked(spec.key))
        }
        return editor
    }

    fun myPageCustomizeItems(
        prefs: SharedPreferences,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): List<KeyedSwitchItem> {
        return MyPageCustomizeSettingsRegistry.specs.map { spec ->
            val visible = isFeatureVisible(spec.key)
            val text = texts.text(spec.key, spec.label, spec.description)
            KeyedSwitchItem(
                key = spec.key,
                item = SwitchItem(
                    label = text.label,
                    description = text.description,
                    prefKey = null,
                    supported = visible,
                    defaultValue = prefs.getBoolean(spec.key, false),
                    visible = visible,
                ),
            )
        }
    }

    fun myPageCustomizeItemsIn(
        section: MyPageCustomizeSettingsSection,
        items: List<KeyedSwitchItem>,
    ): List<KeyedSwitchItem> {
        val keysInSection = MyPageCustomizeSettingsRegistry.specsIn(section)
            .mapTo(linkedSetOf()) { it.key }
        return items.filter { it.key in keysInSection }
    }

    fun hasEnabledMyPageCustomizeOption(
        isFeatureVisible: (String) -> Boolean,
        isChecked: (String) -> Boolean,
    ): Boolean {
        return MyPageCustomizeSettingsRegistry.specs.any { spec ->
            isFeatureVisible(spec.key) && isChecked(spec.key)
        }
    }

    fun putMyPageCustomizeValues(
        editor: SharedPreferences.Editor,
        isFeatureVisible: (String) -> Boolean,
        isChecked: (String) -> Boolean,
    ): SharedPreferences.Editor {
        val hasEnabledOption = hasEnabledMyPageCustomizeOption(
            isFeatureVisible = isFeatureVisible,
            isChecked = isChecked,
        )
        editor.putBoolean(SettingsUserState.KEY_MY_PAGE_CUSTOMIZE, hasEnabledOption)
        MyPageCustomizeSettingsRegistry.specs.filter { spec ->
            isFeatureVisible(spec.key)
        }.forEach { spec ->
            editor.putBoolean(spec.key, isChecked(spec.key))
        }
        return editor
    }

    fun homeCustomizeItems(
        prefs: SharedPreferences,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): List<KeyedSwitchItem> {
        return HomeCustomizeSettingsRegistry.specs.map { spec ->
            val visible = isFeatureVisible(spec.key)
            val text = texts.text(spec.key, spec.label, spec.description)
            KeyedSwitchItem(
                key = spec.key,
                item = SwitchItem(
                    label = text.label,
                    description = text.description,
                    prefKey = null,
                    supported = visible,
                    defaultValue = spec.isChecked(prefs),
                    visible = visible,
                ),
            )
        }
    }

    fun homeCustomizeItemsIn(
        section: HomeCustomizeSettingsSection,
        items: List<KeyedSwitchItem>,
    ): List<KeyedSwitchItem> {
        val keysInSection = HomeCustomizeSettingsRegistry.specsIn(section)
            .mapTo(linkedSetOf()) { it.key }
        return items.filter { it.key in keysInSection }
    }

    fun hasEnabledHomeCustomizeOption(
        prefs: SharedPreferences,
        isFeatureVisible: (String) -> Boolean,
    ): Boolean {
        return HomeCustomizeSettingsRegistry.specs.any { spec ->
            isFeatureVisible(spec.key) && spec.isChecked(prefs)
        }
    }

    fun hasEnabledHomeCustomizeOption(
        isFeatureVisible: (String) -> Boolean,
        isChecked: (String) -> Boolean,
    ): Boolean {
        return HomeCustomizeSettingsRegistry.specs.any { spec ->
            isFeatureVisible(spec.key) && isChecked(spec.key)
        }
    }

    fun putHomeCustomizeValues(
        editor: SharedPreferences.Editor,
        isFeatureVisible: (String) -> Boolean,
        isChecked: (String) -> Boolean,
    ): SharedPreferences.Editor {
        val hasEnabledOption = hasEnabledHomeCustomizeOption(
            isFeatureVisible = isFeatureVisible,
            isChecked = isChecked,
        )
        editor.putBoolean(SettingsUserState.KEY_HOME_CUSTOMIZE, hasEnabledOption)
        HomeCustomizeSettingsRegistry.specs.filter { spec ->
            isFeatureVisible(spec.key)
        }.forEach { spec ->
            editor.putBoolean(spec.key, isChecked(spec.key))
        }
        return editor
    }
}
