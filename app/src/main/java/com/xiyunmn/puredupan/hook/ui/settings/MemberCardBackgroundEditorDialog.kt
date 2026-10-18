package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsHostState
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.MemberCardBackgroundEditorView
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

internal object MemberCardBackgroundEditorDialog {
    private const val LOG_TAG = "[MemberCardBackgroundEditorDialog]"

    fun show(
        context: Context,
        uriString: String,
    ) {
        try {
            if (!SettingsHostState.isFeatureVisibleForContext(
                    context,
                    SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND,
                )
            ) {
                XposedCompat.logW("$LOG_TAG show skipped: unsupported host")
                return
            }
            val prefs = SettingsUserState.getPrefs(context)
            val editorAspectRatio = memberCardBackgroundEditorAspectRatio(context, prefs)
            val bitmap = loadMemberCardBackgroundPreviewBitmap(context, uriString, editorAspectRatio) ?: run {
                Toast.makeText(
                    context,
                    UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_FAILED,
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val editorView = MemberCardBackgroundEditorView(context, bitmap, editorAspectRatio).apply {
                scalePercent = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT, 100)
                rotationDegrees = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES, 0)
                offsetXPermille = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE, 0)
                offsetYPermille = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE, 0)
            }

            val scaleValue = TextView(context).apply {
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                UiStyle.bindTheme(this) { tokens -> setTextColor(tokens.accent) }
                gravity = Gravity.END
                includeFontPadding = false
            }
            fun updateScaleValue(value: Int) {
                scaleValue.text = "${value.coerceIn(100, 300)}%"
            }
            updateScaleValue(editorView.scalePercent)
            var scaleSeekBar: SeekBar? = null
            editorView.onScalePercentChanged = { value ->
                updateScaleValue(value)
                val progress = value.coerceIn(100, 300) - 100
                if (scaleSeekBar?.progress != progress) {
                    scaleSeekBar?.progress = progress
                }
            }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)

                addView(TextView(context).apply {
                    text = UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_HINT
                    textSize = 12.5f
                    UiStyle.bindTheme(this) { tokens -> setTextColor(tokens.textSecondary) }
                    includeFontPadding = false
                    setLineSpacing(1f * density, 1f)
                    setPadding(0, 0, 0, (10 * density).toInt())
                })

                addView(
                    editorView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )

                val scaleHeader = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (14 * density).toInt(), 0, 0)
                    addView(TextView(context).apply {
                        text = UiText.Settings.MEMBER_CARD_BACKGROUND_SCALE_LABEL
                        textSize = 14.5f
                        typeface = Typeface.DEFAULT_BOLD
                        UiStyle.bindTheme(this) { tokens -> setTextColor(tokens.textPrimary) }
                        includeFontPadding = false
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(
                        scaleValue,
                        LinearLayout.LayoutParams((64 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT),
                    )
                }
                addView(scaleHeader)

                addView(SeekBar(context).apply {
                    scaleSeekBar = this
                    max = 200
                    progress = editorView.scalePercent.coerceIn(100, 300) - 100
                    splitTrack = false
                    val horizontalInset = (12 * density).toInt()
                    setPadding(horizontalInset, (4 * density).toInt(), horizontalInset, 0)
                    UiStyle.bindTheme(this) { tokens -> MemberCardSettingsControls.applySeekBarTint(this, tokens) }
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            val value = (100 + progress).coerceIn(100, 300)
                            editorView.scalePercent = value
                            updateScaleValue(value)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    })
                })

                addView(Button(context).apply {
                    text = UiText.Settings.MEMBER_CARD_BACKGROUND_ROTATE
                    UiStyle.bindTheme(this) { tokens -> UiStyle.paintScanActionButton(this, density, tokens.accent) }
                    setOnClickListener {
                        editorView.rotationDegrees = editorView.rotationDegrees + 90
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (40 * density).toInt(),
                ).apply {
                    topMargin = (10 * density).toInt()
                    gravity = Gravity.END
                })
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_TITLE)
                .setView(root)
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE) { _, _ ->
                    val editor = prefs.edit()
                    if (isFeatureVisible(context, SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT)) {
                        editor.putInt(
                            SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT,
                            editorView.scalePercent,
                        )
                    }
                    if (isFeatureVisible(context, SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES)) {
                        editor.putInt(
                            SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES,
                            editorView.rotationDegrees,
                        )
                    }
                    if (isFeatureVisible(context, SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE)) {
                        editor.putInt(
                            SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE,
                            editorView.offsetXPermille,
                        )
                    }
                    if (isFeatureVisible(context, SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE)) {
                        editor.putInt(
                            SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE,
                            editorView.offsetYPermille,
                        )
                    }
                    editor.apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .create()
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG show failed: ${t.message}")
            Toast.makeText(
                context,
                UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_FAILED,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun isFeatureVisible(context: Context, featureKey: String): Boolean {
        return SettingsHostState.isFeatureVisibleForContext(context, featureKey)
    }

    private fun loadMemberCardBackgroundPreviewBitmap(
        context: Context,
        uriString: String,
        cropAspectRatio: Float,
    ): Bitmap? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val resolver = context.contentResolver ?: return null
        val metrics = context.resources.displayMetrics
        val targetWidth = metrics.widthPixels.coerceAtMost(1600).coerceAtLeast(720)
        val targetHeight = (targetWidth / cropAspectRatio.coerceIn(0.35f, 6f))
            .toInt()
            .coerceAtLeast(240)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = calculateBitmapInSampleSize(
                options.outWidth,
                options.outHeight,
                targetWidth,
                targetHeight,
            )
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG load background preview failed: ${t.message}")
            null
        }
    }

    private fun memberCardBackgroundEditorAspectRatio(
        context: Context,
        prefs: SharedPreferences,
    ): Float {
        val statePrefs = SettingsUserState.getModuleStatePrefs(context)
        val defaultWidthPx = statePrefs.getInt(SettingsUserState.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX, 0)
        val defaultHeightPx = statePrefs.getInt(SettingsUserState.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX, 0)
        val defaultAspectRatio = if (defaultWidthPx > 0 && defaultHeightPx > 0) {
            defaultWidthPx.toFloat() / defaultHeightPx.toFloat()
        } else {
            3f
        }.coerceIn(0.35f, 6f)

        if (!prefs.getBoolean(SettingsUserState.KEY_MEMBER_CARD_SIZE_ADJUST, false)) return defaultAspectRatio
        val widthDp = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_SIZE_WIDTH_DP, 0)
        val heightDp = prefs.getInt(SettingsUserState.KEY_MEMBER_CARD_SIZE_HEIGHT_DP, 0)
        val density = context.resources.displayMetrics.density
        val effectiveWidthPx = when {
            widthDp > 0 -> widthDp * density
            defaultWidthPx > 0 -> defaultWidthPx.toFloat()
            heightDp > 0 -> heightDp * density * defaultAspectRatio
            else -> defaultAspectRatio
        }
        val effectiveHeightPx = when {
            heightDp > 0 -> heightDp * density
            defaultHeightPx > 0 -> defaultHeightPx.toFloat()
            widthDp > 0 -> widthDp * density / defaultAspectRatio
            else -> 1f
        }
        return if (effectiveWidthPx > 0f && effectiveHeightPx > 0f) {
            effectiveWidthPx / effectiveHeightPx
        } else {
            defaultAspectRatio
        }.coerceIn(0.35f, 6f)
    }

    private fun calculateBitmapInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sampleSize = 1
        while (
            sourceWidth / (sampleSize * 2) >= targetWidth &&
            sourceHeight / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }
}
