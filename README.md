# Road Bash 🏍️

A loving tribute to the 16-bit classic **Road Rash** — a pseudo-3D motorcycle
racing game where you punch and kick your rivals off their bikes while dodging
two-way highway traffic. Built as a self-contained HTML5 canvas game wrapped in
a native Android WebView app.

## Play it

- **Android**: install `apk/RoadBash.apk` (built automatically by CI).
  You may need to allow "install from unknown sources" since it is signed
  with a debug key.
- **Browser**: open `app/src/main/assets/index.html` in any modern browser.

## How to play

Race 5 rivals — VIPER, AXEL, NATASHA, BIFF and SLADE — to the finish flag
across coast, farmland, desert and mountain zones.

| Action | Phone (landscape) | Keyboard |
|---|---|---|
| Steer | ◀ ▶ buttons | Arrow Left / Right |
| Throttle | automatic | automatic (Up = slight boost) |
| Brake | BRAKE button | Arrow Down |
| Punch | PUNCH button | A or F |
| Kick | KICK button | S or G |

- Punches and kicks drain a rival's stamina — empty it and they wipe out.
- Rivals fight back; if your stamina hits zero, you're knocked off your bike.
- Slamming into cars or roadside objects also puts you on the tarmac.
- Your best finish time is saved on the device.

## Project layout

```
app/src/main/assets/index.html   # the entire game (no external assets)
app/src/main/java/...            # fullscreen WebView wrapper activity
.github/workflows/build-apk.yml  # CI: builds the APK, commits it to apk/
```

## Building locally

Requires JDK 17+ and the Android SDK (API 34):

```
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```
