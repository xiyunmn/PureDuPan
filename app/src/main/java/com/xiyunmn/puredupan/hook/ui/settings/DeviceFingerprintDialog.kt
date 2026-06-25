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
    private const val FIELD_CUID = "CUID"
    private const val FIELD_DEVICE_ID = "Device ID"
    private const val FIELD_ANDROID_ID = "Android ID"
    private const val FIELD_OAID = "OAID"
    private const val FIELD_HONOR_OAID = "HONOR OAID"

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

    @SuppressLint("HardwareIds")
    internal fun sensitiveSummaryLines(context: Context): List<String> {
        val summary = buildSensitiveSummary(context)
        return summary.map { (label, value) -> "$label: $value" }
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
        val fields = linkedMapOf<String, Any?>()
        val summary = buildSensitiveSummary(context)
        if (summary.isNotEmpty()) {
            fields["deviceFingerprint"] = summary
        }
        fields["currentDeviceInfo"] = buildCurrentDeviceInfo(context)
        val hostFingerprint = SettingsHostState.deviceFingerprintFor(context)
        val hostDetails = hostFingerprint
            .filterKeys { key -> key != "deviceFingerprint" }
        if (hostDetails.isNotEmpty()) {
            fields["hostDeviceFingerprintDetails"] = hostDetails
        }
        return fields
    }

    @SuppressLint("HardwareIds")
    private fun buildSensitiveSummary(context: Context): LinkedHashMap<String, String> {
        val hostFingerprint = runCatching {
            SettingsHostState.deviceFingerprintFor(context)
        }.getOrDefault(emptyMap())
        val hostDeviceFingerprint = mapValue(hostFingerprint, "deviceFingerprint")
            ?: mapValue(hostFingerprint, "baiduDeviceInfo")
            ?: emptyMap()
        val androidId = firstDisplayString(
            hostDeviceFingerprint[FIELD_ANDROID_ID],
            hostDeviceFingerprint["Host Android ID"],
            hostDeviceFingerprint["systemAndroidId"],
            runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull(),
        )
        val cuid = firstDisplayString(
            hostDeviceFingerprint[FIELD_CUID],
            hostDeviceFingerprint["cuid"],
            hostDeviceFingerprint[FIELD_DEVICE_ID],
            hostDeviceFingerprint["device_id"],
        )
        val oaid = firstOaidDisplayString(
            hostDeviceFingerprint[FIELD_OAID],
            hostDeviceFingerprint["appCommonOaid"],
            hostDeviceFingerprint[FIELD_HONOR_OAID],
            hostDeviceFingerprint["appCommonHonorOaid"],
        )

        return linkedMapOf<String, String>().apply {
            putIfPresent(FIELD_CUID, cuid)
            putIfPresent(FIELD_ANDROID_ID, androidId)
            putIfPresent(FIELD_OAID, oaid)
        }
    }

    @SuppressLint("HardwareIds")
    private fun buildCurrentDeviceInfo(context: Context): Map<String, Any?> {
        return linkedMapOf(
            FIELD_ANDROID_ID to runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrElse(::unavailable),
            "Android Release" to Build.VERSION.RELEASE.orUnknown(),
            "Android SDK" to Build.VERSION.SDK_INT,
            "Security Patch" to Build.VERSION.SECURITY_PATCH.orUnknown(),
            "Build Fingerprint" to Build.FINGERPRINT.orUnknown(),
            "Brand" to Build.BRAND.orUnknown(),
            "Manufacturer" to Build.MANUFACTURER.orUnknown(),
            "Model" to Build.MODEL.orUnknown(),
            "Device" to Build.DEVICE.orUnknown(),
            "Product" to Build.PRODUCT.orUnknown(),
            "Hardware" to Build.HARDWARE.orUnknown(),
            "Supported ABIs" to Build.SUPPORTED_ABIS.toList(),
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
        val entries = fields.entries.mapNotNull { (key, value) ->
            val keyText = key.toString()
            sanitizeJsonValue(value, keyText)?.let { keyText to it }
        }
        if (entries.isEmpty()) return "{}"
        val currentIndent = " ".repeat(indent)
        val childIndent = " ".repeat(indent + 2)
        return entries.joinToString(
            separator = ",\n",
            prefix = "{\n",
            postfix = "\n$currentIndent}",
        ) { (key, value) ->
            "$childIndent${JSONObject.quote(key)}: ${formatJsonValue(value, indent + 2)}"
        }
    }

    private fun formatJsonArray(values: Iterable<*>, indent: Int): String {
        val list = values.mapNotNull { value -> sanitizeJsonValue(value) }
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

    private fun sanitizeJsonValue(value: Any?, key: String? = null): Any? {
        return when (value) {
            null -> null
            is Map<*, *> -> value.entries.mapNotNull { (key, itemValue) ->
                val keyText = key.toString()
                sanitizeJsonValue(itemValue, keyText)?.let { keyText to it }
            }.toMap(LinkedHashMap()).takeIf { it.isNotEmpty() }
            is Iterable<*> -> value.mapNotNull { item -> sanitizeJsonValue(item) }.takeIf { it.isNotEmpty() }
            is Array<*> -> value.mapNotNull { item -> sanitizeJsonValue(item) }.takeIf { it.isNotEmpty() }
            is String -> value.takeIf { isDisplayStringForKey(key, it) }
            else -> value
        }
    }

    private fun mapValue(map: Map<*, *>, key: String): Map<*, *>? {
        return map[key] as? Map<*, *>
    }

    private fun firstDisplayString(vararg values: Any?): String? {
        return values.firstNotNullOfOrNull { value ->
            value?.toString()?.trim()?.takeIf(::isDisplayString)
        }
    }

    private fun firstOaidDisplayString(vararg values: Any?): String? {
        return values.firstNotNullOfOrNull { value ->
            value?.toString()?.trim()?.takeIf { isDisplayStringForKey(FIELD_OAID, it) }
        }
    }

    private fun MutableMap<String, String>.putIfPresent(key: String, value: String?) {
        if (!value.isNullOrBlank()) {
            this[key] = value
        }
    }

    private fun isDisplayString(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return false
        val lower = normalized.lowercase()
        return normalized != UiText.Settings.UNKNOWN &&
            lower != "unknown" &&
            lower != "missing" &&
            lower != "not_visible_or_missing" &&
            !lower.startsWith("unavailable:") &&
            !lower.startsWith("not_collected:")
    }

    private fun isDisplayStringForKey(key: String?, value: String): Boolean {
        return isDisplayString(value) && (key?.isOaidKey() != true || isOaidDisplayString(value))
    }

    private fun String.isOaidKey(): Boolean {
        return contains("oaid", ignoreCase = true)
    }

    private fun isOaidDisplayString(value: String): Boolean {
        val normalized = value.trim()
        return normalized != "-1" && normalized != "-2"
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
