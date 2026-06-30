package com.xiyunmn.puredupan.hook.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

internal object SettingsSwitchRows {
    private const val DEXKIT_EFFECTIVE_AFTER_ENABLE_PHRASE = "DexKit 解析成功后生效"
    private const val DEXKIT_ENHANCED_AFTER_ENABLE_PHRASE = "DexKit 解析成功后增强覆盖"
    private const val ACTION_TEXT_VIEW_TAG = "settings_action_text"
    private const val STATUS_BADGE_TEXT_VIEW_TAG = "settings_status_badge_text"

    @Suppress("DEPRECATION")
    fun create(
        context: Context,
        prefs: SharedPreferences,
        label: String,
        description: String?,
        prefKey: String?,
        padding: Int,
        enabled: Boolean = true,
        defaultValue: Boolean = false,
        actionIcon: String? = null,
        onActionClick: (() -> Unit)? = null,
        linkedPrefKeys: List<String> = emptyList(),
        showSwitch: Boolean = true,
        actionButtonText: String? = null,
        statusBadgeText: String? = null,
        onStatusBadgeClick: (() -> Unit)? = null,
    ): View {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (padding * 0.55f).toInt(), 0, (padding * 0.55f).toInt())
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        textContainer.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = label
                textSize = 14.5f
                setTextColor(if (enabled) tokens.textPrimary else tokens.textMuted)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setLineSpacing(1.5f * density, 1f)
            })
            if (statusBadgeText != null && onStatusBadgeClick != null) {
                addView(
                    TextView(context).apply {
                        tag = STATUS_BADGE_TEXT_VIEW_TAG
                        text = emphasizeDexKitStatusBadge(statusBadgeText, tokens)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        UiStyle.paintStatusBadge(this, density, tokens, enabled)
                        setOnClickListener {
                            if (enabled) {
                                UiStyle.animateActionPress(this)
                                onStatusBadgeClick()
                            }
                        }
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        setMargins((8 * density).toInt(), 0, 0, 0)
                    },
                )
            }
        })

        if (description != null) {
            textContainer.addView(TextView(context).apply {
                text = emphasizeWarningHints(description, tokens)
                textSize = 11.5f
                setTextColor(if (enabled) tokens.textSecondary else tokens.textMuted)
                setPadding(0, (3 * density).toInt(), (14 * density).toInt(), 0)
                includeFontPadding = false
                setLineSpacing(1f * density, 1f)
            })
        }

        row.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
        )

        if (actionButtonText != null && onActionClick != null) {
            row.addView(Button(context).apply {
                text = actionButtonText
                UiStyle.paintScanActionButton(this, density, if (enabled) tokens.accent else tokens.textMuted)
                setOnClickListener {
                    if (enabled) {
                        UiStyle.animateActionPress(this)
                        onActionClick()
                    }
                }
            })
        } else if (actionIcon != null && onActionClick != null) {
            val materialIcon = materialActionIcon(actionIcon)
            if (materialIcon != null) {
                row.addView(ImageButton(context).apply {
                    UiStyle.paintMaterialIconButton(
                        button = this,
                        density = density,
                        tokens = tokens,
                        icon = materialIcon,
                        enabled = enabled,
                        contentDescription = actionIcon,
                    )
                    setOnClickListener {
                        if (enabled) {
                            UiStyle.animateActionPress(this)
                            onActionClick()
                        }
                    }
                })
            } else {
                row.addView(TextView(context).apply {
                    tag = ACTION_TEXT_VIEW_TAG
                    text = actionIcon
                    gravity = Gravity.CENTER
                    if (actionIcon == UiText.Settings.ACTION_ICON_SIGN_IN) {
                        UiStyle.paintSignInActionIcon(this, density, tokens, enabled)
                    } else {
                        textSize = 18f
                        setTextColor(if (enabled) tokens.accent else tokens.textMuted)
                        setPadding(
                            (12 * density).toInt(),
                            (6 * density).toInt(),
                            (12 * density).toInt(),
                            (6 * density).toInt(),
                        )
                    }
                    setOnClickListener {
                        if (enabled) {
                            UiStyle.animateActionPress(this)
                            onActionClick()
                        }
                    }
                })
            }
        }

        if (!showSwitch) {
            return row
        }

        val sw = Switch(context).apply {
            var reverting = false
            isChecked = if (enabled && prefKey != null) {
                resolveSwitchChecked(prefs, prefKey, linkedPrefKeys, defaultValue)
            } else {
                defaultValue
            }
            isEnabled = enabled

            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked),
            )
            thumbTintList = ColorStateList(
                states,
                intArrayOf(
                    tokens.accent,
                    tokens.accentThumbOff,
                ),
            )
            trackTintList = ColorStateList(
                states,
                intArrayOf(
                    tokens.accentTrackOn,
                    tokens.accentTrackOff,
                ),
            )

            setOnCheckedChangeListener { _, isChecked ->
                if (enabled && !reverting) {
                    if (prefKey != null) {
                        val editor = prefs.edit().putBoolean(prefKey, isChecked)
                        for (linkedPrefKey in linkedPrefKeys) {
                            editor.putBoolean(linkedPrefKey, isChecked)
                        }
                        if (!editor.commit()) {
                            reverting = true
                            this.isChecked = !isChecked
                            reverting = false
                            Toast.makeText(
                                context,
                                UiText.Settings.SETTINGS_SAVE_FAILED,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
        row.addView(sw)

        return row
    }

    fun findSwitchView(root: View): Switch? {
        if (root is Switch) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val found = findSwitchView(root.getChildAt(index))
            if (found != null) return found
        }
        return null
    }

    fun findActionTextView(root: View): TextView? {
        if (root is TextView && root.tag == ACTION_TEXT_VIEW_TAG) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val found = findActionTextView(root.getChildAt(index))
            if (found != null) return found
        }
        return null
    }

    fun findStatusBadgeTextView(root: View): TextView? {
        if (root is TextView && root.tag == STATUS_BADGE_TEXT_VIEW_TAG) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val found = findStatusBadgeTextView(root.getChildAt(index))
            if (found != null) return found
        }
        return null
    }

    fun setRowEnabled(row: View, enabled: Boolean) {
        row.isEnabled = enabled
        row.alpha = if (enabled) 1f else 0.45f
        findSwitchView(row)?.isEnabled = enabled
    }

    private fun emphasizeWarningHints(
        description: String,
        tokens: UiStyle.Tokens,
    ): CharSequence {
        val phrases = listOf(
            DEXKIT_EFFECTIVE_AFTER_ENABLE_PHRASE to tokens.accent,
            DEXKIT_ENHANCED_AFTER_ENABLE_PHRASE to tokens.accent,
            UiText.Settings.AUTO_DAILY_SIGN_IN_RISK_HINT to tokens.warning,
            UiText.Settings.SIGN_IN_RISK_WARNING to tokens.warning,
        )
        val matches = phrases.mapNotNull { (phrase, color) ->
            val start = description.indexOf(phrase)
            if (start < 0) null else Triple(start, start + phrase.length, color)
        }
        if (matches.isEmpty()) return description
        return SpannableString(description).apply {
            matches.forEach { (start, end, color) ->
                setSpan(
                    ForegroundColorSpan(color),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    fun emphasizeDexKitStatusBadge(
        text: String,
        tokens: UiStyle.Tokens,
    ): CharSequence {
        val slash = text.indexOf('/')
        if (slash < 0) return text
        var start = slash - 1
        while (start >= 0 && text[start].isWhitespace()) start--
        while (start >= 0 && text[start].isDigit()) start--
        val successStart = start + 1
        if (successStart >= slash) return text
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(tokens.success),
                successStart,
                slash,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun resolveSwitchChecked(
        prefs: SharedPreferences,
        prefKey: String,
        linkedPrefKeys: List<String>,
        defaultValue: Boolean,
    ): Boolean {
        if (!prefs.contains(prefKey) && linkedPrefKeys.none { prefs.contains(it) }) {
            return defaultValue
        }
        if (prefs.getBoolean(prefKey, false)) return true
        return linkedPrefKeys.any { prefs.getBoolean(it, false) }
    }

    private fun materialActionIcon(actionIcon: String): UiStyle.MaterialActionIcon? {
        return when (actionIcon) {
            UiText.Settings.ACTION_ICON_SETTINGS -> UiStyle.MaterialActionIcon.SETTINGS
            UiText.Settings.ACTION_ICON_SIGN_IN -> UiStyle.MaterialActionIcon.PLAY_PAUSE
            UiText.Settings.ACTION_ICON_CLEAR -> UiStyle.MaterialActionIcon.DELETE
            UiText.Settings.ACTION_ICON_RESET -> UiStyle.MaterialActionIcon.REFRESH
            else -> null
        }
    }
}
