# VoxReader 📖🔊

Turn any **PDF or Word document into an audiobook** — right on your device, fully offline.

VoxReader ships two ways from the same codebase:

1. **Android APK** (Capacitor) — a real installable app, no browser needed. Every push to the app branch builds a signed APK on GitHub Actions and attaches it to a **GitHub Release**: download `VoxReader-v1.0.0.apk` on your phone, open it, allow "install from unknown sources", done. In the APK, speech runs on the **native Android TTS engine** (a custom `NativeTTS` Capacitor plugin), with a real MediaSession notification — play/pause/skip from the notification shade and lock screen.
2. **Installable PWA** — open `www/` from any static host in a browser and "Add to Home screen".

## Features

- **Import & Listen** — PDF, Word (`.docx`) and plain-text files from local storage, or paste text directly. Extraction happens on-device (pdf.js + mammoth); nothing is ever uploaded.
- **Natural text-to-speech** — uses the voices installed on your device (Web Speech API), grouped by language with an instant preview. Voices marked *offline* work with no connection.
- **Speed control** — 0.5× to 3.5× via slider or one-tap presets; listening-time estimates update everywhere.
- **Full player** — play/pause, ±10 s skip, drag-to-seek progress bar, tap any sentence to read from there, live sentence highlighting, page indicator, elapsed/total time.
- **Media notification** — playback controls appear in the notification shade / lock screen (Media Session API) with the book cover as artwork.
- **Reading reminders** — optional notification to pick up where you left off.
- **Library** — search, per-book progress, time remaining, restart, delete; covers are rendered from the PDF's first page.
- **Original view** — PDFs can be read as rendered pages, not just extracted text.
- **Everything persists** — books, position, voice, speed and text size are stored in IndexedDB and survive restarts; a service worker precaches the entire app for offline use.

## 📚 Developer documentation

New to the codebase? **[docs/](docs/README.md)** contains a beginner-friendly guide to the
whole app — architecture, every screen of the UI, the data structures, the playback
engine, the import pipeline, the Android native layer, and step-by-step recipes for
extending it.

## Build / run

**Android APK** — push to the app branch (or run the *Build Android APK* workflow manually); grab the APK from the run's artifacts or the created GitHub Release. Local builds work too if you have the Android SDK:

```bash
npm install
npx cap sync android
cd android && ./gradlew assembleRelease
# → android/app/build/outputs/apk/release/app-release.apk
```

> The committed `android/release.keystore` is a throwaway sideload/testing key so every CI build installs as an update over the previous one. Replace it (and remove it from git) before any store distribution.

**Web/PWA** — any static file server works (service workers require HTTP, not `file://`):

```bash
cd www && python3 -m http.server 8080   # open http://localhost:8080
```

## Structure

```
www/                  the whole app (also the Capacitor webDir)
  index.html          app shell (home, library, reader, profile, sheets)
  css/app.css         dark mobile-first UI
  js/app.js           UI controller & routing
  js/player.js        playback engine — WebSpeech backend (browser) + NativeTTS backend (APK)
  js/extract.js       PDF/DOCX/text extraction & sentence splitting
  js/db.js            IndexedDB storage
  sw.js               service worker (offline precache, web only)
  vendor/             pdf.js (legacy build) & mammoth, vendored for offline
android/              Capacitor Android project
  app/src/main/java/app/voxreader/NativeTTS.java   native TTS + MediaSession notification plugin
test/                 Playwright end-to-end suites (web + native-bridge)
.github/workflows/    CI: builds and releases the APK
```

## Testing

`npm test` runs two Playwright suites: the web e2e (import of real PDF/DOCX fixtures, playback, voice & speed, skip/seek, persistence across reloads, offline via service worker, media session, deletion — 56 checks) and a native-bridge suite that emulates the Capacitor plugin to verify batching, notification payloads, and media-button actions (20 checks).
