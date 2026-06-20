package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.settings.runtime.SettingsRuntimeSession
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

internal object BottomBarCustomizeSettingsDialog {
    private const val LOG_TAG = "[BottomBarCustomizeSettingsDialog]"

    fun show(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_CUSTOM_BOTTOM_BAR)) {
                XposedCompat.logW("$LOG_TAG show skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()
            val tokens = UiStyle.tokens(context)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            val bottomBarGroups = BottomBarSettingsItemsBuilder.bottomBarCustomizeGroups(
                prefs = prefs,
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
            )
            val bottomBarItems = bottomBarGroups.directItems + bottomBarGroups.tabSection.items
            val rowsByKey = linkedMapOf<String, View>()
            for (entry in bottomBarItems) {
                val item = entry.item
                rowsByKey[entry.key] = SettingsSwitchRows.create(
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
            SettingsDialogLayout.addVisibleRows(
                root,
                bottomBarGroups.directItems
                    .filter { it.item.visible }
                    .mapNotNull { rowsByKey[it.key] },
            )
            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = TextView(context).apply {
                    text = bottomBarGroups.tabSection.title
                    textSize = 12.5f
                    letterSpacing = 0.04f
                    setTextColor(tokens.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setPadding(0, (padding * 0.45f).toInt(), 0, (padding * 0.2f).toInt())
                },
                rows = bottomBarGroups.tabSection.items
                    .filter { it.item.visible }
                    .mapNotNull { rowsByKey[it.key] },
                addDividerBefore = root.childCount > 0,
            )

            val switchesByKey = linkedMapOf<String, Switch?>()
            for (entry in bottomBarItems) {
                switchesByKey[entry.key] = rowsByKey[entry.key]?.let {
                    SettingsSwitchRows.findSwitchView(it)
                }
            }
            if (switchesByKey.values.any { it == null }) {
                XposedCompat.logW("$LOG_TAG show failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.BOTTOM_BAR_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val saveValues = BottomBarSettingsItemsBuilder.bottomBarCustomizeSaveValues(
                        isFeatureVisible = settingsSession::isFeatureVisible,
                        isChecked = { key -> switchesByKey[key]?.isChecked == true },
                    )
                    if (!saveValues.hasVisibleTab) {
                        Toast.makeText(
                            context,
                            UiText.Settings.BOTTOM_BAR_AT_LEAST_ONE,
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@setOnClickListener
                    }
                    BottomBarSettingsItemsBuilder.putBottomBarCustomizeValues(
                        editor = prefs.edit(),
                        values = saveValues,
                    ).apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.BOTTOM_BAR_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG show failed: ${t.message}")
        }
    }
}
