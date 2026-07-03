# 7. How to Extend the App — practical recipes

Each recipe lists the files you'll touch and the steps. Test with `npm test` (and by
opening the app: `cd www && python3 -m http.server 8080`). Pushing to the app branch
automatically produces a new APK release.

> **Golden rule:** UI in `app.js` / `index.html` / `app.css`; speech & timing in
> `player.js`; file formats in `extract.js`; persistence in `db.js`; Android-only
> behavior in `NativeTTS.java`.

---

## Recipe A: Change the look (colors, sizes, branding)

**Files:** `www/css/app.css`, maybe icon scripts.

1. All colors are CSS variables at the top of `app.css`. Changing
   `--accent: #5b5bf0;` re-skins buttons, sliders, progress bars and highlights at once.
2. The sentence-highlight color is the `rgba` in `.reader-text span[data-si].active`.
3. App name: `www/index.html` `<title>`, `www/manifest.webmanifest`, and
   `android/app/src/main/res/values/strings.xml`.

---

## Recipe B: Add a new setting (example: "keep screen on while reading")

**Files:** `www/index.html`, `www/js/app.js`.

1. Add a row in the Profile section of `index.html`:
   ```html
   <label class="setting-row">
     <span>Keep screen awake</span>
     <input type="checkbox" id="setting-wakelock" class="switch" />
   </label>
   ```
2. In `wireProfile()` (app.js), wire it and persist with the settings store:
   ```js
   $('#setting-wakelock').addEventListener('change', (e) =>
     db.setSetting('wakelock', e.target.checked));
   ```
3. Load it in `renderProfile()` like the existing toggles, and act on it where relevant
   (for a wake lock: `navigator.wakeLock.request('screen')` when playback starts in
   `wirePlayerEvents`'s `state` handler).

That's the whole pattern every existing setting follows — copy one.

---

## Recipe C: Support a new file format (example: EPUB)

**Files:** `www/js/extract.js` (+ a vendored parser library), `www/index.html` (accept
attribute), `www/sw.js` (cache list if you add a vendor file).

1. Vendor a parser (e.g. build a small unzip+XHTML walk, or add a library file into
   `www/vendor/` — remember: **no CDN**, the app must work offline).
2. Write `extractEpub(file)` that returns the standard shape:
   ```js
   { title, author, type: 'epub', pages: 1, blocks, sentences, cover, wordCount }
   ```
   Build `blocks` (`{tag, text, page}`) chapter by chapter, then reuse the existing
   helpers: `buildSentences(blocks)` and `makeGeneratedCover(title)`.
3. Route it in `extractFile()`:
   ```js
   if (name.endsWith('.epub')) return extractEpub(file);
   ```
4. Add `.epub` to the `<input id="file-input" accept="...">` list and a badge color in
   `app.css` (`.type-badge.epub`).
5. Add a fixture + a few checks in `test/e2e.js` (copy the DOCX section).

Because playback/UI only ever see `blocks` + `sentences`, **no other file changes**.

---

## Recipe D: Smarter sentence splitting

**File:** `www/js/extract.js` → `splitSentences()`.

The current regex splits on `.!?…` and will break "Dr. Smith" or "3.14". Improvements,
in increasing effort:

1. A do-not-split list of abbreviations (`Dr.`, `Mr.`, `e.g.`, `vs.`) checked after the
   regex pass — merge a fragment back if it ends with one.
2. Use the browser's built-in segmenter (good multilingual support, available in modern
   WebViews):
   ```js
   const seg = new Intl.Segmenter(lang, { granularity: 'sentence' });
   const parts = [...seg.segment(clean)].map(s => s.segment);
   ```
   Keep the 280-char hard cap afterwards — that limit protects the TTS engine.
3. Re-run `npm test`; the web suite asserts sentence counts on fixtures, so it will
   catch regressions.

Note: already-imported books keep their stored sentences; re-import to re-split.

---

## Recipe E: A bookmarks list screen

Bookmarks are already **saved** (`book.bookmarks`, via the 🔖 button) — there's just no UI
to view them yet. 

**Files:** `www/index.html`, `www/js/app.js`.

