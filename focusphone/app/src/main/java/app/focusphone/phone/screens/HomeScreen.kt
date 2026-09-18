package app.focusphone.phone.screens

import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import app.focusphone.focus.FocusScheduler
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost

/** Idle screen: big clock, date, focus countdown, unread messages. */
class HomeScreen(host: ScreenHost) : Screen(host) {
    override val title: String get() = "FocusPhone"
    override val leftSoft: String? = "Menu"
    override val rightSoft: String? = "Break"

    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var focus: TextView
    private lateinit var unread: TextView

    override fun createView(): View {
        val col = Lcd.column(ctx)
        col.addView(Lcd.spacer(ctx))
        clock = Lcd.text(ctx, "", 40f, bold = true, center = true)
        date = Lcd.text(ctx, "", 14f, center = true)
        col.addView(clock)
        col.addView(date)
        col.addView(Lcd.spacer(ctx))
        focus = Lcd.text(ctx, "", 13f, center = true)
        unread = Lcd.text(ctx, "", 13f, bold = true, center = true)
        col.addView(focus)
        col.addView(unread)
        col.addView(Lcd.spacer(ctx, 0.4f))
        return col
    }

    override fun refresh() {
        val now = System.currentTimeMillis()
        clock.text = DateFormat.format("HH:mm", now)
        date.text = DateFormat.format("EEE d MMM", now)
        val prefs = ctx.prefs
        val left = maxOf(0L, prefs.sessionEndAt - now)
        val mins = (left + 59_999) / 60_000
        val leftText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
        focus.text = "Focus until ${FocusScheduler.fmt(prefs.sessionEndAt)} · $leftText left"
        val n = prefs.unreadSms
        unread.text = when {
            n <= 0 -> ""
            n == 1 -> "✉ 1 new message"
            else -> "✉ $n new messages"
        }
    }

    override fun onTick() = refresh()

    override fun onKey(key: Key): Boolean {
        when (key) {
            Key.SOFT_LEFT, Key.OK -> host.push(MenuScreen(host))
            Key.SOFT_RIGHT -> host.push(BreakScreen(host))
            Key.CALL -> host.push(RecentCallsScreen(host))
            Key.UP, Key.DOWN -> host.push(ContactsScreen(host))
            Key.LEFT, Key.RIGHT -> host.push(MessagesScreen(host))
            Key.END -> {}
            else -> if (key.isDialChar) host.push(DialerScreen(host, key.char.toString()))
        }
        return true
    }
}
