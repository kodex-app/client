/**
 * Engine half of the Kodex ebook reader — a port of the web UI's `epubLoader.ts` plus the foliate
 * plumbing inside `FoliateEpubReader.vue`. Everything visible (bars, menus, settings, TOC) is native
 * Compose; this file only owns the book itself.
 *
 * It talks to Kotlin over two narrow channels, both provided by `EbookHost`:
 *   - down: global `kdx*` functions, invoked with the WebView's evaluateJavaScript
 *   - up:   JSON POSTed to ./event  (`ready`, `relocate`, `tap`, `key`, `error`)
 *
 * Paths are relative, so they resolve under the caller's per-session token prefix.
 */

const CONFIG = window.KDX_CONFIG || {}
const EVENT_URL = new URL('./event', location.href).href

function post(payload) {
  // keepalive so the last relocate before a chapter swap isn't dropped with the page.
  try {
    fetch(EVENT_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      keepalive: true,
    })
  } catch {
    /* the host went away (reader closed mid-flight) — nothing useful to do */
  }
}

const clamp = (n, lo, hi) => Math.min(hi, Math.max(lo, n))

// ── Theming / typography ─────────────────────────────────────────────────────────────────────────
// Kept identical to the web reader so a book looks the same in both, and so the prefs written by one
// are meaningful to the other.
const THEME_COLORS = {
  light: { fg: '#1b1b1b', bg: '#ffffff', link: '#1565c0' },
  sepia: { fg: '#5b4636', bg: '#f4ecd8', link: '#9a5b2e' },
  dark: { fg: '#cfd2d6', bg: '#181a1b', link: '#6ab0ff' },
}

const GENERIC_STACKS = {
  serif: 'Georgia, "Times New Roman", serif',
  sans: 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
  mono: 'ui-monospace, "Cascadia Code", Consolas, monospace',
}

const SRC_FORMAT = { woff2: 'woff2', woff: 'woff', ttf: 'truetype', otf: 'opentype' }

/** Custom (user-uploaded) fonts are proxied by the host, so a plain relative URL is enough. */
function customFamily(id) {
  return `kdxfont-${id}`
}
function customFaceRule(font) {
  const url = new URL(`./font/${encodeURIComponent(font.id)}`, location.href).href
  const format = SRC_FORMAT[font.format] || 'woff2'
  return `@font-face{font-family:'${customFamily(font.id)}';font-display:swap;src:url('${url}') format('${format}');}`
}

/**
 * Resolves a stored `fontFamily` into the CSS the reader needs. The web additionally ships six OFL
 * faces as bundled webfonts; the app doesn't carry those megabytes, so `bundled:<id>` degrades to a
 * plain family name — it renders if the platform happens to have the face and falls back to serif
 * otherwise. That keeps prefs written in the browser readable here instead of erroring on them.
 */
function resolveFont(value, fonts) {
  if (!value || value === 'publisher') return { faceCss: '', stack: null }
  if (GENERIC_STACKS[value]) return { faceCss: '', stack: GENERIC_STACKS[value] }
  if (value.startsWith('bundled:')) {
    const name = value.slice('bundled:'.length).replace(/-/g, ' ')
    return { faceCss: '', stack: `'${name}', serif` }
  }
  if (value.startsWith('custom:')) {
    const id = value.slice('custom:'.length)
    const font = (fonts || []).find((f) => f.id === id)
    if (font) return { faceCss: customFaceRule(font), stack: `'${customFamily(id)}', serif` }
  }
  return { faceCss: '', stack: null }
}

function contentStyles(p) {
  const c = THEME_COLORS[p.theme] || THEME_COLORS.light
  const font = resolveFont(p.fontFamily, CONFIG.fonts)
  const lh = ((p.lineHeight / 100) * 1.5).toFixed(3)
  return `
    ${font.faceCss}
    html {
      color-scheme: ${p.theme === 'dark' ? 'dark' : 'light'};
      /* foliate paints the full-bleed page background (margins + gaps) from these, and
         --override-color replaces the publisher's own page background — so switching theme
         recolours the whole page, not just the text column. */
      --theme-bg-color: ${c.bg};
      --override-color: true;
    }
    html, body { color: ${c.fg} !important; background: ${c.bg} !important; }
    body { font-size: ${p.fontSize}% !important; }
    body, p, li, blockquote, div { line-height: ${lh} !important; }
    ${font.stack ? `body, body * { font-family: ${font.stack} !important; }` : ''}
    ${p.textAlign === 'left' ? 'p, li, blockquote, dd { text-align: left !important; }' : ''}
    ${p.textAlign === 'justify' ? 'p, li, blockquote, dd { text-align: justify !important; -webkit-hyphens: auto; hyphens: auto; }' : ''}
    ${p.indent != null ? `p { text-indent: ${p.indent}em !important; }` : ''}
    a, a:visited { color: ${c.link} !important; }
    img { max-width: 100% !important; height: auto !important; }
  `
}

