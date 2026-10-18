package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

internal object DefaultStartupTabSettingsDialog {
    fun show(context: Context, prefs: SharedPreferences) {
        val tabs = SettingsUserState.startupTabs(context)
        if (tabs.isEmpty()) {
            Toast.makeText(context, UiText.Settings.DEFAULT_STARTUP_TAB_EMPTY, Toast.LENGTH_LONG).show()
            return
        }
        var selected = tabs.indexOfFirst {
            it.id == prefs.getString(SettingsUserState.KEY_DEFAULT_STARTUP_TAB, null)
        }
        val choices = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_single_choice, tabs.map { it.label }) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).also { view ->
                    if (convertView == null) UiStyle.bindTheme(view as CheckedTextView) { tokens ->
                        setTextColor(tokens.textPrimary)
                        checkMarkTintList = ColorStateList(
                            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                            intArrayOf(tokens.accent, tokens.textMuted),
                        )
                    }
                }
            }
        }
        val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
            .setTitle(UiText.Settings.DEFAULT_STARTUP_TAB_LABEL)
            .setSingleChoiceItems(choices, selected) { _, which -> selected = which }
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .setPositiveButton(UiText.Settings.SAVE) { _, _ ->
                if (selected >= 0) {
                    prefs.edit().putString(SettingsUserState.KEY_DEFAULT_STARTUP_TAB, tabs[selected].id).apply()
                    Toast.makeText(context, UiText.Settings.withRestartHint("默认 Tab 已保存"), Toast.LENGTH_SHORT).show()
                }
            }
            .create()
        SettingsDialogWindows.showStableSubDialog(dialog, context.resources.displayMetrics.density, "[DefaultStartupTabSettingsDialog]")
    }
}
