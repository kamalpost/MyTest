# 2. UI Guide — every screen and how it's built

All markup lives in **one file**: `www/index.html`. All styling lives in
**one file**: `www/css/app.css`. There are no templates or components — each screen is a
`<main>`/`<section>` element that `app.js` shows or hides by toggling the `hidden` CSS
class.

```html
<main id="view-home"    class="view">…</main>
<main id="view-library" class="view hidden">…</main>
<main id="view-profile" class="view hidden">…</main>
<section id="view-reader" class="reader hidden">…</section>   <!-- full-screen overlay -->
```

## Navigation (routing)

`app.js` implements a tiny hash router (see `route()` / `showView()`):

- The URL hash mirrors the current tab: `#home`, `#library`, `#profile`.
- `showView(name)` hides all views, un-hides the requested one, highlights the matching
  bottom-nav button, and re-renders that screen's content.
- The **reader is not a route** — it is an overlay (`#view-reader`) that sits on top of
  whatever tab you were on. `openReader(id)` / `closeReader()` toggle it. This is why the
  Android back gesture returns you to the tab you came from.

The bottom navigation bar (`#bottom-nav`) has five items wired in `wireNav()`:

| Button | Action |
|--------|--------|
| Home / Library / Profile | `showView(...)` |
| ＋ (center) | Clicks the hidden `<input type="file" id="file-input">` |
| Reader | Re-opens the current book (or the most recently opened one) |

## Screen by screen

### Home (`#view-home`) — rendered by `renderHome()`

```
┌──────────────────────────────┐
│  Purple promo card           │  static HTML; "Try 2×" button calls applyRate(2)
│  ────────────────────────    │
│  Continue Listening          │  renderHome() builds one .book-card per book,
│  [cover] [cover] [cover] →   │  sorted by lastOpenedAt, with a progress bar
│  ────────────────────────    │
│  Import & Listen             │
│  [Files][Word][Text][Sample] │  tiles wired in wireHome()
└──────────────────────────────┘
```

- **Files / Word tiles** both open the same file input (it accepts `.pdf,.docx,.txt`).
- **Text tile** opens the paste-text bottom sheet (`#sheet-text`).
- **Sample tile** calls `importSample()` which creates a small built-in book — handy for
  testing changes without hunting for a file.

### Library (`#view-library`) — rendered by `renderLibrary()`

A vertical list of `.library-item` rows: cover thumbnail, title, a colored *type badge*
(PDF / WORD / TEXT), page count, an estimated "X minutes remaining", and a progress bar.
The search box simply filters the in-memory `state.books` array on every keystroke and
re-renders. The `⋮` button opens the book-options sheet (Listen / Restart / Delete).

### Reader (`#view-reader`) — the main experience

```
┌──────────────────────────────┐
│ ⌄            🔖  Aa  📃      │  top bar: close, bookmark, text size, view toggle (PDF only)
│      [ Book title chip ]     │
│                              │
│  Text of the book with the   │  #reader-text — every sentence is a <span data-si="N">
│  current sentence HIGHLIGHTED│  the highlight is just the CSS class .active
│  …                           │
│                              │
│ ───────────●──────────────── │  scrub slider (#reader-scrub)
│ 03:11      3 of 4      05:23 │  elapsed · page indicator · total
│  🗣️   ↺10   (▶)   10↻   1.0  │  voice · back · play/pause · forward · speed
└──────────────────────────────┘
```

Key mechanics (all in `app.js`):

- **`renderReaderText(book)`** builds the text view once per book: it walks
  `book.sentences`, groups them back into paragraphs/headings (using `sentence.b`, the
  block index), and wraps each sentence in `<span data-si="N">`. For PDFs it inserts a
  "Page N" divider whenever the page number changes.
- **Highlighting** (`highlightSentence(i)`) removes `.active` from the previous span, adds
  it to span `i`, and `scrollIntoView()`s it. That's the entire follow-along effect.
- **Tap-to-jump**: a click listener on `#reader-text` finds the closest `span[data-si]`
  and calls `player.seekToSentence(N)`.
- **Original view (PDF only)**: the 📃 button switches to `#reader-original`, where each
  page is rendered onto a `<canvas>` by pdf.js. Pages render **lazily** — an
  `IntersectionObserver` renders a page only when it scrolls near the viewport
  (`ensurePdfView()`), so a 500-page PDF doesn't freeze the app.
- **Chrome updates** (`updateReaderChrome()`): every `sentence`/`state` event refreshes
  the play icon, times, page indicator and slider position.

### Profile (`#view-profile`) — rendered by `renderProfile()`

Settings rows grouped into Playback / Notifications / Storage / About. Rows are `<button>`
or `<label>` elements styled by `.setting-row`. The toggles are ordinary checkboxes styled
into switches with pure CSS (`.switch`). Notable rows:

- **Voice / Speed** — open the same bottom sheets used by the reader.
- **Reading reminders** — requests browser notification permission; if granted, a
  notification is posted when you background the app mid-book (see the
  `visibilitychange` listener at the bottom of `app.js`).
- **Delete all books** — loops `db.deleteBook` over everything (with a `confirm()`).

## Bottom sheets (the slide-up panels)

All sheets share one pattern: a dimmed backdrop `#sheet-backdrop` plus a `.sheet` panel.
`openSheet(id)` / `closeSheets()` toggle them; clicking the backdrop closes.

| Sheet | Opened from | Contents |
|-------|------------|----------|
| `#sheet-voice` | 🗣️ button, Profile | Voices grouped by language, ✓ on the selected one, tap = select + preview |
| `#sheet-speed` | `1.0` button, Profile, promo card | Slider 0.5–3.5 plus preset chips |
| `#sheet-text` | Text tile | Title + textarea + "Save & Listen" |
| `#sheet-font` | Aa button | A− / A+ (scales `--reader-scale` CSS variable) |
| `#sheet-book` | ⋮ in Library | Listen / Restart / Delete |

## Mini player

`#mini-player` is the floating bar above the bottom nav showing the current book, a
play/pause button, and a thin progress line. `renderMiniPlayer()` shows it only when a
book is loaded **and** the reader overlay is closed. Tapping it re-opens the reader.

## Styling conventions (`www/css/app.css`)

- **Dark theme via CSS variables** at the top of the file:
  ```css
  :root { --bg:#0b0b0f; --panel:#17171d; --accent:#5b5bf0; ... }
  ```
  Change `--accent` and the whole app (buttons, highlights, progress bars) recolors.
- **Mobile-first**: the layout is a single column capped at `max-width: 560px`; on a
  desktop it looks like a phone centered on the page.
- `env(safe-area-inset-*)` paddings keep controls clear of notches and gesture bars.
- The reading-text size is `calc(17px * var(--reader-scale))` — the Aa sheet only changes
  that one variable.

## Toasts and overlays

- `toast(msg)` — the small grey pill used for every transient message (errors included).
- `#import-overlay` — full-screen spinner during import; `extractPdf` reports per-page
  progress into it ("Reading page 3 of 120…").
