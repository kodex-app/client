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

        // Dev-channel builds ship as a separate package (app.kodex.client.dev) so they install
        // alongside the production app instead of fighting it for the same applicationId. CI sets
        // APP_ID_SUFFIX=.dev on the dev branch; empty everywhere else.
        val appIdSuffix = System.getenv("APP_ID_SUFFIX")?.trim().orEmpty()
        applicationIdSuffix = appIdSuffix.takeIf { it.isNotEmpty() }
        // Same reason the package differs: two identically-named launcher icons are unusable.
        manifestPlaceholders["appLabel"] =
            if (appIdSuffix.isEmpty()) "Kodex" else "Kodex ${appIdSuffix.removePrefix(".").uppercase()}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The real release key never lands in the repo: CI decodes the KEYSTORE_BASE64 secret to
    // keystore/release.jks (gitignored) and passes the rest as environment variables. Without that
    // file the release config is simply absent, so local `assembleRelease` produces an unsigned APK
    // rather than silently signing with a throwaway key.
    val releaseStore = rootProject.file("keystore/release.jks").takeIf { it.exists() }

    signingConfigs {
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
        getByName("release") {
            // R8 + resource shrinking. material-icons-extended bundles thousands of vectors and the
            // app uses a couple of dozen; without shrinking they all ship. Rules live in
            // proguard-rules.pro — mostly keeps for reflection-driven kotlinx.serialization.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        implementation(libs.compose.uiToolingPreview)
    }
}
