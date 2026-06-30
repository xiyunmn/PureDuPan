package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsDexKitState
import com.xiyunmn.puredupan.hook.settings.registry.SettingsHostState
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object SettingsDebugActions {
    private const val DEXKIT_STATUS_REFRESH_MS = 1000L

    fun executeDexKitScanAndShowStatus(context: Context) {
        val started = SettingsDexKitState.triggerFullScanFromSettings(context)
        Toast.makeText(
            context,
            if (started) UiText.Settings.DEXKIT_SCAN_STARTED else UiText.Settings.DEXKIT_SCAN_PENDING,
            Toast.LENGTH_SHORT,
        ).show()
        showDexKitStatusDialog(context)
    }

    fun showDexKitStatusDialog(context: Context) {
        try {
            if (!SettingsHostState.showDexKitStatusInSettings(context)) {
                XposedCompat.logW("[SettingsDebugActions] DexKit status skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding / 2)
            }
            renderDexKitStatusDialogContent(context, root, padding)

            val scrollView = ScrollView(context).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }

            val refreshHandler = Handler(Looper.getMainLooper())
            lateinit var dialog: AlertDialog
            val refreshRunnable = object : Runnable {
                override fun run() {
                    if (!dialog.isShowing) return
                    renderDexKitStatusDialogContent(context, root, padding)
                    if (shouldContinueDexKitStatusRefresh(context)) {
                        refreshHandler.postDelayed(this, DEXKIT_STATUS_REFRESH_MS)
                    }
                }
            }

            dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.DEXKIT_STATUS_TITLE)
                .setView(scrollView)
                .setNegativeButton(UiText.Settings.DEXKIT_STATUS_VIEW_DETAILS, null)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                showDexKitStatusDetailsDialog(
                    context = context,
                    statuses = SettingsDexKitState.statusViews(context),
                    summary = SettingsDexKitState.summaryText(context),
                )
            }
            dialog.setOnDismissListener {
                refreshHandler.removeCallbacks(refreshRunnable)
            }
            if (shouldContinueDexKitStatusRefresh(context)) {
                refreshHandler.postDelayed(refreshRunnable, DEXKIT_STATUS_REFRESH_MS)
            }
            dialog.window
                ?.let { window ->
                    SettingsDialogWindows.applyCardStyle(
                        window = window,
                        density = window.context.resources.displayMetrics.density,
                        maxWidthDp = 360f,
                        horizontalMarginDp = 28f,
                    )
                }
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsDebugActions] showDexKitStatusDialog failed: ${t.message}")
        }
    }

    private fun shouldContinueDexKitStatusRefresh(context: Context): Boolean {
        return SettingsDexKitState.shouldContinueStatusRefresh(context)
    }

    private fun renderDexKitStatusDialogContent(
        context: Context,
        root: LinearLayout,
        padding: Int,
    ) {
        val density = context.resources.displayMetrics.density
        val tokens = UiStyle.tokens(context)
        val statuses = SettingsDexKitState.statusViews(context)
        val summary = SettingsDexKitState.summaryText(context)

        root.removeAllViews()
        root.addView(TextView(context).apply {
            text = "${UiText.Settings.DEXKIT_STATUS_SUMMARY_PREFIX}: $summary"
            textSize = 14f
            setTextColor(tokens.accent)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(0, 0, 0, (10 * density).toInt())
        })

        statuses.forEachIndexed { index, item ->
            if (index > 0) {
                root.addView(View(context).apply {
                    setBackgroundColor(tokens.divider)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1,
                    ).apply {
                        setMargins(0, (10 * density).toInt(), 0, (10 * density).toInt())
                    }
                })
            }
            root.addView(createDexKitStatusRow(context, item))
        }
    }

    private fun showDexKitStatusDetailsDialog(
        context: Context,
        statuses: List<SettingsDexKitState.TargetStatusView>,
        summary: String,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()
            val tokens = UiStyle.tokens(context)
            val copyText = buildDexKitStatusDetailsText(statuses, summary)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding / 2)
            }
            root.addView(TextView(context).apply {
                text = "${UiText.Settings.DEXKIT_STATUS_SUMMARY_PREFIX}: $summary"
                textSize = 14f
                setTextColor(tokens.accent)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setPadding(0, 0, 0, (10 * density).toInt())
            })

            statuses.forEachIndexed { index, item ->
                if (index > 0) {
                    root.addView(SettingsDialogLayout.createDivider(context, padding))
                }
                root.addView(createDexKitStatusDetailLogRow(context, item, index))
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.DEXKIT_STATUS_DETAILS_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.DEXKIT_STATUS_COPY, null)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                copyDexKitStatusDetails(context, copyText)
            }
            dialog.window
                ?.let { window ->
                    SettingsDialogWindows.applyCardStyle(
                        window = window,
                        density = window.context.resources.displayMetrics.density,
                        maxWidthDp = 420f,
                        horizontalMarginDp = 24f,
                    )
                }
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsDebugActions] show DexKit detail dialog failed: ${t.message}")
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
                        "${UiText.Settings.CLEAR_LOGS_FAILED}：${result.failedCount}"
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
                .show()
            val density = context.resources.displayMetrics.density
            dialog.window
                ?.let { window ->
                    SettingsDialogWindows.applyCardStyle(
                        window = window,
                        density = density,
                        maxWidthDp = 360f,
                        horizontalMarginDp = 28f,
                    )
                }
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
                .show()
            val density = context.resources.displayMetrics.density
            dialog.window
                ?.let { window ->
                    SettingsDialogWindows.applyCardStyle(
                        window = window,
                        density = density,
                        maxWidthDp = 360f,
                        horizontalMarginDp = 28f,
                    )
                }
        } catch (t: Throwable) {
            Toast.makeText(context, UiText.Settings.RESET_MODULE_SETTINGS_FAILED, Toast.LENGTH_SHORT).show()
            XposedCompat.logW("[SettingsDebugActions] showResetModuleSettingsConfirmDialog failed: ${t.message}")
        }
    }

    private fun createDexKitStatusRow(
        context: Context,
        item: SettingsDexKitState.TargetStatusView,
    ): View {
        val density = context.resources.displayMetrics.density
        val tokens = UiStyle.tokens(context)
        val stateText = when (item.state) {
            "success" -> UiText.Settings.DEXKIT_STATUS_SUCCESS
            "not_found" -> UiText.Settings.DEXKIT_STATUS_NOT_FOUND
            "error" -> UiText.Settings.DEXKIT_STATUS_ERROR
            "scanning" -> UiText.Settings.DEXKIT_STATUS_SCANNING
            else -> UiText.Settings.DEXKIT_STATUS_PENDING
        }
        val stateColor = when (item.state) {
            "success" -> tokens.accent
            "error", "not_found" -> 0xFFE66A5E.toInt()
            "scanning" -> 0xFFF0A03A.toInt()
            else -> tokens.textMuted
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = item.id
                    textSize = 14f
                    setTextColor(tokens.textPrimary)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = stateText
                    textSize = 13f
                    setTextColor(stateColor)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.END
                    includeFontPadding = false
                })
            })
            addView(TextView(context).apply {
                text = "${UiText.Settings.DEXKIT_STATUS_TARGET}: ${item.target}"
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                includeFontPadding = false
                setPadding(0, (6 * density).toInt(), 0, 0)
            })
            addView(TextView(context).apply {
                text = "${UiText.Settings.DEXKIT_STATUS_FEATURE}: ${item.feature}"
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                includeFontPadding = false
                setPadding(0, (3 * density).toInt(), 0, 0)
            })
            if (!item.detail.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = item.detail
                    textSize = 12f
                    setTextColor(tokens.textMuted)
                    includeFontPadding = false
                    setPadding(0, (3 * density).toInt(), 0, 0)
                })
            }
        }
    }

    private fun createDexKitStatusDetailLogRow(
        context: Context,
        item: SettingsDexKitState.TargetStatusView,
        index: Int,
    ): View {
        val tokens = UiStyle.tokens(context)
        val failed = isDexKitFailure(item)
        return TextView(context).apply {
            text = buildDexKitStatusLogEntry(item, index)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(if (failed) tokens.danger else tokens.textPrimary)
            includeFontPadding = true
            setTextIsSelectable(true)
        }
    }

    private fun buildDexKitStatusDetailsText(
        statuses: List<SettingsDexKitState.TargetStatusView>,
        summary: String,
    ): String {
        return buildString {
            append(UiText.Settings.DEXKIT_STATUS_DETAILS_TITLE)
            append('\n')
            append(UiText.Settings.DEXKIT_STATUS_SUMMARY_PREFIX)
            append(": ")
            append(summary)
            statuses.forEachIndexed { index, item ->
                append("\n\n")
                append(buildDexKitStatusLogEntry(item, index))
            }
        }
    }

    private fun buildDexKitStatusLogEntry(
        item: SettingsDexKitState.TargetStatusView,
        index: Int,
    ): String {
        val updatedAt = formatDexKitStatusUpdatedAt(item.updatedAt)
        return buildString {
            append('[').append(updatedAt).append("] DexKit target #").append(index + 1).append('\n')
            append("id=").append(item.id).append('\n')
            append("feature=").append(item.feature).append('\n')
            append("target=").append(item.target).append('\n')
            append("state=").append(stateTextFor(item.state)).append(" (").append(item.state).append(")").append('\n')
            append("detail=").append(item.detail?.takeIf { it.isNotBlank() } ?: "-")
        }
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

    private fun isDexKitFailure(item: SettingsDexKitState.TargetStatusView): Boolean {
        return item.state == "error" || item.state == "not_found"
    }

    private fun formatDexKitStatusUpdatedAt(updatedAt: Long): String {
        if (updatedAt <= 0L) return "-"
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(updatedAt))
        }.getOrDefault(updatedAt.toString())
    }
}
