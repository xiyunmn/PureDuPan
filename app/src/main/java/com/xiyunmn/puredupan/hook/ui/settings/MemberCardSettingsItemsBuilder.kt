package com.xiyunmn.puredupan.hook.ui.settings

import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.config.model.MemberCardLayoutMode
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState

internal object MemberCardSettingsItemsBuilder {
    fun memberCardCustomizeSwitchItems(
        prefs: SharedPreferences,
        memberCardLayoutMode: MemberCardLayoutMode,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
        canViewBackgroundOnClick: Boolean,
    ): MemberCardCustomizeSwitchItems {
        val sizeAdjustSpec = MemberCardCustomizeSettingsRegistry.sizeAdjustSpec
        val removeCardClickSpec = MemberCardCustomizeSettingsRegistry.removeCardClickSpec
        val viewBackgroundOnClickSpec = MemberCardCustomizeSettingsRegistry.viewBackgroundOnClickSpec
        val sizeAdjustText = texts.text(sizeAdjustSpec.key, sizeAdjustSpec.label, sizeAdjustSpec.description)
        val removeCardClickText = texts.text(
            removeCardClickSpec.key,
            removeCardClickSpec.label,
            removeCardClickSpec.description,
        )
        val viewBackgroundOnClickText = texts.text(
            viewBackgroundOnClickSpec.key,
            viewBackgroundOnClickSpec.label,
            viewBackgroundOnClickSpec.description,
        )
        val allHideItems = MemberCardCustomizeSettingsRegistry.allHideSpecsForLayout(
            memberCardLayoutMode,
        )
            .map { spec ->
                val text = texts.text(spec.key, spec.label, spec.description)
                MemberCardHideSwitchItem(
                    key = spec.key,
                    item = SwitchItem(
                        label = text.label,
                        description = text.description,
                        prefKey = null,
                        supported = isFeatureVisible(spec.key),
                        defaultValue = prefs.getBoolean(spec.key, false),
                        visible = isFeatureVisible(spec.key),
                    ),
                    saveOnlyWhenFeatureVisible = spec.saveOnlyWhenFeatureVisible,
                )
            }
        val allHideItemsByKey = allHideItems.associateBy { it.key }
        return MemberCardCustomizeSwitchItems(
            sizeAdjustItem = KeyedSwitchItem(
                key = sizeAdjustSpec.key,
                item = SwitchItem(
                    label = sizeAdjustText.label,
                    description = sizeAdjustText.description,
                    prefKey = null,
                    supported = isFeatureVisible(sizeAdjustSpec.key),
                    defaultValue = prefs.getBoolean(sizeAdjustSpec.key, false),
                    visible = isFeatureVisible(sizeAdjustSpec.key),
                ),
            ),
            removeCardClickItem = KeyedSwitchItem(
                key = removeCardClickSpec.key,
                item = SwitchItem(
                    label = removeCardClickText.label,
                    description = removeCardClickText.description,
                    prefKey = null,
                    supported = isFeatureVisible(removeCardClickSpec.key),
                    defaultValue = prefs.getBoolean(removeCardClickSpec.key, false),
                    visible = isFeatureVisible(removeCardClickSpec.key),
                ),
            ),
            viewBackgroundOnClickItem = KeyedSwitchItem(
                key = viewBackgroundOnClickSpec.key,
                item = SwitchItem(
                    label = viewBackgroundOnClickText.label,
                    description = viewBackgroundOnClickText.description,
                    prefKey = null,
                    supported = isFeatureVisible(viewBackgroundOnClickSpec.key) && canViewBackgroundOnClick,
                    defaultValue = canViewBackgroundOnClick &&
                        !prefs.getBoolean(removeCardClickSpec.key, false) &&
                        prefs.getBoolean(viewBackgroundOnClickSpec.key, false),
                    visible = isFeatureVisible(viewBackgroundOnClickSpec.key),
                ),
            ),
            allHideItems = allHideItems,
            visibleHideItems = MemberCardCustomizeSettingsRegistry.visibleHideSpecsForLayout(
                memberCardLayoutMode,
            )
                .mapNotNull { allHideItemsByKey[it.key] },
        )
    }

    fun hasEnabledMemberCardCustomizeOption(
        prefs: SharedPreferences,
        memberCardLayoutMode: MemberCardLayoutMode,
        isFeatureVisible: (String) -> Boolean,
    ): Boolean {
        val sizeAdjustSpec = MemberCardCustomizeSettingsRegistry.sizeAdjustSpec
        val removeCardClickSpec = MemberCardCustomizeSettingsRegistry.removeCardClickSpec
        val viewBackgroundOnClickSpec = MemberCardCustomizeSettingsRegistry.viewBackgroundOnClickSpec
        val hasViewBackgroundClick =
            isFeatureVisible(removeCardClickSpec.key) &&
                isFeatureVisible(SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND) &&
                !prefs.getBoolean(removeCardClickSpec.key, false) &&
                prefs.getBoolean(SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND, false) &&
                !prefs.getString(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_URI, null).isNullOrBlank() &&
                isFeatureVisible(viewBackgroundOnClickSpec.key) &&
                prefs.getBoolean(viewBackgroundOnClickSpec.key, false)
        return isFeatureVisible(SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND) &&
            prefs.getBoolean(SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND, false) ||
            isFeatureVisible(sizeAdjustSpec.key) &&
            prefs.getBoolean(sizeAdjustSpec.key, false) ||
            MemberCardCustomizeSettingsRegistry.allHideSpecsForLayout(memberCardLayoutMode).any { spec ->
                isFeatureVisible(spec.key) && prefs.getBoolean(spec.key, false)
            } ||
            isFeatureVisible(removeCardClickSpec.key) &&
            prefs.getBoolean(removeCardClickSpec.key, false) ||
            hasViewBackgroundClick
    }
}
