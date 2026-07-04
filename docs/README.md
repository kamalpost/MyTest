# VoxReader Developer Documentation

Welcome! These documents explain how VoxReader works from the ground up, written for a
**beginner-level programmer**. Read them in order the first time; afterwards use them as a
reference.

| # | Document | What it covers |
|---|----------|----------------|
| 1 | [Overview & Architecture](01-overview.md) | What the app is, the big picture, how a book travels through the system |
| 2 | [UI Guide](02-ui-guide.md) | Every screen, which file builds it, how navigation and sheets work |
| 3 | [Data Structures & Storage](03-data-structures.md) | The Book object, sentences, settings, and how IndexedDB stores them |
| 4 | [The Playback Engine](04-playback-engine.md) | `player.js` — voices, speed, seeking, time math, and the two backends |
| 5 | [The Import Pipeline](05-import-pipeline.md) | `extract.js` — how PDF/Word files become readable sentences |
| 6 | [Android Native Layer](06-android-native.md) | Capacitor, the `NativeTTS` Java plugin, notifications, and the CI build |
| 7 | [How to Extend the App](07-how-to-extend.md) | Step-by-step recipes for common enhancements |
| 8 | [Language Support](08-language-support.md) | Tamil/Kannada/Hindi & other languages: detection, voices, danda splitting, on-device OCR |

## The 60-second summary

VoxReader is a **text-to-speech book reader**. You give it a PDF, Word (.docx) or text
file; it extracts the text, splits it into sentences, and reads them aloud one by one
while highlighting the current sentence on screen. Everything — the imported books, your
reading position, your voice and speed settings — is stored **on the device**, so the app
works completely offline.

The same code ships two ways:

- **Android APK** — the web app runs inside a native shell (Capacitor). Speech comes from
  Android's own TTS engine via a small Java plugin we wrote (`NativeTTS.java`).
- **PWA (website)** — the app runs in a browser and uses the browser's built-in
  `speechSynthesis` API. A service worker caches everything for offline use.

```
you tap "Files" ──▶ extract.js reads the file ──▶ db.js saves the book
                                                        │
you press play ◀── app.js renders the reader ◀──────────┘
      │
      ▼
player.js speaks sentences one by one ──▶ highlights follow along on screen
```

## Where is the code?

```
www/                the entire app (HTML/CSS/JS). Also packaged into the APK.
android/            the native Android shell (mostly generated; our code is NativeTTS.java)
test/               automated Playwright tests
.github/workflows/  CI pipeline that builds & releases the APK
```

Everything you will normally edit lives in `www/`.
