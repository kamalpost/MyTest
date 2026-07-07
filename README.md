# SP500 Swing Scanner (Android)

A native Android app that scans the S&P 500 for **swing trading ideas** using
**RSI(14)**, the **20/50-day simple moving averages**, and **volume analysis**,
with a dark trading-terminal dashboard.

> ⚠️ Educational tool — **not financial advice**. Signals are computed from
> end-of-day Yahoo Finance data and can be delayed or wrong.

## Install

A prebuilt, signed APK is committed at **`release/app-debug.apk`** (~680 KB).
Copy it to your phone (Android 8.0+), allow "install from unknown sources",
and install. If a version from before 2026-07-07 is installed, uninstall it
once first — earlier builds were signed with a throwaway key, so Android
rejects the update ("App not installed"). From now on every build is signed
with the committed `keystore/debug.p12`, so updates install straight over. The first scan starts automatically and takes ~1–2 minutes
(≈490 network calls); Yahoo occasionally throttles — tap **⟳ RESCAN** to retry.

## Where the data comes from

`yfinance` is a Python library, so it can't run on Android. The app instead
calls the **same Yahoo Finance v8 chart API that yfinance uses internally**
(`https://query1.finance.yahoo.com/v8/finance/chart/<symbol>`), fetching ~6
months of daily OHLCV per symbol directly from the device. No API key needed.
The constituent list (~490 symbols with names/sectors) ships in
`app/src/main/assets/sp500.csv` — edit that file to change the universe.

## Dashboard

- **Market header** — S&P 500 index (^GSPC) price, day change, sparkline,
  index RSI and MA20/MA50 levels, plus market breadth from the scan
  (buy setups / bearish counts / % of stocks above the 50-day MA) and the
  last-scan time.
- **Signal chips** — All · Buy Setups · Oversold · Pullbacks · Breakouts ·
  Overbought · Bearish · ✓ Selected · ✕ Skipped.
- **☰ FILTERS sheet** — screen by market-cap bucket (mega ≥$200B, large
  $50–200B, mid $20–50B, small <$20B — approximate values bundled in the CSV,
  relative to the S&P 500 universe), sector, RSI min–max sliders, minimum
  relative volume, and price range. The button shows how many filter groups
  are active.
- **Idea cards** — ticker, market cap, price and day change, signal badge with
  a 0–100 conviction score, 3-month sparkline with MA20/MA50 overlays, RSI /
  MA-position / relative-volume pills, a one-line rationale, and **✓ TAKE /
  ✕ SKIP** buttons.
- **Detail sheet** (tap a card) — full stats (RSI, MAs, RVOL, 20d avg volume,
  ATR(14), 3-month range), an ATR-based swing plan (entry / stop at 1.5×ATR /
  target at 2.5×ATR), and take/skip actions.
- **Take / skip decisions** — skipped ideas disappear from the main views
  (they live under ✕ Skipped); taken ideas are your personal watchlist under
  ✓ Selected. Decisions persist across restarts and auto-expire when the
  stock's signal changes. "Clear all marks" lives in the filter sheet.
- **Offline cache** — every completed scan is saved to app storage and
  restored instantly on launch; the app only auto-rescans if the cache is
  older than an hour. If Yahoo throttles part of a rescan, symbols that
  failed keep their previous data instead of vanishing.
- **⟳ RESCAN** re-scans all symbols (10 concurrent requests, live progress).
- **Trade journal** — TAKE opens a paper position at the current price with
  the ATR stop/target frozen in; un-taking (or skipping) closes it and records
  the result. Cards show live open P&L; the header shows open/closed counts,
  win rate, and average P&L. Reset lives in the filter sheet.
- **Position sizing** — set account size and risk-%-per-trade in the filter
  sheet; every swing plan then shows the share count that risks exactly that
  amount at the ATR stop.
- **Price alerts** — a JobScheduler background job (~every 30 min, survives
  reboots) checks open positions against their stop/target and fires a
  notification the first time a level is breached. Toggle in the filter sheet.
