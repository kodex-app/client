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
- [ ] Role gating (`UserDto.roles`)

## Phase 1 — core consumer

- [x] Recents → **Updates** (`GET /v1/updates` infinite, day-grouped, sticky headers; tap → reader if downloaded else stream) — verified live
- [x] Recents → **History** (`GET /v1/history` infinite, day-grouped; clear today / last-7-days / all via `DELETE /v1/history`) — verified live
- [x] **Downloads** (`GET /v1/downloads`; per-job pause/resume/retry/cancel; global cancel-all/clear/retry-failed; 2s poll — SSE later) — verified live
- [x] **More** hub (Account card, Downloads · Settings · About rows, switch/sign-out)
- [~] **User settings** — `series.autoUpdateOnOpen` + `series.chapterSort` done (round-trip verified live); reader defaults + `sync.libraries` pending
- [x] **Appearance / theming (Mihon-style)** — palettes ported verbatim from `refs/mihon` (build-verified desktop+Android):
  - [x] Theme mode: System / Light / Dark (segmented control)
  - [x] AMOLED (pure-black) dark toggle (+ dark-surface-container override, per `BaseColorScheme`)
  - [x] Color-theme picker w/ preview swatches — all 13 non-deprecated Mihon palettes ported (Default, Midnight Dusk, Nord, Catppuccin, Green Apple, Strawberry Daiquiri, Tako, Yotsuba, Lavender, Teal Turquoise, Tidal Wave, Yin Yang, Monochrome); Monet handled by the dynamic-color toggle
  - [x] Dynamic color (Monet) on Android 12+ via expect/actual; null fallback → selected palette elsewhere
  - [x] Persist locally (`AppSettings` / multiplatform-settings); applied in `KodexTheme`; reached via More → Appearance

## Phase 2 — enrich existing screens

- [~] Library series list: sort (title/added/updated) · reading-status filter · grid/list toggle (persisted) · refresh (SSE-reload on scan complete) — **done**; group tabs (status/source via `/series/groups`) · WEB category chips · Mihon import still pending
- [~] Series detail: chapter-sort toggle · refresh-chapters (WEB) · mark series read/unread · multi-select (long-press) mark-read/unread + WEB download · overflow refresh-metadata — **done**; sub-series · bookmarks · analyze/migrate still pending
- [~] Book detail: re-analyze · delete (confirm dialog) · edit-metadata sheet (title/number/summary/tags, partial PATCH) — **done**; bookmarks · identifiers/links still pending
- [ ] Browse: favorites + recents quick-access · kind filter · language visibility
- [~] Source catalogue (WebBrowse): Search (query field in top bar, infinite-scroll results) · source `FilterList` filters bottom sheet (text/checkbox/tristate/select/sort/group, polymorphic round-trip) — **done** (compile + routes verified; live data blocked — no source plugins on dev server); multi-select add-to-library still pending
- [ ] Search: source/plugin mode · facet-filter sheet (library/genre/author/status/readingStatus/language/tag/label)

## Phase 3 — content management

- [ ] Libraries CRUD (LOCAL folder picker / WEB source+kind; refresh/analyze/refresh-metadata; hide+reorder nav)
- [ ] Metadata edit sheets (series + book: title/summary/publisher/status/genres/tags/labels/authors/identifiers/field-locks)
- [ ] Labels (create/rename/delete)
- [ ] Plugins (Installed: uninstall + configure · Browse/install from repository · check-updates)
- [ ] Migration: MigrateSeries (from/series/to) → MigrateRun (review + run)

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
