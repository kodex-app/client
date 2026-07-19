# iosApp

SwiftUI shell that hosts the shared Compose Multiplatform UI from `:composeApp` (the Kotlin
`MainViewController()`, exported in the `ComposeApp` framework).

The Swift sources are here, but the **Xcode project (`iosApp.xcodeproj`) must be generated on a Mac**
— it can't be built on Windows (Kotlin/Native iOS + Xcode are macOS-only). On a Mac:

1. Open this folder in Xcode (or create an `iosApp` app target and add `iOSApp.swift` /
   `ContentView.swift`).
2. Add a "Run Script" build phase (before "Compile Sources") that calls the Compose framework task:
   ```
   cd "$SRCROOT/.."
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```
3. Set `Framework Search Paths` to `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.
4. Link the `ComposeApp` framework, then build & run.

Everything else (login, multi-server, navigation, API) is already in `:composeApp/src/commonMain`
and needs no per-platform code.
