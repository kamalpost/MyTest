# HANDOFF — SP500 Swing Scanner

Read this before changing anything. It exists so a future developer or AI
session can continue this project without re-learning its unusual constraints
the hard way. The README covers *what the app does*; this file covers *how the
project really works and why*.

## 1. The one-paragraph summary

Native Android swing-trading scanner (Kotlin, framework-only APIs, zero
AndroidX) that pulls S&P 500 daily data from the Yahoo Finance chart API,
computes RSI/MA/volume/MACD/Bollinger signals plus EPS-P/E-P/B fundamentals
on-device, and ships as a ~670 KB sideloaded APK. It was built entirely inside
a sandbox where **Google's SDK and Maven servers are unreachable**, so the
build pipeline is hand-rolled (`build-apk.sh`) and several design decisions
below only make sense in that light.

## 2. Hard rules — break these and users get broken APKs

1. **Never regenerate or lose `keystore/debug.p12`** (passwords: `android`).
   Android refuses updates signed with a different key. This key is committed
   on purpose; both `build-apk.sh` and the Gradle `signingConfig` use it.
   History: the first three builds each generated a throwaway key, which
   produced "App not installed" for every subsequent update.
2. **applicationId is `com.swingtrader.scanner`, source package is
   `com.swingtrader.sp500`.** They differ deliberately (a leftover install of
   the early broken builds under the old id could block updates forever).
   Manifest component names must stay fully qualified; `build-apk.sh` passes
   `--custom-package` so the R class lands in the source package.
3. **Keep `targetSdk` ≥ 34.** New Android versions refuse to install apps
   targeting old SDKs (generic "App not installed"). `compileSdk` stays 30 —
   that's the newest `android.jar` obtainable in the sandbox — and that
   combination is fine: targetSdk is just a manifest attribute.
4. **Don't add AndroidX/Material/third-party dependencies** unless you've
   confirmed Google Maven is reachable from your environment. In the original
   sandbox only Maven Central, GitHub raw, and a few hosts work (see §4).
   The framework-only UI is a feature: it keeps the APK tiny and the build
   simple.
5. **Bump `versionCode` in BOTH `app/build.gradle.kts` and `build-apk.sh`**
   (aapt2 link flags) on every release, and copy the built APK to
   `release/app-debug.apk` — that committed file is the user's download.
6. **Bump `CacheStore.VERSION`** whenever `Idea` gains/loses persisted fields;
   old caches are then discarded instead of misparsed.

## 3. Build

Two paths, same signing key:

- **`./build-apk.sh`** (canonical; what produced every shipped APK):
  aapt2 compile/link → javac (R.java) → kotlinc (embeddable) → d8 (from
  r8lib.jar) → python zips classes.dex into the APK → apksig v2-only sign
  (v1 needs JDK internals removed in modern Java) → verify.
  Expects `TOOLS=/opt/buildtools` and
  `ANDROID_JAR=/opt/android-sdk/platforms/android-30/android.jar`.
