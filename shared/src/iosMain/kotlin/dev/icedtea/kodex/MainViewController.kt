package dev.icedtea.kodex

import androidx.compose.ui.window.ComposeUIViewController

/** Bridged into the SwiftUI app in iosApp/ — hosts the shared Compose UI in a UIViewController. */
fun MainViewController() = ComposeUIViewController { App() }
