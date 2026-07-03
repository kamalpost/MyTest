# 1. Overview & Architecture

## What kind of app is this?

VoxReader is a **hybrid app**: it is written once as a web app (HTML + CSS + JavaScript)
and then delivered two ways:

1. **In a browser / as a PWA.** A Progressive Web App is a website that can be
   "installed" on a phone's home screen and keeps working offline thanks to a
   *service worker* (a small script the browser runs in the background that serves cached
   files when there is no network).
2. **As an Android APK.** [Capacitor](https://capacitorjs.com) wraps the same web files in
   a native Android application. The web app runs inside a *WebView* (an embedded browser
   with no address bar). Where the WebView lacks a capability — text-to-speech and media
   notifications — we call into our own native Java code through a *plugin bridge*.

The important consequence: **95% of the app is plain JavaScript in `www/`**, and it must
work in both worlds. Wherever the two worlds differ, the code asks "am I running
natively?" and picks a path:

```js
// www/js/player.js
function isNative() {
  return !!(window.Capacitor && window.Capacitor.isNativePlatform());
}
```

## The five JavaScript modules

The app deliberately uses **no framework** (no React/Vue) and **no build step** — the
browser loads the ES modules directly. This keeps the codebase approachable: what you
read in the file is exactly what runs.

```
www/js/
  app.js       ~800 lines  The "controller". Wires buttons to actions, renders lists,
                           routes between screens. If you see it on screen, app.js put it there.
  player.js    ~500 lines  The "engine". Knows how to speak sentences, track position,
                           estimate time. Has NO idea what a button is.
  extract.js   ~300 lines  The "importer". Turns a PDF/DOCX/text file into a Book object.
  db.js        ~120 lines  The "vault". Thin wrapper around IndexedDB for books & settings.
  (sw.js)       ~70 lines  The service worker (web only). Caches files for offline.
```

**Rule of thumb for where code belongs:** anything that touches the DOM (HTML elements)
goes in `app.js`; anything about speech/timing goes in `player.js`; anything about file
formats goes in `extract.js`; anything about saving/loading goes in `db.js`.

## Life of a book (the most important flow to understand)

### Import

```
User taps the "Files" tile (or the big ＋ button)
   │  app.js: a hidden <input type="file"> opens the phone's file picker
   ▼
app.js → importFile(file)
   │  shows the spinner overlay
   ▼
extract.js → extractFile(file)
   │  decides by extension: PDF → extractPdf, .docx → extractDocx, .txt → plain text
   │  produces { title, author, blocks, sentences, cover, wordCount, ... }
   ▼
app.js → saveExtracted(...)
   │  adds an id, cursor = 0 (reading position), timestamps
   ▼
db.js → putBook(book)      ← stored in IndexedDB, survives app restarts
   ▼
app.js → openReader(id)    ← jumps straight into the reader screen
```

### Playback

```
User presses the big play button
   ▼
app.js → togglePlay() → player.play()
   │
   ├── In the browser:  WebPlayer speaks sentence #cursor with speechSynthesis,
   │                    waits for its "end" event, moves to the next sentence.
   │
   └── In the APK:      NativePlayer sends a BATCH of up to 200 sentences to the
                        Java plugin. Android's TTS engine plays them in order and
                        reports back "now speaking sentence N" events.
   ▼
player.js emits a 'sentence' event for every sentence that starts
   ▼
app.js highlights that sentence, scrolls to it, and (every few seconds)
saves the position to the database so you can resume later.
```

### Resume

When you open a book, `app.js` calls `player.load(book, book.cursor)` — `cursor` is the
sentence index you stopped at, stored inside the book record. That's the whole
"continue where you left off" feature: one integer.

## Events: how the modules talk without knowing each other

`player.js` never calls functions in `app.js`. Instead it **emits events** and `app.js`
subscribes to them (the "observer pattern"). This is what keeps the engine reusable for
both backends:

```js
// app.js — subscribing
player.on('sentence', (i) => { highlightSentence(i); ... });
player.on('state',    (info) => { updateReaderChrome(); ... });
player.on('voices',   () => renderVoiceList());
```

| Event | Fired when | Payload |
|-------|-----------|---------|
| `sentence` | A new sentence starts being spoken (or the position jumps) | sentence index |
| `state` | Play/pause/finish/error — anything that changes what buttons should show | `{ error?, finished? }` |
| `voices` | The list of available voices arrives (it can arrive late) | array of voices |

## Files at the root of the repo

| File | Purpose |
|------|---------|
| `capacitor.config.json` | Tells Capacitor the app id (`app.voxreader`), name, and that web assets live in `www/` |
| `package.json` | npm scripts (`npm test`, `npm run sync`, `npm run build:apk`) and the Capacitor dependencies |
| `www/manifest.webmanifest` | PWA manifest: icon, name, colors used when "installed" from a browser |
| `www/sw.js` | Service worker (web only): pre-caches every app file so it opens with no internet |
| `.github/workflows/android-apk.yml` | GitHub Actions pipeline: builds the signed APK and attaches it to a GitHub Release |

## What runs where (quick matrix)

| Capability | Browser / PWA | Android APK |
|------------|---------------|-------------|
| Speech | `speechSynthesis` (WebPlayer) | Android TTS engine via `NativeTTS.java` (NativePlayer) |
| Media notification | Media Session API + silent audio trick | Native `MediaSessionCompat` + notification |
| Offline files | Service worker cache | Files are inside the APK itself |
| Book storage | IndexedDB | IndexedDB (same code — the WebView has it too) |
| File picker | `<input type="file">` | Same (Capacitor forwards it to Android's picker) |
