package com.xiyunmn.puredupan.hook.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsHostState
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.settings.MemberCardBackgroundSelectionState
import com.xiyunmn.puredupan.hook.ui.settings.MemberCardBackgroundEditorDialog
import com.xiyunmn.puredupan.hook.ui.settings.SettingsMainDialog
import com.xiyunmn.puredupan.hook.ui.settings.StorageTreeSelectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

internal const val REQUEST_MEMBER_CARD_BACKGROUND_IMAGE = 0x4D31
internal const val REQUEST_STORAGE_DOWNLOAD_TREE = 0x4D32
internal const val REQUEST_EXPORT_LOGS = 0x4D33

object SettingsMenuHook {
    internal fun launchLogExportPicker(context: Context) {
        try {
            if (!XposedCompat.hasLogFiles(context)) {
                Toast.makeText(context, UiText.Settings.EXPORT_LOGS_EMPTY, Toast.LENGTH_LONG).show()
                return
            }
            val activity = context as? Activity ?: error("settings context is not Activity")
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "PureDuPan-${context.packageName}-$timestamp.zip")
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            val preferred = resolvePreferredSystemDocumentPicker(context, intent)
            try {
                activity.startActivityForResult(Intent(intent).apply { component = preferred }, REQUEST_EXPORT_LOGS)
                XposedCompat.logD(
                    "[SettingsMenuHook] log export picker launched: " +
                        (preferred?.flattenToShortString() ?: "implicit-system"),
                )
            } catch (e: Exception) {
                if (preferred == null) throw e
                XposedCompat.logW(
                    "[SettingsMenuHook] preferred log export picker failed: " +
                        "${preferred.flattenToShortString()} ${e.message}",
                )
                activity.startActivityForResult(intent, REQUEST_EXPORT_LOGS)
            }
        } catch (e: Exception) {
            XposedCompat.logW("[SettingsMenuHook] launch log export picker failed: ${e.message}")
            Toast.makeText(context, UiText.Settings.EXPORT_LOGS_PICK_FAILED, Toast.LENGTH_SHORT).show()
        }
    }

    internal fun handleLogExportResult(
        context: Context?,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_EXPORT_LOGS) return false
        if (context == null || resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        val appContext = context.applicationContext ?: context
        Toast.makeText(appContext, UiText.Settings.EXPORT_LOGS_STARTED, Toast.LENGTH_SHORT).show()
        thread(name = "WPH-LogExport") {
            val message = try {
                appContext.contentResolver.openOutputStream(uri, "wt").use { output ->
                    checkNotNull(output) { "Cannot open export destination" }
                    XposedCompat.exportLogFiles(appContext, output)
                }
                UiText.Settings.EXPORT_LOGS_SUCCESS
            } catch (e: Exception) {
                XposedCompat.logW("[SettingsMenuHook] export logs failed: ${e.message}")
                UiText.Settings.EXPORT_LOGS_FAILED
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    internal fun launchMemberCardBackgroundPicker(context: Context) {
        try {
            if (!canUseMemberCardBackground(context)) {
                XposedCompat.logW("[SettingsMenuHook] launch background picker skipped: unsupported host")
                Toast.makeText(context, UiText.Settings.MEMBER_CARD_BACKGROUND_PICK_FAILED, Toast.LENGTH_SHORT).show()
                return
            }
            val activity = context as? Activity ?: run {
                Toast.makeText(context, UiText.Settings.MEMBER_CARD_BACKGROUND_PICK_FAILED, Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            activity.startActivityForResult(intent, REQUEST_MEMBER_CARD_BACKGROUND_IMAGE)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] launch background picker failed: ${t.message}")
            Toast.makeText(context, UiText.Settings.MEMBER_CARD_BACKGROUND_PICK_FAILED, Toast.LENGTH_SHORT).show()
        }
    }

    internal fun handleMemberCardBackgroundImageResult(
        context: Context?,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_MEMBER_CARD_BACKGROUND_IMAGE) return false
        if (context == null || resultCode != Activity.RESULT_OK) return true

        if (!canUseMemberCardBackground(context)) {
            XposedCompat.logW("[SettingsMenuHook] background picker result ignored: unsupported host")
            return true
        }

        val uri = data?.data ?: return true
        try {
            if ((data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        } catch (t: Throwable) {
            XposedCompat.logD("[SettingsMenuHook] persist image uri permission failed: ${t.message}")
        }

        val editor = SettingsUserState.getPrefs(context).edit()
            .putBoolean(SettingsUserState.KEY_MEMBER_CARD_CUSTOMIZE, true)
            .putBoolean(SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND, true)
            .putString(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_URI, uri.toString())
        if (isFeatureVisible(context, SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT)) {
            editor.putInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT, 100)
        }
        if (isFeatureVisible(context, SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES)) {
            editor.putInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES, 0)
        }
        if (isFeatureVisible(context, SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE)) {
            editor.putInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE, 0)
        }
        if (isFeatureVisible(context, SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE)) {
            editor.putInt(SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE, 0)
        }
        editor.apply()

        MemberCardBackgroundSelectionState.notifySelected(uri.toString())
        MemberCardBackgroundEditorDialog.show(context, uri.toString())
        Toast.makeText(
            context,
            UiText.Settings.withRestartHint(UiText.Settings.MEMBER_CARD_BACKGROUND_PICKED),
            Toast.LENGTH_SHORT,
        ).show()
        return true
    }

    internal fun launchStorageTreePicker(context: Context) {
        try {
            val activity = context as? Activity ?: throw IllegalStateException("settings context is not Activity")
            val baseIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
                SettingsUserState.getPrefs(context)
                    .getString(SettingsUserState.KEY_STORAGE_DOWNLOAD_TREE_URI, null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { stored ->
                        runCatching { putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(stored)) }
                    }
            }
            val preferredComponent = resolvePreferredSystemDocumentPicker(context, baseIntent)
            val preferredIntent = Intent(baseIntent).apply { component = preferredComponent }
            try {
                activity.startActivityForResult(preferredIntent, REQUEST_STORAGE_DOWNLOAD_TREE)
                XposedCompat.logD(
                    "[SettingsMenuHook] storage tree picker launched: " +
                        (preferredComponent?.flattenToShortString() ?: "implicit-system"),
                )
            } catch (preferredFailure: Throwable) {
                if (preferredComponent == null) throw preferredFailure
                XposedCompat.logW(
                    "[SettingsMenuHook] preferred storage picker failed, fallback implicit: " +
                        "${preferredComponent.flattenToShortString()} ${preferredFailure.message}",
                )
                activity.startActivityForResult(Intent(baseIntent).apply { component = null }, REQUEST_STORAGE_DOWNLOAD_TREE)
            }
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] launch storage picker failed: ${t.message}")
            Toast.makeText(context, UiText.Settings.STORAGE_DIRECTORY_PICK_FAILED, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvePreferredSystemDocumentPicker(context: Context, intent: Intent): ComponentName? {
        val packageManager = context.packageManager
        val vendorTokens = deviceVendorTokens()
        // OEM file managers can be installed as ordinary apps, without FLAG_SYSTEM.
        val handlers = runCatching {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList()).filter { info ->
            val activity = info.activityInfo ?: return@filter false
            val application = activity.applicationInfo ?: return@filter false
            val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            activity.enabled && activity.exported && application.enabled &&
                activity.packageName != context.packageName && !isFrameworkResolver(info) &&
                ((application.flags and systemFlags) != 0 || isVendorDocumentHandler(info, vendorTokens))
        }
        if (handlers.isEmpty()) return null

        val resolved = runCatching {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()?.let { default ->
            handlers.firstOrNull { it.toComponentName() == default.toComponentName() }
        }
        val preferred = handlers.maxWithOrNull(
            compareBy<ResolveInfo> { documentPickerScore(it, vendorTokens) }
                .thenBy { it.priority }
                .thenBy { it.preferredOrder },
        ) ?: return null
        if (resolved != null && documentPickerScore(resolved, vendorTokens) >= documentPickerScore(preferred, vendorTokens)) {
            return resolved.toComponentName()
        }
        return preferred.toComponentName()
    }

    private fun isFrameworkResolver(info: ResolveInfo): Boolean {
        val activity = info.activityInfo ?: return true
        return activity.packageName == "android" ||
            activity.name.contains("ResolverActivity", ignoreCase = true) ||
            activity.name.contains("ChooserActivity", ignoreCase = true)
    }

    private fun ResolveInfo.toComponentName(): ComponentName? {
        val activity = activityInfo ?: return null
        return ComponentName(activity.packageName, activity.name)
    }

    private fun isVendorDocumentHandler(info: ResolveInfo, vendorTokens: Set<String>): Boolean {
        return info.activityInfo?.packageName?.lowercase(Locale.ROOT)?.split('.')
            ?.any { it in vendorTokens } == true
    }

    private fun documentPickerScore(info: ResolveInfo, vendorTokens: Set<String>): Int {
        val activity = info.activityInfo ?: return Int.MIN_VALUE
        val standardDocumentsUi = activity.packageName.contains("documentsui", ignoreCase = true)
        return (if (isVendorDocumentHandler(info, vendorTokens)) 1_000 else 0) +
            (if (!standardDocumentsUi) 100 else 0) +
            (if (info.isDefault) 20 else 0)
    }

    private fun deviceVendorTokens(): Set<String> {
        val raw = setOf(Build.MANUFACTURER, Build.BRAND).map { it.lowercase(Locale.ROOT).trim() }.filter { it.isNotBlank() }
        return buildSet {
            addAll(raw)
            raw.forEach { vendor ->
                when {
                    vendor.contains("samsung") -> addAll(setOf("samsung", "sec"))
                    vendor.contains("xiaomi") || vendor.contains("redmi") || vendor.contains("poco") ->
                        addAll(setOf("xiaomi", "mi", "miui", "redmi", "poco"))
                    vendor.contains("huawei") || vendor.contains("honor") ->
                        addAll(setOf("huawei", "honor", "hwfilemanager"))
                    vendor.contains("oppo") || vendor.contains("realme") || vendor.contains("oneplus") ->
                        addAll(setOf("oppo", "oplus", "coloros", "realme", "oneplus"))
                    vendor.contains("vivo") || vendor.contains("iqoo") ->
                        addAll(setOf("vivo", "iqoo", "bbk"))
                    vendor.contains("motorola") || vendor.contains("lenovo") ->
                        addAll(setOf("motorola", "moto", "lenovo"))
                    vendor.contains("zte") || vendor.contains("nubia") -> addAll(setOf("zte", "nubia"))
                    vendor.contains("meizu") -> addAll(setOf("meizu", "flyme"))
                    vendor.contains("asus") -> addAll(setOf("asus", "zenui"))
                    vendor.contains("sony") -> addAll(setOf("sony", "xperia"))
                    vendor.contains("lg") -> addAll(setOf("lge", "lg"))
                    vendor.contains("tcl") -> addAll(setOf("tcl", "alcatel"))
                    vendor.contains("nothing") -> addAll(setOf("nothing", "nothingos"))
                    vendor.contains("google") -> addAll(setOf("google", "pixel", "documentsui"))
                }
            }
        }
    }

    internal fun handleStorageTreeResult(
        context: Context?,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_STORAGE_DOWNLOAD_TREE) return false
        if (context == null || resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        try {
            val flags = data.flags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            context.contentResolver.takePersistableUriPermission(uri, flags)
            SettingsUserState.getPrefs(context).edit()
                .putString(SettingsUserState.KEY_STORAGE_DOWNLOAD_TREE_URI, uri.toString())
                .apply()
            StorageTreeSelectionState.notifySelected()
            Toast.makeText(context, UiText.Settings.STORAGE_DIRECTORY_SELECTED, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] persist storage tree permission failed: ${t.message}")
            Toast.makeText(context, UiText.Settings.STORAGE_DIRECTORY_PERMISSION_INVALID, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun canUseMemberCardBackground(context: Context): Boolean {
        return isFeatureVisible(context, SettingsUserState.KEY_MEMBER_CARD_CUSTOMIZE) &&
            isFeatureVisible(context, SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND)
    }

    private fun isFeatureVisible(context: Context, featureKey: String): Boolean {
        return SettingsHostState.isFeatureVisibleForContext(context, featureKey)
    }

    internal fun showModuleSettingsDialog(
        context: Context,
        classLoader: ClassLoader?,
        initialScrollY: Int = 0,
    ) {
        if (!SettingsHostState.isSupportedHost(context)) {
            XposedCompat.logW("[SettingsMenuHook] settings dialog skipped: unsupported host=${context.packageName}")
            return
        }
        // 首次使用检查免责声明
        if (!SettingsUserState.isDisclaimerAccepted(context)) {
            showDisclaimerDialog(context) {
                SettingsUserState.setDisclaimerAccepted(context)
                showModuleSettingsDialogInternal(context, classLoader, initialScrollY)
            }
            return
        }
        showModuleSettingsDialogInternal(context, classLoader, initialScrollY)
    }

    private fun showModuleSettingsDialogInternal(
        context: Context,
        classLoader: ClassLoader?,
        initialScrollY: Int = 0,
    ) {
        SettingsMainDialog.show(
            context = context,
            initialScrollY = initialScrollY,
            onChooseMemberCardBackground = { launchMemberCardBackgroundPicker(context) },
            onReopenSettings = { scrollY ->
                showModuleSettingsDialog(context, classLoader, scrollY)
            },
            onRestartHost = {
                restartHostApp(context)
            },
        )
    }

    private fun restartHostApp(context: Context) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                )
                context.startActivity(launchIntent)
            } else {
                XposedCompat.logW("[SettingsMenuHook] restart: no launch intent for ${context.packageName}")
            }
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] restart launch failed: ${t.message}")
            XposedCompat.log(t)
            return
        }
        try {
            Runtime.getRuntime().exit(0)
        } catch (t: Throwable) {
            XposedCompat.logD("SettingsMenuHook: ${t.message}")
        }
        try {
            kotlin.system.exitProcess(0)
        } catch (t: Throwable) {
            XposedCompat.logD("SettingsMenuHook: ${t.message}")
        }
    }
}
