package com.xiyunmn.puredupan.hook.ui.settings

import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal object PerformanceSettingsItemsBuilder {
    fun performanceOptimizeSections(
        prefs: SharedPreferences,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): List<TitledKeyedSwitchSection> {
        val itemsByKey = performanceOptimizeItems(
            prefs = prefs,
            texts = texts,
            isFeatureVisible = isFeatureVisible,
        ).associateBy { it.key }
        return performanceSectionTitles.map { (section, title) ->
            TitledKeyedSwitchSection(
                title = title,
                items = PerformanceSettingsRegistry.specsIn(section)
                    .mapNotNull { itemsByKey[it.key] },
            )
        }
    }

    fun hasEnabledPerformanceOptimizeOption(
        prefs: SharedPreferences,
        isFeatureVisible: (String) -> Boolean,
    ): Boolean {
        return PerformanceSettingsRegistry.specs.any { spec ->
            isFeatureVisible(spec.key) && prefs.getBoolean(spec.key, false)
        }
    }

    fun putPerformanceOptimizeValues(
        editor: SharedPreferences.Editor,
        isFeatureVisible: (String) -> Boolean,
        isChecked: (String) -> Boolean,
    ): SharedPreferences.Editor {
        editor.putBoolean(
            SettingsUserState.KEY_PERFORMANCE_OPTIMIZE,
            PerformanceSettingsRegistry.specs.any { spec ->
                isFeatureVisible(spec.key) && isChecked(spec.key)
            },
        )
        PerformanceSettingsRegistry.specs.forEach { spec ->
            if (!isFeatureVisible(spec.key)) return@forEach
            editor.putBoolean(spec.key, isChecked(spec.key))
        }
        return editor
    }

    private fun performanceOptimizeItems(
        prefs: SharedPreferences,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): List<KeyedSwitchItem> {
        return PerformanceSettingsRegistry.specs.map { spec ->
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

    private val performanceSectionTitles: List<Pair<PerformanceSettingsSection, String>> = listOf(
        PerformanceSettingsSection.INTL_STARTUP_DELAY to UiText.Settings.PERFORMANCE_GROUP_INTL_STARTUP_DELAY,
        PerformanceSettingsSection.POST_INIT to UiText.Settings.PERFORMANCE_GROUP_POST_INIT,
        PerformanceSettingsSection.STARTUP_PREFETCH to UiText.Settings.PERFORMANCE_GROUP_STARTUP_PREFETCH,
        PerformanceSettingsSection.RUNTIME_SERVICE to UiText.Settings.PERFORMANCE_GROUP_RUNTIME_SERVICE,
        PerformanceSettingsSection.AD_INCENTIVE to UiText.Settings.PERFORMANCE_GROUP_AD_INCENTIVE,
    )
}
