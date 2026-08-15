package com.xiyunmn.puredupan.hook.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsHostState
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.settings.MemberCardBackgroundSelectionState
import com.xiyunmn.puredupan.hook.ui.settings.MemberCardBackgroundEditorDialog
import com.xiyunmn.puredupan.hook.ui.settings.SettingsMainDialog

internal const val REQUEST_MEMBER_CARD_BACKGROUND_IMAGE = 0x4D31
internal const val REQUEST_STORAGE_DOWNLOAD_TREE = 0x4D32

object SettingsMenuHook {
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
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            }
            activity.startActivityForResult(intent, REQUEST_STORAGE_DOWNLOAD_TREE)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] launch storage picker failed: ${t.message}")
            Toast.makeText(context, UiText.Settings.STORAGE_DIRECTORY_PICK_FAILED, Toast.LENGTH_SHORT).show()
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
                .putBoolean(SettingsUserState.KEY_STORAGE_REDIRECT_ENABLED, true)
                .apply()
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
