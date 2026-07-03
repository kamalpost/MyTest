# VoxReader 📖🔊

Turn any **PDF or Word document into an audiobook** — right on your device, fully offline.

VoxReader is an installable Progressive Web App (PWA). Open it once in a browser, add it to your home screen, and it works like a native app with no internet connection at all.

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

## Run it

Any static file server works (service workers require HTTP, not `file://`):

```bash
cd MyTest
python3 -m http.server 8080
# open http://localhost:8080
```

On a phone: serve it (or host on any static host / GitHub Pages), open in Chrome, then **Add to Home screen** — it installs like a native app and keeps working offline.

## Structure

```
index.html            app shell (home, library, reader, profile, sheets)
css/app.css           dark mobile-first UI
js/app.js             UI controller & routing
js/player.js          TTS engine, media-session/notification integration
js/extract.js         PDF/DOCX/text extraction & sentence splitting
js/db.js              IndexedDB storage
sw.js                 service worker (offline precache)
vendor/               pdf.js (legacy build) & mammoth, vendored for offline
icons/                PWA icons
```

## Testing

An automated end-to-end suite (Playwright) covers import (PDF/DOCX/text), playback, voice & speed selection, skip/seek, persistence across reloads, offline mode via the service worker, media-session state and deletion — 56 checks, all passing.
