package app.focusphone.phone.screens

import android.os.SystemClock
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import app.focusphone.focus.FocusScheduler
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost

/** Clock + session countdown + a stopwatch. */
class ClockScreen(host: ScreenHost) : Screen(host) {
    override val title = "Clock"
    override val leftSoft: String? get() = if (running) "Stop" else "Start"

    private lateinit var time: TextView
    private lateinit var date: TextView
    private lateinit var session: TextView
    private lateinit var stopwatch: TextView

    private var running = false
    private var base = 0L
    private var accumulated = 0L

    override fun createView(): View {
        val col = Lcd.column(ctx)
        time = Lcd.text(ctx, "", 34f, bold = true, center = true)
        date = Lcd.text(ctx, "", 13f, center = true)
        session = Lcd.text(ctx, "", 12f, center = true)
        stopwatch = Lcd.text(ctx, "", 22f, bold = true, center = true)
        col.addView(time)
        col.addView(date)
        col.addView(Lcd.spacer(ctx))
        col.addView(session)
        col.addView(Lcd.spacer(ctx))
        col.addView(Lcd.text(ctx, "Stopwatch", 11f, center = true))
        col.addView(stopwatch)
        col.addView(Lcd.text(ctx, "OK start/stop · * reset", 11f, center = true))
        return col
    }

    override fun refresh() {
        val now = System.currentTimeMillis()
        time.text = DateFormat.format("HH:mm:ss", now)
        date.text = DateFormat.format("EEEE, d MMMM", now)
        val prefs = ctx.prefs
        val mins = maxOf(0L, (prefs.sessionEndAt - now + 59_999) / 60_000)
        session.text = "Focus ends ${FocusScheduler.fmt(prefs.sessionEndAt)} ($mins min)"
        val elapsed = accumulated + if (running) SystemClock.elapsedRealtime() - base else 0L
        val s = elapsed / 1000
        stopwatch.text = "%02d:%02d:%02d.%d".format(s / 3600, (s / 60) % 60, s % 60, (elapsed / 100) % 10)
    }

    override fun onTick() = refresh()

    override fun onKey(key: Key): Boolean {
        when (key) {
            Key.OK, Key.SOFT_LEFT -> {
                if (running) {
                    accumulated += SystemClock.elapsedRealtime() - base
                    running = false
                } else {
                    base = SystemClock.elapsedRealtime()
                    running = true
                }
            }
            Key.STAR, Key.D0 -> {
                running = false
                accumulated = 0
            }
            else -> return false
        }
        refresh()
        return true
    }
}
