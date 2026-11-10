package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.config.ModuleSettingsBackup
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook
import com.xiyunmn.puredupan.hook.ui.UiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ModuleSettingsBackupActions {
    private const val REQUEST_IMPORT_SETTINGS = 0x4D34
    private const val REQUEST_EXPORT_SETTINGS = 0x4D35
    private val mainHandler = Handler(Looper.getMainLooper())
    private var importedListener: (() -> Unit)? = null

    fun bindImportedListener(listener: () -> Unit): () -> Unit {
        importedListener = listener
        return { if (importedListener === listener) importedListener = null }
    }

    fun launchPicker(context: Context, importing: Boolean) {
        val requestCode = if (importing) REQUEST_IMPORT_SETTINGS else REQUEST_EXPORT_SETTINGS
        try {
            val activity = context as? Activity ?: error("settings context is not Activity")
            val intent = Intent(if (importing) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                addFlags(if (importing) Intent.FLAG_GRANT_READ_URI_PERMISSION else Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (importing) {
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"))
                } else {
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    putExtra(Intent.EXTRA_TITLE, "PureDuPan-settings-${context.packageName}-$timestamp.json")
                }
            }
            if (!ModuleLifetime.beginPicker(activity, requestCode)) return
            val preferred = SettingsMenuHook.resolvePreferredSystemDocumentPicker(context, intent)
            try {
                activity.startActivityForResult(Intent(intent).apply { component = preferred }, requestCode)
            } catch (e: Exception) {
                if (preferred == null) throw e
                XposedCompat.logW("[ModuleSettingsBackup] preferred picker failed: ${preferred.flattenToShortString()} ${e.message}")
                activity.startActivityForResult(intent, requestCode)
            }
        } catch (e: Exception) {
            ModuleLifetime.finishPicker(context as? Activity, requestCode)
            XposedCompat.logW("[ModuleSettingsBackup] picker failed: ${e.message}")
            Toast.makeText(context, UiText.Settings.EXPORT_LOGS_PICK_FAILED, Toast.LENGTH_SHORT).show()
        }
    }

    fun handleResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_IMPORT_SETTINGS && requestCode != REQUEST_EXPORT_SETTINGS) return false
        if (activity == null || resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        val context = activity.applicationContext
        if (requestCode == REQUEST_EXPORT_SETTINGS) {
            ModuleLifetime.launchWorker("WPH-SettingsExport") {
                val message = try {
                    context.contentResolver.openOutputStream(uri, "wt").use { output ->
                        checkNotNull(output) { "无法打开目标文件" }
                        ModuleSettingsBackup.write(context, output)
                    }
                    UiText.Settings.EXPORT_SETTINGS_SUCCESS
                } catch (e: Exception) {
                    XposedCompat.logW("[ModuleSettingsBackup] export failed: ${e.message}")
                    UiText.Settings.EXPORT_SETTINGS_FAILED
                }
                ModuleLifetime.postResult(mainHandler) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
            }
        } else {
            ModuleLifetime.launchWorker("WPH-SettingsImportRead") {
                try {
                    val preview = context.contentResolver.openInputStream(uri).use { input ->
                        checkNotNull(input) { "无法读取备份文件" }
                        ModuleSettingsBackup.read(context, input)
                    }
                    ModuleLifetime.postResult(mainHandler) {
                        if (!activity.isFinishing && !activity.isDestroyed) showImportConfirmation(activity, preview)
                    }
                } catch (e: Exception) {
                    XposedCompat.logW("[ModuleSettingsBackup] import read failed: ${e.javaClass.simpleName}")
                    ModuleLifetime.postResult(mainHandler) {
                        showImportMessage(activity, UiText.Settings.IMPORT_SETTINGS_INVALID)
                    }
                }
            }
        }
        return true
    }

    private fun showImportConfirmation(activity: Activity, preview: ModuleSettingsBackup.ImportPreview) {
        val builder = AlertDialog.Builder(activity, SettingsDialogWindows.themeFor(activity))
            .setTitle(UiText.Settings.IMPORT_SETTINGS_LABEL)
            .setMessage(UiText.Settings.settingsImportSummary(
                preview.sourcePackage, preview.keys.size, preview.skippedCount,
            ))
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
        if (preview.keys.isNotEmpty()) {
            builder.setPositiveButton(UiText.Settings.BUTTON_CONFIRM) { _, _ -> restore(activity, preview) }
        }
        SettingsDialogWindows.showStableSubDialog(
            builder.create(), activity.resources.displayMetrics.density, "[ModuleSettingsBackup]",
        )
    }

    private fun restore(activity: Activity, preview: ModuleSettingsBackup.ImportPreview) {
        val context = activity.applicationContext
        ModuleLifetime.launchWorker("WPH-SettingsImportSave") {
            val success = try {
                ModuleSettingsBackup.restore(context, preview)
            } catch (e: Exception) {
                XposedCompat.logW("[ModuleSettingsBackup] import save failed: ${e.message}")
                false
            }
            val needsDirectory = success && runCatching { needsDownloadDirectory(context) }.getOrDefault(true)
            ModuleLifetime.postResult(mainHandler) {
                if (success) importedListener?.invoke()
                if (needsDirectory) {
                    showImportMessage(
                        activity,
                        "设置已导入。请在「存储路径重定向」重新授权下载目录，再重启宿主。",
                    )
                } else {
                    val message = if (success) UiText.Settings.IMPORT_SETTINGS_SUCCESS
                        else UiText.Settings.IMPORT_SETTINGS_FAILED
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showImportMessage(activity: Activity, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = AlertDialog.Builder(activity, SettingsDialogWindows.themeFor(activity))
            .setTitle(UiText.Settings.IMPORT_SETTINGS_LABEL)
            .setMessage(message)
            .setPositiveButton(UiText.Settings.DONE, null)
            .create()
        SettingsDialogWindows.showStableSubDialog(
            dialog, activity.resources.displayMetrics.density, "[ModuleSettingsBackup]",
        )
    }

    private fun needsDownloadDirectory(context: Context): Boolean {
        val prefs = ConfigManager.getPrefs(context)
        if (!prefs.getBoolean(FeatureKeys.KEY_STORAGE_REDIRECT_ENABLED, false) ||
            !prefs.getBoolean(FeatureKeys.KEY_STORAGE_DOWNLOAD_REDIRECT_ENABLED, false)
        ) return false
        val tree = prefs.getString(FeatureKeys.KEY_STORAGE_DOWNLOAD_TREE_URI, null) ?: return true
        return context.contentResolver.persistedUriPermissions.none {
            it.uri == Uri.parse(tree) && it.isReadPermission && it.isWritePermission
        }
    }
}
