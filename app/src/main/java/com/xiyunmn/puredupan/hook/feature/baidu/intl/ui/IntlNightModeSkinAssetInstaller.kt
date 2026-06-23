package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import android.content.Context
import android.os.Environment
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.io.File

internal object IntlNightModeSkinAssetInstaller {
    private const val MODULE_ASSET_PATH = "baidu/intl/skin/dark_theme.skin"
    private const val HOST_SKIN_DIR_NAME = "skin"
    private const val HOST_DARK_SKIN_FILE_NAME = "dark_theme.skin"

    fun ensureDarkSkinAvailable(context: Context): Boolean {
        val hostContext = context.applicationContext ?: context
        val skinDir = resolveHostSkinDir(hostContext)
        val target = File(skinDir, HOST_DARK_SKIN_FILE_NAME)
        if (target.isFile && target.length() > 0L) {
            return true
        }

        return runCatching {
            if (!skinDir.exists() && !skinDir.mkdirs()) {
                XposedCompat.logW("[IntlNightModeSkinAssetInstaller] create skin dir failed: ${skinDir.absolutePath}")
                return false
            }

            val moduleContext = hostContext.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_IGNORE_SECURITY,
            )
            val temp = File(skinDir, "$HOST_DARK_SKIN_FILE_NAME.tmp")
            moduleContext.assets.open(MODULE_ASSET_PATH).use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (temp.length() <= 0L) {
                temp.delete()
                XposedCompat.logW("[IntlNightModeSkinAssetInstaller] copied skin is empty")
                return false
            }
            if (target.exists() && !target.delete()) {
                temp.delete()
                XposedCompat.logW("[IntlNightModeSkinAssetInstaller] replace skin failed: ${target.absolutePath}")
                return false
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                XposedCompat.logW("[IntlNightModeSkinAssetInstaller] rename skin failed: ${target.absolutePath}")
                return false
            }

            XposedCompat.logD("[IntlNightModeSkinAssetInstaller] dark skin installed: ${target.absolutePath}")
            true
        }.getOrElse { t ->
            XposedCompat.logW("[IntlNightModeSkinAssetInstaller] install failed: ${t.message}")
            false
        }
    }

    private fun resolveHostSkinDir(context: Context): File {
        val cacheRoot = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            context.externalCacheDir?.takeIf { it.exists() || it.mkdirs() } ?: context.cacheDir
        } else {
            context.cacheDir
        }
        return File(cacheRoot, HOST_SKIN_DIR_NAME)
    }
}
