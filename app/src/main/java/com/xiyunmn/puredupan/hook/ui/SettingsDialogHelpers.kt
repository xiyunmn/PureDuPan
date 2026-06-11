package com.xiyunmn.puredupan.hook.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.widget.Toast

internal fun showDisclaimerDialog(context: Context, onAccepted: () -> Unit) {
    var countdown = DISCLAIMER_COUNTDOWN_SECONDS
    val dialog = AlertDialog.Builder(context, SettingsMenuHook.getDialogTheme(context))
        .setTitle(UiText.Settings.DISCLAIMER_TITLE)
        .setMessage(UiText.Settings.DISCLAIMER_MESSAGE)
        .setCancelable(false)
        .setPositiveButton(UiText.Settings.disclaimerCountdown(countdown), null)
        .setNegativeButton(UiText.Settings.BUTTON_DISAGREE, null)
        .create()

    dialog.setOnShowListener {
        val window = dialog.window
        if (window != null) {
            val density = context.resources.displayMetrics.density
            SettingsMenuHook.applyDialogStyle(window, density)
        }

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

    dialog.show()
}

internal fun showRestrictedFeatureWarningDialog(context: Context, onConfirmed: () -> Unit) {
    var countdown = RESTRICTED_FEATURE_WARNING_COUNTDOWN_SECONDS
    val dialog = AlertDialog.Builder(context, SettingsMenuHook.getDialogTheme(context))
        .setTitle(UiText.Settings.RESTRICTED_FEATURE_WARNING_TITLE)
        .setMessage(UiText.Settings.RESTRICTED_FEATURE_WARNING_MESSAGE)
        .setPositiveButton(UiText.Settings.restrictedFeatureWarningCountdown(countdown), null)
        .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
        .create()

    dialog.setOnShowListener {
        val window = dialog.window
        if (window != null) {
            val density = context.resources.displayMetrics.density
            SettingsMenuHook.applyDialogStyle(window, density)
        }

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
                    positiveButton.text = UiText.Settings.BUTTON_ENABLE
                    positiveButton.isEnabled = true
                }
            }
        }
        handler.postDelayed(countdownRunnable, 1000)

        positiveButton.setOnClickListener {
            if (countdown <= 0) {
                onConfirmed()
                dialog.dismiss()
            }
        }
    }

    dialog.show()
}

private const val DISCLAIMER_COUNTDOWN_SECONDS = 5
private const val RESTRICTED_FEATURE_WARNING_COUNTDOWN_SECONDS = 3
