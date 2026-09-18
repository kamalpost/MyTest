package app.focusphone.phone.screens

import android.content.Context
import android.media.AudioManager
import android.view.View
import android.widget.TextView
import app.focusphone.phone.CallState
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost

/**
 * Incoming / active call. Stays on top and swallows every key until the call is over:
 * Call or OK answers, End (or the right soft key) rejects / hangs up.
 */
class CallScreen(host: ScreenHost) : Screen(host) {
    override val title: String get() = if (CallState.isRinging) "Incoming call" else "In call"
    override val leftSoft: String?
        get() = if (CallState.isRinging) "Answer" else if (speakerOn()) "Earpiece" else "Speaker"
    override val rightSoft: String? get() = if (CallState.isRinging) "Reject" else "End"

    private lateinit var name: TextView
    private lateinit var number: TextView
    private lateinit var status: TextView

    override fun createView(): View {
        val col = Lcd.column(ctx)
        col.addView(Lcd.spacer(ctx))
        col.addView(Lcd.text(ctx, "☎", 40f, bold = true, center = true))
        name = Lcd.text(ctx, "", 20f, bold = true, center = true, singleLine = true)
        number = Lcd.text(ctx, "", 14f, center = true, singleLine = true)
        status = Lcd.text(ctx, "", 14f, center = true)
        col.addView(name)
        col.addView(number)
        col.addView(Lcd.spacer(ctx))
        col.addView(status)
        col.addView(Lcd.spacer(ctx, 0.5f))
        return col
    }

    override fun refresh() {
        val n = CallState.number
        val who = n?.let { ctx.contacts.nameFor(it) }
        name.text = who ?: n ?: "Unknown number"
        number.text = if (who != null) n else ""
        status.text = when {
            CallState.isRinging -> "Ringing…"
            CallState.isOffHook -> {
                val s = maxOf(0L, (System.currentTimeMillis() - CallState.offHookSince) / 1000)
                "%02d:%02d".format(s / 60, s % 60)
            }
            else -> "Call ended"
        }
    }

    override fun onTick() = refresh()

    override fun onKey(key: Key): Boolean {
        when (key) {
            Key.CALL, Key.OK -> if (CallState.isRinging) answer()
            Key.SOFT_LEFT -> if (CallState.isRinging) answer() else toggleSpeaker()
            Key.END, Key.SOFT_RIGHT -> hangUp()
            else -> {}
        }
        host.refreshChrome()
        return true
    }

    private fun answer() {
        if (!CallState.answer(ctx)) ctx.lcdToast("Answer from the phone's call screen")
    }

    private fun hangUp() {
        if (!CallState.hangUp(ctx)) ctx.lcdToast("Hang up from the phone's call screen")
    }

    private fun audio() = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Suppress("DEPRECATION")
    private fun speakerOn(): Boolean = try { audio().isSpeakerphoneOn } catch (e: Exception) { false }

    @Suppress("DEPRECATION")
    private fun toggleSpeaker() {
        try {
            audio().isSpeakerphoneOn = !speakerOn()
        } catch (e: Exception) {
            ctx.lcdToast("Speaker not available")
        }
    }
}
