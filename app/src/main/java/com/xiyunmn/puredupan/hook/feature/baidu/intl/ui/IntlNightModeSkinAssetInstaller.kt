package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import android.content.Context
import android.os.Environment
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipFile

internal object IntlNightModeSkinAssetInstaller {
    private const val MODULE_ASSET_PATH = "baidu/intl/skin/dark_theme.skin"
    private const val MODULE_CLASSPATH_ASSET_PATH = "assets/$MODULE_ASSET_PATH"
    private const val HOST_SKIN_DIR_NAME = "skin"
    private const val HOST_DARK_SKIN_FILE_NAME = "dark_theme.skin"

    fun ensureDarkSkinAvailable(context: Context): Boolean {
        val hostContext = context.applicationContext ?: context
        val skinDir = resolveHostSkinDir(hostContext)
        val target = File(skinDir, HOST_DARK_SKIN_FILE_NAME)
        if (isUsableSkinPackage(hostContext, target)) {
            return true
        }

        return runCatching {
            if (!skinDir.exists() && !skinDir.mkdirs()) {
                XposedCompat.logW("[IntlNightModeSkinAssetInstaller] create skin dir failed: ${skinDir.absolutePath}")
                return false
            }
            if (target.exists() && !target.delete()) {
                XposedCompat.logW("[IntlNightModeSkinAssetInstaller] replace invalid skin failed: ${target.absolutePath}")
                return false
            }

            val temp = File(skinDir, "$HOST_DARK_SKIN_FILE_NAME.tmp")
            if (temp.exists() && !temp.delete()) {
                XposedCompat.logW("[IntlNightModeSkinAssetInstaller] delete stale temp failed: ${temp.absolutePath}")
                return false
            }

            if (!copyBundledSkin(hostContext, temp)) {
                return false
            }
            if (!isUsableSkinPackage(hostContext, temp)) {
                temp.delete()
                XposedCompat.logW("[IntlNightModeSkinAssetInstaller] copied skin is not a valid package")
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

    private fun copyBundledSkin(hostContext: Context, temp: File): Boolean {
        val failures = mutableListOf<String>()
        if (copyFromModulePackageContext(hostContext, temp, failures)) {
            return true
        }
        if (copyFromModuleApk(temp, failures)) {
            return true
        }
        if (copyFromModuleClassLoader(temp, failures)) {
            return true
        }

        XposedCompat.logW(
            "[IntlNightModeSkinAssetInstaller] bundled skin unavailable: " +
                failures.joinToString("; ").ifBlank { "no readable source" },
        )
        return false
    }

    private fun copyFromModulePackageContext(
        hostContext: Context,
        temp: File,
        failures: MutableList<String>,
    ): Boolean {
        return copySource(
            label = "modulePackageContext",
            temp = temp,
            failures = failures,
        ) {
            val moduleContext = hostContext.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_IGNORE_SECURITY,
            )
            moduleContext.assets.open(MODULE_ASSET_PATH)
        }
    }

    private fun copyFromModuleClassLoader(
        temp: File,
        failures: MutableList<String>,
    ): Boolean {
        val classLoaders = listOfNotNull(
            IntlNightModeSkinAssetInstaller::class.java.classLoader,
            XposedCompat::class.java.classLoader,
            XposedCompat.module?.javaClass?.classLoader,
        ).distinct()
        val assetPaths = listOf(MODULE_CLASSPATH_ASSET_PATH, MODULE_ASSET_PATH)

        for (classLoader in classLoaders) {
            for (assetPath in assetPaths) {
                if (
                    copySource(
                        label = "moduleClassLoader:${classLoader.javaClass.name}:$assetPath",
                        temp = temp,
                        failures = failures,
                    ) {
                        classLoader.getResourceAsStream(assetPath)
                            ?: throw FileNotFoundException(assetPath)
                    }
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun copyFromModuleApk(
        temp: File,
        failures: MutableList<String>,
    ): Boolean {
        val appInfo = runCatching { XposedCompat.module?.moduleApplicationInfo }
            .getOrNull()
        val apkPaths = listOfNotNull(
            appInfo?.sourceDir,
            appInfo?.publicSourceDir,
        ).filter { it.isNotBlank() }.distinct()

        for (apkPath in apkPaths) {
            if (
                copySource(
                    label = "moduleApk:$apkPath",
                    temp = temp,
                    failures = failures,
                ) {
                    val zip = ZipFile(apkPath)
                    val entry = zip.getEntry(MODULE_CLASSPATH_ASSET_PATH)
                    if (entry == null) {
                        zip.close()
                        throw FileNotFoundException(MODULE_CLASSPATH_ASSET_PATH)
                    }
                    object : FilterInputStream(zip.getInputStream(entry)) {
                        override fun close() {
                            runCatching { super.close() }
                            zip.close()
                        }
                    }
                }
            ) {
                return true
            }
        }
        return false
    }

    private fun copySource(
        label: String,
        temp: File,
        failures: MutableList<String>,
        open: () -> InputStream,
    ): Boolean {
        return runCatching {
            open().use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (temp.length() <= 0L) {
                throw IllegalStateException("empty skin asset")
            }
            XposedCompat.logD("[IntlNightModeSkinAssetInstaller] copied bundled skin from $label")
            true
        }.getOrElse { t ->
            temp.delete()
            failures += "$label: ${t.javaClass.simpleName}: ${t.message.orEmpty()}"
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun isUsableSkinPackage(context: Context, file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                ?.packageName
                ?.isNotBlank() == true
        }.getOrDefault(false)
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
