package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

internal object SettingsDebugActions {
    fun showClearLogsConfirmDialog(context: Context) {
        try {
            val path = XposedCompat.logDirectoryPath(context)
            val message = buildString {
                append(UiText.Settings.CLEAR_LOGS_CONFIRM_MESSAGE)
                if (path.isNotBlank()) {
                    append("\n\n")
                    append(path)
                }
            }
            AlertDialog.Builder(context, dialogThemeFor(context))
                .setTitle(UiText.Settings.CLEAR_LOGS_CONFIRM_TITLE)
                .setMessage(message)
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.ACTION_ICON_CLEAR) { _, _ ->
                    val result = XposedCompat.clearLogFiles(context)
                    val text = if (result.success) {
                        UiText.Settings.CLEAR_LOGS_SUCCESS
                    } else {
                        "${UiText.Settings.CLEAR_LOGS_FAILED}：${result.failedCount}"
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
                .show()
                .window
                ?.let { window ->
                    applyDialogCardStyle(window)
                }
        } catch (t: Throwable) {
            Toast.makeText(context, UiText.Settings.CLEAR_LOGS_FAILED, Toast.LENGTH_SHORT).show()
            XposedCompat.logW("[SettingsDebugActions] showClearLogsConfirmDialog failed: ${t.message}")
        }
    }

    private fun dialogThemeFor(context: Context): Int {
        return if (UiStyle.tokens(context).night) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
    }

    private fun applyDialogCardStyle(window: Window) {
        val tokens = UiStyle.tokens(window.context)
        UiStyle.applyDialogCard(window, tokens)
        clearSystemDialogCustomPanelPadding(window)
        applyStableDialogWindowLayout(window)
    }

    private fun clearSystemDialogCustomPanelPadding(window: Window) {
        val customPanel = window.decorView.findViewById<View>(android.R.id.custom) ?: return
        if (
            customPanel.paddingLeft != 0 ||
            customPanel.paddingTop != 0 ||
            customPanel.paddingRight != 0 ||
            customPanel.paddingBottom != 0
        ) {
            customPanel.setPadding(0, 0, 0, 0)
        }
    }

    private fun applyStableDialogWindowLayout(window: Window) {
        val density = window.context.resources.displayMetrics.density
        val screenWidth = window.context.resources.displayMetrics.widthPixels
        val maxWidth = (360 * density).toInt()
        val horizontalMargin = (28 * density).toInt()
        val targetWidth = (screenWidth - horizontalMargin * 2)
            .coerceAtMost(maxWidth)
            .coerceAtLeast((280 * density).toInt())
        window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
