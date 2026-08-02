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
