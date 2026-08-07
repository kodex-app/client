// The Android application. AGP 9 refuses a module that applies both `com.android.application` and
// the Kotlin Multiplatform plugin, so the shared code lives in :shared (an Android-KMP library)
// and this module holds only the entry point, manifest, resources and packaging/signing config.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "app.kodex.client"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.kodex.client"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The real release key never lands in the repo: CI decodes the KEYSTORE_BASE64 secret to
    // keystore/release.jks (gitignored) and passes the rest as environment variables. Without that
    // file the release config is simply absent, so local `assembleRelease` produces an unsigned APK
    // rather than silently signing with the throwaway nightly key below.
    val releaseStore = rootProject.file("keystore/release.jks").takeIf { it.exists() }

    // Stable signing key for the debug/nightly APK so a new nightly installs over a previous one
    // (Android rejects an update whose signature differs from the installed app). The default debug
    // keystore is regenerated per CI run, which is exactly what caused the "signatures don't match"
    // failure — so we ship a fixed keystore. This is a throwaway nightly key, not a release key.
    signingConfigs {
        create("nightly") {
            storeFile = rootProject.file("keystore/nightly.jks")
            storePassword = "kodexnightly"
            keyAlias = "nightly"
            keyPassword = "kodexnightly"
        }
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("nightly")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    dependencies {
        implementation(project(":shared"))
        implementation(libs.androidx.activityCompose)
        implementation(compose.preview)
    }
}
