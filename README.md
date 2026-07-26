# App Lock

A simple Android app locker: pick which apps require a 4-digit numeric PIN
before they open.

## How it works

- **Pick apps to lock** — the main screen lists installed apps with a
  switch to lock/unlock each one.
- **Set a PIN** — on first launch you're asked to create a 4-digit PIN
  (stored only as a salted SHA-256 hash inside encrypted `SharedPreferences`,
  never in plain text).
- **Enforcement** — an `AccessibilityService` watches for foreground app
  changes. When a locked app comes to the front, a full-screen PIN prompt is
  shown on top of it until the correct PIN is entered. Unlocking a package
  keeps it unlocked until the screen turns off or you go back to the home
  screen, so you're not re-prompted on every tab switch.
- Because Android accessibility services must be turned on manually, the app
  shows a banner linking to **Settings > Accessibility** until the service is
  enabled.

## Project structure

```
app/src/main/java/com/applock/numberlock/
  AppLockApplication.kt
  data/            PrefsManager (PIN hash + locked app list), LockSessionManager, AppInfo
  ui/              MainActivity, SetupPinActivity, LockActivity, AppListAdapter
  service/         AppLockAccessibilityService
  util/            PIN keypad wiring, dot indicator + shake animation helpers
```

## Building the APK

This project cannot be compiled inside this sandboxed session: the Android
SDK (`dl.google.com` / `maven.google.com`) is not reachable through this
environment's network policy, so Gradle can't download the Android Gradle
Plugin, platform, or build-tools here. The source is complete and ready to
build anywhere with normal internet access:

### Option A — GitHub Actions (recommended, no local setup)

A workflow at `.github/workflows/build-apk.yml` builds a debug APK on every
push and uploads it as a workflow artifact. After this branch is pushed, open
the **Actions** tab on GitHub, run/find the "Build APK" workflow, and
download `app-lock-debug-apk` from the run's artifacts.

### Option B — Android Studio

1. Open this folder in Android Studio (Giraffe or newer).
2. Let Gradle sync (it will download AGP 8.5.2 / Gradle 8.7 / compileSdk 34).
3. Run **Build > Build Bundle(s) / APK(s) > Build APK(s)**, or click Run on a
   device/emulator.

### Option C — Command line, anywhere with internet access

```
./gradlew assembleDebug
```

The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Installing & using it

1. Install the APK (`adb install app-debug.apk`, or copy it to the device
   and open it — you'll need to allow installs from this source).
2. On first launch, set your 4-digit PIN.
3. Tap **Enable** on the accessibility banner and turn on "App Lock" under
   Settings > Accessibility.
4. Toggle the switch next to any app you want to protect.

## Notes / limitations

- Minimum supported Android version: 7.0 (API 24).
- The accessibility service only reads the foreground **package name** —
  `canRetrieveWindowContent` is off, so it cannot see or record what's on
  screen in other apps.
- This is a straightforward implementation meant as a solid starting point
  (single global PIN, no biometric fallback, no per-app lock timers). Those
  are natural next additions if you want to extend it.
