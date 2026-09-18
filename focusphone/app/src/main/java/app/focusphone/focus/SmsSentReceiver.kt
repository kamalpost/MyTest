package app.focusphone.focus

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

/** Receives the SmsManager "sent" result and relays it to the feature phone UI. */
class SmsSentReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RESULT = "app.focusphone.action.SMS_RESULT"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_OK = "ok"
        const val EXTRA_REASON = "reason"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("silent", false)) return
        val ok = resultCode == Activity.RESULT_OK
        val reason = when (resultCode) {
            Activity.RESULT_OK -> ""
            SmsManager.RESULT_ERROR_NO_SERVICE -> "no service"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off"
            SmsManager.RESULT_ERROR_NULL_PDU -> "bad message"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "limit exceeded"
            else -> "error $resultCode"
        }
        context.sendBroadcast(
            Intent(ACTION_RESULT).setPackage(context.packageName)
                .putExtra(EXTRA_ADDRESS, intent.getStringExtra(EXTRA_ADDRESS))
                .putExtra(EXTRA_OK, ok)
                .putExtra(EXTRA_REASON, reason)
        )
    }
}
