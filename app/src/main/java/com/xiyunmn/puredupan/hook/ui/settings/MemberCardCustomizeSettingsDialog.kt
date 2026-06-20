package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.settings.runtime.SettingsRuntimeSession
import com.xiyunmn.puredupan.hook.ui.UiText

private const val MEMBER_CARD_BACKGROUND_BLUR_MIN = 0
private const val MEMBER_CARD_BACKGROUND_BLUR_MAX = 25
private const val MEMBER_CARD_SIZE_WIDTH_MIN = 0
private const val MEMBER_CARD_SIZE_WIDTH_MAX = 420
private const val MEMBER_CARD_SIZE_HEIGHT_MIN = 0
private const val MEMBER_CARD_SIZE_HEIGHT_MAX = 280

internal object MemberCardCustomizeSettingsDialog {
    private const val LOG_TAG = "[MemberCardCustomizeSettingsDialog]"

    fun show(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
        onChooseBackground: () -> Unit,
        onAdjustBackground: (String) -> Unit,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_MEMBER_CARD_CUSTOMIZE)) {
                XposedCompat.logW("$LOG_TAG show skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            var backgroundUriCleared = false
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
                description = backgroundText.description.orEmpty(),
                padding = padding,
                onChoose = {
                    backgroundUriCleared = false
                    onChooseBackground()
                },
                onAdjust = {
                    val uriString = prefs.getString(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_URI, null)
                    if (uriString.isNullOrBlank() || backgroundUriCleared) {
                        Toast.makeText(
                            context,
                            UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_FAILED,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        onAdjustBackground(uriString)
                    }
                },
                onClear = {
                    backgroundUriCleared = true
                    Toast.makeText(
                        context,
                        UiText.Settings.MEMBER_CARD_BACKGROUND_CLEAR_PENDING,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
            val blurText = texts.text(
                SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS,
                UiText.Settings.MEMBER_CARD_BACKGROUND_BLUR_LABEL,
                UiText.Settings.MEMBER_CARD_BACKGROUND_BLUR_DESC,
            )
            val blurSlider = MemberCardSettingsControls.createIntSliderRow(
                context = context,
                label = blurText.label,
                description = blurText.description.orEmpty(),
                padding = padding,
                minValue = MEMBER_CARD_BACKGROUND_BLUR_MIN,
                maxValue = MEMBER_CARD_BACKGROUND_BLUR_MAX,
                initialValue = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS, 0),
                valueFormatter = { value ->
                    if (value == 0) UiText.Settings.MEMBER_CARD_BACKGROUND_BLUR_NONE else value.toString()
                },
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
                sizeAdjustItem.label,
                sizeAdjustItem.description,
                sizeAdjustItem.prefKey,
                padding,
                sizeAdjustItem.supported,
                sizeAdjustItem.defaultValue,
            )
            val widthText = texts.text(
                SettingsUserState.KEY_MEMBER_CARD_SIZE_WIDTH_DP,
                UiText.Settings.MEMBER_CARD_WIDTH_LABEL,
                sizeAdjustItem.description,
            )
            val widthSlider = MemberCardSettingsControls.createIntSliderRow(
                context = context,
                label = widthText.label,
                description = widthText.description.orEmpty(),
                padding = padding,
                minValue = MEMBER_CARD_SIZE_WIDTH_MIN,
                maxValue = MEMBER_CARD_SIZE_WIDTH_MAX,
                initialValue = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_SIZE_WIDTH_DP, 0),
                valueFormatter = { value ->
                    if (value == 0) UiText.Settings.MEMBER_CARD_SIZE_DEFAULT else "${value}dp"
                },
            )
            val heightText = texts.text(
                SettingsUserState.KEY_MEMBER_CARD_SIZE_HEIGHT_DP,
                UiText.Settings.MEMBER_CARD_SIZE_HEIGHT_LABEL,
                sizeAdjustItem.description,
            )
            val heightSlider = MemberCardSettingsControls.createIntSliderRow(
                context = context,
                label = heightText.label,
                description = heightText.description.orEmpty(),
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
                    description = item.description,
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

            if (canReplaceBackground) {
                root.addView(backgroundImageControl.row)
                root.addView(blurSlider.row)
            }
            if (sizeAdjustItem.visible) {
                root.addView(sizeRow)
                root.addView(widthSlider.row)
                root.addView(heightSlider.row)
            }
            if (root.childCount > 0 && (removeCardClickItem.visible || viewBackgroundOnClickItem.visible)) {
                root.addView(SettingsDialogLayout.createDivider(context, padding))
            }
            if (removeCardClickItem.visible) {
                root.addView(removeCardClickRow)
            }
            if (viewBackgroundOnClickItem.visible) {
                root.addView(viewBackgroundOnClickRow)
            }
            val memberCardHideRows = memberCardItems.visibleHideItems
                .filter { it.item.visible }
                .mapNotNull { memberCardHideRowsByKey[it.key] }
            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = SettingsDialogLayout.createCustomHideWidgetSectionTitle(context, padding),
                rows = memberCardHideRows,
                addDividerBefore = true,
            )

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
                    !backgroundUriCleared &&
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
                backgroundUriCleared = false
                backgroundSwitch.isChecked = true
                backgroundImageControl.updateSelectedUri(uriString)
                updateViewBackgroundClickRow()
            }
            MemberCardBackgroundSelectionState.setListener(selectionListener)

            backgroundSwitch.setOnCheckedChangeListener { _, _ ->
                updateViewBackgroundClickRow()
            }
            removeCardClickSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (removeCardClickItem.visible && isChecked && viewBackgroundOnClickSwitch.isChecked) {
                    viewBackgroundOnClickSwitch.isChecked = false
                }
            }
            viewBackgroundOnClickSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (removeCardClickItem.visible && isChecked && removeCardClickSwitch.isChecked) {
                    removeCardClickSwitch.isChecked = false
                }
            }
            updateViewBackgroundClickRow()

            fun memberCardHideValueForSave(entry: MemberCardHideSwitchItem): Boolean {
                val checked = isMemberCardHideChecked(entry.key)
                return if (entry.saveOnlyWhenFeatureVisible) {
                    settingsSession.isFeatureVisible(entry.key) && checked
                } else {
                    checked
                }
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.MEMBER_CARD_CUSTOMIZE_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val hasEnabledMemberCardOption =
                        canReplaceBackground && backgroundSwitch.isChecked ||
                            sizeAdjustItem.visible && sizeSwitch.isChecked ||
                            memberCardItems.allHideItems.any { entry ->
                                entry.item.visible && memberCardHideValueForSave(entry)
                            } ||
                            removeCardClickItem.visible && removeCardClickSwitch.isChecked ||
                            viewBackgroundOnClickItem.visible && viewBackgroundOnClickSwitch.isChecked
                    val viewBackgroundOnClickEnabled =
                        viewBackgroundOnClickItem.visible &&
                            !(removeCardClickItem.visible && removeCardClickSwitch.isChecked) &&
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
                    if (canReplaceBackground && backgroundUriCleared) {
                        editor.remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_URI)
                            .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT)
                            .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES)
                            .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE)
                            .remove(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE)
                    }
                    editor.apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.MEMBER_CARD_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.setOnDismissListener {
                MemberCardBackgroundSelectionState.clearIfSame(selectionListener)
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG show failed: ${t.message}")
        }
    }
}
