package app.focusphone.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import app.focusphone.phone.CallState

/**
 * Tracks ringing / off-hook / idle so the feature phone can show its own call screen and
 * so focus mode never fights the phone call UI. Declared in the manifest and also registered
 * by [FocusService] for the whole session.
 */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        CallState.update(context.applicationContext, state, number)
    }
}
