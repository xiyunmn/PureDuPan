package com.xiyunmn.puredupan.hook.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.xiyunmn.puredupan.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import java.util.Locale
import java.util.zip.ZipFile

internal object FrameworkRuntimeInfo {
    private const val UNKNOWN = "unknown"
    private const val MISSING = "missing"

    private const val LSPATCH_MANIFEST_META_KEY = "lspatch"
    private const val LSPATCH_CONFIG_ASSET_PATH = "assets/lspatch/config.json"
    private const val LSPATCH_LOADER_DEX_ASSET_PATH = "assets/lspatch/loader.dex"
    private const val LSPATCH_META_LOADER_DEX_ASSET_PATH = "assets/lspatch/metaloader.dex"
    private const val LSPATCH_ORIGIN_APK_ASSET_PATH = "assets/lspatch/origin.apk"
    private const val LSPATCH_EMBEDDED_MODULE_PREFIX = "assets/lspatch/modules/"

    private const val NPATCH_MANIFEST_META_KEY = "npatch"
    private const val NPATCH_CONFIG_ASSET_PATH = "assets/npatch/config.json"
    private const val NPATCH_LOADER_BIN_ASSET_PATH = "assets/npatch/loader.bin"
    private const val NPATCH_META_LOADER_DEX_ASSET_PATH = "assets/npatch/metaloader.dex"
    private const val NPATCH_PROVIDER_DEX_ASSET_PATH = "assets/npatch/mtprovider.dex"
    private const val NPATCH_ORIGIN_APK_ASSET_PATH = "assets/npatch/origin.apk"
    private const val NPATCH_EMBEDDED_MODULE_PREFIX = "assets/npatch/modules/"

    private val managerPackageHints = listOf(
        PackageHint("LSPosed", listOf("org.lsposed.manager", "io.github.lsposed.manager")),
        PackageHint("LSPatch", listOf("org.lsposed.lspatch")),
        PackageHint("NPatch", listOf("top.nkbe.npatch", "org.lsposed.npatch")),
    )

    private val loadedClassHints = listOf(
        ClassHint(
            label = "libxposed",
            classNames = listOf(
                "io.github.libxposed.api.XposedInterface",
                "io.github.libxposed.api.XposedModule",
            ),
        ),
        ClassHint(
            label = "legacyXposed",
            classNames = listOf(
                "de.robv.android.xposed.XposedBridge",
                "de.robv.android.xposed.XposedHelpers",
            ),
        ),
        ClassHint(
            label = "LSPosed",
            classNames = listOf(
                "org.lsposed.lspd.impl.LSPosedBridge",
                "org.lsposed.lspd.core.Main",
            ),
        ),
        ClassHint(
            label = "LSPatch",
            classNames = listOf(
                "org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub",
                "org.lsposed.lspatch.loader.LSPLoader",
                "org.lsposed.lspatch.share.Constants",
            ),
        ),
        ClassHint(
            label = "NPatch",
            classNames = listOf(
                "top.nkbe.npatch.metaloader.LSPAppComponentFactoryStub",
                "top.nkbe.npatch.loader.LSPLoader",
                "top.nkbe.npatch.share.Constants",
                "org.lsposed.npatch.metaloader.LSPAppComponentFactoryStub",
                "org.lsposed.npatch.loader.LSPLoader",
                "org.lsposed.npatch.share.Constants",
            ),
        ),
    )

    private val keywordHints = listOf(
        KeywordHint("LSPatch", listOf("lspatch", "lspatched")),
        KeywordHint("NPatch", listOf("npatch", "npatched")),
        KeywordHint("FPA", listOf("fpa", "funpatch")),
        KeywordHint("Atom", listOf("atom", "yuanzi")),
    )

    private val systemPropertyHintKeys = listOf(
        "ro.lsposed.version",
        "persist.lsposed.version",
        "ro.lspatch.version",
        "persist.lspatch.version",
        "ro.npatch.version",
        "persist.npatch.version",
        "ro.fpa.version",
        "persist.fpa.version",
        "ro.atom.version",
        "persist.atom.version",
    )

