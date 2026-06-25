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
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.runtime.FrameworkRuntimeInfo
import com.xiyunmn.puredupan.hook.settings.registry.SettingsHostState
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText
import org.json.JSONObject

internal object DeviceFingerprintDialog {
    private const val LOG_TAG = "[DeviceFingerprintDialog]"

    fun show(
        context: Context,
        allowHiddenDeviceFingerprint: Boolean,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val tokens = UiStyle.tokens(context)
            val contentPadding = (16 * density).toInt()
            val publicJson = buildPublicDeviceFingerprintJson(context)
            var currentJson = publicJson
            var privateJson: String? = null
            var privateInfoVisible = false
            val privacyWarning = TextView(context).apply {
                text = UiText.Settings.DEVICE_FINGERPRINT_PRIVACY_WARNING
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.warning)
                includeFontPadding = false
                visibility = View.GONE
                setPadding(contentPadding, contentPadding, contentPadding, 0)
            }
            val content = TextView(context).apply {
                text = publicJson
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
            val contentRoot = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(privacyWarning)
                addView(
                    scrollView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }

            val builder = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.DEVICE_FINGERPRINT_DIALOG_TITLE)
                .setView(contentRoot)
                .setNegativeButton(UiText.Settings.DEVICE_FINGERPRINT_COPY, null)
                .setPositiveButton(android.R.string.ok, null)
            if (allowHiddenDeviceFingerprint) {
                builder.setNeutralButton(UiText.Settings.DEVICE_FINGERPRINT_SHOW_HIDDEN, null)
            }
            val dialog = builder.show()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                copyToClipboard(context, currentJson)
            }
            if (allowHiddenDeviceFingerprint) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { button ->
                    try {
                        privateInfoVisible = !privateInfoVisible
                        if (privateInfoVisible) {
                            val expandedJson = privateJson ?: buildExpandedDeviceFingerprintJson(context)
                                .also { privateJson = it }
                            currentJson = expandedJson
                            content.text = expandedJson
                            privacyWarning.visibility = View.VISIBLE
                            (button as? TextView)?.text = UiText.Settings.DEVICE_FINGERPRINT_HIDE_HIDDEN
                        } else {
                            currentJson = publicJson
                            content.text = publicJson
                            privacyWarning.visibility = View.GONE
                            (button as? TextView)?.text = UiText.Settings.DEVICE_FINGERPRINT_SHOW_HIDDEN
                        }
                    } catch (t: Throwable) {
                        Toast.makeText(
                            context,
                            UiText.Settings.DEVICE_FINGERPRINT_SHOW_FAILED,
                            Toast.LENGTH_SHORT,
                        ).show()
                        XposedCompat.logW("$LOG_TAG show hidden info failed: ${t.message}")
                    }
                }
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

    private fun buildPublicDeviceFingerprintJson(context: Context): String {
        return formatJsonObject(buildPublicDeviceFingerprintFields(context))
    }

    private fun buildExpandedDeviceFingerprintJson(context: Context): String {
        val fields = buildPublicDeviceFingerprintFields(context)
        fields["hiddenDeviceFingerprint"] = buildHiddenDeviceFingerprint(context)
        return formatJsonObject(fields)
    }

    private fun buildPublicDeviceFingerprintFields(context: Context): LinkedHashMap<String, Any?> {
        return linkedMapOf(
            "runtimeEnvironment" to buildRuntimeEnvironment(context),
        )
    }

    private fun buildHiddenDeviceFingerprint(context: Context): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>(
            "currentDeviceInfo" to buildCurrentDeviceInfo(context),
        )
        val hostFingerprint = SettingsHostState.deviceFingerprintFor(context)
        if (hostFingerprint.isNotEmpty()) {
            fields["hostDeviceFingerprint"] = hostFingerprint
        }
        return fields
    }

    @SuppressLint("HardwareIds")
    private fun buildCurrentDeviceInfo(context: Context): Map<String, Any?> {
        return linkedMapOf(
            "systemAndroidId" to runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrElse(::unavailable),
            "androidRelease" to Build.VERSION.RELEASE.orUnknown(),
            "androidSdk" to Build.VERSION.SDK_INT,
            "securityPatch" to Build.VERSION.SECURITY_PATCH.orUnknown(),
            "buildFingerprint" to Build.FINGERPRINT.orUnknown(),
            "buildBrand" to Build.BRAND.orUnknown(),
            "buildManufacturer" to Build.MANUFACTURER.orUnknown(),
            "buildModel" to Build.MODEL.orUnknown(),
            "buildDevice" to Build.DEVICE.orUnknown(),
            "buildProduct" to Build.PRODUCT.orUnknown(),
            "buildHardware" to Build.HARDWARE.orUnknown(),
            "supportedAbis" to Build.SUPPORTED_ABIS.toList(),
        )
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
            "frameworkEnvironment" to FrameworkRuntimeInfo.collect(context),
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

    private fun unavailable(t: Throwable): String {
        val name = t::class.java.simpleName.ifBlank { "Throwable" }
        val message = t.message?.takeIf { it.isNotBlank() }
        return if (message == null) {
            "unavailable:$name"
        } else {
            "unavailable:$name:$message"
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
    }
}
