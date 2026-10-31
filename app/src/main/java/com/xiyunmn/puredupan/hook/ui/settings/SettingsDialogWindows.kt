package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import java.util.WeakHashMap
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.UiStyle

internal object SettingsDialogWindows {
    private val themedWindows = WeakHashMap<Window, Boolean>()
    fun themeFor(context: Context): Int {
        return if (UiStyle.tokens(context).night) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
    }

    fun applyCardStyle(
        window: Window,
        density: Float,
        maxWidthDp: Float = 560f,
        horizontalMarginDp: Float = 12f,
        minWidthDp: Float = 280f,
        clearCustomPadding: Boolean = true,
    ) {
        val tokens = UiStyle.tokens(window.context)
        UiStyle.applyDialogCard(window, tokens)
        if (themedWindows.put(window, true) == null) {
            UiStyle.bindTheme(window.decorView) { palette ->
                window.context.setTheme(themeFor(window.context))
                UiStyle.applyDialogCard(window, palette)
                val titleId = resources.getIdentifier("alertTitle", "id", "android")
                window.findViewById<TextView>(titleId)?.setTextColor(palette.textPrimary)
                window.findViewById<TextView>(android.R.id.message)?.setTextColor(palette.textPrimary)
                val buttonColors = ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(palette.textMuted, palette.accent),
                )
                for (id in intArrayOf(android.R.id.button1, android.R.id.button2, android.R.id.button3)) {
                    window.findViewById<TextView>(id)?.setTextColor(buttonColors)
                }
            }
        }
        if (clearCustomPadding) {
            clearSystemDialogCustomPanelPadding(window)
        }
        applyStableDialogWindowLayout(
            window = window,
            density = density,
            maxWidthDp = maxWidthDp,
            horizontalMarginDp = horizontalMarginDp,
            minWidthDp = minWidthDp,
        )
    }

    fun showStableSubDialog(
        dialog: AlertDialog,
        density: Float,
        logTag: String,
    ) {
        try {
            val preShowWindow = dialog.window ?: run {
                XposedCompat.logW("$logTag showStableSubDialog aborted: window missing before show")
                return
            }
            applyStableSubDialogWindow(preShowWindow, density, clearCustomPadding = false, logTag = logTag)

            dialog.show()

            val window = dialog.window ?: run {
                XposedCompat.logW("$logTag showStableSubDialog aborted: window missing after show")
                return
            }
            val decorView = window.decorView
            decorView.animate().cancel()
            applyStableSubDialogWindow(window, density, clearCustomPadding = true, logTag = logTag)
            animateStableSubDialogEntry(decorView, density, logTag)
        } catch (t: Throwable) {
            XposedCompat.logW("$logTag showStableSubDialog failed: ${t.message}")
            try {
                dialog.window?.decorView?.alpha = 1f
            } catch (_: Throwable) {
                // 尝试恢复窗口外观，失败时保留原状态。
            }
        }
    }

    private fun applyStableSubDialogWindow(
        window: Window,
        density: Float,
        clearCustomPadding: Boolean,
        logTag: String,
    ) {
        try {
            window.setGravity(Gravity.CENTER)
            window.setWindowAnimations(0)
            applyCardStyle(
                window = window,
                density = density,
                clearCustomPadding = clearCustomPadding,
            )
        } catch (t: Throwable) {
            XposedCompat.logW("$logTag applyStableSubDialogWindow failed: ${t.message}")
        }
    }

    private fun animateStableSubDialogEntry(root: View, density: Float, logTag: String) {
        try {
            UiStyle.animateSubDialogEntry(root, density)
        } catch (t: Throwable) {
            root.alpha = 1f
            root.translationY = 0f
            XposedCompat.logW("$logTag stable sub dialog entry animation failed: ${t.message}")
        }
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

    private fun applyStableDialogWindowLayout(
        window: Window,
        density: Float,
        maxWidthDp: Float,
        horizontalMarginDp: Float,
        minWidthDp: Float,
    ) {
        val screenWidth = window.context.resources.displayMetrics.widthPixels
        val horizontalMargin = (horizontalMarginDp * density).toInt().coerceAtLeast(1)
        val availableWidth = screenWidth - horizontalMargin * 2
        if (availableWidth <= 0) return

        val maxWidth = (maxWidthDp * density).toInt().coerceAtLeast(1)
        val minWidth = (minWidthDp * density).toInt().coerceAtMost(availableWidth)
        val targetWidth = availableWidth
            .coerceAtMost(maxWidth)
            .coerceAtLeast(minWidth)

        window.setGravity(Gravity.CENTER)
        val attrs = window.attributes
        attrs.gravity = Gravity.CENTER
        attrs.x = 0
        attrs.y = 0
        attrs.width = targetWidth
        attrs.height = ViewGroup.LayoutParams.WRAP_CONTENT
        window.attributes = attrs
        window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
