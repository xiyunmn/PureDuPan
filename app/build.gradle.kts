plugins {
    alias(libs.plugins.android.application)
}

val majorVersion = 1
val minorVersion = 2
val patchVersion = 0

val releaseVersionCode = majorVersion * 10000 + minorVersion * 100 + patchVersion
val betaVersionCode = releaseVersionCode + 50
val debugVersionCode = releaseVersionCode + 99
val baseVersionName = "$majorVersion.$minorVersion.$patchVersion"

fun versionNameForBuildType(buildType: String) = when (buildType) {
    "debug" -> "$baseVersionName-debug"
    "beta" -> "$baseVersionName-beta"
    else -> baseVersionName
}

fun versionCodeForBuildType(buildType: String) = when (buildType) {
    "debug" -> debugVersionCode
    "beta" -> betaVersionCode
    else -> releaseVersionCode
}

fun apkFileNameForBuildType(buildType: String) = when (buildType) {
    "debug" -> "PureDuPan-v${baseVersionName}-debug.apk"
    "beta" -> "PureDuPan-v${baseVersionName}-beta.apk"
    else -> "PureDuPan-v${baseVersionName}-release.apk"
}

android {
    namespace = "com.xiyunmn.puredupan.hook"
    buildFeatures {
        buildConfig = true
    }
    val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
    val releaseSigningProvided = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }
    val releaseSigningPartiallyProvided = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).any { !it.isNullOrBlank() }

    if (releaseSigningProvided) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    } else if (releaseSigningPartiallyProvided) {
        project.logger.warn(
            "[WangPanHook] Release signing properties are incomplete; release build will be unsigned."
        )
    }

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    val minSupportedUserSettingsVersionCode = 20

    defaultConfig {
        applicationId = "com.xiyunmn.puredupan.hook"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = baseVersionName
        buildConfigField(
            "int",
            "MIN_SUPPORTED_USER_SETTINGS_VERSION_CODE",
            minSupportedUserSettingsVersionCode.toString()
        )
    }

    androidResources {
        localeFilters += listOf("zh")
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }

        create("beta") {
            versionNameSuffix = "-beta"
            signingConfig = signingConfigs.findByName("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            matchingFallbacks += listOf("release")
        }

        release {
            signingConfig = signingConfigs.findByName("release")
            if (signingConfig == null) {
                project.logger.lifecycle(
                    "[WangPanHook] :app:release uses unsigned output (no release keystore configured)."
                )
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val buildType = variant.buildType ?: "release"
            output.versionName.set(versionNameForBuildType(buildType))
            output.versionCode.set(versionCodeForBuildType(buildType))
        }
    }
}

