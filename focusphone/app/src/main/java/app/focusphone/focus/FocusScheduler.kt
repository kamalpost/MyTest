package app.focusphone.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.focusphone.data.Prefs
import app.focusphone.data.Schedule
import app.focusphone.phone.FeaturePhoneActivity

/**
 * Owns the focus-session state machine and the exact alarms that drive it.
 *
 *  idle ──(schedule start / "start now")──▶ active ──(end time)──▶ idle
 *                                            │  ▲
 *                                   (break)  ▼  │ (break over)
 *                                          on break
 *                                            │
 *                                   (end session) ──▶ idle (window skipped)
 */
object FocusScheduler {
    private const val TAG = "FocusScheduler"

    const val ACTION_SESSION_START = "app.focusphone.action.SESSION_START"
    const val ACTION_SESSION_END = "app.focusphone.action.SESSION_END"
    const val ACTION_BREAK_END = "app.focusphone.action.BREAK_END"

    /** Sent (package-local) whenever the state changes so open UI can refresh. */
    const val ACTION_STATE_CHANGED = "app.focusphone.action.STATE_CHANGED"

    private const val RC_START = 1
    private const val RC_END = 2
    private const val RC_BREAK = 3

    /** Recompute all alarms from the current state. Safe to call anytime. */
    fun reschedule(context: Context) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        val now = System.currentTimeMillis()

        cancel(ctx, RC_START, ACTION_SESSION_START)
        cancel(ctx, RC_END, ACTION_SESSION_END)
        cancel(ctx, RC_BREAK, ACTION_BREAK_END)

        if (prefs.sessionActive && now >= prefs.sessionEndAt) {
            prefs.sessionActive = false
            prefs.breakUntil = 0
        }

        if (prefs.sessionActive) {
            setExact(ctx, RC_END, ACTION_SESSION_END, prefs.sessionEndAt)
            if (prefs.isOnBreak(now)) {
                setExact(ctx, RC_BREAK, ACTION_BREAK_END, prefs.breakUntil)
            }
        } else {
            val schedules = prefs.schedules
            val current = Schedule.currentWindow(schedules, now)
            if (current != null && current.end > prefs.skipWindowUntil) {
                // We are inside a window (e.g. after a reboot or when a schedule was just added).
                startSession(ctx, current.end)
                return
            }
            val next = Schedule.nextWindow(schedules, now)
            if (next != null) {
                setExact(ctx, RC_START, ACTION_SESSION_START, next.start, alarmClock = true)
                Log.i(TAG, "Next focus window at ${next.start}")
            }
        }
        FocusService.sync(ctx)
    }

    fun startSession(context: Context, endAt: Long) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        prefs.sessionActive = true
        prefs.sessionStartedAt = System.currentTimeMillis()
        prefs.sessionEndAt = endAt
        prefs.breakUntil = 0
        reschedule(ctx)
        launchPhone(ctx)
        notifyChanged(ctx)
    }

    fun startSessionNow(context: Context, minutes: Int) =
        startSession(context, System.currentTimeMillis() + minutes * 60_000L)

    /** Ends the running session; if it came from a schedule that window will not restart. */
    fun endSession(context: Context) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        if (prefs.sessionActive) prefs.skipWindowUntil = prefs.sessionEndAt
        prefs.sessionActive = false
        prefs.breakUntil = 0
        reschedule(ctx)
        notifyChanged(ctx)
    }

    /** The "Break" button: back to the normal phone for [minutes], then focus resumes. */
    fun startBreak(context: Context, minutes: Int) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.sessionActive) return
        val until = minOf(System.currentTimeMillis() + minutes * 60_000L, prefs.sessionEndAt)
        prefs.breakUntil = until
        reschedule(ctx)
        notifyChanged(ctx)
    }

    fun endBreak(context: Context) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        prefs.breakUntil = 0
        reschedule(ctx)
        if (prefs.isFocusActive()) launchPhone(ctx)
        notifyChanged(ctx)
    }

    /** Human readable status line for the setup screen / notification. */
    fun statusText(context: Context): String {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        return when {
            prefs.isOnBreak(now) -> "On a break until ${fmt(prefs.breakUntil)} · focus resumes after"
            prefs.isFocusActive(now) -> "Focus mode active until ${fmt(prefs.sessionEndAt)}"
            else -> {
                val next = Schedule.nextWindow(prefs.schedules, now)
                if (next == null) "No focus time scheduled" else "Next focus: ${fmtDay(next.start)} ${fmt(next.start)} – ${fmt(next.end)}"
            }
        }
    }

    fun fmt(t: Long): String = android.text.format.DateFormat.format("HH:mm", t).toString()
    private fun fmtDay(t: Long): String = android.text.format.DateFormat.format("EEE", t).toString()

    // ---- internals ----

    fun launchPhone(ctx: Context) {
        try {
            val i = Intent(ctx, FeaturePhoneActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ctx.startActivity(i)
        } catch (e: Exception) {
            Log.w(TAG, "Direct launch blocked: ${e.message}")
        }
        // Belt and braces: the foreground service also posts a full-screen intent
        // notification which brings the phone up when background starts are restricted.
        FocusService.sync(ctx)
    }

    fun notifyChanged(ctx: Context) {
        ctx.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(ctx.packageName))
    }

    private fun pending(ctx: Context, rc: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, rc, Intent(ctx, FocusReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancel(ctx: Context, rc: Int, action: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, rc, action))
    }

    fun canScheduleExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun setExact(ctx: Context, rc: Int, action: String, at: Long, alarmClock: Boolean = false) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, rc, action)
        try {
            if (canScheduleExact(ctx)) {
                if (alarmClock) {
                    val show = PendingIntent.getActivity(
                        ctx, 100, Intent(ctx, app.focusphone.setup.SetupActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
