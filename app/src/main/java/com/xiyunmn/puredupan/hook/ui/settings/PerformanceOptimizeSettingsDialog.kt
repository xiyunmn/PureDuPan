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

internal object PerformanceOptimizeSettingsDialog {
    private const val LOG_TAG = "[PerformanceOptimizeSettingsDialog]"

    fun show(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_PERFORMANCE_OPTIMIZE)) {
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

            val sections = PerformanceSettingsItemsBuilder.performanceOptimizeSections(
                prefs = prefs,
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
            )
            val rowsByKey = linkedMapOf<String, View>()
            for (entry in sections.flatMap { it.items }) {
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
            for (section in sections) {
                val sectionRows = section.items
                    .filter { it.item.visible }
                    .mapNotNull { rowsByKey[it.key] }
                SettingsDialogLayout.addTitledSection(
                    root = root,
                    context = context,
                    padding = padding,
                    titleView = SettingsDialogLayout.createPerformanceSectionTitle(context, padding, section.title),
                    rows = sectionRows,
                )
            }

            val switchesByKey = linkedMapOf<String, Switch?>()
            for (entry in sections.flatMap { it.items }) {
                switchesByKey[entry.key] = rowsByKey[entry.key]?.let {
                    SettingsSwitchRows.findSwitchView(it)
                }
            }
            if (switchesByKey.values.any { it == null }) {
                XposedCompat.logW("$LOG_TAG show failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.PERFORMANCE_OPTIMIZE_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    PerformanceSettingsItemsBuilder.putPerformanceOptimizeValues(
                        editor = prefs.edit(),
                        isFeatureVisible = settingsSession::isFeatureVisible,
                        isChecked = { key -> switchesByKey[key]?.isChecked == true },
                    ).apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.PERFORMANCE_OPTIMIZE_SAVED),
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
