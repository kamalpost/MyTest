package app.focusphone.phone.screens

import android.view.View
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost

/** A scrollable block of text with an OK action. */
open class InfoScreen(
    host: ScreenHost,
    override val title: String,
    private val body: () -> String,
    override val leftSoft: String? = "OK",
    override val rightSoft: String? = "Back",
    private val onOk: (() -> Unit)? = null,
) : Screen(host) {
    private lateinit var textView: android.widget.TextView
    private lateinit var scroll: android.widget.ScrollView

    override fun createView(): View {
        textView = Lcd.text(ctx, body(), 14f)
        scroll = Lcd.scroll(ctx, textView)
        return scroll
    }

    override fun refresh() {
        textView.text = body()
    }

    override fun onKey(key: Key): Boolean = when (key) {
        Key.UP -> { scroll.smoothScrollBy(0, -Lcd.dp(ctx, 40)); true }
        Key.DOWN -> { scroll.smoothScrollBy(0, Lcd.dp(ctx, 40)); true }
        Key.OK -> { if (onOk != null) onOk.invoke() else host.pop(); true }
        else -> false
    }
}

/** Yes/No question. */
class ConfirmScreen(
    host: ScreenHost,
    title: String,
    text: String,
    private val onYes: () -> Unit,
) : InfoScreen(host, title, { text }, leftSoft = "Yes", rightSoft = "No") {
    override fun onKey(key: Key): Boolean = when (key) {
        Key.OK, Key.SOFT_LEFT -> { onYes(); true }
        else -> super.onKey(key)
    }
}