    fun collect(context: Context): Map<String, Any?> {
        val module = XposedCompat.module
        val frameworkName = runCatching { module?.frameworkName }.getOrNull().orUnknown()
        val frameworkProperties = runCatching { module?.frameworkProperties }.getOrNull()
        val hostSourcePaths = collectPackageSourcePaths(context)
        val patchArtifacts = collectPatchArtifacts(context, hostSourcePaths)
        val hostSourceKind = classifyHostSource(context.applicationInfo?.sourceDir)

        return linkedMapOf(
            "xposedApiVersion" to runCatching { module?.apiVersion }.getOrNull(),
            "xposedFrameworkName" to frameworkName,
            "xposedFrameworkVersion" to runCatching { module?.frameworkVersion }.getOrNull().orUnknown(),
            "xposedFrameworkVersionCode" to runCatching { module?.frameworkVersionCode }.getOrNull(),
            "xposedFrameworkProperties" to frameworkProperties,
            "xposedFrameworkCapabilities" to formatFrameworkCapabilities(frameworkProperties),
            "runtimeKind" to classifyRuntimeKind(
                frameworkName = frameworkName,
                hostSourceKind = hostSourceKind,
                patchArtifacts = patchArtifacts,
                hostSourcePaths = hostSourcePaths,
            ),
            "patchMode" to classifyPatchMode(hostSourceKind, patchArtifacts),
            "hostSourceKind" to hostSourceKind,
            "hostSourcePaths" to hostSourcePaths,
            "moduleSourceDir" to runCatching { module?.moduleApplicationInfo?.sourceDir }.getOrNull().orUnknown(),
            "modulePublicSourceDir" to runCatching { module?.moduleApplicationInfo?.publicSourceDir }.getOrNull()
                .orUnknown(),
            "moduleNativeLibraryDir" to runCatching { module?.moduleApplicationInfo?.nativeLibraryDir }.getOrNull()
                .orUnknown(),
            "loadedClassHints" to collectLoadedClassHints(context),
            "managerPackageVisibility" to collectManagerPackageVisibility(context),
            "patchArtifactHints" to patchArtifacts.asMap(),
            "keywordHints" to collectKeywordHints(
                frameworkName = frameworkName,
                hostSourcePaths = hostSourcePaths,
                moduleSourceDir = runCatching { module?.moduleApplicationInfo?.sourceDir }.getOrNull(),
            ),
            "systemPropertyHints" to collectSystemPropertyHints(),
        )
    }

    private fun formatFrameworkCapabilities(properties: Long?): List<String> {
        if (properties == null) return emptyList()
        val out = ArrayList<String>(3)
        if ((properties and XposedInterface.PROP_CAP_SYSTEM) != 0L) {
            out.add("PROP_CAP_SYSTEM")
        }
        if ((properties and XposedInterface.PROP_CAP_REMOTE) != 0L) {
            out.add("PROP_CAP_REMOTE")
        }
        if ((properties and XposedInterface.PROP_RT_API_PROTECTION) != 0L) {
            out.add("PROP_RT_API_PROTECTION")
        }
        return out
    }

    private fun classifyRuntimeKind(
        frameworkName: String,
        hostSourceKind: String,
        patchArtifacts: PatchArtifactDetection,
        hostSourcePaths: List<String>,
    ): String {
        val lowerName = frameworkName.lowercase(Locale.ROOT)
        return when {
            lowerName.contains("lsposed") -> "lsposed"
            lowerName.contains("edxposed") -> "edxposed"
            lowerName.contains("xposed") -> "xposed"
            lowerName.contains("vector") -> "vector"
            patchArtifacts.npatchDetected || hostSourceKind == "npatch-origin" -> "npatch"
            patchArtifacts.lspatchDetected || hostSourceKind == "lspatch-origin" -> "lspatch"
            containsKeyword(hostSourcePaths, listOf("fpa", "funpatch")) || lowerName.contains("fpa") -> "fpa-like"
            containsKeyword(hostSourcePaths, listOf("atom", "yuanzi")) || lowerName.contains("atom") -> "atom-like"
            frameworkName == UNKNOWN -> UNKNOWN
            else -> "xposed-compatible"
        }
    }

