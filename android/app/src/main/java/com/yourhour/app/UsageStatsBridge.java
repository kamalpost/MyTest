package com.yourhour.app;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exposes real device usage data (UsageStatsManager) to the web UI.
 * All methods are called from the WebView's JS bridge thread.
 */
public class UsageStatsBridge {

    private final Activity activity;
    private final Map<String, String> iconCache = new HashMap<>();
    private final Map<String, String> labelCache = new HashMap<>();

    public UsageStatsBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public boolean hasPermission() {
        AppOpsManager ops = (AppOpsManager) activity.getSystemService(Context.APP_OPS_SERVICE);
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), activity.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @JavascriptInterface
    public void openUsageAccess() {
        Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
    }

    /* ---- Focus blockers ---- */

    @JavascriptInterface
    public void setBlock(String key, boolean enabled) {
        activity.getSharedPreferences(BlockerService.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(key, enabled).apply();
    }

    @JavascriptInterface
    public String getBlocks() {
        try {
            android.content.SharedPreferences p =
                    activity.getSharedPreferences(BlockerService.PREFS, Context.MODE_PRIVATE);
            JSONObject blocks = new JSONObject();
            for (String k : new String[]{"youtube", "instagram", "snapchat", "facebook"}) {
                blocks.put(k, p.getBoolean(k, false));
            }
            JSONObject out = new JSONObject();
            out.put("blocks", blocks);
            out.put("serviceEnabled", isAccessibilityEnabled());
            return out.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(activity.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(activity.getPackageName() + "/");
    }

    @JavascriptInterface
    public void openAccessibilitySettings() {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
    }

    /** Full data snapshot for the UI, as a JSON string. */
    @JavascriptInterface
    public String getSnapshot() {
        try {
            JSONObject out = new JSONObject();
            boolean granted = hasPermission();
            out.put("granted", granted);
            if (!granted) return out.toString();

            Calendar cal = Calendar.getInstance();
            long now = cal.getTimeInMillis();
            long midnight = dayStart(0);

            DayUsage today = collectDay(midnight, now);
            out.put("todayMinutes", today.totalMs / 60000.0);
            out.put("unlocks", today.unlocks);

            JSONArray hourly = new JSONArray();
            for (double slotMs : today.slotMs) hourly.put(slotMs / 60000.0);
            out.put("hourly", hourly);

            // last 7 days (oldest first), reusing today's scan for index 6
            SimpleDateFormat weekFmt = new SimpleDateFormat("dd-MM", Locale.US);
            SimpleDateFormat dayFmt = new SimpleDateFormat("dd MMM, yyyy", Locale.US);
            SimpleDateFormat numFmt = new SimpleDateFormat("dd", Locale.US);
            JSONArray week = new JSONArray();
            JSONArray days = new JSONArray();
            DayUsage[] history = new DayUsage[7];
            for (int back = 6; back >= 0; back--) {
                long start = dayStart(-back);
                long end = (back == 0) ? now : dayStart(-back + 1);
                DayUsage du = (back == 0) ? today : collectDay(start, end);
                history[6 - back] = du;

                JSONObject w = new JSONObject();
                w.put("label", back == 0 ? "TODAY" : weekFmt.format(start));
                w.put("mins", Math.round(du.totalMs / 60000.0));
                week.put(w);

                JSONObject d = new JSONObject();
                d.put("num", numFmt.format(start));
                d.put("date", dayFmt.format(start));
                d.put("mins", Math.round(du.totalMs / 60000.0));
                d.put("unlocks", du.unlocks);
                days.put(d);
            }
            out.put("week", week);
            out.put("days", days);

            // category minutes for today
            JSONObject cats = new JSONObject();
            double social = 0, games = 0, media = 0, prod = 0, custom = 0;
            PackageManager pm = activity.getPackageManager();
            for (Map.Entry<String, Long> e : today.perApp.entrySet()) {
                double mins = e.getValue() / 60000.0;
                switch (appCategory(pm, e.getKey())) {
                    case ApplicationInfo.CATEGORY_SOCIAL: social += mins; break;
                    case ApplicationInfo.CATEGORY_GAME: games += mins; break;
                    case ApplicationInfo.CATEGORY_VIDEO:
                    case ApplicationInfo.CATEGORY_AUDIO:
                    case ApplicationInfo.CATEGORY_IMAGE: media += mins; break;
                    case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                    case ApplicationInfo.CATEGORY_NEWS:
                    case ApplicationInfo.CATEGORY_MAPS: prod += mins; break;
                    default: custom += mins;
                }
            }
            cats.put("social", social);
            cats.put("games", games);
            cats.put("media", media);
            cats.put("productivity", prod);
            cats.put("custom", custom);
            out.put("categories", cats);

            // hourly timeline for today: top apps per hour
            JSONArray timeline = new JSONArray();
            for (int h = 0; h < 24; h++) {
                JSONObject row = new JSONObject();
                Map<String, Long> perApp = today.hourApps.get(h);
                long secsTotal = 0;
                JSONArray apps = new JSONArray();
                if (perApp != null) {
                    List<Map.Entry<String, Long>> entries = new ArrayList<>(perApp.entrySet());
                    entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                    for (Map.Entry<String, Long> e : entries) secsTotal += e.getValue() / 1000;
                    int limit = Math.min(5, entries.size());
                    for (int i = 0; i < limit; i++) {
                        Map.Entry<String, Long> e = entries.get(i);
                        JSONObject app = new JSONObject();
                        app.put("name", appLabel(pm, e.getKey()));
                        app.put("icon", appIconB64(pm, e.getKey()));
                        app.put("secs", e.getValue() / 1000);
                        apps.put(app);
                    }
                }
                row.put("secs", secsTotal);
                row.put("apps", apps);
                timeline.put(row);
            }
            out.put("timeline", timeline);

            return out.toString();
        } catch (JSONException e) {
            return "{\"granted\":false}";
        }
    }

    /* ---------------- internals ---------------- */

    private static class DayUsage {
        long totalMs;
        int unlocks;
        double[] slotMs = new double[48];               // per 30-min slot
        Map<String, Long> perApp = new HashMap<>();     // ms per package
        Map<Integer, Map<String, Long>> hourApps = new HashMap<>(); // ms per package per hour
    }

    private long dayStart(int daysFromToday) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, daysFromToday);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private DayUsage collectDay(long start, long end) {
        DayUsage du = new DayUsage();
        UsageStatsManager usm =
                (UsageStatsManager) activity.getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents events = usm.queryEvents(start, end);
        UsageEvents.Event ev = new UsageEvents.Event();
        String fgPkg = null;
        long fgSince = 0;

        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            int type = ev.getEventType();
            long ts = ev.getTimeStamp();

            if (type == UsageEvents.Event.KEYGUARD_HIDDEN) {
                du.unlocks++;
            } else if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (fgPkg == null) {
                    fgPkg = ev.getPackageName();
                    fgSince = ts;
                } else if (!fgPkg.equals(ev.getPackageName())) {
                    addSpan(du, fgPkg, fgSince, ts, start);
                    fgPkg = ev.getPackageName();
                    fgSince = ts;
                }
            } else if (type == UsageEvents.Event.ACTIVITY_PAUSED
                    || type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                if (fgPkg != null
                        && (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                            || fgPkg.equals(ev.getPackageName()))) {
                    addSpan(du, fgPkg, fgSince, ts, start);
                    fgPkg = null;
                }
            }
        }
        if (fgPkg != null) addSpan(du, fgPkg, fgSince, end, start);
        return du;
    }