// ── Book loading ─────────────────────────────────────────────────────────────────────────────────

/**
 * A foliate-js EPUB loader backed by the host's proxy: the Kodex server holds the EPUB and
 * random-accesses the zip, so the whole file is never downloaded. foliate resolves hrefs against the
 * OPF and calls loadText/loadBlob with full zip-internal paths, which `./resource?href=` reads by
 * exact entry name.
 */
async function openEpub() {
  const { EPUB } = await import('./foliate/epub.js')
  const manifest = await (await fetch(new URL('./manifest', location.href))).json()
  const sizes = new Map((manifest.entries || []).map((e) => [e.name, e.size]))
  const entries = (manifest.entries || []).map((e) => ({ filename: e.name }))

  const resourceUrl = (name) => new URL(`./resource?href=${encodeURIComponent(name)}`, location.href).href
  const loadText = async (name) => {
    const res = await fetch(resourceUrl(name))
    return res.ok ? res.text() : null
  }
  const loadBlob = async (name) => {
    const res = await fetch(resourceUrl(name))
    return res.ok ? res.blob() : null
  }
  const getSize = (name) => sizes.get(name) ?? 0

  return new EPUB({ entries, loadText, loadBlob, getSize, sha1: undefined }).init()
}

/** Inflate a zlib/deflate stream — foliate's MOBI reader needs it for KF8 FLATE resources and fonts. */
async function unzlib(data) {
  let lastErr
  for (const format of ['deflate', 'deflate-raw']) {
    try {
      const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream(format))
      return new Uint8Array(await new Response(stream).arrayBuffer())
    } catch (e) {
      lastErr = e
    }
  }
  throw lastErr instanceof Error ? lastErr : new Error('Failed to inflate stream')
}

/** MOBI/KF8/FB2 are parsed from the raw file rather than streamed by entry, so fetch it once. */
async function openWholeFile(format) {
  const res = await fetch(new URL('./file', location.href))
  if (!res.ok) throw new Error(`Failed to download book (${res.status})`)
  const blob = await res.blob()
  if (format === 'fb2') {
    const { makeFB2 } = await import('./foliate/fb2.js')
    return makeFB2(blob)
  }
  // foliate's MOBI reader auto-detects MOBI6 vs KF8/AZW3 from the file itself.
  const { MOBI } = await import('./foliate/mobi.js')
  return new MOBI({ unzlib }).open(blob)
}

// ── View lifecycle ───────────────────────────────────────────────────────────────────────────────

let view = null
let prefs = CONFIG.prefs || {}
let sectionTotal = 1
let atStart = false
let atEnd = false

function applyPrefs(next) {
  prefs = next
  if (!view || !view.renderer) return
  // Update the injected CSS (incl. --theme-bg-color) FIRST, then re-set the renderer attributes:
  // each observed-attribute set triggers a paginator re-render which re-samples the page background
  // with the new colours. Applying styles last would repaint against the previous theme.
  view.renderer.setStyles?.(contentStyles(next))
  view.renderer.setAttribute('flow', next.flow)
  view.renderer.setAttribute('max-inline-size', '720px')
  view.renderer.setAttribute('gap', '6%')
  // Column cap: 'one' forces single; 'auto'/'two' let width decide up to two.
  view.renderer.setAttribute('max-column-count', next.columns === 'one' ? '1' : '2')
  view.renderer.setAttribute('margin-left', `${next.margin}px`)
  view.renderer.setAttribute('margin-right', `${next.margin}px`)
  if (next.flow === 'paginated') view.renderer.setAttribute('animated', '')
  else view.renderer.removeAttribute('animated')
}

function onRelocate(e) {
  const d = e.detail || {}
  const foliateFrac = typeof d.fraction === 'number' ? d.fraction : 0
  let frac = foliateFrac
  // foliate's `fraction` is end-of-page-inclusive: on a single-section document (a one-chapter novel
  // EPUB, or any source chapter) page 1 of N already reads as 1/N, so the bar jumps off zero on open.
  // When paginated over a single section, derive the *displayed* progress from the page index so it
  // runs a true 0→100%. Boundary state must still come from foliate's own getters: in two-column mode
  // `pages` is a rounded spread count that undercounts, which would report "at end" a spread early
  // and block turning to the last page.
  if (sectionTotal <= 1 && prefs.flow === 'paginated' && view && view.renderer) {
    const pages = view.renderer.pages ?? 0
    const page = view.renderer.page ?? 0
    if (pages > 1) frac = clamp(page / (pages - 1), 0, 1)
    atStart = !!view.renderer.atStart
    atEnd = !!view.renderer.atEnd
    // Pin the exact ends to foliate's authoritative boundary state, so 100% shows only at the true
    // last page (never early from page-count rounding) and 0% at the first.
    if (atEnd) frac = 1
    else if (frac >= 1) frac = 0.99
    if (atStart) frac = 0
  } else {
    atEnd = foliateFrac >= 0.999
    atStart = foliateFrac <= 0.001
  }

  const section = d.section && typeof d.section.current === 'number' ? d.section : null
  post({
    type: 'relocate',
    fraction: frac,
    cfi: d.cfi ?? null,
    chapter: (d.tocItem && d.tocItem.label ? d.tocItem.label : '').trim(),
    sectionCurrent: section ? section.current : 0,
    sectionTotal: section ? section.total : sectionTotal,
    atStart,
    atEnd,
  })
}

