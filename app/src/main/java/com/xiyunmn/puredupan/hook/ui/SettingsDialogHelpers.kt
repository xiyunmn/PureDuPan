package com.xiyunmn.puredupan.hook.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.ui.settings.SettingsDialogLayout
import com.xiyunmn.puredupan.hook.ui.settings.SettingsDialogWindows
import com.xiyunmn.puredupan.hook.ui.settings.SettingsReloadCleanup

internal fun showDisclaimerDialog(context: Context, onAccepted: () -> Unit) {
    var countdown = DISCLAIMER_COUNTDOWN_SECONDS
    val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
        .setTitle(UiText.Settings.DISCLAIMER_TITLE)
        .setView(createNoticeContent(
            context,
            UiText.Settings.DISCLAIMER_RISK,
            listOf(UiText.Settings.DISCLAIMER_RELATION, UiText.Settings.DISCLAIMER_RESPONSIBILITY),
            UiText.Settings.DISCLAIMER_ACCEPTANCE_HINT,
        ))
        .setCancelable(false)
        .setPositiveButton(UiText.Settings.disclaimerCountdown(countdown), null)
        .setNegativeButton(UiText.Settings.BUTTON_DISAGREE, null)
        .create()

    dialog.setOnShowListener {
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        positiveButton.isEnabled = false

        val handler = Handler(Looper.getMainLooper())
        val countdownRunnable = object : Runnable {
            override fun run() {
                countdown--
                if (countdown > 0) {
                    positiveButton.text = UiText.Settings.disclaimerCountdown(countdown)
                    handler.postDelayed(this, 1000)
                } else {
                    positiveButton.text = UiText.Settings.BUTTON_AGREE
                    positiveButton.isEnabled = true
                }
            }
        }
        SettingsReloadCleanup.onDispose(dialog) { handler.removeCallbacks(countdownRunnable) }
        handler.postDelayed(countdownRunnable, 1000)

        positiveButton.setOnClickListener {
            if (countdown <= 0) {
                onAccepted()
                dialog.dismiss()
            }
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(context, UiText.Settings.DISCLAIMER_REJECTED, Toast.LENGTH_SHORT).show()
        }
    }

    SettingsDialogWindows.showStableSubDialog(
        dialog, context.resources.displayMetrics.density, "[SettingsDialogHelpers]",
    )
}

internal fun showRestrictedFeatureWarningDialog(context: Context, onConfirmed: () -> Unit) {
    var countdown = RESTRICTED_FEATURE_WARNING_COUNTDOWN_SECONDS
    val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
        .setTitle(UiText.Settings.RESTRICTED_FEATURE_WARNING_TITLE)
        .setView(createNoticeContent(
            context,
            UiText.Settings.RESTRICTED_FEATURE_RISK,
            listOf(UiText.Settings.RESTRICTED_FEATURE_SCOPE, UiText.Settings.RESTRICTED_FEATURE_RESET_HINT),
        ))
        .setPositiveButton(UiText.Settings.restrictedFeatureWarningCountdown(countdown), null)
        .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
        .create()

    dialog.setOnShowListener {
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        positiveButton.isEnabled = false

        val handler = Handler(Looper.getMainLooper())
        val countdownRunnable = object : Runnable {
            override fun run() {
                countdown--
                if (countdown > 0) {
                    positiveButton.text = UiText.Settings.restrictedFeatureWarningCountdown(countdown)
                    handler.postDelayed(this, 1000)
                } else {
                    positiveButton.text = UiText.Settings.BUTTON_UNLOCK
                    positiveButton.isEnabled = true
                }
            }
        }
        SettingsReloadCleanup.onDispose(dialog) { handler.removeCallbacks(countdownRunnable) }
        handler.postDelayed(countdownRunnable, 1000)

        positiveButton.setOnClickListener {
            if (countdown <= 0) {
                onConfirmed()
                dialog.dismiss()
            }
        }
    }

    SettingsDialogWindows.showStableSubDialog(
        dialog, context.resources.displayMetrics.density, "[SettingsDialogHelpers]",
    )
}

/** 风险区先读，正文用段距分隔；小屏幕保持正文可滚动。 */
private fun createNoticeContent(
    context: Context,
    risk: String,
    paragraphs: List<String>,
    acceptanceHint: String? = null,
): ScrollView {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()
    fun textView(value: String, secondary: Boolean = false): TextView = TextView(context).apply {
        text = value
        textSize = if (secondary) 12.5f else 16f
        includeFontPadding = false
        gravity = Gravity.START
        setLineSpacing(0f, 1.25f)
        UiStyle.bindTheme(this) { tokens ->
            setTextColor(if (secondary) tokens.textSecondary else tokens.textPrimary)
        }
    }

    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(4))
    }
    val riskBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        UiStyle.bindTheme(this) { tokens ->
            background = GradientDrawable().apply {
                cornerRadius = 12f * density
                setColor(Color.argb(24, Color.red(tokens.danger), Color.green(tokens.danger), Color.blue(tokens.danger)))
            }
        }
    }
    val icon = context.getDrawable(android.R.drawable.ic_dialog_alert)?.mutate()?.apply {
        setBounds(0, 0, dp(15), dp(15))
    }
    val riskTitle = TextView(context).apply {
        text = UiText.Settings.NOTICE_RISK_TITLE
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        compoundDrawablePadding = dp(6)
        UiStyle.bindTheme(this) { tokens ->
            // 浅色下加深标题红色，保证小字清晰。
            val color = if (tokens.night) tokens.danger else 0xFFB8323A.toInt()
            setTextColor(color)
            icon?.setTint(color)
            setCompoundDrawablesRelative(icon, null, null, null)
        }
    }
    riskBox.addView(riskTitle)
    riskBox.addView(textView(risk), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(6) })
    content.addView(riskBox, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ))

    paragraphs.forEach { paragraph ->
        content.addView(textView(paragraph).apply { setPadding(dp(4), 0, dp(4), 0) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) },
        )
    }
    acceptanceHint?.let { hint ->
        content.addView(textView(hint, secondary = true).apply { setPadding(dp(4), 0, dp(4), 0) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) },
        )
    }
    return SettingsDialogLayout.createDialogScrollContainer(context, content)
}

private const val DISCLAIMER_COUNTDOWN_SECONDS = 8
private const val RESTRICTED_FEATURE_WARNING_COUNTDOWN_SECONDS = 5
