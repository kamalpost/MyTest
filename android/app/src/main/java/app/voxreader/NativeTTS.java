package app.voxreader;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Base64;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Native text-to-speech + media-notification backend for VoxReader.
 *
 * The WebView has no speechSynthesis and no Media Session, so the web layer
 * delegates here on Android. Sentences are queued in batches into the native
 * TTS engine (playback survives the WebView being throttled in background) and
 * progress is reported back per utterance. A MediaSessionCompat plus a
 * MediaStyle notification give lock-screen / notification-shade controls.
 */
@CapacitorPlugin(
    name = "NativeTTS",
    permissions = @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
)
public class NativeTTS extends Plugin {

    private static final String CHANNEL_ID = "voxreader_playback";
    private static final int NOTIFICATION_ID = 7401;
    private static final String ACTION_PLAY = "app.voxreader.action.PLAY";
    private static final String ACTION_PAUSE = "app.voxreader.action.PAUSE";
    private static final String ACTION_NEXT = "app.voxreader.action.NEXT";
    private static final String ACTION_PREV = "app.voxreader.action.PREV";

    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private final List<PluginCall> waitingForReady = new ArrayList<>();
    private volatile int lastQueuedId = -1;

    private MediaSessionCompat session;
    private BroadcastReceiver buttonReceiver;
    private Bitmap coverBitmap;
    private String npTitle = "";
    private String npAuthor = "";
    private boolean npPlaying = false;

