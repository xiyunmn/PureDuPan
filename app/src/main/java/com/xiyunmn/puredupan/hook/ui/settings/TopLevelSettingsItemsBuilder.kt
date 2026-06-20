package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.ui.UiText

internal object TopLevelSettingsItemsBuilder {
    fun topLevelGroups(
        primarySplashAdFeatureKey: String?,
        restrictedUnlocked: Boolean,
        defaultValues: TopLevelSettingsDefaultValues,
        actionHandlers: TopLevelSettingsActionHandlers,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): TopLevelSettingsGroups {
        val contentBlockItems = buildList {
            if (restrictedUnlocked) {
                addAll(
                    topLevelItems(
                        TopLevelSettingsRegistry.restrictedContentSpecs(primarySplashAdFeatureKey),
                        defaultValues,
                        actionHandlers,
                        texts,
                        isFeatureVisible,
                    )
                )
            }
            addAll(
                topLevelItems(
                    TopLevelSettingsRegistry.contentSpecs,
                    defaultValues,
                    actionHandlers,
                    texts,
                    isFeatureVisible,
                )
            )
        }

        val uiSimplifyItems = buildList {
            if (restrictedUnlocked) {
                addAll(
                    topLevelItems(
                        TopLevelSettingsRegistry.restrictedUiSpecs,
                        defaultValues,
                        actionHandlers,
                        texts,
                        isFeatureVisible,
                    )
                )
            }
            addAll(
                topLevelItems(
                    TopLevelSettingsRegistry.uiSpecs,
                    defaultValues,
                    actionHandlers,
                    texts,
                    isFeatureVisible,
                )
            )
        }

        val themeItems = buildList {
            addAll(
                topLevelItems(
                    TopLevelSettingsRegistry.themeSpecs,
                    defaultValues,
                    actionHandlers,
                    texts,
                    isFeatureVisible,
                )
            )
            if (restrictedUnlocked) {
                addAll(
                    topLevelItems(
                        TopLevelSettingsRegistry.restrictedThemeSpecs,
                        defaultValues,
                        actionHandlers,
                        texts,
                        isFeatureVisible,
                    )
                )
            }
        }

        return TopLevelSettingsGroups(
            contentBlockItems = contentBlockItems,
            uiSimplifyItems = uiSimplifyItems,
            themeItems = themeItems,
        )
    }

    fun debugItems(
        hostPackageName: String,
        showDexKitStatus: Boolean,
        dexKitSummaryText: String,
        actionHandlers: DebugSettingsActionHandlers,
        texts: SettingsTextResolver,
    ): List<SwitchItem> {
        return DebugSettingsRegistry.specs(hostPackageName).map { spec ->
            switchItemForDebugSpec(
                spec = spec,
                showDexKitStatus = showDexKitStatus,
                dexKitSummaryText = dexKitSummaryText,
                actionHandlers = actionHandlers,
                texts = texts,
            )
        }
    }

    private fun topLevelItems(
        specs: List<TopLevelSwitchSpec>,
        defaultValues: TopLevelSettingsDefaultValues,
        actionHandlers: TopLevelSettingsActionHandlers,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): List<SwitchItem> {
        return specs.map { spec ->
            switchItemForTopLevelSpec(
                spec = spec,
                defaultValues = defaultValues,
                actionHandlers = actionHandlers,
                texts = texts,
                isFeatureVisible = isFeatureVisible,
            )
        }
    }

    private fun switchItemForTopLevelSpec(
        spec: TopLevelSwitchSpec,
        defaultValues: TopLevelSettingsDefaultValues,
        actionHandlers: TopLevelSettingsActionHandlers,
        texts: SettingsTextResolver,
        isFeatureVisible: (String) -> Boolean,
    ): SwitchItem {
        val text = texts.text(spec.key, spec.label, spec.description)
        return SwitchItem(
            label = text.label,
            description = text.description,
            prefKey = spec.key,
            supported = isFeatureVisible(spec.key),
            defaultValue = defaultValueForTopLevelSpec(spec, defaultValues),
            actionIcon = if (spec.action == TopLevelSettingsAction.NONE) {
                null
            } else {
                UiText.Settings.ACTION_ICON_SETTINGS
            },
            onActionClick = actionClickForTopLevelSpec(spec, actionHandlers),
            visible = isFeatureVisible(spec.key),
        )
    }

