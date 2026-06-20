package com.xiyunmn.puredupan.hook.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

internal object MemberCardSettingsControls {
    fun createBackgroundImageRow(
        context: Context,
        prefs: SharedPreferences,
        label: String,
        description: String,
        padding: Int,
        onChoose: () -> Unit,
        onAdjust: () -> Unit,
        onClear: () -> Unit,
    ): MemberCardBackgroundImageControl {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (padding * 0.5f).toInt(), 0, (padding * 0.55f).toInt())
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        textContainer.addView(TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(tokens.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        textContainer.addView(TextView(context).apply {
            text = description
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, (3 * density).toInt(), (12 * density).toInt(), 0)
            includeFontPadding = false
            setLineSpacing(1f * density, 1f)
        })
        header.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
        )

        val enabledSwitch = Switch(context).apply {
            isChecked = prefs.getBoolean(SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND, false)
            applySwitchTint(this, tokens)
        }
        header.addView(
            enabledSwitch,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(header)

        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        val imageUri = prefs.getString(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_URI, null)
        val display = TextView(context).apply {
            text = backgroundDisplayText(imageUri)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setTextColor(tokens.textPrimary)
            includeFontPadding = false
            background = UiStyle.createPlainInputUnderlineBackground(tokens, density)
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener {
                enabledSwitch.isChecked = true
                onChoose()
            }
        }
        controlRow.addView(
            display,
            LinearLayout.LayoutParams(0, (40 * density).toInt(), 1.0f),
        )

        val chooseButton = Button(context).apply {
            text = UiText.Settings.MEMBER_CARD_BACKGROUND_CHOOSE
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            setOnClickListener {
                enabledSwitch.isChecked = true
                onChoose()
            }
        }
        controlRow.addView(
            chooseButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (8 * density).toInt()
            },
        )

        val adjustButton = Button(context).apply {
            text = UiText.Settings.MEMBER_CARD_BACKGROUND_ADJUST
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            updateButtonEnabledState(!imageUri.isNullOrBlank())
            setOnClickListener {
                enabledSwitch.isChecked = true
                onAdjust()
            }
        }
        controlRow.addView(
            adjustButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (4 * density).toInt()
            },
        )

        val clearButton = Button(context).apply {
            text = UiText.Settings.MEMBER_CARD_BACKGROUND_CLEAR
            UiStyle.paintScanActionButton(this, density, tokens.textSecondary)
            setOnClickListener {
                enabledSwitch.isChecked = false
                display.text = backgroundDisplayText(null)
                adjustButton.updateButtonEnabledState(false)
                onClear()
            }
        }
        controlRow.addView(
            clearButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (4 * density).toInt()
            },
        )
        root.addView(controlRow)

        return MemberCardBackgroundImageControl(
            row = root,
            switch = enabledSwitch,
            updateSelectedUri = { uri ->
                display.text = backgroundDisplayText(uri)
                adjustButton.updateButtonEnabledState(!uri.isNullOrBlank())
            },
        )
    }

    fun createIntSliderRow(
        context: Context,
        label: String,
        description: String,
        padding: Int,
        minValue: Int,
        maxValue: Int,
        initialValue: Int,
        valueFormatter: (Int) -> String,
    ): IntSliderControl {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val currentValue = intArrayOf(initialValue.coerceIn(minValue, maxValue))
        val valueText = TextView(context).apply {
            textSize = 13f
            setTextColor(tokens.accent)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            includeFontPadding = false
            setPadding((8 * density).toInt(), 0, 0, 0)
        }

        fun updateValueText(value: Int) {
            valueText.text = valueFormatter(value)
        }
        updateValueText(currentValue[0])

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            setPadding(0, (padding * 0.5f).toInt(), 0, (padding * 0.55f).toInt())

            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            textContainer.addView(TextView(context).apply {
                text = label
                textSize = 15f
                setTextColor(tokens.textPrimary)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setLineSpacing(1.5f * density, 1f)
            })
            textContainer.addView(TextView(context).apply {
                text = description
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, (3 * density).toInt(), (12 * density).toInt(), 0)
                includeFontPadding = false
                setLineSpacing(1f * density, 1f)
            })
            titleRow.addView(
                textContainer,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
            )
            titleRow.addView(
                valueText,
                LinearLayout.LayoutParams((72 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(titleRow)

            addView(SeekBar(context).apply {
                max = (maxValue - minValue).coerceAtLeast(0)
                progress = currentValue[0] - minValue
                splitTrack = false
                val horizontalInset = (12 * density).toInt()
                setPadding(horizontalInset, (4 * density).toInt(), horizontalInset, 0)
                applySeekBarTint(this, tokens)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val value = (minValue + progress).coerceIn(minValue, maxValue)
                        currentValue[0] = value
                        updateValueText(value)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            })
        }

        return IntSliderControl(
            row = row,
            getValue = { currentValue[0] },
        )
    }

    fun applySeekBarTint(
        seekBar: SeekBar,
        tokens: UiStyle.Tokens,
    ) {
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_enabled),
        )
        seekBar.progressTintList = ColorStateList(states, intArrayOf(tokens.textMuted, tokens.accent))
        seekBar.thumbTintList = ColorStateList(states, intArrayOf(tokens.textMuted, tokens.accent))
        seekBar.progressBackgroundTintList = ColorStateList(states, intArrayOf(tokens.divider, tokens.inputStroke))
        seekBar.secondaryProgressTintList = ColorStateList(states, intArrayOf(tokens.divider, tokens.inputStroke))
    }

    private fun backgroundDisplayText(uri: String?): String {
        if (uri.isNullOrBlank()) return UiText.Settings.MEMBER_CARD_BACKGROUND_NONE
        val name = runCatching {
            android.net.Uri.parse(uri).lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
            ?: uri.takeLast(36)
        return UiText.Settings.memberCardBackgroundSelected(name)
    }

    private fun applySwitchTint(
        switch: Switch,
        tokens: UiStyle.Tokens,
    ) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        )
        switch.thumbTintList = ColorStateList(states, intArrayOf(tokens.accent, tokens.accentThumbOff))
        switch.trackTintList = ColorStateList(states, intArrayOf(tokens.accentTrackOn, tokens.accentTrackOff))
    }

    private fun Button.updateButtonEnabledState(enabled: Boolean) {
        UiStyle.setButtonEnabledState(this, enabled)
    }
}
