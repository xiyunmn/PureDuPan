package com.xiyunmn.puredupan.hook.ui.settings

import android.view.View
import android.widget.Switch

internal data class SwitchItem(
    val label: String,
    val description: String?,
    val prefKey: String?,
    val supported: Boolean,
    val defaultValue: Boolean = false,
    val actionIcon: String? = null,
    val actionButtonText: String? = null,
    val statusBadgeText: String? = null,
    val onStatusBadgeClick: (() -> Unit)? = null,
    val linkedPrefKeys: List<String> = emptyList(),
    val showSwitch: Boolean = true,
    val onActionClick: (() -> Unit)? = null,
    val visible: Boolean = true,
)

internal data class SettingGroup(
    val name: String,
    val items: List<SwitchItem>,
)

internal data class TopLevelSettingsDefaultValues(
    val homeCustomize: Boolean,
    val filePageCustomize: Boolean,
    val searchPageCustomize: Boolean,
    val sharePageCustomize: Boolean,
    val myPageCustomize: Boolean,
    val memberCardCustomize: Boolean,
    val bottomBarCustomize: Boolean,
    val performanceOptimize: Boolean,
)

internal data class TopLevelSettingsActionHandlers(
    val onHomeCustomizeClick: () -> Unit,
    val onFilePageCustomizeClick: () -> Unit,
    val onSearchPageCustomizeClick: () -> Unit,
    val onSharePageCustomizeClick: () -> Unit,
    val onMyPageCustomizeClick: () -> Unit,
    val onMemberCardCustomizeClick: () -> Unit,
    val onBottomBarCustomizeClick: () -> Unit,
    val onPerformanceOptimizeClick: () -> Unit,
    val onAutoDailySignInNowClick: () -> Unit,
)

internal data class DebugSettingsActionHandlers(
    val onDexKitScanClick: () -> Unit,
    val onDexKitDetailsClick: () -> Unit,
    val onClearLogsClick: () -> Unit,
    val onResetModuleSettingsClick: () -> Unit,
)

internal data class TopLevelSettingsGroups(
    val contentBlockItems: List<SwitchItem>,
    val uiSimplifyItems: List<SwitchItem>,
    val themeItems: List<SwitchItem>,
)

internal data class KeyedSwitchItem(
    val key: String,
    val item: SwitchItem,
)

internal data class TitledKeyedSwitchSection(
    val title: String,
    val items: List<KeyedSwitchItem>,
)

internal data class BottomBarCustomizeGroups(
    val directItems: List<KeyedSwitchItem>,
    val tabSection: TitledKeyedSwitchSection,
)

internal data class BottomBarCustomizeSaveValues(
    val hasVisibleTab: Boolean,
    val hasEnabledOption: Boolean,
    val directValues: Map<String, Boolean>,
    val tabValues: Map<String, Boolean>,
)

internal data class MemberCardHideSwitchItem(
    val key: String,
    val item: SwitchItem,
    val saveOnlyWhenFeatureVisible: Boolean,
)

internal data class MemberCardCustomizeSwitchItems(
    val sizeAdjustItem: KeyedSwitchItem,
    val removeCardClickItem: KeyedSwitchItem,
    val viewBackgroundOnClickItem: KeyedSwitchItem,
    val allHideItems: List<MemberCardHideSwitchItem>,
    val visibleHideItems: List<MemberCardHideSwitchItem>,
)

internal class IntSliderControl(
    val row: View,
    val getValue: () -> Int,
)

internal class MemberCardBackgroundImageControl(
    val row: View,
    val switch: Switch,
    val updateSelectedUri: (String?) -> Unit,
)

internal data class SwitchRuntimeSupport(
    val supported: Boolean,
    val partial: Boolean,
    val note: String?,
)

internal data class VersionDisplayInfo(
    val hostName: String,
    val hostVersion: String,
    val hostBuildType: String,
    val hostPackageName: String,
    val moduleVersion: String,
    val moduleBuildType: String,
)