    @Override
    public void load() {
        Context ctx = getContext();
        tts = new TextToSpeech(ctx, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            synchronized (waitingForReady) {
                for (PluginCall call : waitingForReady) {
                    if (ttsReady) resolveVoices(call);
                    else call.reject("Text-to-speech engine failed to start");
                }
                waitingForReady.clear();
            }
            notifyListeners("ttsReady", new JSObject().put("ready", ttsReady));
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                Integer id = parseId(utteranceId);
                if (id != null) notifyListeners("utterance", new JSObject().put("id", (int) id));
            }
            @Override public void onDone(String utteranceId) {
                Integer id = parseId(utteranceId);
                if (id != null && id == lastQueuedId) notifyListeners("queueDone", new JSObject());
            }
            @Override public void onError(String utteranceId) {
                Integer id = parseId(utteranceId);
                if (id != null) notifyListeners("utteranceError", new JSObject().put("id", (int) id));
            }
        });

        session = new MediaSessionCompat(ctx, "VoxReader");
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { emitMediaAction("play"); }
            @Override public void onPause() { emitMediaAction("pause"); }
            @Override public void onStop() { emitMediaAction("stop"); }
            @Override public void onSkipToNext() { emitMediaAction("next"); }
            @Override public void onSkipToPrevious() { emitMediaAction("previous"); }
            @Override public void onSeekTo(long pos) {
                notifyListeners("mediaAction", new JSObject().put("action", "seekTo").put("positionSec", pos / 1000d));
            }
        });

        buttonReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if (ACTION_PLAY.equals(a)) emitMediaAction("play");
                else if (ACTION_PAUSE.equals(a)) emitMediaAction("pause");
                else if (ACTION_NEXT.equals(a)) emitMediaAction("next");
                else if (ACTION_PREV.equals(a)) emitMediaAction("previous");
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREV);
        ContextCompat.registerReceiver(ctx, buttonReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void emitMediaAction(String action) {
        notifyListeners("mediaAction", new JSObject().put("action", action));
    }

    private Integer parseId(String utteranceId) {
        try { return Integer.parseInt(utteranceId); } catch (Exception e) { return null; }
    }

    /* ---------------- voices ---------------- */

    @PluginMethod
    public void getVoices(PluginCall call) {
        if (!ttsReady) {
            synchronized (waitingForReady) {
                if (!ttsReady) { waitingForReady.add(call); return; }
            }
        }
        resolveVoices(call);
    }

    private void resolveVoices(PluginCall call) {
        JSArray out = new JSArray();
        try {
            for (Voice v : tts.getVoices()) {
                Locale loc = v.getLocale();
                JSObject o = new JSObject();
                o.put("id", v.getName());
                o.put("name", displayName(v));
                o.put("lang", loc.toLanguageTag());
                o.put("networkRequired", v.isNetworkConnectionRequired());
                out.put(o);
            }
        } catch (Exception ignored) {
            // some engines return null voice sets; resolve with what we have
        }
        JSObject res = new JSObject();
        res.put("voices", out);
        call.resolve(res);
    }

    private String displayName(Voice v) {
        Locale loc = v.getLocale();
        String base = loc.getDisplayLanguage() +
            (loc.getCountry().isEmpty() ? "" : " (" + loc.getDisplayCountry() + ")");
        // raw engine names look like "en-us-x-sfg#male_1-local" — surface a short variant tag
        String raw = v.getName();
        int hash = raw.indexOf('#');
        String variant = hash >= 0 ? raw.substring(hash + 1).replace("-local", "").replace('_', ' ') : raw;
        return base + " · " + variant;
    }

    /* ---------------- speaking ---------------- */

    @PluginMethod
    public void configure(PluginCall call) {
        String voiceId = call.getString("voice");
        String lang = call.getString("lang");
        Float rate = call.getFloat("rate");
        if (rate != null) tts.setSpeechRate(rate);
        boolean voiceSet = false;
        if (voiceId != null && ttsReady) {
            try {
                for (Voice v : tts.getVoices()) {
                    if (v.getName().equals(voiceId)) { tts.setVoice(v); voiceSet = true; break; }
                }
            } catch (Exception ignored) {}
        }
        // No exact voice match — at least switch the engine to the book's language
        // (e.g. Tamil/Hindi books on engines that expose languages but few voices).
        if (!voiceSet && lang != null && ttsReady) {
            try { tts.setLanguage(Locale.forLanguageTag(lang)); } catch (Exception ignored) {}
        }
        call.resolve();
    }

    @PluginMethod
    public void speakBatch(PluginCall call) {
        if (!ttsReady) { call.reject("Text-to-speech engine not ready"); return; }
        JSArray sentences = call.getArray("sentences");
        if (sentences == null || sentences.length() == 0) { call.reject("No sentences"); return; }
        try {
            tts.stop();
            int last = -1;
            for (int i = 0; i < sentences.length(); i++) {
                JSONObject s = sentences.getJSONObject(i);
                int id = s.getInt("id");
                String text = s.getString("text");
                int mode = i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
                tts.speak(text, mode, new Bundle(), String.valueOf(id));
                last = id;
            }
            lastQueuedId = last;
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not queue speech: " + e.getMessage());
        }
    }

    @PluginMethod
    public void preview(PluginCall call) {
        if (!ttsReady) { call.reject("Text-to-speech engine not ready"); return; }
        String text = call.getString("text", "This is how I sound.");
        lastQueuedId = -1; // don't emit queueDone for previews
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), "preview");
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        lastQueuedId = -1;
        if (tts != null) tts.stop();
        call.resolve();
    }

    /* ---------------- media session + notification ---------------- */

    @PluginMethod
    public void setNowPlaying(PluginCall call) {
        npTitle = call.getString("title", "");
        npAuthor = call.getString("author", "");
        npPlaying = Boolean.TRUE.equals(call.getBoolean("playing", false));
        double positionSec = call.getDouble("positionSec") != null ? call.getDouble("positionSec") : 0;
        double durationSec = call.getDouble("durationSec") != null ? call.getDouble("durationSec") : 0;

        String cover = call.getString("cover");
        if (cover != null && cover.startsWith("data:")) {
            try {
                byte[] bytes = Base64.decode(cover.substring(cover.indexOf(',') + 1), Base64.DEFAULT);
                coverBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception ignored) {}
        }

        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, npTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, npAuthor)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) (durationSec * 1000));
        if (coverBitmap != null) meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap);
        session.setMetadata(meta.build());

        PlaybackStateCompat.Builder state = new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO)
            .setState(npPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                (long) (positionSec * 1000), npPlaying ? 1f : 0f);
        session.setPlaybackState(state.build());
        session.setActive(true);

        showNotification();
        call.resolve();
    }

    @PluginMethod
    public void clearNowPlaying(PluginCall call) {
        NotificationManagerCompat.from(getContext()).cancel(NOTIFICATION_ID);
        if (session != null) session.setActive(false);
        call.resolve();
    }

    private void showNotification() {
        Context ctx = getContext();
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return; // no permission — lock-screen controls via the media session still work
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Book playback controls");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_voxreader)
            .setContentTitle(npTitle)
            .setContentText(npAuthor)
            .setContentIntent(contentIntent)
            .setOngoing(npPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(new NotificationCompat.Action(R.drawable.ic_stat_prev, "Back", broadcast(ACTION_PREV, 1)))
            .addAction(npPlaying
                ? new NotificationCompat.Action(R.drawable.ic_stat_pause, "Pause", broadcast(ACTION_PAUSE, 2))
                : new NotificationCompat.Action(R.drawable.ic_stat_play, "Play", broadcast(ACTION_PLAY, 3)))
            .addAction(new NotificationCompat.Action(R.drawable.ic_stat_next, "Forward", broadcast(ACTION_NEXT, 4)))
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        if (coverBitmap != null) b.setLargeIcon(coverBitmap);

        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, b.build());
    }

    private PendingIntent broadcast(String action, int requestCode) {
        Intent i = new Intent(action).setPackage(getContext().getPackageName());
        return PendingIntent.getBroadcast(getContext(), requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /* ---------------- notification permission ---------------- */

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            call.resolve(new JSObject().put("granted", true));
            return;
        }
        requestPermissionForAlias("notifications", call, "notifPermDone");
    }

    @PermissionCallback
    private void notifPermDone(PluginCall call) {
        boolean granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        call.resolve(new JSObject().put("granted", granted));
    }

    /* ---------------- lifecycle ---------------- */

    @Override
    protected void handleOnDestroy() {
        try {
            if (tts != null) { tts.stop(); tts.shutdown(); }
            if (buttonReceiver != null) getContext().unregisterReceiver(buttonReceiver);
            if (session != null) session.release();
            NotificationManagerCompat.from(getContext()).cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {}
    }
}