    private fun classifyPatchMode(hostSourceKind: String, patchArtifacts: PatchArtifactDetection): String {
        return when {
            patchArtifacts.embeddedModulesFound -> "integrated"
            hostSourceKind == "npatch-origin" || hostSourceKind == "lspatch-origin" -> "integrated"
            patchArtifacts.configFound && patchArtifacts.sourceChecked -> "local"
            patchArtifacts.configFound || patchArtifacts.sourceLooksPatched -> UNKNOWN
            else -> "none"
        }
    }

    private fun classifyHostSource(sourceDir: String?): String {
        val value = sourceDir?.replace('\\', '/')?.lowercase(Locale.ROOT).orEmpty()
        return when {
            value.isBlank() -> UNKNOWN
            value.contains("/cache/npatch/origin/") -> "npatch-origin"
            value.contains("/cache/lspatch/origin/") -> "lspatch-origin"
            value.endsWith(".apk") -> "apk"
            else -> "other"
        }
    }

    private fun collectPackageSourcePaths(context: Context): List<String> {
        val paths = LinkedHashSet<String>()
        fun addPath(path: String?) {
            if (!path.isNullOrBlank()) paths.add(path)
        }

        addPath(context.applicationInfo?.sourceDir)
        addPath(context.applicationInfo?.publicSourceDir)
        addPath(context.packageResourcePath)
        getApplicationInfoCompat(context)?.let { appInfo ->
            addPath(appInfo.sourceDir)
            addPath(appInfo.publicSourceDir)
        }
        return paths.toList()
    }

    private fun collectPatchArtifacts(context: Context, sourcePaths: List<String>): PatchArtifactDetection {
        val manifestMetadata = collectPatchManifestMetadata(context)
        val sourcePathHints = collectSourcePathPatchHints(sourcePaths)
        val zipEntries = inspectPatchZipSources(sourcePaths)
        val assetEntries = inspectPatchAssets(context)

        return PatchArtifactDetection(
            lspatchManifestMetadata = manifestMetadata[LSPATCH_MANIFEST_META_KEY] == true,
            npatchManifestMetadata = manifestMetadata[NPATCH_MANIFEST_META_KEY] == true,
            lspatchSourcePath = sourcePathHints["lspatch"] == true,
            npatchSourcePath = sourcePathHints["npatch"] == true,
            fpaSourcePath = sourcePathHints["fpa"] == true,
            atomSourcePath = sourcePathHints["atom"] == true,
            lspatchConfigInApk = zipEntries.lspatchConfig,
            lspatchLoaderDexInApk = zipEntries.lspatchLoaderDex,
            lspatchMetaLoaderDexInApk = zipEntries.lspatchMetaLoaderDex,
            lspatchOriginApkInApk = zipEntries.lspatchOriginApk,
            lspatchEmbeddedModuleCountInApk = zipEntries.lspatchEmbeddedModuleCount,
            npatchConfigInApk = zipEntries.npatchConfig,
            npatchLoaderBinInApk = zipEntries.npatchLoaderBin,
            npatchMetaLoaderDexInApk = zipEntries.npatchMetaLoaderDex,
            npatchProviderDexInApk = zipEntries.npatchProviderDex,
            npatchOriginApkInApk = zipEntries.npatchOriginApk,
            npatchEmbeddedModuleCountInApk = zipEntries.npatchEmbeddedModuleCount,
            lspatchConfigInAssets = assetEntries.lspatchConfig,
            lspatchEmbeddedModuleCountInAssets = assetEntries.lspatchEmbeddedModuleCount,
            npatchConfigInAssets = assetEntries.npatchConfig,
            npatchEmbeddedModuleCountInAssets = assetEntries.npatchEmbeddedModuleCount,
            sourceChecked = zipEntries.sourceChecked || assetEntries.sourceChecked,
            zipOpenFailures = zipEntries.openFailures,
        )
    }