- **MACD(12,26,9) + Bollinger(20,2)** — confirmation layer on the conviction
  score (fresh MACD bull cross, positive histogram, BB squeeze), shown in the
  detail stats, with "MACD bullish" / "BB squeeze" filter toggles.
- **CSV export** — share open positions, closed trades, and selections as CSV
  through any app (email, Drive, etc.).
- **Custom universe** — add any Yahoo ticker in the filter sheet (validated
  against the API before it's added); long-press a card to remove a symbol
  from future scans.
- **Fundamentals** — EPS (ttm), trailing P/E, and P/B fetched per scan from
  Yahoo's quote API (cookie+crumb handshake, batched 100 symbols/call).
  P/E shows on each card, all three in the detail sheet, and the filter
  sheet has max-P/E / max-P/B caps (which also exclude loss-makers and
  symbols Yahoo didn't answer for). Shown as "—" when unavailable.

## Signal rules

| Signal | Conditions (simplified) |
|---|---|
| Oversold Bounce | RSI ≤ 30; stronger if volume confirms and price turned up |
| Pullback Buy | MA20 > MA50 uptrend, price pulled back to the MA20 with RSI 33–52 |
| Volume Breakout | Price reclaimed MA20 on ≥ 1.5× relative volume, RSI 50–68 |
| Golden Cross | MA20 crossed above MA50 within the last 7 sessions, price above both |
| Uptrend Momentum | Price > MA20 > MA50, RSI 50–65 (watch — buy pullbacks) |
| Overbought | RSI ≥ 70 — take profits / don't chase |
| Breakdown | Price lost both MAs on ≥ 1.4× volume |
| Death Cross | MA20 crossed below MA50 within the last 7 sessions |

Volume analysis: RVOL = today's volume ÷ 20-day average volume, plus a 5d/20d
volume-trend ratio; both feed the conviction score and breakout/breakdown
confirmation.

## Architecture

Pure Kotlin on **framework-only Android APIs** — no AndroidX, no Material
library, no third-party dependencies beyond the Kotlin stdlib. This is
deliberate: the app was built in a sandbox where Google's Maven repository is
unreachable, and it also keeps the APK tiny. UI is classic View system
(ListView + custom `SparklineView`); networking is `HttpURLConnection`;
JSON parsing is the platform `org.json`; concurrency is a plain thread pool.

```
app/src/main/java/com/swingtrader/sp500/
├── MainActivity.kt          # dashboard, filters, scan orchestration
├── data/YahooFinanceClient.kt  # v8 chart API client (query1 + query2 fallback)
├── data/StockRepository.kt     # CSV universe + concurrent scanner
├── analysis/Indicators.kt      # SMA, Wilder RSI, ATR, cross detection
├── analysis/SignalEngine.kt    # signal classification + conviction score
├── model/Models.kt
└── ui/                         # list adapter, detail sheet, sparkline, formats
```

## Building

Two ways:

**1. Gradle/Android Studio** (normal path): needs the Android SDK.
```
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleDebug
```

**2. `./build-apk.sh`** (no Gradle, no Android SDK manager — the pipeline used
to produce the committed APK): kotlinc → d8 → aapt2 → apksig. It expects
`/opt/buildtools` with `aapt2` (extractable from the Apktool source tree),
`r8lib.jar` (R8 releases bucket), `kotlin-compiler-embeddable.jar`,
`kotlin-stdlib.jar`, `annotations.jar`, `trove4j.jar`, `apksig.jar` (all Maven
Central), plus `platforms/android-30/android.jar` (mirrored in
`Sable/android-platforms` on GitHub).

`compileSdk` is 30 / `targetSdk` 29 because API 30 was the newest platform jar
mirrorable in the build sandbox; the app runs on all Android 8.0+ (minSdk 26)
devices. Bump them freely if you build with a full SDK.
