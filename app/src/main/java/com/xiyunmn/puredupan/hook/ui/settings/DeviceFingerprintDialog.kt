package com.xiyunmn.puredupan.hook.ui.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Application
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
            val content = TextView(context).apply {
                text = buildRuntimeEnvironmentJson(context)
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

            AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.DEVICE_FINGERPRINT_DIALOG_TITLE)
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .show()
                .window
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

    private fun buildRuntimeEnvironmentJson(context: Context): String {
        val hostPackageName = context.packageName
        val hostInfo = packageInfo(context, hostPackageName)
        val fields = linkedMapOf<String, Any?>(
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
        return fields.entries.joinToString(
            separator = ",\n",
            prefix = "{\n",
            postfix = "\n}",
        ) { (key, value) ->
            "  ${JSONObject.quote(key)}: ${formatJsonValue(value)}"
        }
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

    private fun formatJsonValue(value: Any?): String {
        return when (value) {
            null -> "null"
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
