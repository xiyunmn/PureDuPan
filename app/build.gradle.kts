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

    val moduleVersionCode = 100
    val minSupportedUserSettingsVersionCode = 20

    defaultConfig {
        applicationId = "com.xiyunmn.puredupan.hook"
        minSdk = 24
        targetSdk = 36
        versionCode = moduleVersionCode
        versionName = "1.0.0"
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
        release {
            signingConfig = signingConfigs.findByName("release")
            if (signingConfig == null) {
                project.logger.lifecycle(
                    "[WangPanHook] :app:release uses unsigned output (no release keystore configured)."
                )
            }
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

dependencies {
    compileOnly(libs.xposed.api)
}
