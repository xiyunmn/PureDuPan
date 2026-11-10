package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import android.widget.TextView
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.settings.runtime.SettingsRuntimeSession
import com.xiyunmn.puredupan.hook.ui.UiText

private const val MEMBER_CARD_BACKGROUND_BLUR_MIN = 0
private const val MEMBER_CARD_BACKGROUND_BLUR_MAX = 25
private const val MEMBER_CARD_SIZE_WIDTH_MIN = 0
private const val MEMBER_CARD_SIZE_WIDTH_MAX = 420
private const val MEMBER_CARD_SIZE_HEIGHT_MIN = 0
private const val MEMBER_CARD_SIZE_HEIGHT_MAX = 560

internal object MemberCardCustomizeSettingsDialog {
    private const val LOG_TAG = "[MemberCardCustomizeSettingsDialog]"

    fun show(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
        onChooseBackground: () -> Unit,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_CUSTOMIZE)) {
                XposedCompat.logW("$LOG_TAG show skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()
            lateinit var dialog: AlertDialog

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            val canReplaceBackground =
                settingsSession.isFeatureVisible(SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND)
            val backgroundText = texts.text(
                SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND,
                UiText.Settings.REPLACE_MEMBER_CARD_BACKGROUND_LABEL,
                UiText.Settings.REPLACE_MEMBER_CARD_BACKGROUND_DESC,
            )
            val backgroundImageControl = MemberCardSettingsControls.createBackgroundImageRow(
                context = context,
                prefs = prefs,
                label = backgroundText.label,
                description = "建议使用 3:1 横图，支持裁剪与模糊。",
                padding = padding,
                onChoose = onChooseBackground,
                onAdjust = {
                    val uriString = prefs.getString(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_URI, null)
                    if (uriString.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            "请先选择背景图片",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        MemberCardBackgroundEditorDialog.show(context, uriString, dialog)
                    }
                },
                onClear = {
                    prefs.edit()
                        .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_URI)
                        .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT)
                        .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES)
                        .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_MIRROR)
                        .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE)
                        .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE)
                        .apply()
                },
            )
            val blurSlider = MemberCardSettingsControls.createIntSliderRow(
                context = context,
                label = "模糊程度",
                description = "",
                padding = padding,
                minValue = MEMBER_CARD_BACKGROUND_BLUR_MIN,
                maxValue = MEMBER_CARD_BACKGROUND_BLUR_MAX,
                initialValue = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS, 0),
                valueFormatter = { value ->
                    if (value == 0) UiText.Settings.MEMBER_CARD_BACKGROUND_BLUR_NONE else value.toString()
                },
            )
            val dimLightSlider = MemberCardSettingsControls.createIntSliderRow(
                context = context,
                label = "浅色模式",
                description = "",
                padding = padding,
                minValue = 0,
                maxValue = 100,
                initialValue = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_LIGHT_PERCENT, 0),
                valueFormatter = { value -> "${value}%" },
            )
            val dimDarkSlider = MemberCardSettingsControls.createIntSliderRow(
                context = context,
                label = "深色模式",
                description = "",
                padding = padding,
                minValue = 0,
                maxValue = 100,
                initialValue = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_DARK_PERCENT, 0),
                valueFormatter = { value -> "${value}%" },
            )
            val canViewBackgroundOnClick =
                canReplaceBackground &&
                    prefs.getBoolean(SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND, false) &&
                    !prefs.getString(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_URI, null).isNullOrBlank()
            val memberCardItems = MemberCardSettingsItemsBuilder.memberCardCustomizeSwitchItems(
                prefs = prefs,
                memberCardLayoutMode = settingsSession.memberCardLayoutMode,
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
                canViewBackgroundOnClick = canViewBackgroundOnClick,
            )
            val sizeAdjustItem = memberCardItems.sizeAdjustItem.item
            val sizeRow = SettingsSwitchRows.create(
                context,
                prefs,
                "调整尺寸",
                "设为 0 时使用宿主默认尺寸。",
                sizeAdjustItem.prefKey,
                padding,
                sizeAdjustItem.supported,
                sizeAdjustItem.defaultValue,
            )
            val widthSlider = MemberCardSettingsControls.createIntSliderRow(
                context = context,
                label = "宽度",
                description = "",
                padding = padding,
                minValue = MEMBER_CARD_SIZE_WIDTH_MIN,
                maxValue = MEMBER_CARD_SIZE_WIDTH_MAX,
                initialValue = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_SIZE_WIDTH_DP, 0),
                valueFormatter = { value ->
                    if (value == 0) UiText.Settings.MEMBER_CARD_SIZE_DEFAULT else "${value}dp"
                },
            )
            val heightSlider = MemberCardSettingsControls.createIntSliderRow(
                context = context,
                label = "高度",
                description = "",
                padding = padding,
                minValue = MEMBER_CARD_SIZE_HEIGHT_MIN,
                maxValue = MEMBER_CARD_SIZE_HEIGHT_MAX,
                initialValue = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_SIZE_HEIGHT_DP, 0),
                valueFormatter = { value ->
                    if (value == 0) UiText.Settings.MEMBER_CARD_SIZE_DEFAULT else "${value}dp"
                },
            )
            val memberCardHideRowsByKey = linkedMapOf<String, View>()
            for (entry in memberCardItems.allHideItems) {
                val item = entry.item
                memberCardHideRowsByKey[entry.key] = SettingsSwitchRows.create(
                    context = context,
                    prefs = prefs,
                    label = item.label,
                    description = null,
                    prefKey = item.prefKey,
                    padding = padding,
                    enabled = item.supported,
                    defaultValue = item.defaultValue,
                    actionIcon = item.actionIcon,
                    onActionClick = item.onActionClick,
                    linkedPrefKeys = item.linkedPrefKeys,
                    showSwitch = item.showSwitch,
                )
            }
            val removeCardClickItem = memberCardItems.removeCardClickItem.item
            val removeCardClickRow = SettingsSwitchRows.create(
                context,
                prefs,
                removeCardClickItem.label,
                removeCardClickItem.description,
                removeCardClickItem.prefKey,
                padding,
                removeCardClickItem.supported,
                removeCardClickItem.defaultValue,
            )
            val viewBackgroundOnClickItem = memberCardItems.viewBackgroundOnClickItem.item
            val viewBackgroundOnClickRow = SettingsSwitchRows.create(
                context,
                prefs,
                viewBackgroundOnClickItem.label,
                viewBackgroundOnClickItem.description,
                viewBackgroundOnClickItem.prefKey,
                padding,
                viewBackgroundOnClickItem.supported,
                viewBackgroundOnClickItem.defaultValue,
            )

            fun section(title: String, rows: List<View>) {
                SettingsDialogLayout.addTitledSection(
                    root, context, padding,
                    SettingsDialogLayout.createPerformanceSectionTitle(context, padding, title),
                    rows, addDividerBefore = root.childCount > 0,
                )
            }
            if (canReplaceBackground) {
                section("背景图片", listOf(backgroundImageControl.row, blurSlider.row))
            }
            val brightnessRows = buildList<View> {
                if (settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_LIGHT_PERCENT)) add(dimLightSlider.row)
                if (settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_DARK_PERCENT)) add(dimDarkSlider.row)
            }
            if (brightnessRows.isNotEmpty()) {
                val description = TextView(context).apply {
                    text = "原生背景与自定义图片均可调暗，不影响文字。\n0% 保持原亮度，100% 全黑。"
                    textSize = 12f
                    includeFontPadding = false
                    setPadding(0, (4 * density).toInt(), 0, (6 * density).toInt())
                    UiStyle.bindTheme(this) { tokens -> setTextColor(tokens.textSecondary) }
                }
                section("背景亮度", listOf(description) + brightnessRows)
            }
            if (sizeAdjustItem.visible) {
                section("卡片尺寸", listOf(sizeRow, widthSlider.row, heightSlider.row))
            }
            section("点击行为", buildList {
                if (removeCardClickItem.visible) add(removeCardClickRow)
                if (viewBackgroundOnClickItem.visible) add(viewBackgroundOnClickRow)
            })
            val memberCardHideRows = memberCardItems.visibleHideItems
                .filter { it.item.visible }
                .mapNotNull { memberCardHideRowsByKey[it.key] }
            section("隐藏控件", memberCardHideRows)

            val backgroundSwitch = backgroundImageControl.switch
            val sizeSwitch = SettingsSwitchRows.findSwitchView(sizeRow)
            val removeCardClickSwitch = SettingsSwitchRows.findSwitchView(removeCardClickRow)
            val viewBackgroundOnClickSwitch = SettingsSwitchRows.findSwitchView(viewBackgroundOnClickRow)
            val memberCardHideSwitchesByKey = linkedMapOf<String, Switch?>()
            for (entry in memberCardItems.allHideItems) {
                memberCardHideSwitchesByKey[entry.key] =
                    memberCardHideRowsByKey[entry.key]?.let {
                        SettingsSwitchRows.findSwitchView(it)
                    }
            }
            if (
                sizeSwitch == null ||
                removeCardClickSwitch == null ||
                viewBackgroundOnClickSwitch == null ||
                memberCardHideSwitchesByKey.values.any { it == null }
            ) {
                XposedCompat.logW("$LOG_TAG show failed: switch view missing")
                return
            }

            fun isMemberCardHideChecked(key: String): Boolean {
                return memberCardHideSwitchesByKey[key]?.isChecked == true
            }

            fun canUseViewBackgroundClick(): Boolean {
                return canReplaceBackground &&
                    backgroundSwitch.isChecked &&
                    !prefs.getString(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_URI, null).isNullOrBlank()
            }

            fun updateViewBackgroundClickRow() {
                val enabled = viewBackgroundOnClickItem.visible && canUseViewBackgroundClick()
                SettingsSwitchRows.setRowEnabled(viewBackgroundOnClickRow, enabled)
                if (!enabled) {
                    viewBackgroundOnClickSwitch.isChecked = false
                }
            }

            val selectionListener: (String) -> Unit = { uriString ->
                backgroundSwitch.isChecked = true
                backgroundImageControl.updateSelectedUri(uriString)
                updateViewBackgroundClickRow()
                MemberCardBackgroundEditorDialog.show(context, uriString, dialog)
            }
            MemberCardBackgroundSelectionState.setListener(selectionListener)

            updateViewBackgroundClickRow()

            fun memberCardHideValueForSave(entry: MemberCardHideSwitchItem): Boolean {
                val checked = isMemberCardHideChecked(entry.key)
                return if (entry.saveOnlyWhenFeatureVisible) {
                    settingsSession.isFeatureVisible(entry.key) && checked
                } else {
                    checked
                }
            }

            fun persist() {
                val hasEnabledMemberCardOption =
                    canReplaceBackground && backgroundSwitch.isChecked ||
                        settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_LIGHT_PERCENT) && dimLightSlider.getValue() > 0 ||
                        settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_DARK_PERCENT) && dimDarkSlider.getValue() > 0 ||
                        sizeAdjustItem.visible && sizeSwitch.isChecked ||
                        memberCardItems.allHideItems.any { entry ->
                            entry.item.visible && memberCardHideValueForSave(entry)
                        } ||
                        removeCardClickItem.visible && removeCardClickSwitch.isChecked ||
                        viewBackgroundOnClickItem.visible && viewBackgroundOnClickSwitch.isChecked
                val viewBackgroundOnClickEnabled =
                    viewBackgroundOnClickItem.visible &&
                        canUseViewBackgroundClick() &&
                        viewBackgroundOnClickSwitch.isChecked
                val editor = prefs.edit()
                    .putBoolean(SettingsUserState.KEY_MEMBER_CARD_CUSTOMIZE, hasEnabledMemberCardOption)
                if (canReplaceBackground) {
                    editor.putBoolean(
                        SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND,
                        backgroundSwitch.isChecked,
                    )
                }
                if (settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS)) {
                    editor.putInt(
                        SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS,
                        blurSlider.getValue(),
                    )
                }
                if (settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_LIGHT_PERCENT)) {
                    editor.putInt(
                        SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_LIGHT_PERCENT,
                        dimLightSlider.getValue(),
                    )
                }
                if (settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_DARK_PERCENT)) {
                    editor.putInt(
                        SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_DIM_DARK_PERCENT,
                        dimDarkSlider.getValue(),
                    )
                }
                if (sizeAdjustItem.visible) {
                    editor.putBoolean(
                        memberCardItems.sizeAdjustItem.key,
                        sizeSwitch.isChecked,
                    )
                }
                if (settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_SIZE_WIDTH_DP)) {
                    editor.putInt(
                        SettingsUserState.KEY_MEMBER_CARD_SIZE_WIDTH_DP,
                        widthSlider.getValue(),
                    )
                }
                if (settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_SIZE_HEIGHT_DP)) {
                    editor.putInt(
                        SettingsUserState.KEY_MEMBER_CARD_SIZE_HEIGHT_DP,
                        heightSlider.getValue(),
                    )
                }
                if (removeCardClickItem.visible) {
                    editor.putBoolean(
                        memberCardItems.removeCardClickItem.key,
                        removeCardClickSwitch.isChecked,
                    )
                }
                if (viewBackgroundOnClickItem.visible) {
                    editor.putBoolean(
                        memberCardItems.viewBackgroundOnClickItem.key,
                        viewBackgroundOnClickEnabled,
                    )
                }
                for (entry in memberCardItems.allHideItems) {
                    if (!settingsSession.isFeatureVisible(entry.key)) continue
                    editor.putBoolean(entry.key, memberCardHideValueForSave(entry))
                }
                editor.apply()
            }
            val switchRows = listOf(sizeRow, removeCardClickRow, viewBackgroundOnClickRow) + memberCardHideRowsByKey.values
            SettingsSwitchRows.observeChanges(switchRows, ::persist)
            backgroundSwitch.setOnCheckedChangeListener { _, _ ->
                updateViewBackgroundClickRow()
                persist()
            }
            listOf(blurSlider, dimLightSlider, dimDarkSlider, widthSlider, heightSlider)
                .forEach { it.setOnValueChanged(::persist) }

            dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.MEMBER_CARD_CUSTOMIZE_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            SettingsReloadCleanup.onDispose(dialog) {
                MemberCardBackgroundEditorDialog.cancel(dialog)
                MemberCardBackgroundSelectionState.clearIfSame(selectionListener)
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG show failed: ${t.message}")
        }
    }
}
