# 4. The Playback Engine (`www/js/player.js`)

This is the most interesting file in the app. It has **three classes**:

```
BasePlayer            shared brain: position, time math, seek/skip, events
   ├── WebPlayer      speaks with the browser's speechSynthesis API
   └── NativePlayer   speaks through the Android NativeTTS plugin (APK)
```

`createPlayer()` at the top picks the right one at startup:

```js
export function createPlayer() {
  if (isNative() && window.Capacitor.Plugins.NativeTTS) {
    return new NativePlayer(window.Capacitor.Plugins.NativeTTS);
  }
  return new WebPlayer();
}
```

`app.js` only ever talks to the shared API, so it doesn't know (or care) which backend is
running. **If you add a feature to the player, add it to `BasePlayer` if it's about
position/time, or implement it in both subclasses if it's about actual speech.**

## The shared API (what `app.js` uses)

| Member | Purpose |
|--------|---------|
| `load(book, cursor)` | Prepare a book; precompute the `cumWords` prefix sums |
| `play() / pause() / toggle() / stop()` | Transport controls |
| `seekToSentence(i)` / `seekToProgress(0..1)` / `skip(±seconds)` | Position changes |
| `setVoice(voiceURI)` / `setRate(rate)` | Restart the current sentence with new settings if playing |
| `getVoice()` | The selected voice, or a sensible default (prefers your language + offline voices) |
| `elapsedSec() / totalSec() / remainingSec() / progress()` | Time estimates (see doc 3) |
| `preview(voice)` | Speak a short sample ("This is how I sound.") |
| `on(event, fn)` | Subscribe to `sentence` / `state` / `voices` events |
| `voices` | Normalized list: `{ voiceURI, name, lang, localService }` |
| `cursor`, `playing`, `book` | Readable state |

## WebPlayer — the browser backend

The mental model: **a chain of one-sentence utterances.**

```
play() → _speakCurrent()
            creates SpeechSynthesisUtterance(sentences[cursor].t)
            utterance.onend → cursor++ → _speakCurrent()   (the chain)
            utterance.onerror('interrupted') → ignored     (that's just cancel())
```

Pausing doesn't use `speechSynthesis.pause()` (unreliable across browsers); it simply
**cancels** the current utterance and remembers the cursor. Resuming re-speaks the current
sentence from its start — for sentence-sized chunks that feels natural.

### The browser quirks this file works around (don't delete these!)

1. **`_stopping` flag** — calling `cancel()` fires a *late, asynchronous* `onend`/`onerror`
   on the cancelled utterance. Without the flag, that ghost event would advance the cursor
   after you paused. The flag is set during cancel and cleared a tick later.
2. **`_utterance !== u` guard** — same idea: only the *current* utterance may drive the
   chain; stale ones are ignored.
3. **Keep-alive interval** — desktop Chrome silently stops speech after ~15s of
   continuous synthesis. A 12-second `pause(); resume();` heartbeat prevents it
   (`_startKeepAlive`). Only enabled on desktop Chrome user agents.
4. **60 ms delay in `_restartCurrent`** — Chrome misbehaves if `speak()` is called
   synchronously right after `cancel()`.
5. **The silent audio loop** (`_startAudioFocus`) — `speechSynthesis` alone doesn't count
   as "playing media", so the OS shows no media notification. Playing a looping, nearly
   silent WAV (generated in-memory by `makeSilentWavUrl()`) makes the browser treat us as
   a real media session — that's what makes the lock-screen controls appear in the PWA.

### Media Session (web)

`_setupMediaSession()` publishes title/author/cover to `navigator.mediaSession` and maps
the hardware/notification buttons:

- play/pause/stop → the obvious calls
- seek backward/forward → `skip(±10)`
- previous/next track → `skip(∓30)` (headset double-tap etc.)

## NativePlayer — the Android backend

The WebView has **no speechSynthesis**, so speech must happen in Java. The design goal:
**playback must survive the WebView being frozen in the background.** That rules out a
sentence-by-sentence chain over the JS bridge; instead we queue in batches:

```
play()
  → plugin.configure({ voice, rate })
  → _queueFrom(cursor): sends up to 200 sentences [{id, text}, …] in ONE call
       Java: tts.speak(text, QUEUE_ADD, …, utteranceId = id) for each

Android TTS engine plays them natively, one after another
  ← 'utterance' {id} event when each one STARTS   → cursor = id, highlight follows
  ← 'queueDone' event after the LAST one          → queue the next 200, or finish
```

Even if Android throttles our JavaScript, the *native queue keeps speaking*; the UI
catches up whenever events get through. Pause = `plugin.stop()` (Android TTS has no real
pause) + remember cursor; resume re-queues from the cursor. Any change of voice/speed/seek
while playing also stops and re-queues — that's `_restartCurrent()`.

### Now-playing notification

`_sendNowPlaying()` pushes `{ title, author, playing, positionSec, durationSec, cover }`
to the plugin, which renders the media notification (see doc 6). Details worth knowing:

- The **cover** (a base64 JPEG, ~20 KB) is sent only **once per book** — the Java side
  caches the decoded bitmap. Re-sending it on every update would waste bridge traffic.
- Position updates are **throttled to every ~5 s** — the notification doesn't need
  per-sentence precision.
- Buttons in the notification / lock screen arrive back as `mediaAction` events:
  `play`, `pause`, `stop`, `next` (→ `skip(+30)`), `previous` (→ `skip(-30)`),
  `seekTo` (lock-screen slider → `seekToProgress`).

## Voices

Both backends normalize voices to the same shape so the UI has a single code path:

```js
{ voiceURI: "en-us-x-sfg#female_1-local",  // stable id used for selection & persistence
  name: "English (US) · female 1",         // display name
  lang: "en-US",
  localService: true }                      // true = works offline
```

- Web: mapped from `speechSynthesis.getVoices()` (which may arrive late — hence the
  `voiceschanged` listener and the `voices` event).
- Native: mapped from Android's `TextToSpeech.getVoices()` by the plugin
  (`networkRequired` is inverted into `localService`).

`getVoice()` picks a default when the user hasn't chosen: exact language match → same
language family → English → anything, preferring offline voices at each step.

## Speed

`SPEED_PRESETS = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3, 3.5]` feeds the chips in the
speed sheet. The rate is passed straight through: `utterance.rate` on the web,
`tts.setSpeechRate()` natively. It also changes all time *estimates* (doc 3), which is why
changing speed instantly updates "minutes remaining" everywhere.
