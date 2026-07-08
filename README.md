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
across three tracks: **Pacific Coast** ★, **Sierra Climb** ★★ and
**Devil's Canyon** ★★★. Finish in the top 3 to unlock the next track.

| Action | Phone (landscape) | Keyboard |
|---|---|---|
| Steer | ◀ ▶ buttons | Arrow Left / Right |
| Throttle | automatic | automatic (Up = slight boost) |
| Brake | BRAKE button | Arrow Down |
| Punch | PUNCH button | A or F |
| Kick | KICK button | S or G |
| Nitro | NITRO button | N or Shift |

- Punches and kicks drain a rival's stamina — empty it and they wipe out.
- Rivals fight back (some carry clubs); at zero stamina you're knocked off.
- **Weapon pickups** on the tarmac: grab a **club** (heavy damage) or a
  **chain** (longer reach). Weapons wear out after a few swings.
- **Nitro cans** refill your boost (max 3 charges); **wrenches** patch you up.
- **Cops** patrol every track. If you crash while one is chasing you,
  you're BUSTED and fined $40. Deck the cop for a $25 bounty — if you dare.
- Race purses ($400/$250/$150…) fill your wallet; busts drain it.
- Best time per track and your wallet are saved on the device.

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
