# 3. Data Structures & Storage

## The Book object — the heart of the app

Everything revolves around one object shape, produced by `extract.js` and completed by
`saveExtracted()` in `app.js`. Here is a real example with every field explained:

```js
{
  // ---- identity ----
  id: "bk_1783098541480_yjdlop",   // unique: "bk_" + timestamp + random suffix
  title: "The Art of Testing",     // from PDF metadata / first Word heading / filename
  author: "Vox Fixture",           // from metadata, or a label like "Word document"
  type: "pdf",                     // "pdf" | "docx" | "txt" | "text" (pasted)

  // ---- content ----
  pages: 3,                        // PDF page count (1 for docx/text)
  blocks: [                        // paragraphs & headings, in reading order
    { tag: "p",  text: "Chapter One. The beginning…", page: 1 },
    { tag: "h1", text: "A Heading",                   page: 1 },
    ...
  ],
  sentences: [                     // THE playback unit. Short keys to keep storage small:
    { t: "Chapter One.",           //   t = text of the sentence
      b: 0,                        //   b = index into blocks[] (which paragraph it belongs to)
      p: 1,                        //   p = page number (1-based)
      w: 2 },                      //   w = word count (used for time estimates)
    ...
  ],
  wordCount: 214,                  // total words (sum of all sentence.w)
  cover: "data:image/jpeg;base64,…", // thumbnail: PDF page 1 render, or a generated gradient
  file: Blob,                      // the ORIGINAL file (PDFs need it for the page view;
                                   // null for pasted text)

  // ---- reading state ----
  sentenceCount: 42,               // sentences.length, duplicated so list views don't
                                   // need to load the heavy sentences array
  cursor: 17,                      // reading position = index of the current sentence.
                                   // This one integer IS the "resume where you left off" feature.
  bookmarks: [                     // saved positions (🔖 button)
    { sentence: 17, page: 2, at: 1783098700000 }
  ],
  addedAt: 1783098541480,          // Date.now() when imported
  lastOpenedAt: 1783099000000      // drives "Continue Listening" ordering
}
```

### Why sentences and not just text?

Three reasons, and they shape the whole app:

1. **TTS reliability.** Speech engines choke on long inputs (Chrome cuts utterances off
   after ~15 seconds). Short sentences (capped at 280 chars in `extract.js`) always play
   fully.
2. **Position tracking.** "Sentence #17" is a stable, tiny thing to save. Resume, seek,
   skip ±10s, and the progress bar are all arithmetic over the sentence list.
3. **Highlighting.** Each sentence maps 1:1 to a `<span data-si>` element, so following
   along is just toggling a CSS class.

### The `b` and `p` back-references

A sentence knows which **block** (paragraph) and **page** it came from. This lets the
reader rebuild the visual paragraph structure (`renderReaderText`) and lets the PDF
"original view" auto-scroll to the right page while listening (`scrollPdfToCurrent`).

## Time math (in `player.js`)

There is no audio file, so durations are **estimates from word counts**, assuming
narration at `BASE_WPM = 170` words per minute, scaled by the speed setting:

```
wordsPerSec = 170 × rate / 60
elapsedSec  = (words before cursor) / wordsPerSec
totalSec    = totalWords / wordsPerSec
```

To make "words before sentence N" an O(1) lookup, `player.load()` precomputes a
**prefix-sum array** `cumWords`, where `cumWords[i]` = total words in sentences `0..i-1`:

```
sentences (words):  [5, 12, 8, 20, …]
cumWords:           [0,  5, 17, 25, 45, …]
```

- Progress fraction = `cumWords[cursor] / totalWords`.
- "Skip forward 10 seconds" = walk forward through sentences until you've passed
  `10 × wordsPerSec` words (`skip()`).
- Dragging the slider = binary-search `cumWords` for the sentence at that fraction
  (`seekToProgress()`).

This is the only slightly "algorithmic" code in the app, and it's ~30 lines.

## Storage: IndexedDB via `db.js`

**IndexedDB** is the browser's built-in database — think of it as a key-value store that
can hold large binary objects (like our PDF blobs), is asynchronous, and persists across
restarts. Its raw API is callback-heavy, so `db.js` wraps it in small `Promise`-based
functions.

Database name: `voxreader`, version 1, two object stores:

```
books     keyPath: "id"      one record per imported book (the full object above,
                             including the file Blob and sentences array)
settings  keyPath: "key"     tiny { key, value } records
```

### The API surface (all of it)

| Function | What it does | Notes |
|----------|-------------|-------|
| `putBook(book)` | Insert or replace a whole book | |
| `getBook(id)` | Load one full book | Used when opening the reader |
| `listBooks()` | Load ALL books **minus** `file`, `sentences`, `blocks` | The "light" list that Home/Library render — avoids pulling megabytes just to draw covers |
| `updateBook(id, patch)` | Read-modify-write a few fields | Used for `cursor`, `lastOpenedAt` — never rewrites the blob from memory |
| `deleteBook(id)` | Remove a book | |
| `getSetting(key, fallback)` / `setSetting(key, value)` | Settings | |
| `storageEstimate()` | Bytes used (Profile screen) | |
| `requestPersistence()` | Asks the browser not to evict our data under storage pressure | |

### Settings keys currently in use

| Key | Type | Meaning |
|-----|------|---------|
| `rate` | number | Playback speed (0.5 – 3.5) |
| `voiceURI` | string | Selected voice id |
| `fontScale` | number | Reader text size multiplier (0.7 – 1.6) |
| `mediaNotif` | boolean | Show media controls notification |
| `reminders` | boolean | "Continue listening" reminder notifications |

## In-memory state (`app.js`)

```js
const state = {
  books: [],           // the LIGHT book list (no blobs) — what Home/Library render
  currentBookId: null, // which book the player has loaded
  pdfDoc: null,        // open pdf.js document for the original view
  readerView: 'text',  // 'text' | 'original'
  fontScale: 1,
  saveTimer: null,     // debounce handle for saving the cursor
};
```

The full book (with sentences) lives inside `player.book` while it's loaded. When you
switch books, the old one is simply replaced — nothing else to clean up.

### When is the reading position saved?

Writing to IndexedDB on every sentence would be wasteful, so `scheduleSavePosition()`
debounces it: at most one write every 3 seconds while playing, plus an immediate save on
pause, on closing the reader, and when the app is backgrounded (`visibilitychange` /
`pagehide`). Worst case after a crash you lose ~3 seconds of position.

## A note on sizes

A 300-page PDF typically becomes: the original blob (a few MB) + ~5,000 sentence objects
(a few hundred KB as stored JSON) + a ~20 KB cover JPEG. IndexedDB on Android comfortably
holds hundreds of MB, and the Profile screen shows actual usage via `storageEstimate()`.
