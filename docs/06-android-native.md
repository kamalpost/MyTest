# 6. Android Native Layer

This doc explains everything outside `www/`: how the web app becomes an APK, and the one
piece of real native code we wrote.

## Capacitor in one paragraph

[Capacitor](https://capacitorjs.com) generates a normal Android Studio project
(`android/`) whose single Activity hosts a **WebView** loading our files from inside the
APK (served internally at `https://localhost`). A **bridge** lets JavaScript call
annotated Java methods and lets Java push events back. `npx cap sync android` copies
`www/` into `android/app/src/main/assets/public/` — that's the only "build step" the web
code has.

```
capacitor.config.json     appId "app.voxreader", appName "VoxReader", webDir "www"
android/                  generated project — MOSTLY do not touch
  app/src/main/java/app/voxreader/
    MainActivity.java     3 lines: registers our plugin
    NativeTTS.java        ★ our native code (the only Java you need to read)
  app/src/main/res/drawable/ic_stat_*.xml   notification icons (vector XML)
  app/src/main/res/mipmap-*/               launcher icons (generated PNGs)
  release.keystore        signing key (sideload/testing — see below)
```

## Why native code at all?

Two WebView gaps:

| Missing in WebView | Native replacement |
|--------------------|--------------------|
| `speechSynthesis` (voices, speak) | Android's `TextToSpeech` engine |
| Media Session / media notification | `MediaSessionCompat` + `NotificationCompat.MediaStyle` |

Both live in one plugin class: **`NativeTTS.java`** (~350 lines).

## NativeTTS.java — a guided tour

### How a Capacitor plugin works

```java
@CapacitorPlugin(name = "NativeTTS", permissions = …POST_NOTIFICATIONS…)
public class NativeTTS extends Plugin {

    @PluginMethod
    public void speakBatch(PluginCall call) {   // JS: Capacitor.Plugins.NativeTTS.speakBatch({...})
        ...
        call.resolve();                          // completes the JS Promise
    }
}
```

- `@PluginMethod` methods are callable from JS as Promises.
- `notifyListeners("utterance", data)` fires events that JS subscribed to with
  `plugin.addListener('utterance', fn)`.
- `MainActivity` registers the class: `registerPlugin(NativeTTS.class);`.

### The TTS half

- `load()` (runs once at startup) creates the `TextToSpeech` engine. Engine startup is
  asynchronous, so any `getVoices()` call arriving early is parked in `waitingForReady`
  and resolved when the engine reports ready.
- `getVoices()` → maps Android `Voice` objects to `{ id, name, lang, networkRequired }`.
  Raw Android voice names look like `en-us-x-sfg#female_1-local`; `displayName()` turns
  the locale into "English (United States) · female 1".
- `configure({ voice, rate })` → `tts.setVoice(...)`, `tts.setSpeechRate(...)`.
- `speakBatch({ sentences: [{id, text}, …] })` → the core. First sentence uses
  `QUEUE_FLUSH` (replaces whatever was playing), the rest `QUEUE_ADD`. Each utterance's
  id is the **sentence index as a string**, which is how progress maps back to the UI.
- `UtteranceProgressListener`:
  - `onStart(id)` → `notifyListeners("utterance", {id})` → JS moves the highlight.
  - `onDone(lastQueuedId)` → `notifyListeners("queueDone")` → JS queues the next batch.
- `preview({text})` speaks one sample with a non-numeric id (so it emits no progress).
- `stop()` — Android TTS has **no pause**; stopping and re-queueing from the remembered
  sentence index is how pause/resume works (JS side handles that logic).

### The notification half

- One `MediaSessionCompat` is created in `load()`. Its callback (lock-screen buttons,
  headset keys, the seek slider) emits `mediaAction` events to JS: `play`, `pause`,
  `stop`, `next`, `previous`, `seekTo`.
- `setNowPlaying({title, author, playing, positionSec, durationSec, cover})`:
  1. decodes the base64 `cover` into a Bitmap (cached — JS sends it once per book),
  2. publishes `MediaMetadataCompat` (title/artist/duration/art) and
     `PlaybackStateCompat` (playing/paused + position → the lock-screen slider),
  3. builds the **MediaStyle notification** with three actions
     (⏮ Back / ⏯ Play-Pause / ⏭ Forward). Those action buttons fire explicit broadcast
     intents (`app.voxreader.action.PLAY`…) handled by a dynamically registered
     `BroadcastReceiver`, which again just emits `mediaAction` to JS.
- `clearNowPlaying()` removes the notification and deactivates the session (called when a
  book finishes or playback stops entirely).
- **Permission:** Android 13+ requires `POST_NOTIFICATIONS` at runtime.
  `requestNotifications()` triggers the system prompt; the JS side calls it on first
  play. If denied, playback still works — only the shade notification is skipped
  (lock-screen media controls come from the session and still function).

### Threading note

`UtteranceProgressListener` callbacks arrive on a background thread. Capacitor's
`notifyListeners` is safe to call from there — do **not** try to touch Android UI
directly from those callbacks.

## The JS side of the bridge

`NativePlayer` in `www/js/player.js` is the mirror image (see doc 4): it batches 200
sentences per `speakBatch` call, listens for `utterance`/`queueDone`/`mediaAction`, and
throttles `setNowPlaying` updates.

## Building the APK

### On GitHub Actions (the normal way)

`.github/workflows/android-apk.yml` runs on every push to the app branch that touches
`www/`, `android/`, or the workflow itself (and can be run manually — *Actions → Build
Android APK → Run workflow*):

```
checkout → set up JDK 21 + Node → npm install → npx cap sync android
        → ./gradlew assembleRelease → upload artifact → create GitHub Release with the APK
```

Each run publishes a release tagged `apk-v1.0.0-buildN` with `VoxReader-v1.0.0.apk`
attached — that's the download link for phones.

### Locally (needs the Android SDK)

```bash
npm install
npx cap sync android          # copy www/ into the android project
cd android && ./gradlew assembleRelease
# → android/app/build/outputs/apk/release/app-release.apk
```

## Signing (read before distributing)

Android requires every APK to be signed, and **updates must be signed with the same key**
or the phone refuses to install over the old version. To keep CI builds installable over
each other, a throwaway keystore is **committed to the repo**:

```
android/release.keystore   alias: voxreader   passwords: voxreader1  (in app/build.gradle)
```

This is fine for sideloading/testing. **Before any real distribution (Play Store or wide
sharing): generate a private keystore, keep it out of git (CI secret), and update the
`signingConfigs` block in `android/app/build.gradle`.**

## Version numbers

- User-visible version: `versionName "1.0"` in `android/app/build.gradle` (+ the
  `APP_VERSION` constant in `app.js` shown on the Profile screen).
- `versionCode` (an integer) must **increase** for Android to treat an APK as an update.
  Bump both when releasing meaningful changes, and update the APK filename/tag in the
  workflow if you want release names to match.

## Known limitations of the native layer (honest notes)

- No **foreground service**: very aggressive battery savers may eventually kill the app
  during long background listening. The batch design (up to 200 sentences queued natively)
  makes this rare in practice. Adding a `MediaBrowserService` would be the full fix.
- Voice **preview** changes the engine's current voice (it re-`configure`s); the next
  play uses your selection anyway, so this is invisible in practice.
- Android TTS has no pause — pause/resume restarts the current sentence, which sounds
  natural for sentence-sized chunks.
