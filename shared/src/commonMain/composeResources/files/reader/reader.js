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

// Page-turn animations. The values are the ones stored device-side by `AppSettings.ebookPageAnim`.
const ANIM_SLIDE = 'slide'
const ANIM_FLIP = 'flip'

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
    /* Hand the gesture to foliate when paginated. Without this Android's WebView claims the pan
       itself and stops dispatching touchmove, so foliate's preventDefault() never gets a say and
       swipe-to-turn silently does nothing. Scrolled flow keeps native vertical panning. */
    html, body { touch-action: ${p.flow === 'paginated' ? 'none' : 'pan-y'}; }
    html, body { color: ${c.fg} !important; background: ${c.bg} !important; }
    body { font-size: ${p.fontSize}% !important; }
    body, p, li, blockquote, div { line-height: ${lh} !important; }
    ${font.stack ? `body, body * { font-family: ${font.stack} !important; }` : ''}
    ${p.textAlign === 'left' ? 'p, li, blockquote, dd { text-align: left !important; }' : ''}
    ${p.textAlign === 'justify' ? 'p, li, blockquote, dd { text-align: justify !important; -webkit-hyphens: auto; hyphens: auto; }' : ''}
    ${p.indent != null ? `p { text-indent: ${p.indent}em !important; }` : ''}
    a, a:visited { color: ${c.link} !important; }
    img { max-width: 100% !important; height: auto !important; }
    /* The word being read aloud (CSS Custom Highlight API — see the read-aloud section below). Amber
       reads as "here" on all three themes; the dark one is toned down so it doesn't glare. */
    ::highlight(${TTS_HIGHLIGHT}) {
      background: ${p.theme === 'dark' ? 'rgba(255,196,0,0.30)' : 'rgba(255,196,0,0.45)'};
      color: inherit;
    }
  `
}

// ── Book loading ─────────────────────────────────────────────────────────────────────────────────

// A BOOK source's chapter HTML keeps the illustrations where the source put them — on its own CDN —
// so the EPUB the core builds carries absolute `https://…` <img> URLs rather than entries of its own.
// Left alone, those are fetched by the *device*, which frequently can't have them: Hako's images are
// hotlink-protected (they want the site as Referer), the CDNs are blocked on plenty of the networks a
// phone sits on, and a server reached through a proxy has an internet the phone doesn't share. So
// point them at the host's `./image`, which fetches them through the core the same way source covers
// already load. Library books keep their images inside the EPUB and are never rewritten.
const IMG_SRC = /(<img\b[^>]*?\bsrc\s*=\s*")(https?:\/\/[^"]+)(")/gi
const XML_ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" }

/** The attribute is XML-escaped in the EPUB; the proxy needs the URL the source actually wrote. */
function unescapeXml(s) {
  return s.replace(/&(?:(amp|lt|gt|quot|apos)|#(\d+));/g, (m, name, dec) =>
    dec ? String.fromCharCode(Number(dec)) : (XML_ENTITIES[name] ?? m))
}

function proxyImages(name, text) {
  if (!CONFIG.imageProxy || !text || !/\.x?html?$/i.test(name)) return text
  return text.replace(IMG_SRC, (_, before, url, after) => {
    const proxied = new URL(`./image?url=${encodeURIComponent(unescapeXml(url))}`, location.href).href
    // Percent-encoded, so the result carries nothing the XHTML parser has to see escaped.
    return before + proxied + after
  })
}

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
    // Rewritten here rather than after the section renders, so each illustration is requested once —
    // from the host — instead of failing direct first and then being re-fetched.
    return res.ok ? proxyImages(name, await res.text()) : null
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
/** Page-turn animation: 'slide' (foliate's own), 'flip' (a page swinging over) or 'none'. */
let pageAnim = CONFIG.pageAnim || ANIM_SLIDE
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
  // Same reason as the CSS above, for the host-side element the paginator's own touch listeners are
  // bound to (the iframe only covers the chapter's own document).
  view.style.touchAction = next.flow === 'paginated' ? 'none' : 'pan-y'
  view.renderer.setAttribute('flow', next.flow)
  view.renderer.setAttribute('max-inline-size', '720px')
  view.renderer.setAttribute('gap', '6%')
  // Column cap: 'one' forces single; 'auto'/'two' let width decide up to two.
  view.renderer.setAttribute('max-column-count', next.columns === 'one' ? '1' : '2')
  view.renderer.setAttribute('margin-left', `${next.margin}px`)
  view.renderer.setAttribute('margin-right', `${next.margin}px`)
  // The host document is normally hidden behind the book, but the flip rotates the page away from
  // it — so it has to carry the theme's colour rather than the boot page's white.
  document.body.style.background = (THEME_COLORS[next.theme] || THEME_COLORS.light).bg
  applyMotion()
}

/**
 * The renderer attributes that decide how a turn moves, derived from [pageAnim].
 *
 * `slide` is foliate's own: it tracks the finger and animates the scroll. The other two take the
 * gesture off it (`no-swipe`) and make its scroll instant (`eink`), because both are drawn here —
 * `flip` as a rotation of the whole page, `none` as no animation at all. Turning is then driven by
 * [turn] alone, from the swipe handler in [bindDocument] as well as from the native chrome, so there
 * is exactly one path a page turn can take.
 */
function applyMotion() {
  if (!view || !view.renderer) return
  const r = view.renderer
  if (prefs.flow !== 'paginated' || pageAnim === ANIM_SLIDE) {
    if (prefs.flow === 'paginated') r.setAttribute('animated', '')
    else r.removeAttribute('animated')
    r.removeAttribute('eink')
    r.removeAttribute('no-swipe')
    return
  }
  r.removeAttribute('animated')
  r.setAttribute('eink', '')
  r.setAttribute('no-swipe', '')
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
    // last page (never early from page-count rounding) and 0% at the first. A document that fits on a
    // single page is at both ends at once — the end has to win, or a one-page book would sit at 0%
    // and never reach the fraction that marks it finished.
    if (atEnd) frac = 1
    else if (atStart) frac = 0
    else if (frac >= 1) frac = 0.99
  } else {
    atEnd = foliateFrac >= 0.999
    atStart = foliateFrac <= 0.001
    // Same one-page case in scrolled flow (and in any section that fits on one screen): foliate's
    // fraction never leaves 0 because nothing scrolls, so 0.999 is unreachable. The renderer reporting
    // both boundaries at once means there is nothing left to read here — treat it as finished, and let
    // a swipe hand over to the neighbouring chapter rather than dead-ending.
    if (view && view.renderer && view.renderer.atStart && view.renderer.atEnd) {
      atStart = true
      atEnd = true
      frac = 1
    }
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

// ── Turning pages ────────────────────────────────────────────────────────────────────────────────

/** Half a flip. Both halves run back to back, so a turn costs twice this. */
const FLIP_MS = 190
/** How far the page tilts before the swap happens — short of 90° so it never disappears entirely. */
const FLIP_DEG = 88
const FLIP_SHADE = 0.42

let flipping = false

/**
 * Turn one page. `next`/`prev` are geometric, matching foliate's own goRight/goLeft: `next` shows
 * what lies to the right and so swings the page leftwards, in an RTL book as much as an LTR one.
 */
async function turn(dir) {
  if (!view) return
  const go = () => (dir === 'next' ? view.goRight() : view.goLeft())
  if (pageAnim !== ANIM_FLIP || prefs.flow !== 'paginated') return go()
  // A second turn arriving mid-flip would rotate from a half-turned state and leave the stage
  // stranded if its cleanup ran second. Dropping it costs one page press at most.
  if (flipping) return
  flipping = true
  try {
    await flipPage(dir, go)
  } finally {
    flipping = false
  }
}

/**
 * The overlay that darkens the page as it tilts away, the way a lifted leaf shades itself. Lives
 * inside `#view` so the rotation carries it, and is created once per reader.
 */
function flipShade() {
  let el = document.getElementById('shade')
  if (!el) {
    el = document.createElement('div')
    el.id = 'shade'
    el.style.cssText = 'position:absolute;inset:0;background:#000;opacity:0;pointer-events:none'
    document.getElementById('view').appendChild(el)
  }
  return el
}

/**
 * A page turn drawn as a leaf swinging over: the whole page tilts away around the spine edge, the
 * turn happens while it is edge-on and invisible, and the new page swings back in from the far side.
 *
 * The page is rotated as a whole rather than a real curl of one leaf: what foliate paints is a
 * single scrolling column strip inside an iframe, and a browser gives no way to take a picture of a
 * page and animate that copy separately (this reader has no compositing surface of its own to draw
 * a curl on either). Rotating the stage is the one flip that needs no such copy — and because the
 * swap lands while the page is edge-on, what the eye gets is the same: a page leaving, then a
 * different one arriving.
 */
async function flipPage(dir, go) {
  const stage = document.getElementById('view')
  if (!stage || typeof stage.animate !== 'function') return go()
  // Turning towards the right-hand page lifts it at the left edge, and the reverse going back.
  const leftwards = dir === 'next'
  const out = leftwards ? -FLIP_DEG : FLIP_DEG
  const shade = flipShade()
  const spin = (from, to, easing) => stage.animate(
    [{ transform: `rotateY(${from}deg)` }, { transform: `rotateY(${to}deg)` }],
    { duration: FLIP_MS, easing, fill: 'forwards' },
  ).finished
  const shading = (from, to) => shade.animate(
    [{ opacity: from }, { opacity: to }],
    { duration: FLIP_MS, easing: 'linear', fill: 'forwards' },
  ).finished

  document.body.style.perspective = '1400px'
  stage.style.transformOrigin = leftwards ? 'left center' : 'right center'
  stage.style.backfaceVisibility = 'hidden'
  stage.style.willChange = 'transform'
  try {
    await Promise.all([spin(0, out, 'cubic-bezier(.4,0,1,.65)'), shading(0, FLIP_SHADE)])
    await go()
    // Two frames: one for the paginator's instant scroll to land, one for it to paint. Swapping
    // pages a frame early shows the change through the last sliver of the outgoing page.
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    await Promise.all([spin(-out, 0, 'cubic-bezier(0,.35,.6,1)'), shading(FLIP_SHADE, 0)])
  } catch {
    // An animation cancelled out from under us (the reader closing) still has to un-tilt the page.
  } finally {
    for (const a of stage.getAnimations()) a.cancel()
    for (const a of shade.getAnimations()) a.cancel()
    stage.style.transform = ''
    stage.style.transformOrigin = ''
    stage.style.backfaceVisibility = ''
    stage.style.willChange = ''
    document.body.style.perspective = ''
  }
}

// A tap toggles the native bars; a swipe belongs to foliate and must not. Distinguished by distance
// and duration, because the two gestures start identically — reporting on touchstart (as this once
// did) made every page swipe pop the toolbar open.
const TAP_SLOP_PX = 12
const TAP_MAX_MS = 400

/** How far a swipe has to travel before it counts as an attempt to leave the book. */
const EDGE_SWIPE_PX = 48

/** How far a swipe has to travel to turn a page when this file, not foliate, owns the gesture. */
const SWIPE_TURN_PX = 40

/**
 * Turn on a swipe in the modes that took the gesture off foliate (`flip`/`none` — see [applyMotion]).
 * Returns whether the swipe was spent: a swipe running off either end is not, so it falls through to
 * [reportEdgeSwipe] and becomes the native side's chapter change.
 */
function handleSwipeTurn(dx, dy, wasAtStart, wasAtEnd) {
  if (!view || prefs.flow !== 'paginated' || pageAnim === ANIM_SLIDE) return false
  if (Math.abs(dx) < SWIPE_TURN_PX || Math.abs(dx) <= Math.abs(dy)) return false
  // Dragging the page leftwards asks for what lies to its right. Whether that is forwards through
  // the book depends on its direction — the boundary flags are about the book, not the screen.
  const rtl = !!(view.book && view.book.dir === 'rtl')
  const forward = (dx < 0) !== rtl
  if (forward ? wasAtEnd : wasAtStart) return false
  turn(dx < 0 ? 'next' : 'prev')
  return true
}

/**
 * foliate swallows a swipe that would run off the first or last page: the drag is clamped to the
 * book's own bounds and snaps back, so no page turn happens and — until this — nothing was reported
 * either. On the last page of a chapter that left swiping forwards doing nothing at all, with only
 * the toolbar's skip button to get to the next one. The neighbouring chapters are the native side's
 * to know about, so report the *attempt* and let it decide (see `goNext`/`goPrev`, which raise the
 * between-chapters screen).
 *
 * [atStart]/[atEnd] are taken as they were when the gesture began, not as they are now: in scrolled
 * flow the very swipe that carries you to the end would otherwise report running off it.
 */
function reportEdgeSwipe(dx, dy, wasAtStart, wasAtEnd) {
  if (!wasAtStart && !wasAtEnd) return
  // Paginated turns are horizontal — the same axis foliate reads the swipe on. Scrolled flow runs
  // vertically, so there "past the end" is a swipe up.
  const scrolled = prefs.flow === 'scrolled'
  const along = scrolled ? dy : dx
  const across = scrolled ? dx : dy
  if (Math.abs(along) < EDGE_SWIPE_PX || Math.abs(along) <= Math.abs(across)) return
  // Dragging the content backwards moves you forwards. An RTL book swaps the two horizontal
  // directions, exactly as foliate's own goLeft/goRight do.
  const rtl = !scrolled && view && view.book && view.book.dir === 'rtl'
  const forward = (along < 0) !== rtl
  if (forward && wasAtEnd) post({ type: 'edge', dir: 'next' })
  else if (!forward && wasAtStart) post({ type: 'edge', dir: 'prev' })
}

/**
 * Each chapter renders in its own same-origin iframe, so events inside it never reach this document.
 * Bind the handlers the native chrome needs to every document: tap-to-toggle-bars, and the arrow
 * keys for anyone on a keyboard.
 */
function bindDocument(doc) {
  if (!doc || !doc.addEventListener) return

  let sx = 0
  let sy = 0
  let st = 0
  let sAtStart = false
  let sAtEnd = false
  let touching = false

  doc.addEventListener(
    'touchstart',
    (ev) => {
      const t = ev.changedTouches && ev.changedTouches[0]
      if (!t) return
      touching = true
      sx = t.screenX
      sy = t.screenY
      st = ev.timeStamp
      sAtStart = atStart
      sAtEnd = atEnd
    },
    { passive: true },
  )
  doc.addEventListener(
    'touchend',
    (ev) => {
      const t = ev.changedTouches && ev.changedTouches[0]
      if (!t) return
      const dx = t.screenX - sx
      const dy = t.screenY - sy
      const moved = Math.hypot(dx, dy)
      if (moved <= TAP_SLOP_PX && ev.timeStamp - st <= TAP_MAX_MS) post({ type: 'tap' })
      else if (!handleSwipeTurn(dx, dy, sAtStart, sAtEnd)) reportEdgeSwipe(dx, dy, sAtStart, sAtEnd)
      // Leave `touching` set briefly so the synthetic click below doesn't double-fire.
      setTimeout(() => {
        touching = false
      }, 500)
    },
    { passive: true },
  )
  doc.addEventListener(
    'touchcancel',
    () => {
      touching = false
    },
    { passive: true },
  )
  // Mouse/desktop: a click is already a completed tap, and a drag doesn't produce one. Touch also
  // synthesizes a click after touchend, which `touching` swallows so a tap doesn't toggle twice.
  doc.addEventListener('click', () => {
    if (!touching) post({ type: 'tap' })
  })

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

    // `lang` rides along so the native side can order the voice picker by the book's own
    // language before a single block has been read.
    post({ type: 'ready', toc: flattenToc(book.toc), sectionTotal, lang: ttsLang() })
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

// ── Read aloud ───────────────────────────────────────────────────────────────────────────────────
// The engine half of text-to-speech. foliate's own TTS does the hard part — walking the chapter into
// blocks and marking every word — but it only produces SSML; something else has to speak it. In the
// browser that is the Web Speech API (`tts.ts` in the web UI); here it is the platform voice on the
// Kotlin side, because neither Android's WebView nor WKWebView implements speechSynthesis.
//
// So the split is: this file owns the text and the highlight, Kotlin owns the voice. Per block we
// post the plain text; Kotlin speaks it and reports back the character it is currently saying
// (`ttsMark`) and when it has finished (`ttsDone`), which is what advances the reading.

const TTS_HIGHLIGHT = 'kdx-tts'

/** Marks of the block currently being spoken: where each word starts in the text Kotlin was given. */
let ttsMarks = []
let ttsLastMark = null
let ttsActive = false

/**
 * Flattens one SSML block into the text a synthesizer speaks plus the character offset of every
 * `<mark>`, so a word-boundary report (a character index) can be turned back into the foliate mark —
 * and from there into the live range to highlight.
 *
 * Taken verbatim, whitespace and all: normalizing would shift every offset out from under the marks,
 * and the platform engines collapse runs of whitespace themselves.
 */
function ssmlToSpeech(ssml) {
  const doc = new DOMParser().parseFromString(ssml, 'application/xml')
  const marks = []
  let text = ''
  const walk = (node) => {
    for (const child of node.childNodes) {
      if (child.nodeType === 3 || child.nodeType === 4) {
        text += child.nodeValue || ''
      } else if (child.nodeType === 1) {
        const name = child.localName.toLowerCase()
        if (name === 'mark') {
          const markName = child.getAttribute('name')
          if (markName != null) marks.push({ name: markName, index: text.length })
        } else if (name === 'break') {
          text += ' '
        } else {
          walk(child)
        }
      }
    }
  }
  walk(doc.documentElement)
  return { text, marks }
}

/**
 * Paints the spoken word inside the chapter's own document. The Custom Highlight API is used rather
 * than foliate's overlayer because it needs no CFI round-trip and leaves the book's DOM untouched —
 * a word changes a few times a second. A WebView without it still gets the scroll-along.
 */
function paintTtsHighlight(range) {
  const win = range.startContainer.ownerDocument && range.startContainer.ownerDocument.defaultView
  const highlights = win && win.CSS && win.CSS.highlights
  if (!win || !win.Highlight || !highlights) return
  try {
    highlights.set(TTS_HIGHLIGHT, new win.Highlight(range))
  } catch {
    /* the range went stale (the section re-rendered under us) — the next word repaints */
  }
}

function clearTtsHighlight() {
  for (const { doc } of (view && view.renderer ? view.renderer.getContents() : [])) {
    const highlights = doc && doc.defaultView && doc.defaultView.CSS && doc.defaultView.CSS.highlights
    if (highlights) highlights.delete(TTS_HIGHLIGHT)
  }
}

/** foliate builds its TTS over the section on screen, so it is rebuilt whenever reading leaves one. */
async function ensureTts() {
  if (!view) return null
  // `select: false` — foliate's own default selects the spoken word, which on top of the highlight
  // is a second marker and, on touch, a long-press menu waiting to happen. The page still scrolls to
  // keep the word in view.
  await view.initTTS('word', undefined, (range) => {
    if (view.renderer && view.renderer.scrollToAnchor) view.renderer.scrollToAnchor(range, false)
    paintTtsHighlight(range)
  })
  return view.tts || null
}

/** The language of the text being read, so the native side can pick a voice that speaks it. */
function ttsLang() {
  const contents = view && view.renderer ? view.renderer.getContents() : []
  const primary = contents.find((x) => x.index === view.renderer.primaryIndex) || contents[0]
  const doc = primary && primary.doc
  const lang = doc && doc.documentElement && doc.documentElement.lang
  return (lang || (view.book && view.book.metadata && view.book.metadata.language) || '').toString()
}

/**
 * Hands one block to Kotlin to speak. Blocks with nothing sayable (an illustration, a rule, stray
 * punctuation) are stepped over here rather than bouncing an empty utterance off the native engine.
 */
function ttsEmit(ssml) {
  let cur = ssml
  // Bounded, so a book that somehow keeps yielding empty blocks can't spin the page forever.
  for (let guard = 0; guard < 500; guard++) {
    if (!cur) return ttsCrossSection()
    const { text, marks } = ssmlToSpeech(cur)
    if (text.trim()) {
      ttsMarks = marks
      ttsLastMark = null
      return post({ type: 'tts-block', text, lang: ttsLang() })
    }
    cur = view.tts && view.tts.next()
  }
  return post({ type: 'tts-end' })
}

/** Blocks exhausted: carry on into the next section, or report the end of the book. */
async function ttsCrossSection() {
  const r = view && view.renderer
  if (!r || !r.nextSection) return post({ type: 'tts-end' })
  const before = r.primaryIndex
  try {
    await r.nextSection()
  } catch {
    return post({ type: 'tts-end' })
  }
  // The same section back means there was no next one (or the rest are non-linear): book finished.
  if (r.primaryIndex === before) return post({ type: 'tts-end' })
  view.tts = null
  const tts = await ensureTts()
  if (!tts) return post({ type: 'tts-end' })
  return ttsEmit(tts.start())
}

/** Highlights the word containing [charIndex] — the last mark at or before the character spoken. */
function ttsMark(charIndex) {
  let name = null
  for (const m of ttsMarks) {
    if (m.index > charIndex) break
    name = m.name
  }
  if (name == null || name === ttsLastMark) return
  ttsLastMark = name
  try {
    if (view.tts && view.tts.setMark) view.tts.setMark(name)
  } catch {
    /* the section re-rendered mid-utterance; the next word re-anchors */
  }
}

function ttsStop() {
  ttsActive = false
  ttsMarks = []
  ttsLastMark = null
  clearTtsHighlight()
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
      return turn('prev')
    case 'next':
      return turn('next')
    case 'anim':
      pageAnim = c.value || ANIM_SLIDE
      return applyMotion()
    case 'goToFraction':
      return view.goToFraction && view.goToFraction(clamp(c.fraction, 0, 1))
    case 'goTo':
      return view.goTo && view.goTo(c.href)
    case 'prefs':
      return applyPrefs(c.prefs)

    // Read aloud. The native voice drives the pace: it asks for a block, says it, reports each word
    // it reaches and then asks for the next one.
    case 'ttsStart': {
      ttsActive = true
      const tts = await ensureTts()
      if (!tts) return post({ type: 'tts-end' })
      // Start where the reader is looking, not at the top of the chapter. `from()` throws when the
      // location can't be matched to a block (an empty page, a jump caught mid-render) — exactly when
      // the section's first block is the right fallback.
      let first
      const range = view.lastLocation && view.lastLocation.range
      if (range) {
        try {
          first = tts.from(range)
        } catch {
          first = undefined
        }
      }
      return ttsEmit(first || tts.start())
    }
    case 'ttsSkip': {
      const tts = await ensureTts()
      if (!tts) return
      // `paused` makes foliate highlight the whole block it moved to; while speaking, the per-word
      // marks do that job instead.
      const ssml = c.delta < 0 ? tts.prev(!!c.paused) : tts.next(!!c.paused)
      if (!ssml) return c.delta < 0 ? undefined : ttsCrossSection()
      return ttsEmit(ssml)
    }
    case 'ttsDone':
      if (!ttsActive) return
      return ttsEmit(view.tts && view.tts.next())
    case 'ttsMark':
      return ttsMark(c.charIndex | 0)
    case 'ttsStop':
      return ttsStop()

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

// The host document gets the same treatment — taps landing in the margins around the text column
// arrive here rather than in the chapter's iframe.
bindDocument(document)

boot()
pumpCommands()
