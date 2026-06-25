package com.xiyunmn.puredupan.hook.ui.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.Typeface
import android.os.Build
import android.os.Process
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsHostState
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText
import org.json.JSONObject

internal object DeviceFingerprintDialog {
    private const val LOG_TAG = "[DeviceFingerprintDialog]"

    fun show(context: Context) {
        try {
            val density = context.resources.displayMetrics.density
            val tokens = UiStyle.tokens(context)
            val contentPadding = (16 * density).toInt()
            val fingerprintJson = buildDeviceFingerprintJson(context)
            val content = TextView(context).apply {
                text = fingerprintJson
                textSize = 12.5f
                typeface = Typeface.MONOSPACE
                setTextColor(tokens.textPrimary)
                setTextIsSelectable(true)
                includeFontPadding = true
                setLineSpacing(1.5f * density, 1f)
                setPadding(contentPadding, contentPadding, contentPadding, contentPadding / 2)
            }
            val scrollView = ScrollView(context).apply {
                addView(content)
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.DEVICE_FINGERPRINT_DIALOG_TITLE)
                .setView(scrollView)
                .setNegativeButton(UiText.Settings.DEVICE_FINGERPRINT_COPY, null)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                copyToClipboard(context, fingerprintJson)
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
            Toast.makeText(context, UiText.Settings.DEVICE_FINGERPRINT_SHOW_FAILED, Toast.LENGTH_SHORT).show()
            XposedCompat.logW("$LOG_TAG show failed: ${t.message}")
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            XposedCompat.logW("$LOG_TAG copy failed: clipboard service unavailable")
            return
        }
        clipboard.setPrimaryClip(
            ClipData.newPlainText(UiText.Settings.DEVICE_FINGERPRINT_LABEL, text)
        )
        Toast.makeText(context, UiText.Settings.DEVICE_FINGERPRINT_COPIED, Toast.LENGTH_SHORT).show()
    }

    private fun buildDeviceFingerprintJson(context: Context): String {
        val fields = linkedMapOf<String, Any?>(
            "runtimeEnvironment" to buildRuntimeEnvironment(context),
        )
        val hostFingerprint = SettingsHostState.deviceFingerprintFor(context)
        if (hostFingerprint.isNotEmpty()) {
            fields["hostDeviceFingerprint"] = hostFingerprint
        }
        return formatJsonObject(fields)
    }

    private fun buildRuntimeEnvironment(context: Context): Map<String, Any?> {
        val hostPackageName = context.packageName
        val hostInfo = packageInfo(context, hostPackageName)
        return linkedMapOf(
            "hostPackageName" to hostPackageName,
            "hostVersionName" to hostInfo?.versionName.orUnknown(),
            "hostVersionCode" to hostInfo?.longVersionCodeCompat(),
            "modulePackageName" to BuildConfig.APPLICATION_ID,
            "moduleVersionName" to BuildConfig.VERSION_NAME,
            "moduleVersionCode" to BuildConfig.VERSION_CODE,
            "moduleDebug" to BuildConfig.DEBUG,
            "androidSdk" to Build.VERSION.SDK_INT,
            "processName" to resolveProcessName(context),
            "pid" to Process.myPid(),
            "lastRuntimeUpdateTime" to System.currentTimeMillis(),
        )
    }

    private fun packageInfo(context: Context, packageName: String): PackageInfo? {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
        }.getOrNull()
    }

    private fun resolveProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        readProcCmdline()?.takeIf { it.isNotBlank() }?.let { return it }
        return XposedCompat.currentPackageName().orUnknown().takeIf { it != UiText.Settings.UNKNOWN }
            ?: context.packageName
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun readProcCmdline(): String? {
        return runCatching {
            java.io.File("/proc/self/cmdline")
                .readText()
                .trimEnd('\u0000')
        }.getOrNull()
    }

    private fun formatJsonObject(fields: Map<*, *>, indent: Int = 0): String {
        if (fields.isEmpty()) return "{}"
        val currentIndent = " ".repeat(indent)
        val childIndent = " ".repeat(indent + 2)
        return fields.entries.joinToString(
            separator = ",\n",
            prefix = "{\n",
            postfix = "\n$currentIndent}",
        ) { (key, value) ->
            "$childIndent${JSONObject.quote(key.toString())}: ${formatJsonValue(value, indent + 2)}"
        }
    }

    private fun formatJsonArray(values: Iterable<*>, indent: Int): String {
        val list = values.toList()
        if (list.isEmpty()) return "[]"
        val currentIndent = " ".repeat(indent)
        val childIndent = " ".repeat(indent + 2)
        return list.joinToString(
            separator = ",\n",
            prefix = "[\n",
            postfix = "\n$currentIndent]",
        ) { value ->
            "$childIndent${formatJsonValue(value, indent + 2)}"
        }
    }

    private fun formatJsonValue(value: Any?, indent: Int): String {
        return when (value) {
            null -> "null"
            is Map<*, *> -> formatJsonObject(value, indent)
            is Iterable<*> -> formatJsonArray(value, indent)
            is Array<*> -> formatJsonArray(value.asList(), indent)
            is Boolean -> value.toString()
            is Number -> value.toString()
            else -> JSONObject.quote(value.toString())
        }
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: UiText.Settings.UNKNOWN

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
    }
}
