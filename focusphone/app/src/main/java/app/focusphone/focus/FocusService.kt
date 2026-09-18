package app.focusphone.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.content.IntentFilter
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import app.focusphone.R
import app.focusphone.data.Prefs
import app.focusphone.phone.CallState
import app.focusphone.phone.FeaturePhoneActivity

/**
 * Foreground service that lives for the whole focus session (including breaks).
 * Its job is to be the process the system keeps alive, and to act as a watchdog:
 * if focus is active but the feature phone is not on screen (the user escaped
 * screen pinning, or the session started while another app was open) it brings
 * the phone back, using a full-screen intent when background launches are blocked.
 */
class FocusService : Service() {
    companion object {
        private const val TAG = "FocusService"
        const val CHANNEL_SESSION = "focus_session"
        const val CHANNEL_ALERTS = "focus_alerts"
        private const val NOTIF_ID = 42
        private const val ALERT_ID = 43
        private const val WATCHDOG_MS = 2_500L

        fun sync(ctx: Context) {
            val prefs = Prefs(ctx)
            val i = Intent(ctx, FocusService::class.java)
            if (prefs.sessionActive) {
                try {
                    ctx.startForegroundService(i)
                } catch (e: Exception) {
                    Log.w(TAG, "startForegroundService failed: ${e.message}")
                }
            } else {
                ctx.stopService(i)
            }
        }

        fun ensureChannels(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SESSION, ctx.getString(R.string.notif_channel_focus), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, ctx.getString(R.string.notif_channel_alerts), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastAlertAt = 0L
    private val callReceiver = CallStateReceiver()

    private val watchdog = object : Runnable {
        override fun run() {
            val prefs = Prefs(this@FocusService)
            if (!prefs.sessionActive || System.currentTimeMillis() >= prefs.sessionEndAt) {
                FocusScheduler.reschedule(this@FocusService)
                stopSelf()
                return
            }
            updateNotification()
            // Never pull the feature phone over a ringing or active call.
            if (prefs.isFocusActive() && !FeaturePhoneActivity.isInForeground &&
                !CallState.isInCall(this@FocusService)
            ) {
                bringPhoneBack()
            }
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        val f = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(callReceiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(callReceiver, f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        try {
            unregisterReceiver(callReceiver)
        } catch (e: IllegalArgumentException) {
            // not registered
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ALERT_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun phoneIntent(): PendingIntent = PendingIntent.getActivity(
        this, 7,
        Intent(this, FeaturePhoneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(): Notification {
        val prefs = Prefs(this)
        val text = if (prefs.isOnBreak()) "On a break until ${FocusScheduler.fmt(prefs.breakUntil)}"
        else "Focus mode until ${FocusScheduler.fmt(prefs.sessionEndAt)}"
        return Notification.Builder(this, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("FocusPhone")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(phoneIntent())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun bringPhoneBack() {
        try {
            startActivity(
                Intent(this, FeaturePhoneActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            Log.w(TAG, "launch blocked: ${e.message}")
        }
        val now = System.currentTimeMillis()
        if (now - lastAlertAt < 15_000) return
        lastAlertAt = now
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = Notification.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Focus time")
            .setContentText("Tap to return to your focus phone")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(phoneIntent())
            .setFullScreenIntent(phoneIntent(), true)
            .build()
        nm.notify(ALERT_ID, n)
    }
}
