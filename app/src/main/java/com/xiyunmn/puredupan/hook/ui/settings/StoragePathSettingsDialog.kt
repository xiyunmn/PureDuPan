package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

/** Small host-side dialog for SAF tree selection; values remain host-scoped in SharedPreferences. */
internal object StoragePathSettingsDialog {
    fun show(context: Context, prefs: SharedPreferences, isSamsungHost: Boolean) {
        val density = context.resources.displayMetrics.density
        val padding = (20 * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val current = TextView(context).apply {
            text = currentDirectoryText(context, prefs)
            textSize = 12f
            setTextColor(UiStyle.tokens(context).textSecondary)
            setPadding(0, 0, 0, padding / 2)
        }
        root.addView(current)
        root.addView(
            android.widget.Button(context).apply {
                text = if (prefs.getString(SettingsUserState.KEY_STORAGE_DOWNLOAD_TREE_URI, null).isNullOrBlank()) {
                    UiText.Settings.STORAGE_SELECT_DIRECTORY
                } else {
                    UiText.Settings.STORAGE_RESELECT_DIRECTORY
                }
                setOnClickListener { SettingsMenuHook.launchStorageTreePicker(context) }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(
            TextView(context).apply {
                text = "SAF 目录失效、权限撤销或 provider 不可写时，下载任务会停止，不会回退旧公共目录。"
                textSize = 11.5f
                setTextColor(UiStyle.tokens(context).textMuted)
                setPadding(0, padding / 2, 0, 0)
            },
        )
        val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
            .setTitle(UiText.Settings.STORAGE_PATH_MANAGEMENT_LABEL)
            .setView(root)
            .setPositiveButton(UiText.Settings.BUTTON_CONFIRM, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                Toast.makeText(context, UiText.Settings.withRestartHint(UiText.Settings.STORAGE_DIRECTORY_SELECTED), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun currentDirectoryText(context: Context, prefs: SharedPreferences): String {
        val uri = prefs.getString(SettingsUserState.KEY_STORAGE_DOWNLOAD_TREE_URI, null)
        if (uri.isNullOrBlank()) return UiText.Settings.STORAGE_DIRECTORY_NONE
        val valid = runCatching {
            context.contentResolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isWritePermission }
        }.getOrDefault(false)
        return if (valid) "当前目录：$uri" else "${UiText.Settings.STORAGE_DIRECTORY_PERMISSION_INVALID}\n$uri"
    }
}
