package app.focusphone.phone.screens

import android.text.format.DateFormat
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.MultiTap
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost
import app.focusphone.phone.SmsThread

class MessagesScreen(host: ScreenHost) : ListScreen(host, digitShortcuts = true) {
    override val title = "Messages"
    override fun items() = listOf("Inbox", "Write message")

    override fun onShow() {
        ctx.prefs.unreadSms = 0
        super.onShow()
    }

    override fun onSelect(index: Int) {
        when (index) {
            0 -> ctx.ensureSmsPermission { host.push(InboxScreen(host)) }
            1 -> ctx.ensureSmsPermission { host.push(ComposeScreen(host, null)) }
        }
    }
}

class InboxScreen(host: ScreenHost) : ListScreen(host) {
    override val title = "Inbox"
    override val emptyText: String
        get() = if (ctx.sms.canRead()) "No messages" else "No SMS access.\nPress OK to allow."
    override val leftSoft: String? get() = "Open"

    private var threads: List<SmsThread> = emptyList()

    override fun items(): List<String> {
        threads = ctx.sms.threads()
        return threads.map { t ->
            val who = ctx.contacts.nameFor(t.address) ?: t.address
            val arrow = if (t.last.outgoing) "→ " else ""
            "$who: $arrow${t.last.body.replace('\n', ' ')}"
        }
    }

    override fun onSelect(index: Int) {
        if (threads.isEmpty()) {
            if (!ctx.sms.canRead()) ctx.ensureSmsPermission { invalidateItems() }
            return
        }
        host.push(ConversationScreen(host, threads[index].address))
    }
}

class ConversationScreen(host: ScreenHost, private val address: String) : Screen(host) {
    override val title: String get() = ctx.contacts.nameFor(address) ?: address
    override val leftSoft: String? = "Reply"

    private lateinit var scroll: ScrollView
    private lateinit var column: LinearLayout

    override fun createView(): View {
        column = Lcd.column(ctx, fill = false)
        scroll = Lcd.scroll(ctx, column)
        return scroll
    }

    override fun refresh() {
        column.removeAllViews()
        val msgs = ctx.sms.conversation(address)
        if (msgs.isEmpty()) column.addView(Lcd.text(ctx, "No messages", 14f, center = true))
        for (m in msgs) {
            val who = if (m.outgoing) "Me" else (ctx.contacts.nameFor(m.address) ?: m.address)
            val stamp = DateFormat.format("d MMM HH:mm", m.date)
            column.addView(Lcd.text(ctx, "$who · $stamp", 11f, bold = true, inverted = m.outgoing))
            val body: TextView = Lcd.text(ctx, m.body, 14f)
            column.addView(body)
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onKey(key: Key): Boolean = when (key) {
        Key.UP -> { scroll.smoothScrollBy(0, -Lcd.dp(ctx, 48)); true }
        Key.DOWN -> { scroll.smoothScrollBy(0, Lcd.dp(ctx, 48)); true }
        Key.OK, Key.SOFT_LEFT -> { host.push(ComposeScreen(host, address)); true }
        Key.CALL -> { ctx.placeCall(address); true }
        else -> false
    }
}

/** Write an SMS with multi-tap text entry, optionally choosing the recipient first. */
class ComposeScreen(host: ScreenHost, presetAddress: String?) : Screen(host) {
    private enum class Phase { RECIPIENT, TEXT }

    private var phase = if (presetAddress == null) Phase.RECIPIENT else Phase.TEXT
    private val address = StringBuilder(presetAddress ?: "")
    private val input = MultiTap()

    override val title: String
        get() = if (phase == Phase.RECIPIENT) "To:" else "Write · ${input.mode.label}"
    override val leftSoft: String?
        get() = if (phase == Phase.RECIPIENT) "Contacts" else "Send"
    override val rightSoft: String?
        get() = when (phase) {
            Phase.RECIPIENT -> if (address.isEmpty()) "Back" else "Clear"
            Phase.TEXT -> if (input.isEmpty) "Back" else "Clear"
        }

    private lateinit var headline: TextView
    private lateinit var body: TextView
    private lateinit var hint: TextView
    private lateinit var scroll: ScrollView

    override fun createView(): View {
        val col = Lcd.column(ctx)
        headline = Lcd.text(ctx, "", 13f, bold = true, singleLine = true)
        col.addView(headline)
        body = Lcd.text(ctx, "", 16f)
        scroll = Lcd.scroll(ctx, body)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        col.addView(scroll)
        hint = Lcd.text(ctx, "", 11f, center = true)
        col.addView(hint)
        return col
    }

    override fun refresh() {
        when (phase) {
            Phase.RECIPIENT -> {
                headline.text = "Enter number"
                body.text = if (address.isEmpty()) "_" else address.toString()
                hint.text = "OK: next · Contacts: pick"
            }
            Phase.TEXT -> {
                headline.text = "To: ${ctx.contacts.nameFor(address.toString()) ?: address}"
                body.text = input.text + "▏"
                hint.text = "${input.text.length} chars · # case · 0 space"
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    override fun onKey(key: Key): Boolean {
        when (phase) {
            Phase.RECIPIENT -> when {
                key == Key.SOFT_LEFT -> host.push(ContactsScreen(host) { c ->
                    address.setLength(0); address.append(c.number)
                    phase = Phase.TEXT
                    refresh()
                })
                key.isDialChar -> address.append(if (key == Key.STAR) '+' else key.char)
                key == Key.SOFT_RIGHT -> {
                    if (address.isEmpty()) return false
                    address.setLength(address.length - 1)
                }
                key == Key.OK -> if (address.isNotEmpty()) phase = Phase.TEXT
                else -> return false
            }
            Phase.TEXT -> when {
                key.isDialChar -> input.press(key)
                key == Key.SOFT_RIGHT -> {
                    if (input.isEmpty) return false
                    input.backspace()
                }
                key == Key.OK || key == Key.SOFT_LEFT -> {
                    send(); return true
                }
                key == Key.RIGHT -> input.commit()
                key == Key.LEFT -> input.backspace()
                else -> return false
            }
        }
        refresh()
        return true
    }

    private fun send() {
        val text = input.text.trim()
        if (text.isEmpty()) {
            ctx.lcdToast("Message is empty")
            return
        }
        ctx.ensureSmsPermission {
            val err = ctx.sms.send(address.toString(), text)
            if (err == null) {
                // Back to whatever opened the composer (conversation, Messages menu or a
                // contact); the real result arrives as an LCD toast from the network.
                host.pop()
                ctx.lcdToast("Sending…")
            } else {
                ctx.lcdToast("Failed: $err")
            }
        }
    }
}
