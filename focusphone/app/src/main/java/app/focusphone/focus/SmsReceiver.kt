package app.focusphone.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.focusphone.data.Prefs

/** Counts incoming texts so the feature phone can show "1 new message" on its home screen. */
class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_NEW_SMS = "app.focusphone.action.NEW_SMS"
        const val EXTRA_FROM = "from"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val prefs = Prefs(context)
        prefs.unreadSms = prefs.unreadSms + 1
        val from = msgs.firstOrNull()?.displayOriginatingAddress ?: "unknown"
        context.sendBroadcast(
            Intent(ACTION_NEW_SMS).setPackage(context.packageName).putExtra(EXTRA_FROM, from)
        )
    }
}
