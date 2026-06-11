plugins {
    alias(libs.plugins.android.application)
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

    // 版本号配置
    val majorVersion = 1
    val minorVersion = 0
    val patchVersion = 0

    val releaseVersionCode = majorVersion * 10000 + minorVersion * 100 + patchVersion
    val betaVersionCode = releaseVersionCode + 50
    val debugVersionCode = releaseVersionCode + 99
    val baseVersionName = "$majorVersion.$minorVersion.$patchVersion"

    val minSupportedUserSettingsVersionCode = 20

    defaultConfig {
        applicationId = "com.xiyunmn.puredupan.hook"
        minSdk = 24
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

android.applicationVariants.all {
    val variant = this
    variant.outputs
        .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
        .forEach { output ->
            val appName = "PureDuPan"
            val versionName = variant.versionName
            val buildType = variant.buildType.name
            output.outputFileName = "${appName}-v${versionName}-${buildType}.apk"
        }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val appName = "PureDuPan"
            val versionName = variant.name.let {
                when {
                    it.contains("debug", ignoreCase = true) -> "${android.defaultConfig.versionName}-debug"
                    it.contains("beta", ignoreCase = true) -> "${android.defaultConfig.versionName}-beta"
                    else -> android.defaultConfig.versionName
                }
            }
            val buildType = variant.buildType ?: "release"
            output.versionName.set(versionName)
            output.versionCode.set(when (buildType) {
                "debug" -> 10099
                "beta" -> 10050
                else -> 10000
            })
        }
    }
}

dependencies {
    compileOnly(libs.xposed.api)
}