val validateXposedScope = tasks.register("validateXposedScope") {
    val hostPackagesFile = layout.projectDirectory.file(
        "src/main/java/com/xiyunmn/puredupan/hook/host/HostPackages.kt"
    )
    val scopeFile = layout.projectDirectory.file("src/main/resources/META-INF/xposed/scope.list")

    inputs.files(hostPackagesFile, scopeFile)

    doLast {
        val hostPackages = Regex("""const\s+val\s+[A-Z0-9_]+\s*=\s*"([^"]+)"""")
            .findAll(hostPackagesFile.asFile.readText())
            .map { it.groupValues[1] }
            .toList()
        val scopePackages = scopeFile.asFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val duplicateHostPackages = hostPackages.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val duplicateScopePackages = scopePackages.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val missingFromScope = hostPackages.toSet() - scopePackages.toSet()
        val extraInScope = scopePackages.toSet() - hostPackages.toSet()

        if (
            duplicateHostPackages.isNotEmpty() ||
            duplicateScopePackages.isNotEmpty() ||
            missingFromScope.isNotEmpty() ||
            extraInScope.isNotEmpty()
        ) {
            throw GradleException(
                "Xposed scope list does not match HostPackages. " +
                    "duplicateHostPackages=${duplicateHostPackages.joinToString()}, " +
                    "duplicateScopePackages=${duplicateScopePackages.joinToString()}, " +
                    "missingFromScope=${missingFromScope.joinToString()}, " +
                    "extraInScope=${extraInScope.joinToString()}"
            )
        }
    }
}

val validateHookArchitecture = tasks.register("validateHookArchitecture") {
    dependsOn(validateXposedScope)

    val javaSourceDir = layout.projectDirectory.dir("src/main/java")

    inputs.dir(javaSourceDir)

    doLast {
        val sourceRoot = javaSourceDir.asFile
        val allKotlinFiles = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        fun File.relativePath(): String =
            relativeTo(sourceRoot).invariantSeparatorsPath

        fun extractConstants(files: Iterable<File>, pattern: Regex): Set<String> =
            files.flatMap { file ->
                pattern.findAll(file.readText()).map { match -> match.groupValues[1] }.toList()
            }.toSortedSet()

        fun extractConstantNames(file: File): Set<String> =
            Regex("""const\s+val\s+([A-Z0-9_]+)\s*=""")
                .findAll(file.readText())
                .map { match -> match.groupValues[1] }
                .toSortedSet()

        fun failIfNotEmpty(label: String, values: Collection<String>) {
            if (values.isNotEmpty()) {
                throw GradleException("$label: ${values.joinToString()}")
            }
        }

        fun duplicateValues(values: Iterable<String>): Set<String> =
            values
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys

        val packagePathMismatches = allKotlinFiles.mapNotNull { file ->
            val text = file.readText().removePrefix("\uFEFF")
            val packageName = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.]+)""")
                .find(text)
                ?.groupValues
                ?.get(1)
                ?: return@mapNotNull "missing package: ${file.relativePath()}"
            val expectedPackage = file.parentFile
                .relativeTo(sourceRoot)
                .invariantSeparatorsPath
                .replace('/', '.')
            if (packageName == expectedPackage) null else
                "package mismatch: ${file.relativePath()} expected=$expectedPackage actual=$packageName"
        }
        failIfNotEmpty("Kotlin package/path mismatch", packagePathMismatches)

        val legacySourcePathPrefixes = listOf(
            "com/xiyunmn/puredupan/hook/feature/ad/",
            "com/xiyunmn/puredupan/hook/feature/performance/",
            "com/xiyunmn/puredupan/hook/feature/startup/",
            "com/xiyunmn/puredupan/hook/feature/ui/",
        )
        val legacySourcePaths = setOf(
            "com/xiyunmn/puredupan/hook/HookInstallPlan.kt",
            "com/xiyunmn/puredupan/hook/core/DexKitCacheWarmUp.kt",
            "com/xiyunmn/puredupan/hook/core/DexKitCompat.kt",
            "com/xiyunmn/puredupan/hook/core/StableBaiduPanHookPoints.kt",
            "com/xiyunmn/puredupan/hook/core/TitanRuntimeState.kt",
            "com/xiyunmn/puredupan/hook/host/HostFeatureAvailabilityRegistry.kt",
            "com/xiyunmn/puredupan/hook/host/HostFlavor.kt",
        )
        val legacySourcePathMatches = allKotlinFiles
            .map { file -> file.relativePath() }
            .filter { path ->
                legacySourcePaths.contains(path) ||
                    legacySourcePathPrefixes.any { prefix -> path.startsWith(prefix) }
            }
        failIfNotEmpty("Legacy architecture source paths", legacySourcePathMatches)

        val sourceTextByPath = allKotlinFiles.associate { it.relativePath() to it.readText() }
        val oldArchitecturePatterns = listOf(
            Regex("""\bHostFlavor\b"""),
            Regex("""\.flavor\b"""),
            Regex("""host=\$\{host\.flavor\}"""),
            Regex("""\bHostHookCatalogIds\b"""),
            Regex("""\bHostDexKitTargetRegistryIds\b"""),
            Regex("""\bHostFeatureRegistry\b"""),
            Regex("""\ballFeatureKeys\b"""),
            Regex("""\bapplicationClassName\b"""),
            Regex("""\brequireByPackageName\b"""),
            Regex("""\busesBenefitSlotMemberCardLayout\b"""),
            Regex("""\busesIntlMemberCardLayout\b"""),
            Regex("""hosts\s*=\s*setOf"""),
            Regex("""\bregisteredProfiles\b"""),
            Regex("""\bcurrentCapabilities\s*\("""),
            Regex("""\bisBaiduIntlPackage\b"""),
            Regex("""\bisCurrentHostIntl\b"""),
            Regex("""\bdexKitTargetRegistryId\b"""),
            Regex("""\bshowDexKitStatusInSettings\s*="""),
            Regex("""\bcapabilities\.stableActivityClassNames\b"""),
            Regex("""\bcapabilities\.dexKitTargetRegistryId\b"""),
            Regex("""\bcapabilities\.showDexKitStatusInSettings\b"""),
            Regex("""\bcapabilities\.primarySplashAdFeatureKey\b"""),
            Regex("""\bcapabilities\.memberCardLayoutMode\b"""),
            Regex("""\bcapabilities\.availableFeatureKeys\b"""),
            Regex("""\bcapabilities\.hookCatalogId\b"""),
            Regex("""\bcapabilities\.supportsOemPushHook\b"""),
        )
        val oldArchitectureMatches = sourceTextByPath.flatMap { (path, text) ->
            oldArchitecturePatterns.mapNotNull { pattern ->
                if (pattern.containsMatchIn(text)) "$path matches ${pattern.pattern}" else null
            }
        }
        failIfNotEmpty("Old host architecture references", oldArchitectureMatches)

        val hostCatalogImportPattern = Regex(
            """import\s+com\.xiyunmn\.puredupan\.hook\.plan\.catalogs\.baidu\.(cn|intl|samsung)\.""",
        )
        val hostCatalogBoundaryMatches = sourceTextByPath.flatMap { (path, text) ->
            val currentHost = Regex("""com/xiyunmn/puredupan/hook/plan/catalogs/baidu/(cn|intl|samsung)/""")
                .find(path)
                ?.groupValues
                ?.get(1)
                ?: return@flatMap emptyList()
            hostCatalogImportPattern.findAll(text).mapNotNull { match ->
                val importedHost = match.groupValues[1]
                if (importedHost != currentHost) {
                    "$path imports $importedHost catalog"
                } else {
                    null
                }
            }.toList()
        }
        failIfNotEmpty("Host catalogs must not import sibling host catalogs", hostCatalogBoundaryMatches)

        val domesticCatalogImportPattern = Regex(
            """import\s+com\.xiyunmn\.puredupan\.hook\.plan\.catalogs\.baidu\.domestic\.""",
        )
        val domesticCatalogBoundaryMatches = sourceTextByPath.mapNotNull { (path, text) ->
            if (!domesticCatalogImportPattern.containsMatchIn(text)) {
                return@mapNotNull null
            }
            val allowed =
                path.startsWith("com/xiyunmn/puredupan/hook/plan/catalogs/baidu/cn/") ||
                    path.startsWith("com/xiyunmn/puredupan/hook/plan/catalogs/baidu/samsung/")
            if (allowed) null else "$path imports domestic catalog specs"
        }
        failIfNotEmpty("Domestic catalog specs are limited to CN/Samsung catalogs", domesticCatalogBoundaryMatches)

        val baiduFeatureSetsPath =
            "com/xiyunmn/puredupan/hook/host/features/baidu/BaiduFeatureSets.kt"
        val baiduFeatureSetsText = sourceTextByPath[baiduFeatureSetsPath].orEmpty()
        val samsungFeatureSetBlock = Regex(
            """val\s+baiduSamsungAvailableKeys:[\s\S]*?\n\s*\)\n""",
        ).find(baiduFeatureSetsText)?.value.orEmpty()
        val samsungFeatureSetInheritsCn = Regex("""\bbaiduCn[A-Za-z0-9_]*""")
            .findAll(samsungFeatureSetBlock)
            .map { match -> match.value }
            .toSortedSet()
        val samsungFeatureSetBoundaryMatches = if (samsungFeatureSetInheritsCn.isNotEmpty()) {
            listOf(
                "$baiduFeatureSetsPath baiduSamsungAvailableKeys references " +
                    samsungFeatureSetInheritsCn.joinToString(),
            )
        } else {
            emptyList()
        }
        failIfNotEmpty("Samsung feature set must not reference CN feature groups", samsungFeatureSetBoundaryMatches)

        val hostHardcodePatterns = listOf(
            Regex("""BaiduSharedHookPoints"""),
            Regex("""com\.baidu"""),
            Regex("""rubik\.generate"""),
            Regex("""com\.netdisk"""),
        )
        val featureAndDexKitFiles = allKotlinFiles.filter { file ->
            val path = file.relativePath()
            path.startsWith("com/xiyunmn/puredupan/hook/feature/") ||
                path.startsWith("com/xiyunmn/puredupan/hook/dexkit/")
        }
        val hostHardcodeMatches = featureAndDexKitFiles.flatMap { file ->
            val text = file.readText()
            hostHardcodePatterns.mapNotNull { pattern ->
                if (pattern.containsMatchIn(text)) "${file.relativePath()} matches ${pattern.pattern}" else null
            }
        }
        failIfNotEmpty("Feature/DexKit direct host symbols", hostHardcodeMatches)

        val featureLayerFiles = allKotlinFiles.filter { file ->
            file.relativePath().startsWith("com/xiyunmn/puredupan/hook/feature/")
        }
        val forbiddenFeatureSettingsImports = listOf(
            Regex("""import\s+com\.xiyunmn\.puredupan\.hook\.config\.ConfigManager\b"""),
            Regex("""import\s+com\.xiyunmn\.puredupan\.hook\.settings\."""),
        )
        val featureSettingsBoundaryMatches = featureLayerFiles.flatMap { file ->
            val text = file.readText()
            forbiddenFeatureSettingsImports.mapNotNull { pattern ->
                if (pattern.containsMatchIn(text)) "${file.relativePath()} matches ${pattern.pattern}" else null
            }
        }
        failIfNotEmpty("Feature layer bypasses HookSettings boundary", featureSettingsBoundaryMatches)

        val featureHostRuntimeFacadeFiles = setOf(
            "com/xiyunmn/puredupan/hook/feature/baidu/shared/runtime/BaiduFeatureRuntime.kt",
        )
        val featureRuntimeHostMatches = featureLayerFiles
            .filter { file -> file.relativePath() !in featureHostRuntimeFacadeFiles }
            .filter { file ->
                Regex("""import\s+com\.xiyunmn\.puredupan\.hook\.host\.""")
                    .containsMatchIn(file.readText())
            }
            .map { file -> file.relativePath() }
        failIfNotEmpty("Feature layer must use host runtime facades", featureRuntimeHostMatches)

        val settingsBypassImportPatterns = listOf(
            Regex("""import\s+com\.xiyunmn\.puredupan\.hook\.config\.ConfigManager\b"""),
            Regex("""import\s+com\.xiyunmn\.puredupan\.hook\.config\.runtime\.HookSettings\b"""),
            Regex("""import\s+com\.xiyunmn\.puredupan\.hook\.settings\."""),
        )
        val snapshotDrivenLayerFiles = allKotlinFiles.filter { file ->
            val path = file.relativePath()
            path.startsWith("com/xiyunmn/puredupan/hook/plan/") ||
                path.startsWith("com/xiyunmn/puredupan/hook/dexkit/")
        }
        val snapshotDrivenLayerSettingsMatches = snapshotDrivenLayerFiles.flatMap { file ->
            val text = file.readText()
            settingsBypassImportPatterns.mapNotNull { pattern ->
                if (pattern.containsMatchIn(text)) "${file.relativePath()} matches ${pattern.pattern}" else null
            }
        }
        failIfNotEmpty(
            "Plan/DexKit layer bypasses explicit settings snapshots",
            snapshotDrivenLayerSettingsMatches,
        )

        val hostRuntimeStateFacadeFiles = setOf(
            "com/xiyunmn/puredupan/hook/config/runtime/ConfigHostRuntime.kt",
            "com/xiyunmn/puredupan/hook/feature/baidu/shared/runtime/BaiduFeatureRuntime.kt",
            "com/xiyunmn/puredupan/hook/runtime/HostLoadRuntime.kt",
            "com/xiyunmn/puredupan/hook/settings/registry/SettingsDexKitState.kt",
            "com/xiyunmn/puredupan/hook/settings/registry/SettingsHostState.kt",
            "com/xiyunmn/puredupan/hook/ui/HostThemeRuntime.kt",
        )
        val hostRuntimeStateImportMatches = allKotlinFiles
            .filter { file -> file.relativePath() !in hostRuntimeStateFacadeFiles }
            .filter { file ->
                Regex("""import\s+com\.xiyunmn\.puredupan\.hook\.host\.HostRuntimeState\b""")
                    .containsMatchIn(file.readText())
            }
            .map { file -> file.relativePath() }
        failIfNotEmpty("HostRuntimeState must stay behind runtime facades", hostRuntimeStateImportMatches)

        val hostRuntimeFacadeBypassMatches = sourceTextByPath.mapNotNull { (path, text) ->
            val isHostRuntimeFacade =
                path in hostRuntimeStateFacadeFiles &&
                    path != "com/xiyunmn/puredupan/hook/runtime/HostLoadRuntime.kt"
            if (
                isHostRuntimeFacade &&
                Regex("""HostRuntimeState\.(capabilitiesFor|capabilitiesForPackage|profileForPackage|currentCapabilities)\b""")
                    .containsMatchIn(text)
            ) {
                "$path bypasses HostRuntimeState semantic methods"
            } else {
                null
            }
        }
        failIfNotEmpty("HostRuntimeState facades must use semantic accessors", hostRuntimeFacadeBypassMatches)

        val hostProfileImportPattern = Regex(
            """import\s+com\.xiyunmn\.puredupan\.hook\.host\.HostProfile\b""",
        )
        val hostProfileAllowedPathPrefixes = listOf(
            "com/xiyunmn/puredupan/hook/host/",
        )
        val hostProfileAllowedFiles = setOf(
            "com/xiyunmn/puredupan/hook/plan/HookInstallPlan.kt",
            "com/xiyunmn/puredupan/hook/plan/HookPlanHostContext.kt",
            "com/xiyunmn/puredupan/hook/runtime/HostLoadRuntime.kt",
        )
        val hostProfileBoundaryMatches = sourceTextByPath.mapNotNull { (path, text) ->
            val allowed =
                path in hostProfileAllowedFiles ||
                    hostProfileAllowedPathPrefixes.any { prefix -> path.startsWith(prefix) }
            if (!allowed && hostProfileImportPattern.containsMatchIn(text)) {
                "$path imports HostProfile directly"
            } else {
                null
            }
        }
        failIfNotEmpty("HostProfile leaked outside orchestration layers", hostProfileBoundaryMatches)

        val dexKitRuntimeHostMatches = allKotlinFiles
            .filter { file -> file.relativePath().startsWith("com/xiyunmn/puredupan/hook/dexkit/") }
            .filter { file ->
                Regex("""import\s+com\.xiyunmn\.puredupan\.hook\.host\.HostRuntimeState\b""")
                    .containsMatchIn(file.readText())
            }
            .map { file -> file.relativePath() }
        failIfNotEmpty("DexKit layer must not resolve host runtime state directly", dexKitRuntimeHostMatches)

        val hostRuntimeFacadeFiles = setOf(
            "com/xiyunmn/puredupan/hook/config/runtime/ConfigHostRuntime.kt",
            "com/xiyunmn/puredupan/hook/settings/registry/SettingsDexKitState.kt",
            "com/xiyunmn/puredupan/hook/settings/registry/SettingsHostState.kt",
            "com/xiyunmn/puredupan/hook/ui/HostThemeRuntime.kt",
        )
        val hostLayerImportPattern = Regex("""import\s+com\.xiyunmn\.puredupan\.hook\.host\.""")
        val genericLayerHostImportMatches = sourceTextByPath.mapNotNull { (path, text) ->
            val isGenericRuntimeLayer =
                path.startsWith("com/xiyunmn/puredupan/hook/config/") ||
                    path.startsWith("com/xiyunmn/puredupan/hook/settings/") ||
                    path.startsWith("com/xiyunmn/puredupan/hook/ui/")
            if (
                isGenericRuntimeLayer &&
                path !in hostRuntimeFacadeFiles &&
                hostLayerImportPattern.containsMatchIn(text)
            ) {
                "$path imports host layer directly"
            } else {
                null
            }
        }
        failIfNotEmpty("Generic runtime layer bypasses host runtime facades", genericLayerHostImportMatches)

        val genericHostFacadeBypassMatches = sourceTextByPath.mapNotNull { (path, text) ->
            when (path) {
                "com/xiyunmn/puredupan/hook/settings/registry/SettingsHostState.kt",
                "com/xiyunmn/puredupan/hook/ui/HostThemeRuntime.kt" ->
                    if (Regex("""HostRuntimeState\.capabilitiesFor\b""").containsMatchIn(text)) {
                        "$path calls HostRuntimeState.capabilitiesFor directly"
                    } else {
                        null
                    }
                "com/xiyunmn/puredupan/hook/settings/registry/SettingsDexKitState.kt" ->
                    if (Regex("""HostRuntimeState\.profileForPackage\b""").containsMatchIn(text)) {
                        "$path calls HostRuntimeState.profileForPackage directly"
                    } else {
                        null
                    }
                else -> null
            }
        }
        failIfNotEmpty("Generic host facades must use semantic HostRuntimeState methods", genericHostFacadeBypassMatches)

        val entryPointHostLayerImportMatches = sourceTextByPath.mapNotNull { (path, text) ->
            if (
                path == "com/xiyunmn/puredupan/hook/MainHook.kt" &&
                hostLayerImportPattern.containsMatchIn(text)
            ) {
                "$path imports host layer directly"
            } else {
                null
            }
        }
        failIfNotEmpty("MainHook must use HostLoadRuntime boundary", entryPointHostLayerImportMatches)

        val planCatalogHostAccessMatches = sourceTextByPath.mapNotNull { (path, text) ->
            if (
                path.startsWith("com/xiyunmn/puredupan/hook/plan/catalogs/") &&
                Regex("""\bcontext\.host\b""").containsMatchIn(text)
            ) {
                "$path accesses PlanContext.host directly"
            } else {
                null
            }
        }
        failIfNotEmpty("Plan catalogs must use PlanContext semantic accessors", planCatalogHostAccessMatches)

        val baiduSpecificPatterns = listOf(
            Regex("""com\.baidu"""),
            Regex("""rubik\.generate"""),
            Regex("""com\.netdisk"""),
            Regex("""\bBaidu[A-Za-z0-9_]*"""),
            Regex("""\bBAIDU_[A-Z0-9_]+\b"""),
            Regex("""\bbaidu_[a-z0-9_]+\b"""),
        )
        val baiduSpecificPathPrefixes = listOf(
            "com/xiyunmn/puredupan/hook/dexkit/baidu/",
            "com/xiyunmn/puredupan/hook/feature/baidu/",
            "com/xiyunmn/puredupan/hook/host/features/baidu/",
            "com/xiyunmn/puredupan/hook/host/profiles/baidu/",
            "com/xiyunmn/puredupan/hook/host/runtime/baidu/",
            "com/xiyunmn/puredupan/hook/plan/catalogs/baidu/",
            "com/xiyunmn/puredupan/hook/symbols/baidu/",
        )
        val baiduSpecificRegistrationFiles = setOf(
            "com/xiyunmn/puredupan/hook/dexkit/HostDexKitTargetRegistries.kt",
            "com/xiyunmn/puredupan/hook/host/HostIds.kt",
            "com/xiyunmn/puredupan/hook/host/HostPackages.kt",
            "com/xiyunmn/puredupan/hook/host/HostRegistry.kt",
            "com/xiyunmn/puredupan/hook/plan/HostHookCatalogs.kt",
        )
        fun isAllowedBaiduSpecificPath(path: String): Boolean {
            return baiduSpecificRegistrationFiles.contains(path) ||
                baiduSpecificPathPrefixes.any { prefix -> path.startsWith(prefix) }
        }
        val misplacedBaiduSpecificMatches = sourceTextByPath.flatMap { (path, text) ->
            if (isAllowedBaiduSpecificPath(path)) {
                emptyList()
            } else {
                baiduSpecificPatterns.mapNotNull { pattern ->
                    if (pattern.containsMatchIn(text)) "$path matches ${pattern.pattern}" else null
                }
            }
        }
        failIfNotEmpty("Baidu-specific references outside Baidu namespaces", misplacedBaiduSpecificMatches)

        fun forbiddenImportMatches(
            pathPrefixes: List<String>,
            patterns: List<Regex>,
        ): List<String> {
            return sourceTextByPath.flatMap { (path, text) ->
                if (pathPrefixes.none { prefix -> path.startsWith(prefix) }) {
                    emptyList()
                } else {
                    patterns.mapNotNull { pattern ->
                        if (pattern.containsMatchIn(text)) "$path matches ${pattern.pattern}" else null
                    }
                }
            }
        }
        val baiduCnImportPattern = Regex(
            """import\s+com\.xiyunmn\.puredupan\.hook\.(feature|symbols|plan\.catalogs)\.baidu\.cn\.""",
        )
        val baiduIntlImportPattern = Regex(
            """import\s+com\.xiyunmn\.puredupan\.hook\.(feature|symbols|plan\.catalogs)\.baidu\.intl\.""",
        )
        failIfNotEmpty(
            "Baidu shared namespace imports host-specific code",
            forbiddenImportMatches(
                pathPrefixes = listOf(
                    "com/xiyunmn/puredupan/hook/feature/baidu/shared/",
                    "com/xiyunmn/puredupan/hook/plan/catalogs/baidu/shared/",
                ),
                patterns = listOf(baiduCnImportPattern, baiduIntlImportPattern),
            ),
        )
        failIfNotEmpty(
            "Baidu CN namespace imports Intl code",
            forbiddenImportMatches(
                pathPrefixes = listOf(
                    "com/xiyunmn/puredupan/hook/feature/baidu/cn/",
                    "com/xiyunmn/puredupan/hook/plan/catalogs/baidu/cn/",
                ),
                patterns = listOf(baiduIntlImportPattern),
            ),
        )
        failIfNotEmpty(
            "Baidu Intl namespace imports CN code",
            forbiddenImportMatches(
                pathPrefixes = listOf(
                    "com/xiyunmn/puredupan/hook/feature/baidu/intl/",
                    "com/xiyunmn/puredupan/hook/plan/catalogs/baidu/intl/",
                ),
                patterns = listOf(baiduCnImportPattern),
            ),
        )

        val hostIdsFile = sourceRoot.resolve(
            "com/xiyunmn/puredupan/hook/host/HostIds.kt"
        )
        val hostPackagesFile = sourceRoot.resolve(
            "com/xiyunmn/puredupan/hook/host/HostPackages.kt"
        )
        val hostProfilesDir = sourceRoot.resolve("com/xiyunmn/puredupan/hook/host/profiles")
        val hostRegistryFile = sourceRoot.resolve(
            "com/xiyunmn/puredupan/hook/host/HostRegistry.kt"
        )
        val hostIdConstants = extractConstantNames(hostIdsFile)
        val hostPackageConstants = extractConstantNames(hostPackagesFile)
        failIfNotEmpty("HostIds missing HostPackages constants", hostIdConstants - hostPackageConstants)
        failIfNotEmpty("HostPackages missing HostIds constants", hostPackageConstants - hostIdConstants)

        val hostProfileFiles = hostProfilesDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val hostProfileDeclarations = hostProfileFiles.flatMap { file ->
            Regex("""internal\s+val\s+([A-Za-z0-9_]+)\s*=\s*HostProfile\(""")
                .findAll(file.readText())
                .map { match -> file.relativePath() to match.groupValues[1] }
                .toList()
        }
        val hostProfileDeclarationNames = hostProfileDeclarations.mapTo(linkedSetOf()) { it.second }
        val hostProfileIdRefs = hostProfileFiles.flatMap { file ->
            Regex("""id\s*=\s*HostIds\.([A-Z0-9_]+)""")
                .findAll(file.readText())
                .map { match -> file.relativePath() to match.groupValues[1] }
                .toList()
        }
        val hostProfilePackageRefs = hostProfileFiles.flatMap { file ->
            Regex("""packageName\s*=\s*HostPackages\.([A-Z0-9_]+)""")
                .findAll(file.readText())
                .map { match -> file.relativePath() to match.groupValues[1] }
                .toList()
        }
        val profileFilesWithLiteralIds = hostProfileFiles.filter { file ->
            Regex("""id\s*=\s*"[^"]+"""").containsMatchIn(file.readText())
        }.map { it.relativePath() }
        val profileFilesWithLiteralPackages = hostProfileFiles.filter { file ->
            Regex("""packageName\s*=\s*"[^"]+"""").containsMatchIn(file.readText())
        }.map { it.relativePath() }
        failIfNotEmpty("Host profiles must use HostIds constants", profileFilesWithLiteralIds)
        failIfNotEmpty("Host profiles must use HostPackages constants", profileFilesWithLiteralPackages)

        val hostProfileIdRefConstants = hostProfileIdRefs.mapTo(linkedSetOf()) { it.second }
        val hostProfilePackageRefConstants = hostProfilePackageRefs.mapTo(linkedSetOf()) { it.second }
        failIfNotEmpty("HostIds constants missing host profile", hostIdConstants - hostProfileIdRefConstants)
        failIfNotEmpty("HostPackages constants missing host profile", hostPackageConstants - hostProfilePackageRefConstants)
        failIfNotEmpty("Host profile references undefined HostIds", hostProfileIdRefConstants - hostIdConstants)
        failIfNotEmpty(
            "Host profile references undefined HostPackages",
            hostProfilePackageRefConstants - hostPackageConstants,
        )
        val hostProfileConstantMismatches = hostProfileFiles.flatMap { file ->
            val text = file.readText()
            val idRefs = Regex("""id\s*=\s*HostIds\.([A-Z0-9_]+)""")
                .findAll(text)
                .map { match -> match.groupValues[1] }
                .toList()
            val packageRefs = Regex("""packageName\s*=\s*HostPackages\.([A-Z0-9_]+)""")
                .findAll(text)
                .map { match -> match.groupValues[1] }
                .toList()
            if (idRefs.size == 1 && packageRefs.size == 1 && idRefs.single() == packageRefs.single()) {
                emptyList()
            } else {
                listOf("${file.relativePath()} idRefs=${idRefs.joinToString()} packageRefs=${packageRefs.joinToString()}")
            }
        }
        failIfNotEmpty("Host profile id/package constant mismatch", hostProfileConstantMismatches)

        val hostRegistryText = hostRegistryFile.readText()
        val hostRegistryProfilesBody = Regex(
            """private\s+val\s+profiles\s*=\s*listOf\(([\s\S]*?)\)""",
        ).find(hostRegistryText)?.groupValues?.get(1).orEmpty()
        val hostRegistryProfileRefs = hostRegistryProfilesBody
            .lines()
            .map { line -> line.substringBefore("//").trim().removeSuffix(",").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        failIfNotEmpty("Host profiles missing HostRegistry registration", hostProfileDeclarationNames - hostRegistryProfileRefs)
        failIfNotEmpty("HostRegistry references unknown host profiles", hostRegistryProfileRefs - hostProfileDeclarationNames)

        val hookCatalogRegistriesFile = sourceRoot.resolve(
            "com/xiyunmn/puredupan/hook/plan/HostHookCatalogs.kt"
        )
        val dexKitTargetRegistriesFile = sourceRoot.resolve(
            "com/xiyunmn/puredupan/hook/dexkit/HostDexKitTargetRegistries.kt"
        )
        val profileHookCatalogRefs = hostProfileFiles.flatMap { file ->
            Regex("""catalogId\s*=\s*HostIds\.([A-Z0-9_]+)""")
                .findAll(file.readText())
                .map { match -> file.relativePath() to match.groupValues[1] }
                .toList()
        }
        val profileDexKitRegistryRefs = hostProfileFiles.flatMap { file ->
            Regex("""targetRegistryId\s*=\s*HostIds\.([A-Z0-9_]+)""")
                .findAll(file.readText())
                .map { match -> file.relativePath() to match.groupValues[1] }
                .toList()
        }
        val profileFilesWithLiteralHookCatalogIds = hostProfileFiles.filter { file ->
            Regex("""catalogId\s*=\s*"[^"]+"""").containsMatchIn(file.readText())
        }.map { it.relativePath() }
        val profileFilesWithLiteralDexKitRegistryIds = hostProfileFiles.filter { file ->
            Regex("""targetRegistryId\s*=\s*"[^"]+"""").containsMatchIn(file.readText())
        }.map { it.relativePath() }
        failIfNotEmpty("Host profiles must use HostIds for hook catalog ids", profileFilesWithLiteralHookCatalogIds)
        failIfNotEmpty("Host profiles must use HostIds for DexKit registry ids", profileFilesWithLiteralDexKitRegistryIds)

        val registeredHookCatalogRefs = Regex("""HostIds\.([A-Z0-9_]+)\s+to\s+[A-Za-z0-9_]+""")
            .findAll(hookCatalogRegistriesFile.readText())
            .map { match -> match.groupValues[1] }
            .toList()
        val registeredDexKitRegistryRefs = Regex("""HostIds\.([A-Z0-9_]+)\s+to\s+[A-Za-z0-9_]+""")
            .findAll(dexKitTargetRegistriesFile.readText())
            .map { match -> match.groupValues[1] }
            .toList()
        val profileHookCatalogConstants = profileHookCatalogRefs.mapTo(linkedSetOf()) { it.second }
        val profileDexKitRegistryConstants = profileDexKitRegistryRefs.mapTo(linkedSetOf()) { it.second }
        val registeredHookCatalogConstants = registeredHookCatalogRefs.toSet()
        val registeredDexKitRegistryConstants = registeredDexKitRegistryRefs.toSet()
        failIfNotEmpty("Duplicate hook catalog registrations", duplicateValues(registeredHookCatalogRefs))
        failIfNotEmpty("Duplicate DexKit registry registrations", duplicateValues(registeredDexKitRegistryRefs))
        failIfNotEmpty("Hook catalog registrations reference undefined HostIds", registeredHookCatalogConstants - hostIdConstants)
        failIfNotEmpty("DexKit registry registrations reference undefined HostIds", registeredDexKitRegistryConstants - hostIdConstants)
        failIfNotEmpty("Host profile hook catalog refs missing HostIds", profileHookCatalogConstants - hostIdConstants)
        failIfNotEmpty("Host profile DexKit registry refs missing HostIds", profileDexKitRegistryConstants - hostIdConstants)
        failIfNotEmpty("Host hook catalogs missing registrations", profileHookCatalogConstants - registeredHookCatalogConstants)
        failIfNotEmpty("Hook catalog registrations unused by host profiles", registeredHookCatalogConstants - profileHookCatalogConstants)
        failIfNotEmpty("Host DexKit registries missing registrations", profileDexKitRegistryConstants - registeredDexKitRegistryConstants)
        failIfNotEmpty("DexKit registry registrations unused by host profiles", registeredDexKitRegistryConstants - profileDexKitRegistryConstants)

        val featureKeysFile = sourceRoot.resolve(
            "com/xiyunmn/puredupan/hook/config/model/FeatureKeys.kt"
        )
        val configManagerFile = sourceRoot.resolve(
            "com/xiyunmn/puredupan/hook/config/ConfigManager.kt"
        )
        val settingsUserStateFile = sourceRoot.resolve(
            "com/xiyunmn/puredupan/hook/settings/registry/SettingsUserState.kt"
        )
        val featureSetsDir = sourceRoot.resolve("com/xiyunmn/puredupan/hook/host/features")
        val catalogsDir = sourceRoot.resolve("com/xiyunmn/puredupan/hook/plan/catalogs")
        val uiSettingsDir = sourceRoot.resolve("com/xiyunmn/puredupan/hook/ui/settings")

        val featureKeys = extractConstants(
            listOf(featureKeysFile),
            Regex("""const\s+val\s+(KEY_[A-Z0-9_]+)\s*="""),
        )
        val configAliases = extractConstants(
            listOf(configManagerFile),
            Regex("""const\s+val\s+(KEY_[A-Z0-9_]+)\s*=\s*FeatureKeys\."""),
        )
        val settingsAliases = extractConstants(
            listOf(settingsUserStateFile),
            Regex("""const\s+val\s+(KEY_[A-Z0-9_]+)\s*=\s*ConfigManager\."""),
        )
        val featureSetFiles = featureSetsDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val featureSetRefs = extractConstants(
            featureSetFiles,
            Regex("""FeatureKeys\.(KEY_[A-Z0-9_]+)"""),
        )
        val catalogRefs = extractConstants(
            catalogsDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList(),
            Regex("""FeatureKeys\.(KEY_[A-Z0-9_]+)"""),
        )
        val uiRefs = extractConstants(
            uiSettingsDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList(),
            Regex("""SettingsUserState\.(KEY_[A-Z0-9_]+)"""),
        )

        failIfNotEmpty("FeatureKeys missing ConfigManager aliases", featureKeys - configAliases)
        failIfNotEmpty("ConfigManager aliases missing SettingsUserState aliases", configAliases - settingsAliases)
        failIfNotEmpty("UI setting keys missing from feature sets", uiRefs - featureSetRefs)
        failIfNotEmpty("Catalog keys missing from feature sets", catalogRefs - featureSetRefs)
        failIfNotEmpty("Feature set keys missing FeatureKeys definitions", featureSetRefs - featureKeys)
        failIfNotEmpty("SettingsUserState keys missing FeatureKeys definitions", settingsAliases - featureKeys)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(validateXposedScope, validateHookArchitecture)
}

listOf("debug", "beta", "release").forEach { buildType ->
    val capitalizedBuildType = buildType.replaceFirstChar { it.uppercase() }
    val apkFileName = apkFileNameForBuildType(buildType)
    val apkDirProvider = layout.buildDirectory.dir("outputs/apk/$buildType")
    val renameTask = tasks.register("rename${capitalizedBuildType}Apk") {
        inputs.dir(apkDirProvider)
        outputs.file(apkDirProvider.map { it.file(apkFileName) })

        doLast {
            val apkDir = apkDirProvider.get().asFile
            if (!apkDir.isDirectory) {
                return@doLast
            }

            val target = apkDir.resolve(apkFileName)
            val sourceApks = apkDir.listFiles { file ->
                file.isFile && file.extension == "apk" && file.name != apkFileName
            }.orEmpty()

            if (sourceApks.isEmpty()) {
                return@doLast
            }
            if (sourceApks.size > 1) {
                throw GradleException(
                    "Expected one APK in ${apkDir.path}, found: ${sourceApks.joinToString { it.name }}"
                )
            }

            val sourceApk = sourceApks.single()
            sourceApk.copyTo(target, overwrite = true)
            sourceApk.delete()

            val metadataFile = apkDir.resolve("output-metadata.json")
            if (metadataFile.isFile) {
                metadataFile.writeText(
                    metadataFile.readText().replace(sourceApk.name, target.name)
                )
            }
        }
    }

    tasks.matching { it.name == "assemble$capitalizedBuildType" }.configureEach {
        finalizedBy(renameTask)
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    implementation(libs.dexkit)
}