/**
 * Each chapter renders in its own same-origin iframe, so events inside it never reach this document.
 * Forward the ones the native chrome needs: a tap toggles the bars (as in the image reader) and the
 * arrow keys drive page turns for anyone on a keyboard.
 */
function bindDocument(doc) {
  if (!doc || !doc.addEventListener) return
  doc.addEventListener('pointerdown', () => post({ type: 'tap' }))
  doc.addEventListener('keydown', (ev) => {
    if (ev.key === 'ArrowLeft' || ev.key === 'ArrowRight' || ev.key === ' ' || ev.key === 'Escape') {
      ev.preventDefault()
      post({ type: 'key', key: ev.key })
    }
  })
}

async function boot() {
  try {
    await import('./foliate/view.js') // registers <foliate-view>
    const format = CONFIG.format || 'epub'
    const book = format === 'epub' ? await openEpub() : await openWholeFile(format)

    view = document.createElement('foliate-view')
    document.getElementById('view').appendChild(view)
    // Bound before open() so the very first chapter document is covered too.
    view.addEventListener('load', (e) => bindDocument(e.detail && e.detail.doc))

    await view.open(book)

    sectionTotal = (book.sections && book.sections.length) || 1
    view.addEventListener('relocate', onRelocate)
    applyPrefs(prefs)

    if (CONFIG.initialLocator) await view.init({ lastLocation: CONFIG.initialLocator })
    else await view.goToFraction(CONFIG.initialFraction ?? 0)

    post({ type: 'ready', toc: flattenToc(book.toc), sectionTotal })
  } catch (e) {
    post({ type: 'error', message: e && e.message ? e.message : String(e) })
  }
}

/**
 * The TOC as a flat list with depth, which is what the native list renders. foliate nests subitems;
 * flattening here keeps the Kotlin side from having to model a recursive tree for a menu.
 */
function flattenToc(items, depth = 0, out = []) {
  for (const it of items || []) {
    out.push({ label: (it.label || '').trim(), href: it.href || '', depth })
    if (it.subitems && it.subitems.length) flattenToc(it.subitems, depth + 1, out)
  }
  return out
}

// ── Commands from Kotlin ─────────────────────────────────────────────────────────────────────────
// Delivered by long-polling ./commands rather than injected with the WebView's evaluateJavaScript:
// that API is a separate implementation per platform and never arrived at all on desktop's Chromium
// backend, which left the book rendered but impossible to page through. Polling the host we are
// already talking to is one mechanism that behaves the same everywhere.
//
// Turning past either end is NOT handled here: the native side owns cross-chapter navigation, so it
// acts on the boundary flags reported with every relocate rather than a silent no-op down here.

async function dispatch(c) {
  if (!view) return
  switch (c.cmd) {
    case 'prev':
      return view.goLeft && view.goLeft()
    case 'next':
      return view.goRight && view.goRight()
    case 'goToFraction':
      return view.goToFraction && view.goToFraction(clamp(c.fraction, 0, 1))
    case 'goTo':
      return view.goTo && view.goTo(c.href)
    case 'prefs':
      return applyPrefs(c.prefs)
    case 'section': {
      // Jump to the start of another spine section, clamped to the book's sections.
      if (!view.renderer || !view.renderer.goTo) return
      const target = clamp(c.index, 0, (c.total || 1) - 1)
      return view.renderer.goTo({ index: target, anchor: 0 })
    }
    default:
      return
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function pumpCommands() {
  const url = new URL('./commands', location.href).href
  for (;;) {
    try {
      const res = await fetch(url)
      // The token is gone once the reader closes — stop rather than spin against a dead host.
      if (res.status === 404) return
      if (!res.ok) {
        await sleep(500)
        continue
      }
      for (const c of await res.json()) {
        try {
          await dispatch(c)
        } catch (e) {
          post({ type: 'error', message: e && e.message ? e.message : String(e) })
        }
      }
    } catch {
      // Host briefly unreachable (or the poll was cut short) — back off and re-issue.
      await sleep(500)
    }
  }
}

document.addEventListener('pointerdown', () => post({ type: 'tap' }))
document.addEventListener('keydown', (ev) => {
  if (ev.key === 'ArrowLeft' || ev.key === 'ArrowRight' || ev.key === ' ' || ev.key === 'Escape') {
    ev.preventDefault()
    post({ type: 'key', key: ev.key })
  }
})

boot()
pumpCommands()