    private fun collectPatchManifestMetadata(context: Context): Map<String, Boolean> {
        val appInfos = listOfNotNull(context.applicationInfo, getApplicationInfoCompat(context))
        return linkedMapOf(
            LSPATCH_MANIFEST_META_KEY to appInfos.any { it.metaData?.containsKey(LSPATCH_MANIFEST_META_KEY) == true },
            NPATCH_MANIFEST_META_KEY to appInfos.any { it.metaData?.containsKey(NPATCH_MANIFEST_META_KEY) == true },
        )
    }

    private fun collectSourcePathPatchHints(sourcePaths: List<String>): Map<String, Boolean> {
        val normalizedPaths = sourcePaths.map { it.replace('\\', '/').lowercase(Locale.ROOT) }
        return linkedMapOf(
            "lspatch" to normalizedPaths.any {
                it.contains("/lspatch/") || it.contains("-lspatched.apk")
            },
            "npatch" to normalizedPaths.any {
                it.contains("/npatch/") || it.contains("-npatched.apk")
            },
            "fpa" to containsKeyword(normalizedPaths, listOf("/fpa/", "-fpa", "funpatch")),
            "atom" to containsKeyword(normalizedPaths, listOf("/atom/", "-atom", "yuanzi")),
        )
    }

    private fun inspectPatchZipSources(sourcePaths: List<String>): ZipPatchEntries {
        var sourceChecked = false
        var lspatchConfig = false
        var lspatchLoaderDex = false
        var lspatchMetaLoaderDex = false
        var lspatchOriginApk = false
        var lspatchEmbeddedModuleCount = 0
        var npatchConfig = false
        var npatchLoaderBin = false
        var npatchMetaLoaderDex = false
        var npatchProviderDex = false
        var npatchOriginApk = false
        var npatchEmbeddedModuleCount = 0
        val openFailures = ArrayList<String>()

        for (sourcePath in sourcePaths) {
            if (!sourcePath.endsWith(".apk", ignoreCase = true)) continue
            runCatching {
                ZipFile(sourcePath).use { zip ->
                    sourceChecked = true
                    lspatchConfig = lspatchConfig || zip.getEntry(LSPATCH_CONFIG_ASSET_PATH) != null
                    lspatchLoaderDex = lspatchLoaderDex || zip.getEntry(LSPATCH_LOADER_DEX_ASSET_PATH) != null
                    lspatchMetaLoaderDex =
                        lspatchMetaLoaderDex || zip.getEntry(LSPATCH_META_LOADER_DEX_ASSET_PATH) != null
                    lspatchOriginApk = lspatchOriginApk || zip.getEntry(LSPATCH_ORIGIN_APK_ASSET_PATH) != null
                    npatchConfig = npatchConfig || zip.getEntry(NPATCH_CONFIG_ASSET_PATH) != null
                    npatchLoaderBin = npatchLoaderBin || zip.getEntry(NPATCH_LOADER_BIN_ASSET_PATH) != null
                    npatchMetaLoaderDex =
                        npatchMetaLoaderDex || zip.getEntry(NPATCH_META_LOADER_DEX_ASSET_PATH) != null
                    npatchProviderDex = npatchProviderDex || zip.getEntry(NPATCH_PROVIDER_DEX_ASSET_PATH) != null
                    npatchOriginApk = npatchOriginApk || zip.getEntry(NPATCH_ORIGIN_APK_ASSET_PATH) != null
                    lspatchEmbeddedModuleCount += countZipEntries(zip, LSPATCH_EMBEDDED_MODULE_PREFIX)
                    npatchEmbeddedModuleCount += countZipEntries(zip, NPATCH_EMBEDDED_MODULE_PREFIX)
                }
            }.onFailure { t ->
                openFailures.add("${t::class.java.simpleName}:${t.message.orEmpty()}")
            }
        }

        return ZipPatchEntries(
            sourceChecked = sourceChecked,
            lspatchConfig = lspatchConfig,
            lspatchLoaderDex = lspatchLoaderDex,
            lspatchMetaLoaderDex = lspatchMetaLoaderDex,
            lspatchOriginApk = lspatchOriginApk,
            lspatchEmbeddedModuleCount = lspatchEmbeddedModuleCount,
            npatchConfig = npatchConfig,
            npatchLoaderBin = npatchLoaderBin,
            npatchMetaLoaderDex = npatchMetaLoaderDex,
            npatchProviderDex = npatchProviderDex,
            npatchOriginApk = npatchOriginApk,
            npatchEmbeddedModuleCount = npatchEmbeddedModuleCount,
            openFailures = openFailures,
        )
    }

