package com.yourhour.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * Focus blocker: when a toggled-on short-video surface (YouTube Shorts,
 * Instagram Reels, Snapchat Spotlight, Facebook Reels) comes on screen,
 * gently navigates back out of it.
 */
public class BlockerService extends AccessibilityService {

    public static final String PREFS = "blocks";
    private static final long COOLDOWN_MS = 1500;
    private long lastBack = 0;
    private long lastToast = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        String key = keyForPackage(pkg);
        if (key == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(key, false)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        boolean blocked;
        switch (key) {
            case "youtube":
                blocked = hasViewId(root, "com.google.android.youtube:id/reel_recycler")
                        || hasViewId(root, "com.google.android.youtube:id/reel_player_page_container")
                        || hasViewId(root, "com.google.android.youtube:id/reel_watch_fragment_root")
                        || hasViewId(root, "com.google.android.youtube:id/shorts_video_container");
                break;
            case "instagram":
                blocked = hasViewId(root, "com.instagram.android:id/clips_viewer_view_pager")
                        || hasViewId(root, "com.instagram.android:id/clips_video_container")
                        || hasSelectedLabel(root, "Reels");
                break;
            case "snapchat":
                blocked = hasSelectedLabel(root, "Spotlight");
                break;
            case "facebook":
                blocked = hasSelectedLabel(root, "Reels")
                        || hasViewId(root, "com.facebook.katana:id/reels_viewer_root");
                break;
            default:
                blocked = false;
        }

        long now = System.currentTimeMillis();
        if (blocked && now - lastBack > COOLDOWN_MS) {
            lastBack = now;
            performGlobalAction(GLOBAL_ACTION_BACK);
            if (now - lastToast > 5000) {
                lastToast = now;
                Toast.makeText(this, "Blocked by YourHour Focus", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onInterrupt() { }

    private static String keyForPackage(String pkg) {
        switch (pkg) {
            case "com.google.android.youtube": return "youtube";
            case "com.instagram.android": return "instagram";
            case "com.snapchat.android": return "snapchat";
            case "com.facebook.katana": return "facebook";
            default: return null;
        }
    }

    private static boolean hasViewId(AccessibilityNodeInfo root, String viewId) {
        List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByViewId(viewId);
        return hits != null && !hits.isEmpty();
    }

    /** A tab/label with this text exists and is currently selected. */
    private static boolean hasSelectedLabel(AccessibilityNodeInfo root, String label) {
        List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(label);
        if (hits == null) return false;
        for (AccessibilityNodeInfo n : hits) {
            AccessibilityNodeInfo cur = n;
            // the selected flag is often on an ancestor of the text node
            for (int up = 0; cur != null && up < 4; up++) {
                if (cur.isSelected()) return true;
                cur = cur.getParent();
            }
        }
        return false;
    }
}
