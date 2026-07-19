# kodex-client

Compose Multiplatform mobile client for [Kodex](https://github.com/kodex-app) — Android, iOS
(iPhone/iPad), with a desktop target used as a fast dev harness.

## Stack

- Kotlin 2.1 · Compose Multiplatform 1.7.3 · AGP 8.7 (Gradle 8.11)
- Ktor 3 client (OkHttp / Darwin / CIO engines) · kotlinx.serialization
- multiplatform-settings for persistence

## Structure

```
composeApp/src/
  commonMain/   all shared UI + logic (see app/kodex/client/…)
    auth/       SessionManager — servers, active session, current user
    data/       ServerStore (persistence) + models
    network/    KodexApi (Ktor) + DTOs
    ui/         theme, login/, main/ (bottom-nav scaffold + tabs)
  androidMain/  MainActivity + Android engine/manifest
  iosMain/      MainViewController + iOS engine
  desktopMain/  desktop window entry point
iosApp/         SwiftUI shell (Xcode project must be generated on a Mac — see iosApp/README.md)
```

## Auth model (verified against the server openapi.json)

Header-based, multi-server:
1. `POST {server}/api/v1/api-keys` with **HTTP Basic** (email:password) → mints a raw key (once).
2. Store the key per server; send `X-API-Key: <key>` on every request.
3. `GET {server}/api/v1/users/me` validates the key and drives role-gated UI.

The app keeps several servers; the login screen lists them (most-recent first) and launch
auto-selects the most recently used one.

## Build / run

```bash
./gradlew :composeApp:assembleDebug          # Android APK
./gradlew :composeApp:run                     # desktop dev harness
# iOS: open iosApp in Xcode on a Mac (see iosApp/README.md)
```

`local.properties` needs `sdk.dir=<Android SDK path>` (auto-created here; gitignored).

## Status

Scaffold: multi-server login + main scaffold with 5-tab bottom nav (Home · Libraries · Recents ·
Browse · More). Tab bodies past login are placeholders pending feature work.

> TODO: move stored API keys from plaintext prefs into the platform keystore/keychain.