1. Add a sheet `#sheet-bookmarks` to `index.html` (copy the voice sheet's structure).
2. Open it from the reader's `⋮`-style area or long-press on 🔖; render rows from
   `(await db.getBook(id)).bookmarks` — show page + a text snippet
   (`book.sentences[bm.sentence].t`).
3. Tapping a row: `player.seekToSentence(bm.sentence); closeSheets();`.
4. Deletion: filter the array and `db.updateBook(id, { bookmarks })`.

---

## Recipe F: Sleep timer (stop playback after N minutes)

**Files:** `www/js/app.js` only.

1. Add a "Sleep timer" row to the speed sheet (or its own sheet) with chips
   (15/30/60 min).
2. On selection: `state.sleepTimer = setTimeout(() => player.pause(), min*60*1000);`
   clear it on manual pause (`state` event) and when starting a new one.
3. Show remaining time in the mini player subtitle if you want polish.

No player/native changes needed — `pause()` already does the right thing in both worlds.

---

## Recipe G: Change speed range or presets

**File:** `www/js/player.js` → `SPEED_PRESETS` array, and the slider's `min/max` in
`index.html` (`#speed-slider`). `setRate()` clamps to 0.5–4; Android's engine handles up
to ~4× cleanly. Everything else (time estimates, labels) adapts automatically.

---

## Recipe H: Ship a new APK release

1. Commit your changes to the app branch and push — CI builds automatically
   (or run the workflow manually from the Actions tab).
2. Grab the APK from the new GitHub Release (`apk-v1.0.0-buildN`).
3. For a "real" version bump: increase `versionCode` and `versionName` in
   `android/app/build.gradle`, `APP_VERSION` in `www/js/app.js`, the `CACHE` name in
   `www/sw.js` (this is what makes installed PWAs pick up new files!), and optionally the
   filename/tag in `.github/workflows/android-apk.yml`.

> **PWA gotcha worth memorizing:** the service worker serves cached files forever until
> its `CACHE` version string changes. If you change `www/` files and the browser keeps
> showing the old app, you forgot to bump `voxreader-v1.0.0` in `sw.js`. The APK doesn't
> have this issue (no service worker inside it).

---

## Recipe I: Add a native Android capability

Pattern to copy: `NativeTTS.java` + `NativePlayer`.

1. Add a `@PluginMethod` to `NativeTTS.java` (or a new `@CapacitorPlugin` class —
   register it in `MainActivity`).
2. Call it from JS: `window.Capacitor.Plugins.NativeTTS.myMethod({...})` → Promise.
3. Java → JS events: `notifyListeners('eventName', new JSObject().put(...))`, subscribed
   with `plugin.addListener('eventName', fn)`.
4. Always leave a web fallback (feature-detect with `isNative()`), so the PWA keeps
   working.
5. Permissions go in `AndroidManifest.xml` + the `permissions` annotation, with a
   `requestPermissionForAlias` flow — copy `requestNotifications()`.

---

## Testing your changes

```bash
npm install            # once (installs playwright + pdf-lib for tests)
npm test               # builds fixtures, runs the 56-check web suite + 20-check native-bridge suite
```

- The **web suite** (`test/e2e.js`) drives the real UI in headless Chromium with a
  deterministic fake `speechSynthesis` (headless Linux has no voices).
- The **native-bridge suite** (`test/e2e-native.js`) fakes `window.Capacitor` + the
  plugin, verifying batching, notification payloads and media-button handling.
- When you add a feature, add a `check(...)` line near the related section — the helpers
  make this a one-liner.

## Debugging tips

- **In a desktop browser:** serve `www/`, open DevTools. `window.__vox.player` and
  `window.__vox.state` are exposed exactly for poking around (try
  `__vox.player.skip(30)` in the console).
- **In the APK:** connect the phone via USB, enable USB debugging, open
  `chrome://inspect` on your desktop — you get full DevTools into the WebView, console
  logs included. Native logs: `adb logcat | grep -iE "voxreader|tts|Capacitor"`.
- **Import problems:** every failure surfaces as a toast with the thrown message; the
  full stack is in the console.