    private void addSpan(DayUsage du, String pkg, long from, long to, long dayStart) {
        if (to <= from) return;
        du.totalMs += to - from;
        Long prev = du.perApp.get(pkg);
        du.perApp.put(pkg, (prev == null ? 0 : prev) + (to - from));

        // distribute across 30-min slots and hours
        long cursor = from;
        while (cursor < to) {
            int slot = (int) ((cursor - dayStart) / (30 * 60000L));
            long slotEnd = dayStart + (slot + 1) * 30 * 60000L;
            long chunk = Math.min(to, slotEnd) - cursor;
            if (slot >= 0 && slot < 48) {
                du.slotMs[slot] += chunk;
                int hour = slot / 2;
                Map<String, Long> apps = du.hourApps.get(hour);
                if (apps == null) {
                    apps = new HashMap<>();
                    du.hourApps.put(hour, apps);
                }
                Long p = apps.get(pkg);
                apps.put(pkg, (p == null ? 0 : p) + chunk);
            }
            cursor += chunk;
        }
    }

    private int appCategory(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationInfo(pkg, 0).category;
        } catch (PackageManager.NameNotFoundException e) {
            return ApplicationInfo.CATEGORY_UNDEFINED;
        }
    }

    private String appLabel(PackageManager pm, String pkg) {
        String cached = labelCache.get(pkg);
        if (cached != null) return cached;
        String label;
        try {
            label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            label = pkg;
        }
        labelCache.put(pkg, label);
        return label;
    }

    private String appIconB64(PackageManager pm, String pkg) {
        String cached = iconCache.get(pkg);
        if (cached != null) return cached;
        String b64 = "";
        try {
            Drawable d = pm.getApplicationIcon(pkg);
            Bitmap bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, 48, 48);
            d.draw(canvas);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            b64 = "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) { }
        iconCache.put(pkg, b64);
        return b64;
    }
}