    private fun defaultValueForTopLevelSpec(
        spec: TopLevelSwitchSpec,
        defaultValues: TopLevelSettingsDefaultValues,
    ): Boolean {
        return when (spec.action) {
            TopLevelSettingsAction.HOME_CUSTOMIZE -> defaultValues.homeCustomize
            TopLevelSettingsAction.SHARE_PAGE_CUSTOMIZE -> defaultValues.sharePageCustomize
            TopLevelSettingsAction.MY_PAGE_CUSTOMIZE -> defaultValues.myPageCustomize
            TopLevelSettingsAction.MEMBER_CARD_CUSTOMIZE -> defaultValues.memberCardCustomize
            TopLevelSettingsAction.BOTTOM_BAR_CUSTOMIZE -> defaultValues.bottomBarCustomize
            TopLevelSettingsAction.PERFORMANCE_OPTIMIZE -> defaultValues.performanceOptimize
            TopLevelSettingsAction.NONE -> false
        }
    }

    private fun actionClickForTopLevelSpec(
        spec: TopLevelSwitchSpec,
        actionHandlers: TopLevelSettingsActionHandlers,
    ): (() -> Unit)? {
        return when (spec.action) {
            TopLevelSettingsAction.HOME_CUSTOMIZE -> actionHandlers.onHomeCustomizeClick
            TopLevelSettingsAction.SHARE_PAGE_CUSTOMIZE -> actionHandlers.onSharePageCustomizeClick
            TopLevelSettingsAction.MY_PAGE_CUSTOMIZE -> actionHandlers.onMyPageCustomizeClick
            TopLevelSettingsAction.MEMBER_CARD_CUSTOMIZE -> actionHandlers.onMemberCardCustomizeClick
            TopLevelSettingsAction.BOTTOM_BAR_CUSTOMIZE -> actionHandlers.onBottomBarCustomizeClick
            TopLevelSettingsAction.PERFORMANCE_OPTIMIZE -> actionHandlers.onPerformanceOptimizeClick
            TopLevelSettingsAction.NONE -> null
        }
    }

    private fun switchItemForDebugSpec(
        spec: DebugSwitchSpec,
        showDexKitStatus: Boolean,
        dexKitSummaryText: String,
        actionHandlers: DebugSettingsActionHandlers,
        texts: SettingsTextResolver,
    ): SwitchItem {
        val visible = when (spec.action) {
            DebugSettingsAction.DEXKIT_STATUS -> showDexKitStatus
            else -> true
        }
        val text = spec.key?.let { key -> texts.text(key, spec.label, spec.description) }
            ?: SettingsText(spec.label, spec.description)
        return SwitchItem(
            label = text.label,
            description = text.description,
            prefKey = spec.key,
            supported = visible,
            defaultValue = false,
            actionIcon = actionIconForDebugSpec(spec, dexKitSummaryText),
            showSwitch = spec.showSwitch,
            onActionClick = actionClickForDebugSpec(spec, actionHandlers),
            visible = visible,
        )
    }

    private fun actionIconForDebugSpec(
        spec: DebugSwitchSpec,
        dexKitSummaryText: String,
    ): String? {
        return when (spec.action) {
            DebugSettingsAction.DEXKIT_STATUS -> dexKitSummaryText
            DebugSettingsAction.CLEAR_LOGS -> UiText.Settings.ACTION_ICON_CLEAR
            DebugSettingsAction.RESET_MODULE_SETTINGS -> UiText.Settings.ACTION_ICON_RESET
            DebugSettingsAction.NONE -> null
        }
    }

    private fun actionClickForDebugSpec(
        spec: DebugSwitchSpec,
        actionHandlers: DebugSettingsActionHandlers,
    ): (() -> Unit)? {
        return when (spec.action) {
            DebugSettingsAction.DEXKIT_STATUS -> actionHandlers.onDexKitStatusClick
            DebugSettingsAction.CLEAR_LOGS -> actionHandlers.onClearLogsClick
            DebugSettingsAction.RESET_MODULE_SETTINGS -> actionHandlers.onResetModuleSettingsClick
            DebugSettingsAction.NONE -> null
        }
    }
}
