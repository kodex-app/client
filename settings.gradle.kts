rootProject.name = "kodex-client"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Resolves a JDK matching the Daemon JVM criteria in gradle/gradle-daemon-jvm.properties (and any
// toolchain request), downloading one when the machine has none. Without it Gradle can only use
// JDKs already installed locally.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Desktop only: the reader's WebView is Chromium (KCEF) there, and JogAmp hosts its native
        // GL bindings. Android and iOS use the system WebView and never pull from here.
        maven("https://jogamp.org/deployment/maven") {
            mavenContent { includeGroupAndSubgroups("org.jogamp") }
        }
    }
}

include(":composeApp")
