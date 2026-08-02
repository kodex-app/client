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
    ui/reader/  image reader (comic/PDF) + reader/ebook/ (EPUB · MOBI/KF8 · FB2)
  commonMain/composeResources/files/
    foliate/    vendored foliate-js engine (same copy the web UI ships)
    reader/     reader.js — the page that drives foliate
  androidMain/  MainActivity + Android engine/manifest
  iosMain/      MainViewController + iOS engine
  desktopMain/  desktop window entry point
iosApp/         SwiftUI shell (Xcode project must be generated on a Mac — see iosApp/README.md)
```

## Ebook reader

Reflowable books (EPUB, MOBI/KF8, FB2) render with **foliate-js** — the same engine the web UI uses —
inside a WebView. All the chrome (bars, TOC, settings, bookmarks, chapter navigation) is native
Compose; only the page itself is web content.

The engine, the host page and the book's bytes are all served by a small **in-app loopback HTTP
server** (`ui/reader/ebook/EbookHost.kt`):

- foliate-js is a set of ES modules and expects to `fetch` book resources — neither works from a
  `file://` or `data:` document, so it needs a real origin.
- Book bytes are proxied from the Kodex server with the `X-API-Key` attached on the Kotlin side,
  rather than handing a key to a page that renders untrusted book markup.
- Each open reader gets a random path token; requests with an unknown token 404. This matters on
  Android, where any other app can reach the port.

Both directions of the bridge go over that same connection: the page POSTs events (`ready`,
`relocate`, `tap`) to `./event` and long-polls `./commands` for page turns, seeks and settings
changes. It deliberately does **not** use the WebView's `evaluateJavaScript` — that is a different
implementation per platform and never arrived at all on desktop's Chromium backend, which left the
book rendered but impossible to page through.

Progress persists as a foliate **CFI** plus a fraction (`locator`/`fraction` on read-progress), with a
coarse page proxy so existing progress bars and "continue reading" keep working.

Desktop has no system WebView, so it downloads Chromium (KCEF, ~100 MB) the first time an ebook is
opened; the reader shows that as progress. Android and iOS use the system WebView.

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

Live checks against a running server (host/key read from `../kodex/.env.test`):

```bash
./gradlew :composeApp:verifyApi            # KodexApi deserializes real responses
./gradlew :composeApp:verifyEbookHost      # reader host: assets, proxy routes, token/traversal guards
./gradlew :composeApp:verifyEbookRender    # drives a real WebView: renders a book, then pages/seeks it
```

`verifyEbookRender` needs an EPUB in the library and downloads Chromium on first run.

## Status

Scaffold: multi-server login + main scaffold with 5-tab bottom nav (Home · Libraries · Recents ·
Browse · More). Tab bodies past login are placeholders pending feature work.

> TODO: move stored API keys from plaintext prefs into the platform keystore/keychain.
