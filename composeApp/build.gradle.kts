import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.compose.materialIconsCore)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activityCompose)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
        }
    }
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

// Temporary: run the real client against a live server (see VerifyApi.kt). Reads host/key from
// kodex/.env.test so no secret lands in source or build config.
tasks.register<JavaExec>("verifyApi") {
    group = "verification"
    val comp = kotlin.targets.getByName("desktop").compilations.getByName("main")
    dependsOn(comp.compileTaskProvider)
    classpath = files(comp.output.allOutputs, comp.runtimeDependencyFiles)
    mainClass.set("app.kodex.client.VerifyApiKt")
    val env = rootProject.file("../kodex/.env.test")
    if (env.exists()) {
        val props = env.readLines().mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else null
        }.toMap()
        args(props["HOST"] ?: "http://localhost:26000", props["API_KEY"] ?: "")
    }
}

compose.desktop {
    application {
        mainClass = "app.kodex.client.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "app.kodex.client"
            packageVersion = "1.0.0"
        }
    }
}