    private fun countZipEntries(zip: ZipFile, prefix: String): Int {
        var count = 0
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val name = entries.nextElement().name
            if (name.startsWith(prefix) && name.endsWith(".apk", ignoreCase = true)) {
                count++
            }
        }
        return count
    }

    private fun inspectPatchAssets(context: Context): AssetPatchEntries {
        val lspatchConfig = assetExists(context, "lspatch/config.json")
        val npatchConfig = assetExists(context, "npatch/config.json")
        return AssetPatchEntries(
            sourceChecked = true,
            lspatchConfig = lspatchConfig,
            lspatchEmbeddedModuleCount = countAssetEntries(context, "lspatch/modules"),
            npatchConfig = npatchConfig,
            npatchEmbeddedModuleCount = countAssetEntries(context, "npatch/modules"),
        )
    }

    private fun assetExists(context: Context, path: String): Boolean {
        return runCatching {
            context.assets.open(path).close()
            true
        }.getOrDefault(false)
    }

    private fun countAssetEntries(context: Context, path: String): Int {
        return runCatching {
            context.assets.list(path)
                ?.count { it.endsWith(".apk", ignoreCase = true) }
                ?: 0
        }.getOrDefault(0)
    }

    private fun collectLoadedClassHints(context: Context): Map<String, Any?> {
        val loaders = listOfNotNull(
            context.classLoader,
            XposedCompat.module?.javaClass?.classLoader,
            FrameworkRuntimeInfo::class.java.classLoader,
            ClassLoader.getSystemClassLoader(),
        ).distinctBy { System.identityHashCode(it) }

        return loadedClassHints.associateTo(linkedMapOf()) { hint ->
            val available = hint.classNames.filter { className ->
                loaders.any { loader -> isClassAvailable(className, loader) }
            }
            hint.label to linkedMapOf(
                "available" to available.isNotEmpty(),
                "classes" to available,
            )
        }
    }

    private fun isClassAvailable(className: String, classLoader: ClassLoader): Boolean {
        return runCatching {
            Class.forName(className, false, classLoader)
            true
        }.getOrDefault(false)
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun collectManagerPackageVisibility(context: Context): Map<String, Any?> {
        return managerPackageHints.associateTo(linkedMapOf()) { hint ->
            hint.label to hint.packageNames.associateTo(linkedMapOf()) { packageName ->
                packageName to packageVisibilityStatus(context, packageName)
            }
        }
    }

    private fun packageVisibilityStatus(context: Context, packageName: String): Map<String, Any?> {
        val packageInfo = packageInfoOrNull(context, packageName)
        return if (packageInfo == null) {
            linkedMapOf(
                "visible" to false,
                "status" to "not_visible_or_missing",
            )
        } else {
            linkedMapOf(
                "visible" to true,
                "versionName" to packageInfo.versionName.orUnknown(),
                "versionCode" to packageInfo.longVersionCodeCompat(),
                "sourceDir" to packageInfo.applicationInfo?.sourceDir.orUnknown(),
            )
        }
    }

    private fun collectKeywordHints(
        frameworkName: String,
        hostSourcePaths: List<String>,
        moduleSourceDir: String?,
    ): Map<String, Any?> {
        val haystacks = linkedMapOf(
            "xposedFrameworkName" to frameworkName,
            "moduleSourceDir" to moduleSourceDir.orEmpty(),
        )
        hostSourcePaths.forEachIndexed { index, path ->
            haystacks["hostSourcePath$index"] = path
        }

        return keywordHints.associateTo(linkedMapOf()) { hint ->
            val matches = haystacks
                .filterValues { value ->
                    val lower = value.lowercase(Locale.ROOT)
                    hint.keywords.any { keyword -> lower.contains(keyword) }
                }
                .keys
                .toList()
            hint.label to linkedMapOf(
                "matched" to matches.isNotEmpty(),
                "locations" to matches,
            )
        }
    }

    @SuppressLint("PrivateApi")
    private fun collectSystemPropertyHints(): Map<String, Any?> {
        val systemPropertiesClass = runCatching {
            Class.forName("android.os.SystemProperties")
        }.getOrNull() ?: return linkedMapOf(
            "status" to "unavailable:SystemPropertiesClassMissing",
        )
        val getMethod = runCatching {
            systemPropertiesClass.getDeclaredMethod("get", String::class.java)
                .apply { isAccessible = true }
        }.getOrNull() ?: return linkedMapOf(
            "status" to "unavailable:SystemPropertiesGetMissing",
        )

        return systemPropertyHintKeys.associateTo(linkedMapOf()) { key ->
            key to runCatching {
                (getMethod.invoke(null, key) as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?: MISSING
            }.getOrElse { t ->
                "unavailable:${t::class.java.simpleName}"
            }
        }
    }

    private fun getApplicationInfoCompat(context: Context): ApplicationInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            }
        }.getOrNull()
    }

    private fun packageInfoOrNull(context: Context, packageName: String): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull()
    }

    private fun containsKeyword(values: Iterable<String>, keywords: List<String>): Boolean {
        return values.any { value ->
            val lower = value.lowercase(Locale.ROOT)
            keywords.any { keyword -> lower.contains(keyword) }
        }
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: UNKNOWN

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
    }

    private data class PackageHint(
        val label: String,
        val packageNames: List<String>,
    )

    private data class ClassHint(
        val label: String,
        val classNames: List<String>,
    )

    private data class KeywordHint(
        val label: String,
        val keywords: List<String>,
    )

    private data class ZipPatchEntries(
        val sourceChecked: Boolean,
        val lspatchConfig: Boolean,
        val lspatchLoaderDex: Boolean,
        val lspatchMetaLoaderDex: Boolean,
        val lspatchOriginApk: Boolean,
        val lspatchEmbeddedModuleCount: Int,
        val npatchConfig: Boolean,
        val npatchLoaderBin: Boolean,
        val npatchMetaLoaderDex: Boolean,
        val npatchProviderDex: Boolean,
        val npatchOriginApk: Boolean,
        val npatchEmbeddedModuleCount: Int,
        val openFailures: List<String>,
    )

    private data class AssetPatchEntries(
        val sourceChecked: Boolean,
        val lspatchConfig: Boolean,
        val lspatchEmbeddedModuleCount: Int,
        val npatchConfig: Boolean,
        val npatchEmbeddedModuleCount: Int,
    )

    private data class PatchArtifactDetection(
        val lspatchManifestMetadata: Boolean,
        val npatchManifestMetadata: Boolean,
        val lspatchSourcePath: Boolean,
        val npatchSourcePath: Boolean,
        val fpaSourcePath: Boolean,
        val atomSourcePath: Boolean,
        val lspatchConfigInApk: Boolean,
        val lspatchLoaderDexInApk: Boolean,
        val lspatchMetaLoaderDexInApk: Boolean,
        val lspatchOriginApkInApk: Boolean,
        val lspatchEmbeddedModuleCountInApk: Int,
        val npatchConfigInApk: Boolean,
        val npatchLoaderBinInApk: Boolean,
        val npatchMetaLoaderDexInApk: Boolean,
        val npatchProviderDexInApk: Boolean,
        val npatchOriginApkInApk: Boolean,
        val npatchEmbeddedModuleCountInApk: Int,
        val lspatchConfigInAssets: Boolean,
        val lspatchEmbeddedModuleCountInAssets: Int,
        val npatchConfigInAssets: Boolean,
        val npatchEmbeddedModuleCountInAssets: Int,
        val sourceChecked: Boolean,
        val zipOpenFailures: List<String>,
    ) {
        val lspatchDetected: Boolean
            get() = lspatchManifestMetadata ||
                lspatchSourcePath ||
                lspatchConfigInApk ||
                lspatchLoaderDexInApk ||
                lspatchMetaLoaderDexInApk ||
                lspatchOriginApkInApk ||
                lspatchEmbeddedModuleCountInApk > 0 ||
                lspatchConfigInAssets ||
                lspatchEmbeddedModuleCountInAssets > 0

        val npatchDetected: Boolean
            get() = npatchManifestMetadata ||
                npatchSourcePath ||
                npatchConfigInApk ||
                npatchLoaderBinInApk ||
                npatchMetaLoaderDexInApk ||
                npatchProviderDexInApk ||
                npatchOriginApkInApk ||
                npatchEmbeddedModuleCountInApk > 0 ||
                npatchConfigInAssets ||
                npatchEmbeddedModuleCountInAssets > 0

        val embeddedModulesFound: Boolean
            get() = lspatchEmbeddedModuleCountInApk > 0 ||
                npatchEmbeddedModuleCountInApk > 0 ||
                lspatchEmbeddedModuleCountInAssets > 0 ||
                npatchEmbeddedModuleCountInAssets > 0

        val configFound: Boolean
            get() = lspatchManifestMetadata ||
                npatchManifestMetadata ||
                lspatchConfigInApk ||
                npatchConfigInApk ||
                lspatchConfigInAssets ||
                npatchConfigInAssets

        val sourceLooksPatched: Boolean
            get() = lspatchSourcePath ||
                npatchSourcePath ||
                fpaSourcePath ||
                atomSourcePath ||
                lspatchDetected ||
                npatchDetected

        fun asMap(): Map<String, Any?> {
            return linkedMapOf(
                "manifestMetadata" to linkedMapOf(
                    LSPATCH_MANIFEST_META_KEY to lspatchManifestMetadata,
                    NPATCH_MANIFEST_META_KEY to npatchManifestMetadata,
                ),
                "sourcePath" to linkedMapOf(
                    "lspatch" to lspatchSourcePath,
                    "npatch" to npatchSourcePath,
                    "fpa" to fpaSourcePath,
                    "atom" to atomSourcePath,
                ),
                "apkEntries" to linkedMapOf(
                    "lspatch" to linkedMapOf(
                        "config" to lspatchConfigInApk,
                        "loaderDex" to lspatchLoaderDexInApk,
                        "metaLoaderDex" to lspatchMetaLoaderDexInApk,
                        "originApk" to lspatchOriginApkInApk,
                        "embeddedModuleCount" to lspatchEmbeddedModuleCountInApk,
                    ),
                    "npatch" to linkedMapOf(
                        "config" to npatchConfigInApk,
                        "loaderBin" to npatchLoaderBinInApk,
                        "metaLoaderDex" to npatchMetaLoaderDexInApk,
                        "providerDex" to npatchProviderDexInApk,
                        "originApk" to npatchOriginApkInApk,
                        "embeddedModuleCount" to npatchEmbeddedModuleCountInApk,
                    ),
                ),
                "assetEntries" to linkedMapOf(
                    "lspatch" to linkedMapOf(
                        "config" to lspatchConfigInAssets,
                        "embeddedModuleCount" to lspatchEmbeddedModuleCountInAssets,
                    ),
                    "npatch" to linkedMapOf(
                        "config" to npatchConfigInAssets,
                        "embeddedModuleCount" to npatchEmbeddedModuleCountInAssets,
                    ),
                ),
                "summary" to linkedMapOf(
                    "lspatchDetected" to lspatchDetected,
                    "npatchDetected" to npatchDetected,
                    "embeddedModulesFound" to embeddedModulesFound,
                    "configFound" to configFound,
                    "sourceChecked" to sourceChecked,
                    "zipOpenFailures" to zipOpenFailures,
                ),
            )
        }
    }
}
