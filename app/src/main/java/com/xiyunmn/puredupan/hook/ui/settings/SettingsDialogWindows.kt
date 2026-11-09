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
        maxWidthDp: Float = 560f,
        horizontalMarginDp: Float = 12f,
    ) {
        try {
            dialog.create()
            val window = dialog.window ?: run {
                XposedCompat.logW("$logTag showStableSubDialog aborted: window missing before show")
                return
            }
            // 卡片沿用系统动画，嵌套弹窗单独渐变父页面遮罩。
            window.setWindowAnimations(android.R.style.Animation_Dialog)
            themedWindows.keys.firstOrNull { parent ->
                parent !== window && parent.decorView.isShown && parent.decorView.hasWindowFocus()
            }?.let { parent -> SettingsDialogShade.bind(parent, window) }
            applyCardStyle(
                window = window,
                density = density,
                maxWidthDp = maxWidthDp,
                horizontalMarginDp = horizontalMarginDp,
            )
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("$logTag showStableSubDialog failed: ${t.message}")
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
