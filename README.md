# YourHour — Phone Usage Tracker (PWA replica)

A pixel-matched recreation of the YourHour phone-usage app, built from device
screenshots as a self-contained, installable Progressive Web App. Open it on a
phone, "Add to Home Screen", and it runs full-screen like a native app
(offline too, via a service worker).

## Run it

Any static file server works:

```bash
python3 -m http.server 8080
# then open http://localhost:8080
```

For the phone-accurate look in a desktop browser, open devtools device mode at
412 x 925 (Pixel-class viewport).

## Screens

| Screen | What's on it |
|---|---|
| Dashboard | Buddy-badge banner, usage/unlock progress rings ("1H 14M LEFT"), Usage-by-Category donut, usage-per-30-minutes bar chart, past-7-days bar chart with the 225-minute goal line, Screen Time / Unlock Count buttons |
| Reports | Daily / Weekly / Monthly tabs with per-day usage + unlock deltas |
| Day detail | Usage/unlock summary card, Peak Usage card, Total Screen Time card |
| Timeline | Hour-by-hour "What's Been Opened" list with app icons and happy hours |
| Focus | Block Reels & Shorts toggles (YouTube, Instagram, Snapchat, Facebook) |
| Challenges / Stories | Simple companion screens |

Navigation works throughout: bottom tabs, report cards open the day detail,
the cyan summary card opens the hourly timeline, focus toggles switch state.

## Real usage measurement

The dashboards show the sample dataset from the screenshots (06 Jul 2026) so
every screen matches the reference pixels. Alongside that, `app.js` contains a
real tracker for what a web app is allowed to measure:

- time the app is actually on screen (Page Visibility API + heartbeat)
- number of times it is brought back to the foreground ("unlocks")
- persisted per-day in `localStorage`

Tap **Screen Time** / **Unlock Count** on the dashboard to see today's
measured values. System-wide per-app usage (what the real YourHour reads via
Android's `UsageStatsManager`) is not accessible from the web platform — that
would require a native Android build.

## Demo query params

Used for reproducible screenshots and demos:

- `?screen=reports|daydetail|timeline|focus|dashboard` — open a screen directly
- `?t=2:59` — freeze the status-bar clock
- `?b=62` — set the battery indicator

## Structure

```
index.html          app shell: status bar, app bars, screens, bottom nav
style.css           all styling (palette sampled from the screenshots)
app.js              data, SVG charts (rings/donut/bars), navigation, tracker
sw.js               offline cache
manifest.webmanifest, icons/   PWA install metadata
fonts/              Pacifico (logo), Open Sans (body)
```

No build step, no dependencies — plain HTML/CSS/JS.
