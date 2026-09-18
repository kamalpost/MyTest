# FocusPhone 📟

An Android app that turns your smartphone into an **old-school feature phone during
scheduled focus time**. When a focus window starts, a monochrome-LCD "Nokia-style" phone
takes over the screen and stays there: **calls and text messages only**, no other apps,
no pop-ups, no notification shade. A **Break** key hands the smartphone back for a few
minutes (or ends the session).

```
 ┌───────────────────────┐
 │ ▂▄▆    FocusPhone  ▮▮▮│
 │ ──────────────────────│
 │                       │
 │        10:42          │
 │      Wed 16 Sep       │
 │                       │
 │ Focus until 12:00 · 1h│
 │   ✉ 1 new message     │
 │ Menu             Break│
 └───────────────────────┘
   [ — ]   [ ▲ ]   [ — ]
   [ ◀ ]   [ OK]   [ ▶ ]
   [ 📞 ]  [ ▼ ]   [ ⏻ ]
   [1 .,?!][2 ABC][3 DEF]
   [4 GHI ][5 JKL][6 MNO]
   [7 PQRS][8 TUV][9 WXYZ]
   [ * +  ][0  ␣ ][# ⇧  ]
```

## What you get during focus time

| Feature phone "app" | What it does |
|---|---|
| **Phone** | Dialer with keypad tones, name lookup as you type, `*` twice for `+`. **Incoming calls** ring on the LCD with caller name: 📞 / OK answers, ⏻ rejects; during a call the right soft key hangs up and the left toggles speaker. |
| **Messages** | Inbox grouped by sender, conversation view, reply / new message with genuine **multi-tap T9 typing** (`#` toggles Abc/ABC/abc/123, `0` = space, `*` = symbols). |
| **Contacts** | Address book with T9 filtering (type `5 6` to find "John", "Kim"…). Call or text a contact. |
| **Break** | Right soft key on the home screen: 5 / 15 / 30 / 60 minute break (smartphone comes back, focus resumes automatically) or *End focus session*. |

Physical/Bluetooth keyboards work too (digits, `*`, `#`, arrows, Enter, Backspace, Call/End keys).

## How it keeps other apps out

1. **Lock task mode (screen pinning).** The feature phone activity pins itself: Home,
   Recents and the notification shade are blocked and other apps cannot come to the front.
   * Out of the box Android asks once *"Pin this app?"*; holding Back + Recents can still
     unpin it (Android's built-in escape hatch).
   * Make the app **device owner** and it becomes a real kiosk with no escape hatch:
     ```bash
     # phone with no Google/other accounts added yet, USB debugging on
     adb shell dpm set-device-owner app.focusphone/.focus.FocusAdminReceiver
     ```
     (Remove with `adb shell dpm remove-active-admin app.focusphone/.focus.FocusAdminReceiver`.)
2. **Do Not Disturb.** With notification-policy access granted, focus mode switches to a
   priority mode that silences and hides every notification but still lets phone calls ring.
   Your previous DND settings are restored when focus ends.
3. **Watchdog service.** A foreground service runs for the whole session. If focus is active
   but the feature phone is not on screen (session started while you were in another app,
   or you unpinned it), it brings the phone back — directly when *Display over other apps*
   is granted, otherwise via a full-screen alert.
4. **Exact alarms + boot receiver.** Sessions start and stop on the minute and survive reboots.
5. **Calls always win.** Screen pinning normally hides the system's incoming-call screen, so the
   app tracks the phone state itself: when a call rings it unpins, shows its own call screen
   (answer / reject / hang up via Telecom), pauses the watchdog, and pins again once the line is idle.
   Texts are sent on an explicitly chosen SIM (dual-SIM phones without a default SMS SIM would
   otherwise show a chooser that kiosk mode blocks) and the LCD reports the real send result.

## Setup screen (the normal smartphone UI)

Open the app outside focus time to get the setup screen:

* **Start focus now** — 25 min to 4 h, immediately.
* **Scheduled focus time** — add windows: days of the week + start + end (overnight
  windows such as 22:00–02:00 are fine). Toggle or delete each one.
* On first launch the app asks for the phone / SMS / contacts permissions right away (permission
  prompts cannot be relied on once the feature phone is pinned), and again before *Start focus now*
  if any are still missing.
* **Blocking strength** — a checklist with *Grant* buttons: notifications, phone/SMS/contacts,
  Do Not Disturb access, display over other apps, exact alarms, battery optimisation, plus
  the device-owner command above.
* **Options** — screen pinning on/off, silence notifications on/off, keypad tones, and an optional
  **Break PIN** (4–8 digits, stored hashed): when set, the feature phone asks for it before a break
  or before ending a session, and so does *End focus session* on this screen. Five wrong tries
  lock it for 30 s.

### "Why does my phone ask for its lock PIN when I press Break?"

That prompt comes from Android, not from the app. Leaving a pinned app triggers the screen lock
whenever the system setting *Ask for PIN before unpinning* is on (the default). The app cannot
change that setting; turn it off under **Settings → Security → App pinning** (the setup screen has
an *Open security settings* shortcut), or make the app device owner, which uses real lock task
mode and never shows the prompt.

## Build

```bash
cd focusphone
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest     # schedule-window and T9 unit tests
```

Every push touching `focusphone/` also builds the APK on GitHub Actions
(`.github/workflows/focusphone-apk.yml`) and attaches it to a GitHub Release.
The committed `release.keystore` is a throwaway sideload key so each build installs over
the previous one; replace it before any store distribution.

The app uses only Android framework APIs (no AndroidX), Kotlin, minSdk 26 (Android 8),
targetSdk 35.

## Code map

```
app/src/main/java/app/focusphone/
  data/Schedule.kt            recurring window model + "current / next window" math
  data/Prefs.kt               all persisted state (schedules, session, break, options)
  focus/FocusScheduler.kt     session state machine + exact alarms
  focus/FocusReceiver.kt      alarm / boot / time-change entry point
  focus/FocusService.kt       foreground watchdog + "return to focus" alert
  focus/FocusModeController.kt lock task (kiosk) + Do Not Disturb on/off
  focus/FocusAdminReceiver.kt device-admin hook for device-owner kiosk
  focus/SmsReceiver.kt        unread-message counter
  focus/SmsSentReceiver.kt    real "sent / failed" result of an outgoing text
  focus/CallStateReceiver.kt  ringing / off-hook / idle tracking (feeds phone/CallState.kt)
  phone/FeaturePhoneActivity.kt the pinned feature phone: LCD + keypad + actions
  phone/ScreenHost.kt         navigation stack for LCD screens
  phone/MultiTap.kt           T9 multi-tap text entry
  phone/screens/*.kt          Home, Menu, Dialer, Contacts, Messages, Call, Break, Pin
  setup/SetupActivity.kt      smartphone-style schedule / permissions / options screen
```
