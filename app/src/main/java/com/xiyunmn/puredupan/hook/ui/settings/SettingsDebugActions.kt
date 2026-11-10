package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsDexKitState
import com.xiyunmn.puredupan.hook.settings.registry.SettingsHostState
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.settings.runtime.SettingsRuntimeSession
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object SettingsDebugActions {
    private const val DEXKIT_STATUS_REFRESH_MS = 1000L

    fun executeDexKitScan(context: Context) {
        val started = SettingsDexKitState.triggerFullScanFromSettings(context)
        Toast.makeText(
            context,
            if (started) UiText.Settings.DEXKIT_SCAN_STARTED else UiText.Settings.DEXKIT_SCAN_PENDING,
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun showDexKitStatusDialog(context: Context) {
        if (!SettingsHostState.showDexKitStatusInSettings(context)) return
        showDexKitDialog(context, details = false)
    }

    private fun showDexKitDialog(context: Context, details: Boolean) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (24 * density).toInt()
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, (16 * density).toInt(), padding, (8 * density).toInt())
            }
            val summaryView = TextView(context).apply {
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                UiStyle.bindTheme(this) { tokens -> setTextColor(tokens.accent) }
                includeFontPadding = false
            }
            val contentRoot = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(summaryView, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(padding, (8 * density).toInt(), padding, (18 * density).toInt())
                })
                addView(android.view.View(context).apply {
                    UiStyle.bindTheme(this) { tokens -> setBackgroundColor(tokens.divider) }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, density.toInt().coerceAtLeast(1),
                ).apply { setMargins(padding, 0, padding, 0) })
                addView(SettingsDialogLayout.createDialogScrollContainer(context, root),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                    ))
            }
            val rows = linkedMapOf<String, DexKitStatusRow>()
            var latestCopyText = ""
            var hasDeferredUpdates = false
            fun refresh() {
                val statuses = SettingsDexKitState.statusViews(context)
                val summary = SettingsDexKitState.summaryText(context)
                val summaryText = "${UiText.Settings.DEXKIT_STATUS_SUMMARY_PREFIX}: $summary"
                if (summaryView.text.toString() != summaryText) summaryView.text = summaryText
                latestCopyText = buildDexKitStatusDetailsText(context, statuses, summary)
                hasDeferredUpdates = false
                statuses.forEachIndexed { index, item ->
                    val row = rows.getOrPut(item.id) {
                        if (rows.isNotEmpty()) root.addView(SettingsDialogLayout.createDivider(context, padding))
                        DexKitStatusRow(context, details).also { root.addView(it.root) }
                    }
                    if (!row.update(item, index)) hasDeferredUpdates = true
                }
            }
            refresh()
            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(if (details) UiText.Settings.DEXKIT_STATUS_DETAILS_TITLE else UiText.Settings.DEXKIT_STATUS_TITLE)
                .setView(contentRoot)
                .setNegativeButton(
                    if (details) UiText.Settings.DEXKIT_STATUS_COPY else UiText.Settings.DEXKIT_STATUS_VIEW_DETAILS,
                    null,
                )
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            val refreshHandler = Handler(Looper.getMainLooper())
            val refreshRunnable = object : Runnable {
                override fun run() {
                    if (!dialog.isShowing) return
                    refresh()
                    if (hasDeferredUpdates || SettingsDexKitState.shouldContinueStatusRefresh(context)) {
                        refreshHandler.postDelayed(this, DEXKIT_STATUS_REFRESH_MS)
                    }
                }
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, "[SettingsDebugActions]")
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                if (details) copyDexKitStatusDetails(context, latestCopyText)
                else showDexKitDialog(context, details = true)
            }
            dialog.setOnDismissListener { refreshHandler.removeCallbacks(refreshRunnable) }
            if (SettingsDexKitState.shouldContinueStatusRefresh(context)) {
                refreshHandler.postDelayed(refreshRunnable, DEXKIT_STATUS_REFRESH_MS)
            }
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsDebugActions] show DexKit dialog failed: ${t.message}")
        }
    }

    private class DexKitStatusRow(private val context: Context, private val details: Boolean) {
        private val density = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val title = TextView(context).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            UiStyle.bindTheme(this) { tokens -> setTextColor(tokens.textPrimary) }
        }
        private val state = TextView(context).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding((12 * density).toInt(), 0, 0, 0)
        }
        private val body = TextView(context).apply {
            textSize = if (details) 12f else 13f
            if (details) {
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                setHorizontallyScrolling(true)
            }
            setPadding(0, (6 * density).toInt(), 0, 0)
            UiStyle.bindTheme(this) { tokens -> setTextColor(tokens.textSecondary) }
        }
        private var renderedItem: SettingsDexKitState.TargetStatusView? = null

        init {
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(state)
            })
            if (details) {
                root.addView(HorizontalScrollView(context).apply {
                    addView(body, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ))
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            } else {
                root.addView(body)
            }
            UiStyle.bindTheme(state) { tokens ->
                state.setTextColor(when (renderedItem?.state) {
                    "error", "not_found" -> tokens.danger
                    "scanning" -> tokens.warning
                    "success" -> tokens.accent
                    else -> tokens.textMuted
                })
            }
        }

        fun update(item: SettingsDexKitState.TargetStatusView, index: Int): Boolean {
            if (renderedItem == item) return true
            // 选中的原始记录保持不动，结束选择后再补上本轮变化。
            if (body.hasSelection()) return false
            renderedItem = item
            val feature = dexKitFeatureLabel(context, item)
            if (title.text.toString() != feature) title.text = feature
            val stateText = stateTextFor(item.state)
            if (state.text.toString() != stateText) state.text = stateText
            val tokens = UiStyle.tokens(context)
            state.setTextColor(when (item.state) {
                "error", "not_found" -> tokens.danger
                "scanning" -> tokens.warning
                "success" -> tokens.accent
                else -> tokens.textMuted
            })
            val content = if (details) buildDexKitStatusLogEntry(context, item, index) else buildString {
                append("ID：").append(item.id).append('\n')
                append("目标：").append(item.target)
                if (!item.detail.isNullOrBlank()) append("\n记录：").append(item.detail)
            }
            if (body.text.toString() != content) body.text = content
            return true
        }
    }

    fun copyLogDirectoryPath(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("日志路径", XposedCompat.logDirectoryPath(context)))
            Toast.makeText(context, UiText.Settings.LOG_DIRECTORY_COPIED, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, UiText.Settings.LOG_DIRECTORY_COPY_FAILED, Toast.LENGTH_SHORT).show()
        }
    }

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
            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.CLEAR_LOGS_CONFIRM_TITLE)
                .setMessage(message)
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.BUTTON_CONFIRM) { _, _ ->
                    val result = XposedCompat.clearLogFiles(context)
                    val text = if (result.success) {
                        UiText.Settings.CLEAR_LOGS_SUCCESS
                    } else {
                        "${result.failedCount} 个日志文件未能清理"
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
                .create()
            SettingsDialogWindows.showStableSubDialog(
                dialog, context.resources.displayMetrics.density, "[SettingsDebugActions]",
                maxWidthDp = 360f, horizontalMarginDp = 28f,
            )
        } catch (t: Throwable) {
            Toast.makeText(context, UiText.Settings.CLEAR_LOGS_FAILED, Toast.LENGTH_SHORT).show()
            XposedCompat.logW("[SettingsDebugActions] showClearLogsConfirmDialog failed: ${t.message}")
        }
    }

    fun showResetModuleSettingsConfirmDialog(context: Context, onRestart: () -> Unit) {
        try {
            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.RESET_MODULE_SETTINGS_CONFIRM_TITLE)
                .setMessage(UiText.Settings.RESET_MODULE_SETTINGS_CONFIRM_MESSAGE)
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.BUTTON_CONFIRM) { _, _ ->
                    val success = SettingsUserState.resetUserSettings(context)
                    val text = if (success) {
                        UiText.Settings.RESET_MODULE_SETTINGS_SUCCESS
                    } else {
                        UiText.Settings.RESET_MODULE_SETTINGS_FAILED
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    if (success) {
                        onRestart()
                    }
                }
                .create()
            SettingsDialogWindows.showStableSubDialog(
                dialog, context.resources.displayMetrics.density, "[SettingsDebugActions]",
                maxWidthDp = 360f, horizontalMarginDp = 28f,
            )
        } catch (t: Throwable) {
            Toast.makeText(context, UiText.Settings.RESET_MODULE_SETTINGS_FAILED, Toast.LENGTH_SHORT).show()
            XposedCompat.logW("[SettingsDebugActions] showResetModuleSettingsConfirmDialog failed: ${t.message}")
        }
    }

    private fun buildDexKitStatusDetailsText(
        context: Context,
        statuses: List<SettingsDexKitState.TargetStatusView>,
        summary: String,
    ): String {
        return buildString {
            append(UiText.Settings.DEXKIT_STATUS_TITLE)
            append('\n')
            append(UiText.Settings.DEXKIT_STATUS_SUMMARY_PREFIX)
            append(": ")
            append(summary)
            statuses.forEachIndexed { index, item ->
                append("\n\n")
                append(buildDexKitStatusLogEntry(context, item, index))
            }
        }
    }

    private fun buildDexKitStatusLogEntry(
        context: Context,
        item: SettingsDexKitState.TargetStatusView,
        index: Int,
    ): String {
        val updatedAt = formatDexKitStatusUpdatedAt(item.updatedAt)
        val featureLabel = dexKitFeatureLabel(context, item)
        return buildString {
            append('[').append(updatedAt).append("] DexKit target #").append(index + 1).append('\n')
            append("id=").append(item.id).append('\n')
            append("功能=").append(featureLabel).append('\n')
            append("目标=").append(item.target).append('\n')
            append("状态=").append(stateTextFor(item.state)).append(" (").append(item.state).append(")").append('\n')
            append("记录=").append(item.detail?.takeIf { it.isNotBlank() } ?: "-")
        }
    }

    private fun dexKitFeatureLabel(
        context: Context,
        item: SettingsDexKitState.TargetStatusView,
    ): String {
        val featureKey = item.featureKey ?: return item.feature
        val fallbackLabel = if (featureKey == SettingsUserState.KEY_FOLLOW_SYSTEM_NIGHT_MODE) {
            UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_LABEL
        } else item.feature
        return runCatching {
            val hostId = SettingsRuntimeSession.create(context).hostId
            SettingsHostTextCatalog.forHostId(hostId)
                .text(featureKey, fallbackLabel, null)
                .label
        }.getOrDefault(fallbackLabel)
    }

    private fun copyDexKitStatusDetails(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            XposedCompat.logW("[SettingsDebugActions] DexKit details copy failed: clipboard unavailable")
            return
        }
        clipboard.setPrimaryClip(
            ClipData.newPlainText(UiText.Settings.DEXKIT_STATUS_DETAILS_TITLE, text)
        )
        Toast.makeText(context, UiText.Settings.DEXKIT_STATUS_COPIED, Toast.LENGTH_SHORT).show()
    }

    private fun stateTextFor(state: String): String {
        return when (state) {
            "success" -> UiText.Settings.DEXKIT_STATUS_SUCCESS
            "not_found" -> UiText.Settings.DEXKIT_STATUS_NOT_FOUND
            "error" -> UiText.Settings.DEXKIT_STATUS_ERROR
            "scanning" -> UiText.Settings.DEXKIT_STATUS_SCANNING
            else -> UiText.Settings.DEXKIT_STATUS_PENDING
        }
    }

    private fun formatDexKitStatusUpdatedAt(updatedAt: Long): String {
        if (updatedAt <= 0L) return "-"
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(updatedAt))
        }.getOrDefault(updatedAt.toString())
    }
}
