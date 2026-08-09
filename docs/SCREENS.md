# Kodex mobile client — screens checklist

Porting the full web UI (`kodex/kodex-web/src/pages/*.vue`) to Compose Multiplatform, **adapted for
mobile** — idiomatic Material3, not a visual clone. Endpoints/DTOs are **verified against the live
server** before each screen is built.

Legend: `[x]` done · `[~]` partial (built, gaps listed) · `[ ]` not started

---

## Done (foundation)

- [x] Multi-server login / server picker
- [x] Main scaffold — 5-tab bottom nav (Home · Libraries · Recents · Browse · More)
- [x] Toolbar + global search entry
- [x] Back-stack navigation (`DetailHost` / `DetailRoute`)
- [x] Home (4 cover rails)
- [x] Readers — image (paged/continuous/auto, zoom, RTL, settings, per-series prefs) + source streaming; **ebook** (EPUB streamed by entry, MOBI/KF8/FB2 whole-file) on foliate-js in a WebView over an in-app loopback host — native chrome, TOC, CFI progress, bookmarks, per-series display prefs
- [x] Series detail (header, books/chapters, read button)
- [x] Book detail (cover, metadata, read + mark read/unread)
- [x] Source-series (header, chapters, **read-from-source**, follow/download/unfollow)
- [x] GitHub Actions APK build + publish + Telegram

---

## Phase 0 — cross-cutting enablers (build just-in-time)

- [x] SSE event stream (Ktor `SSE` plugin from `ktor-client-core`, `GET /api/v1/sse/events` + `X-API-Key`) → `EventBus` shared flow, provided via `LocalEventBus`, auto-reconnect; consumers use `OnServerEvent(...)`. Wired: Downloads (`DownloadStatusChanged`, poll retained for smooth progress), Updates (`LibraryScanCompleted`/`BookAdded`)
- [x] Snackbar host — app-level `SnackbarController` via `LocalSnackbar`, hosted at the root over any screen's Scaffold; wired to Downloads global actions + History clear (success + failure feedback)
- [x] Pull-to-refresh (`PullToRefreshBox`) — in the `PagedList` helper
- [x] Reusable paged infinite-list helper (`PagedListState` + `PagedList`) — Updates/History/Downloads; + `Dates.kt` day-grouping utils
- [x] Selection mode (`SelectionState` + `rememberSelection`) — long-press → contextual top bar + bulk actions; first used in Series detail
- [x] Bottom-sheet form / filter-sheet pattern (`ModalBottomSheet`) — first used by the Library view/sort/filter sheet
- [x] Role gating — `rememberIsAdmin` / `rememberIsManager` off `UserDto.roles` (for Phase 4 admin sections)

## Phase 1 — core consumer

- [x] Recents → **Updates / History** — switched via a bottom **floating toggle button** (was top sub-tabs); Updates day-grouped infinite feed (tap → reader/stream), History with clear today/7d/all
- [x] Browse — source list grouped by language; **kind + language filter** chips; per-source **favicon logo** (Google favicon service from `website`, coloured-initial fallback); tap → Popular, **Latest** button → latest feed
- [x] **Downloads** (`GET /v1/downloads`; per-job pause/resume/retry/cancel; global cancel-all/clear/retry-failed; 2s poll — SSE later) — verified live
- [x] **More** hub (Account card, Downloads · Settings · About rows, switch/sign-out)
- [x] **User settings** — `series.autoUpdateOnOpen` · `series.chapterSort` · **reader defaults** (comic/pdf: mode/direction/zoom → `reader.comic`/`reader.pdf`; ebook → `reader.epub`) · **`sync.libraries`** multi-select (empty = all)
- [x] **Appearance / theming (Mihon-style)** — palettes ported verbatim from `refs/mihon` (build-verified desktop+Android):
  - [x] Theme mode: System / Light / Dark (segmented control)
  - [x] AMOLED (pure-black) dark toggle (+ dark-surface-container override, per `BaseColorScheme`)
  - [x] Color-theme picker w/ preview swatches — all 13 non-deprecated Mihon palettes ported (Default, Midnight Dusk, Nord, Catppuccin, Green Apple, Strawberry Daiquiri, Tako, Yotsuba, Lavender, Teal Turquoise, Tidal Wave, Yin Yang, Monochrome); Monet handled by the dynamic-color toggle
  - [x] Dynamic color (Monet) on Android 12+ via expect/actual; null fallback → selected palette elsewhere
  - [x] Persist locally (`AppSettings` / multiplatform-settings); applied in `KodexTheme`; reached via More → Appearance

## Phase 2 — enrich existing screens

