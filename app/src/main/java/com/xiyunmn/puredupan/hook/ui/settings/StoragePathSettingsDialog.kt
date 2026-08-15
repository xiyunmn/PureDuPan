package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.storage.StorageDestinationResolver
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

/** Secondary storage settings page opened from 设置 > 拓展功能. */
internal object StoragePathSettingsDialog {
    fun show(context: Context, prefs: SharedPreferences, isSamsungHost: Boolean) {
        val density = context.resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        root.addView(
            SettingsDialogLayout.createPerformanceSectionTitle(
                context,
                padding,
                UiText.Settings.STORAGE_DIRECTORY_SECTION_TITLE,
            ),
        )
        val directoryCard = createDirectoryCard(context, density) {
            SettingsMenuHook.launchStorageTreePicker(context)
        }
        updateDirectoryCard(context, prefs, directoryCard)
        root.addView(
            directoryCard.root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(TextView(context).apply {
            text = "所选目录即下载根目录；授权失效或 provider 不可写时任务会停止，不会回退旧公共目录。"
            textSize = 11.5f
            setTextColor(UiStyle.tokens(context).textMuted)
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
            includeFontPadding = false
            setLineSpacing(1f * density, 1f)
        })
        root.addView(SettingsDialogLayout.createDivider(context, padding))
        root.addView(
            SettingsDialogLayout.createPerformanceSectionTitle(
                context,
                padding,
                UiText.Settings.STORAGE_OPTIONS_SECTION_TITLE,
            ),
        )

        val specs = storageSwitchSpecs(isSamsungHost)
        val switches = linkedMapOf<String, Switch>()
        specs.forEach { spec ->
            SettingsSwitchRows.create(
                context = context,
                prefs = prefs,
                label = spec.label,
                description = spec.description,
                prefKey = spec.key,
                padding = padding,
                defaultValue = spec.defaultValue,
            ).also { row ->
                SettingsSwitchRows.findSwitchView(row)?.let { switches[spec.key] = it }
                root.addView(row)
            }
        }

        val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
            .setTitle(UiText.Settings.STORAGE_PATH_MANAGEMENT_LABEL)
            .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .setPositiveButton(UiText.Settings.SAVE, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val editor = prefs.edit()
                switches.forEach { (key, switch) -> editor.putBoolean(key, switch.isChecked) }
                if (!editor.commit()) {
                    Toast.makeText(context, UiText.Settings.SETTINGS_SAVE_FAILED, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Toast.makeText(
                    context,
                    UiText.Settings.withRestartHint(UiText.Settings.STORAGE_SETTINGS_SAVED),
                    Toast.LENGTH_SHORT,
                ).show()
                dialog.dismiss()
            }
        }
        val selectionListener = { updateDirectoryCard(context, prefs, directoryCard) }
        StorageTreeSelectionState.setListener(selectionListener)
        dialog.setOnDismissListener { StorageTreeSelectionState.clearIfSame(selectionListener) }
        SettingsDialogWindows.showStableSubDialog(dialog, density, "[StoragePathSettingsDialog]")
    }

    private fun storageSwitchSpecs(isSamsungHost: Boolean): List<StorageSwitchSpec> = buildList {
        add(
            StorageSwitchSpec(
                SettingsUserState.KEY_STORAGE_DOWNLOAD_REDIRECT_ENABLED,
                UiText.Settings.STORAGE_DOWNLOAD_REDIRECT_LABEL,
                UiText.Settings.STORAGE_DOWNLOAD_REDIRECT_DESC,
                false,
            ),
        )
        add(
            StorageSwitchSpec(
                SettingsUserState.KEY_STORAGE_REMOVE_OUTER_PATH,
                UiText.Settings.STORAGE_REMOVE_OUTER_PATH_LABEL,
                UiText.Settings.STORAGE_REMOVE_OUTER_PATH_DESC,
                false,
            ),
        )
        add(
            StorageSwitchSpec(
                SettingsUserState.KEY_STORAGE_ROOT_GUARD_ENABLED,
                UiText.Settings.STORAGE_ROOT_GUARD_LABEL,
                UiText.Settings.STORAGE_ROOT_GUARD_DESC,
                false,
            ),
        )
        if (isSamsungHost) {
            add(
                StorageSwitchSpec(
                    SettingsUserState.KEY_STORAGE_WECHAT_BACKUP_REDIRECT_ENABLED,
                    UiText.Settings.STORAGE_WECHAT_BACKUP_LABEL,
                    UiText.Settings.STORAGE_WECHAT_BACKUP_DESC,
                    false,
                ),
            )
        }
        add(
            StorageSwitchSpec(
                SettingsUserState.KEY_STORAGE_READER_SDK_REDIRECT_ENABLED,
                UiText.Settings.STORAGE_READER_SDK_LABEL,
                UiText.Settings.STORAGE_READER_SDK_DESC,
                false,
            ),
        )
    }

    private fun createDirectoryCard(
        context: Context,
        density: Float,
        onClick: () -> Unit,
    ): DirectoryCard {
        val tokens = UiStyle.tokens(context)
        val horizontalPadding = (13 * density).toInt()
        val verticalPadding = (12 * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = (88 * density).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            background = GradientDrawable().apply {
                cornerRadius = 17f * density
                setColor(tokens.surfaceAlt)
                setStroke((1f * density).toInt().coerceAtLeast(1), tokens.inputStroke)
            }
            setOnClickListener {
                UiStyle.animateActionPress(this)
                onClick()
            }
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, (8 * density).toInt(), 0)
            addView(TextView(context).apply {
                text = UiText.Settings.STORAGE_DOWNLOAD_DIRECTORY_LABEL
                textSize = 12f
                setTextColor(tokens.textSecondary)
                includeFontPadding = false
            })
        }
        val path = TextView(context).apply {
            textSize = 14f
            setTextColor(tokens.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            maxLines = 2
            setPadding(0, (3 * density).toInt(), 0, 0)
        }
        val status = TextView(context).apply {
            textSize = 11.2f
            includeFontPadding = false
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        textColumn.addView(path)
        textColumn.addView(status)
        root.addView(
            textColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )

        val action = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = (52 * density).toInt()
            setPadding(
                (10 * density).toInt(),
                (7 * density).toInt(),
                (10 * density).toInt(),
                (7 * density).toInt(),
            )
            setTextColor(tokens.accent)
            background = GradientDrawable().apply {
                cornerRadius = 14f * density
                setColor(tokens.accentSoft)
                setStroke((1f * density).toInt().coerceAtLeast(1), tokens.inputStroke)
            }
        }
        root.addView(action)
        return DirectoryCard(root, path, status, action)
    }

    private fun updateDirectoryCard(
        context: Context,
        prefs: SharedPreferences,
        card: DirectoryCard,
    ) {
        val uri = prefs.getString(SettingsUserState.KEY_STORAGE_DOWNLOAD_TREE_URI, null)
        val tokens = UiStyle.tokens(context)
        if (uri.isNullOrBlank()) {
            card.path.text = UiText.Settings.STORAGE_DIRECTORY_NONE
            card.status.text = UiText.Settings.STORAGE_DIRECTORY_SELECT_HINT
            card.status.setTextColor(tokens.textMuted)
            card.action.text = UiText.Settings.STORAGE_SELECT_DIRECTORY
            return
        }
        val visiblePath = StorageDestinationResolver.displayPathForTreeUri(context, uri)
        val valid = runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == uri && it.isReadPermission && it.isWritePermission
            }
        }.getOrDefault(false)
        card.path.text = visiblePath ?: "已选择 SAF 公共目录"
        card.status.text = if (valid) {
            UiText.Settings.STORAGE_DIRECTORY_PERMISSION_VALID
        } else {
            UiText.Settings.STORAGE_DIRECTORY_PERMISSION_INVALID
        }
        card.status.setTextColor(if (valid) tokens.success else tokens.warning)
        card.action.text = UiText.Settings.STORAGE_RESELECT_DIRECTORY
    }

    private data class StorageSwitchSpec(
        val key: String,
        val label: String,
        val description: String,
        val defaultValue: Boolean,
    )

    private data class DirectoryCard(
        val root: View,
        val path: TextView,
        val status: TextView,
        val action: TextView,
    )
}