- **Gradle/Android Studio**: normal `gradle assembleDebug` with a real SDK.
  Untested end-to-end (AGP can't run in the sandbox) but kept consistent.

### Restoring /opt/buildtools in a fresh sandbox

| Piece | Source (all reachable from the sandbox) |
|---|---|
| `android.jar` (API 30) | `raw.githubusercontent.com/Sable/android-platforms/master/android-30/android.jar` |
| `aapt2` | `raw.githubusercontent.com/iBotPeaches/Apktool/v2.9.3/brut.apktool/apktool-lib/src/main/resources/prebuilt/linux/aapt2_64` |
| `r8lib.jar` (d8/r8) | `storage.googleapis.com/r8-releases/raw/8.3.37/r8lib.jar` |
| `kotlin-compiler-embeddable.jar` 1.9.24 | Maven Central |
| `kotlin-stdlib.jar` 1.9.24 + `annotations.jar` 13.0 + `trove4j.jar` 1.0.20200330 (`org.jetbrains.intellij.deps:trove4j`) | Maven Central |
| `apksig.jar` 2.3.0 | Maven Central (only version there; v1 signing in it is dead on JDK 17+, hence v2-only) |

Blocked in the sandbox (don't waste time): `dl.google.com`,
`maven.google.com` (redirects to dl.google.com), all Google-Maven mirrors
(Aliyun/Huawei/Tencent/Tsinghua), `jitpack.io`, Yahoo Finance itself (so live
API calls are untestable from the sandbox — the app is the only test bed),
and GitHub *release assets* (repo-scoped proxy) — but `raw.githubusercontent`
is open.

### Testing

`scratchpad/Smoke.kt` (session scratchpad, not committed) compiles the
analysis layer against `build-manual/classes` and asserts: SMA/EMA math, the
textbook Wilder RSI value (≈70.46), MACD sign behavior, Bollinger bands,
signal classification (pullback/oversold), cap buckets, and every filter rule.
Rebuild it from this description if lost — the analysis package has no Android
imports, so it runs on a plain JVM. There is no emulator anywhere in this
setup; on-device behavior is verified by the user.

## 4. Data sources

- **Prices/volume**: `https://query{1,2}.finance.yahoo.com/v8/finance/chart/
  <symbol>?range=6mo&interval=1d` — no auth, just a browser User-Agent.
  This is the same endpoint the Python yfinance library wraps.
- **Fundamentals (EPS ttm, trailing P/E, P/B)**: `v7/finance/quote` batched
  100 symbols/call — requires the cookie+crumb handshake (hit `fc.yahoo.com`
  to get cookies, then `/v1/test/getcrumb`), implemented in
  `YahooFinanceClient`. Everything degrades gracefully to NaN/"—" when Yahoo
  throttles or the handshake fails.
- **Universe**: `app/src/main/assets/sp500.csv`
  (`symbol,name,sector,approx_cap_$B`) — caps are hand-estimated, display/
  bucketing only. User edits (add/remove tickers) overlay it via
  `UserUniverseStore`.

## 5. Architecture map

```
MainActivity            dashboard, chips, scan orchestration, decisions wiring
data/
  YahooFinanceClient    chart API + crumb/cookie + v7 quote fundamentals
  StockRepository       universe load, 10-thread scan, fundamentals merge
  CacheStore            scan persistence (JSON, filesDir, VERSION gate)
  DecisionStore         take/skip per symbol, auto-expires on signal change
  JournalStore          paper positions + closed trades + stats (JSON)
  Settings              account size / risk% / alerts toggle (prefs)
  UserUniverseStore     added/removed tickers (prefs)
analysis/
  Indicators            SMA, EMA, Wilder RSI, ATR, MACD, Bollinger, crosses
  SignalEngine          classification + conviction score + confirmations
alerts/
  AlertJobService       periodic stop/target check → notifications (deduped)
  AlertScheduler        schedules/cancels the JobScheduler job
ui/
  IdeaAdapter (ListView BaseAdapter), DetailSheet & FilterSheet (Dialogs),
  SparklineView (custom View), FilterState, Format
```

Threading: one background `worker` executor in MainActivity + a 10-thread
pool inside `StockRepository.scan`; UI updates via `Handler(mainLooper)`.
No coroutines (dependency-free by design).

## 6. Scars — bugs already paid for, don't reintroduce them

| Symptom | Root cause | Fix (in place) |
|---|---|---|
| "App not installed" on update | New signing key generated every build | Persistent committed keystore (§2.1) |
| "App not installed" on fresh install | targetSdk 29 below modern Android's install floor | targetSdk 34 (§2.3) |
| Still failing after both fixes | Ghost install of an early build (possibly in Private Space) | New applicationId (§2.2) |
| Top half of screen dead space | Title/subtitle in fixed (non-scrolling) chrome wrapped at large font scales | Single-line auto-shrinking app bar; subtitle and pills scroll, `maxLines=1` everywhere pill-like |
| kotlinc NoClassDefFoundError | embeddable compiler needs stdlib + trove4j on ITS OWN classpath | build-apk.sh compiler `-cp` |
| apksig crash on JDK 21 | v1 signing uses removed `sun.security` APIs | v2-only signing (valid for minSdk ≥ 24) |

## 7. Working with this repo from an AI session

Suggested opening instruction:

> Read HANDOFF.md and build-apk.sh first. This project intentionally builds
> without Gradle, without AndroidX, and without the Android SDK manager —
> don't "normalize" it. Rebuild /opt/buildtools from the HANDOFF table if
> missing, bump versionCode in both places, run the smoke test and
> ./build-apk.sh, and copy the APK to release/app-debug.apk before pushing.

Branch: `claude/android-swing-trading-app-xd5302`. Version history: 1.0
initial → 1.1 filters/cache/decisions → 1.2 journal/alerts/sizing/MACD/BB/
export/universe → 1.2.1–1.2.3 install + layout fixes → 1.3.0 fundamentals.

## 8. Roadmap candidates (user-approved direction)

- Alert on MA-cross events for watchlist names, not just stop/target
- Journal analytics per signal type (which setups actually win?)
- Backtest mode over the cached 6-month histories
- Widget with top-3 setups; CSV import of custom universes
