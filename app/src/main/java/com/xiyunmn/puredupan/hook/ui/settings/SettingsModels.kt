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
    val linkedPrefKeys: List<String> = emptyList(),
    val showSwitch: Boolean = true,
    val onActionClick: (() -> Unit)? = null,
)

internal data class SettingGroup(
    val name: String,
    val items: List<SwitchItem>,
)

internal class IntSliderControl(
    val row: View,
    val getValue: () -> Int,
)

internal class MemberCardBackgroundImageControl(
    val row: View,
    val switch: Switch,
)

internal data class SwitchRuntimeSupport(
    val supported: Boolean,
    val partial: Boolean,
    val note: String?,
)

internal data class VersionDisplayInfo(
    val hostVersion: String,
    val hostBuildType: String,
    val moduleVersion: String,
    val moduleBuildType: String,
)
