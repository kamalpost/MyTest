package app.focusphone.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.focusphone.data.Prefs
import app.focusphone.data.Schedule

/** Receives the exact alarms plus boot/time changes and advances the session state machine. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        val now = System.currentTimeMillis()
        Log.i("FocusReceiver", "onReceive ${intent.action}")
        when (intent.action) {
            FocusScheduler.ACTION_SESSION_START -> {
                if (prefs.sessionActive) return
                // Re-derive the window instead of trusting a stale extra.
                val win = Schedule.currentWindow(prefs.schedules, now + 1_000)
                if (win != null && win.end > prefs.skipWindowUntil) {
                    FocusScheduler.startSession(ctx, win.end)
                } else {
                    FocusScheduler.reschedule(ctx)
                }
            }
            FocusScheduler.ACTION_SESSION_END -> {
                prefs.sessionActive = false
                prefs.breakUntil = 0
                FocusScheduler.reschedule(ctx)
                FocusScheduler.notifyChanged(ctx)
            }
            FocusScheduler.ACTION_BREAK_END -> FocusScheduler.endBreak(ctx)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                FocusScheduler.reschedule(ctx)
                if (prefs.isFocusActive(now)) FocusScheduler.launchPhone(ctx)
            }
        }
    }
}
