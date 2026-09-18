package app.focusphone.phone.screens

import android.view.View
import android.widget.TextView
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost

class DialerScreen(host: ScreenHost, initial: String = "") : Screen(host) {
    override val title = "Phone"
    override val leftSoft: String? get() = "Call"
    override val rightSoft: String? get() = if (number.isEmpty()) "Back" else "Clear"

    private val number = StringBuilder(initial)
    private lateinit var numberView: TextView
    private lateinit var nameView: TextView
    private var lastStarAt = 0L

    override fun createView(): View {
        val col = Lcd.column(ctx)
        col.addView(Lcd.spacer(ctx))
        numberView = Lcd.text(ctx, "", 28f, bold = true, center = true)
        nameView = Lcd.text(ctx, "", 13f, center = true, singleLine = true)
        col.addView(numberView)
        col.addView(nameView)
        col.addView(Lcd.spacer(ctx))
        col.addView(Lcd.text(ctx, "Type a number · ▲▼ contacts", 12f, center = true))
        return col
    }

    override fun refresh() {
        numberView.text = if (number.isEmpty()) "_" else number.toString()
        nameView.text = if (number.length >= 3) (ctx.contacts.nameFor(number.toString()) ?: "") else ""
    }

    override fun onKey(key: Key): Boolean {
        when {
            key == Key.STAR -> {
                val now = System.currentTimeMillis()
                if (number.endsWith("*") && now - lastStarAt < 900) number.setLength(number.length - 1).also { number.append('+') }
                else number.append('*')
                lastStarAt = now
            }
            key.isDialChar -> number.append(key.char)
            key == Key.SOFT_RIGHT -> {
                if (number.isEmpty()) return false
                number.setLength(number.length - 1)
            }
            key == Key.CALL || key == Key.OK || key == Key.SOFT_LEFT -> {
                if (number.isEmpty()) host.push(ContactsScreen(host)) else ctx.placeCall(number.toString())
                return true
            }
            key == Key.UP || key == Key.DOWN -> {
                host.push(ContactsScreen(host))
                return true
            }
            else -> return false
        }
        refresh()
        return true
    }
}
