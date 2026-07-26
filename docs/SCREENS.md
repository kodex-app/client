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
- [x] Readers — image (paged/continuous/auto, zoom, RTL, settings, per-series prefs) + source streaming
- [x] Series detail (header, books/chapters, read button)
- [x] Book detail (cover, metadata, read + mark read/unread)
- [x] Source-series (header, chapters, follow/download/unfollow)
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
- [x] **User settings** — `series.autoUpdateOnOpen` · `series.chapterSort` · **reader defaults** (comic/pdf: mode/direction/zoom → `reader.comic`/`reader.pdf`) · **`sync.libraries`** multi-select (empty = all)
- [x] **Appearance / theming (Mihon-style)** — palettes ported verbatim from `refs/mihon` (build-verified desktop+Android):
  - [x] Theme mode: System / Light / Dark (segmented control)
  - [x] AMOLED (pure-black) dark toggle (+ dark-surface-container override, per `BaseColorScheme`)
  - [x] Color-theme picker w/ preview swatches — all 13 non-deprecated Mihon palettes ported (Default, Midnight Dusk, Nord, Catppuccin, Green Apple, Strawberry Daiquiri, Tako, Yotsuba, Lavender, Teal Turquoise, Tidal Wave, Yin Yang, Monochrome); Monet handled by the dynamic-color toggle
  - [x] Dynamic color (Monet) on Android 12+ via expect/actual; null fallback → selected palette elsewhere
  - [x] Persist locally (`AppSettings` / multiplatform-settings); applied in `KodexTheme`; reached via More → Appearance

## Phase 2 — enrich existing screens

- [~] Library series list: sort · reading-status filter · grid/list toggle · refresh (SSE-reload) · **Group by** None/Status/Source (`/series/groups`) w/ chips · WEB category chips · **long-press multi-select** → bulk mark read/unread — **done**; Mihon import still pending (blocked — needs source plugins)
- [x] Series detail: chapter-sort · refresh-chapters · mark series read/unread · multi-select bulk · refresh-metadata · re-analyze · bookmarks (page-jump) · **edit-metadata sheet** · **sub-series rail** · **migrate**
- [x] Book detail: re-analyze · delete · edit-metadata sheet · bookmarks (page-jump) · **identifiers + external links** (tap to open)
- [~] Browse: **kind filter** chips · language grouping — **done**; favorites + recents quick-access still pending
- [~] Source catalogue (WebBrowse): Search · source `FilterList` filters (polymorphic) — **done** (live data blocked — no source plugins); multi-select add-to-library still pending
- [x] Search: library search + **facet-filter sheet** (genre/status/readingStatus/language/tag/label via `/series` + `/series/{genres,tags,languages}` + `/labels`) — facets can browse with empty query

## Phase 3 — content management

- [~] Libraries CRUD — **done**: create (LOCAL server-side folder picker via `/filesystem` · WEB source+kind), edit, delete, refresh/deep-scan/analyze (More → Libraries); nav hide+reorder still pending
- [~] Metadata edit sheets — **done**: series (title/summary/publisher/status/language/genres/tags, partial PATCH) + book (title/number/summary/tags) + book identifiers/links shown; authors/labels multi-select · field-locks still pending
- [x] Labels — create / rename / delete (More → Labels, admin-gated)
- [~] Plugins — **done**: Installed (enable/disable/update/uninstall) · Browse & install from repository · check-updates (More → Plugins, admin); per-plugin config schema · repository CRUD still pending
- [x] Migration: MigrateSeries — pick target source, find matches, choose carry-over (read/metadata/downloads), migrate (Series detail → Migrate)

## Phase 4 — server admin

- [ ] Users (list/create/edit-limits/delete)
- [ ] Admin actions (refresh-all / deep-scan / cancel-tasks / shutdown)
- [ ] Tasks queue (SSE)
- [ ] Backup/restore + scheduled auto-backups
- [ ] Network settings (proxy / DoH)
- [ ] Logs viewer (SSE)
- [ ] TOTP / 2FA account setup (optional)
- [ ] ~~ClaimPage first-run~~ — skipped on mobile

---

## Mobile adaptation rules

Tables → card lists · popovers/menus → bottom sheets · modals → full-screen / bottom-sheet forms ·
drag-reorder → drag handles · bulk actions → long-press selection + contextual bar · day-grouped feeds
→ `LazyColumn` + sticky headers + paging · admin tucked under **More → Server**, role-gated.