- [~] Library series list: **full sort** (title/name A–Z/Z–A · added · updated · total chapters · unread · last read) · reading-status filter · grid/list toggle · refresh · **Group by** None/Status/Source (WEB) as swipeable tabs · **category chips** as the category filter (WEB), combinable with grouping — matching the web, which moved categories out of grouping · **long-press multi-select** → bulk mark read/unread — **done**; Mihon import still pending (blocked)
- [x] Series detail: header (author/artist/library/source · genres/tags · **3-line summary + Read more**) · **Start Reading/Continue** button + **Read incognito** · chapter-sort · refresh-chapters · mark read/unread · multi-select bulk · edit-metadata · re-analyze · bookmarks (page-jump) · sub-series rail · migrate
- [x] Book detail: re-analyze · delete · edit-metadata sheet · bookmarks (page-jump) · **identifiers + external links** (tap to open)
- [x] Browse: **kind filter** chips · language grouping · favorites + recents quick-access (with language badges) · swipeable tabs
- [x] Source catalogue (WebBrowse): Search · source `FilterList` filters (polymorphic) · multi-select add-to-library · "in library" marks — live data still unverified (no source plugins on the test server)
- [x] **Read straight from a source while browsing** (no follow, no download): tap a chapter (long-press = incognito) · **Continue/Start reading** button off `/series-progress` · read dots + "Page N" markers · volume group headers · prev/next chapter + chapter menu inside the reader (swaps in place, back exits to the series) · progress recorded with the source series' name/cover so History resumes it with navigation intact · BOOK-kind (novel) sources open in the ebook reader via the core's ephemeral single-chapter EPUB
- [x] Search: library search + **facet-filter sheet** (genre/status/readingStatus/language/tag/label via `/series` + `/series/{genres,tags,languages}` + `/labels`) — facets can browse with empty query

## Phase 3 — content management

- [x] Libraries CRUD — create (LOCAL server-side folder picker via `/filesystem` · WEB source+kind), edit, delete, refresh/deep-scan/analyze, reorder, hide, hide-from-homepage (More → Libraries)
- [x] Metadata edit sheets — series (title/summary/publisher/status/language/genres/tags · **labels multi-select** · **locked fields**, partial PATCH) + book (title/number/summary/tags · **authors name+role rows** · **locked fields**) + book identifiers/links shown
- [x] Labels — create / rename / delete (More → Labels, admin-gated)
- [x] Plugins — Installed (enable/disable/update/uninstall · **schema-driven per-plugin settings sheet**: STRING/SECRET/BOOLEAN/INTEGER/ENUM) · Browse & install from repository · check-updates · **repository CRUD** (add/edit/enable/remove, write-only tokens) — More → Plugins, admin
- [x] Migration: MigrateSeries — pick target source, find matches, choose carry-over (read/metadata/downloads), migrate (Series detail → Migrate)

## Phase 4 — server admin

All under **More → Server**, gated on the ADMIN role (Security sits under Account, since it is the
signed-in user's own).

- [x] **Users** — list with roles/limits/2FA badges · create (email + password + roles) · edit WEB
  limits and adult-content flag · reset password · clear another user's second factor · delete
  (never yourself — the server rejects it, so the row omits the action)
- [x] **Server actions** — refresh-all · deep-scan-all · cancel-all-tasks · shutdown, each confirmed
- [x] **Tasks queue** — live off `TaskStatusChanged` SSE (no polling) · running/queued counts ·
  cancel-all (the server has no per-task cancel, so none is offered)
- [~] **Backup** — stored archives (list/restore/delete) + the auto-backup schedule (frequency,
  custom interval, keep-count, thumbnails, encryption password). Uploading an archive to restore and
  downloading one to the device stay on the web UI: both need a file picker the app doesn't have
- [x] **Network settings** — proxy (HTTP/SOCKS4/SOCKS5 + credentials) · DNS-over-HTTPS · Cloudflare
  solver; write-only password fields (blank keeps the stored one)
- [x] **Logs viewer** — recent buffer with a level filter + the server-side debug-logging toggle.
  Reads `/server/logs` on demand rather than holding the `/stream` SSE open beside the event bus
- [x] **TOTP / 2FA** (More → Security) — enroll (secret + "open in authenticator app" rather than a
  QR, since the authenticator is on the same device) · activate · disable · change own password
- [ ] ~~ClaimPage first-run~~ — skipped on mobile

---

## Mobile adaptation rules

Tables → card lists · popovers/menus → bottom sheets · modals → full-screen / bottom-sheet forms ·
drag-reorder → drag handles · bulk actions → long-press selection + contextual bar · day-grouped feeds
→ `LazyColumn` + sticky headers + paging · admin tucked under **More → Server**, role-gated.
