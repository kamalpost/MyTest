package app.focusphone.phone.screens

import android.view.View
import android.widget.TextView
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost

/** Asks for the optional Break PIN before leaving focus mode. */
class PinScreen(host: ScreenHost, private val onSuccess: () -> Unit) : Screen(host) {
    override val title = "Break PIN"
    override val leftSoft: String? = "OK"
    override val rightSoft: String? get() = if (entered.isEmpty()) "Back" else "Clear"

    private val entered = StringBuilder()
    private lateinit var dots: TextView
    private lateinit var hint: TextView

    override fun createView(): View {
        val col = Lcd.column(ctx)
        col.addView(Lcd.spacer(ctx))
        col.addView(Lcd.text(ctx, "Enter PIN to leave focus", 14f, center = true))
        dots = Lcd.text(ctx, "", 30f, bold = true, center = true)
        col.addView(dots)
        hint = Lcd.text(ctx, "", 12f, center = true)
        col.addView(hint)
        col.addView(Lcd.spacer(ctx))
        return col
    }

    override fun refresh() {
        dots.text = if (entered.isEmpty()) "_" else "●".repeat(entered.length)
        val wait = ctx.prefs.pinLockedForSeconds()
        hint.text = if (wait > 0) "Too many tries · wait ${wait}s" else ""
    }

    override fun onTick() = refresh()

    override fun onKey(key: Key): Boolean {
        when {
            key.isDigit -> if (entered.length < 8) entered.append(key.char)
            key == Key.SOFT_RIGHT -> {
                if (entered.isEmpty()) return false
                entered.setLength(entered.length - 1)
            }
            key == Key.OK || key == Key.SOFT_LEFT -> {
                val prefs = ctx.prefs
                if (prefs.pinLockedForSeconds() > 0) {
                    ctx.lcdToast("Wait before trying again")
                } else if (prefs.checkBreakPin(entered.toString())) {
                    host.pop()
                    onSuccess()
                    return true
                } else {
                    ctx.lcdToast("Wrong PIN")
                    entered.setLength(0)
                }
            }
            else -> return false
        }
        refresh()
        return true
    }
}
