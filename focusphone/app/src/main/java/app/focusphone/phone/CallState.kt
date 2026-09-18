package app.focusphone.phone

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

/** Process-wide snapshot of the phone call state, fed by [app.focusphone.focus.CallStateReceiver]. */
object CallState {
    const val ACTION_CHANGED = "app.focusphone.action.CALL_STATE"

    @Volatile var state: String = TelephonyManager.EXTRA_STATE_IDLE
        private set
    @Volatile var number: String? = null
    @Volatile var offHookSince: Long = 0L
        private set

    val isIdle: Boolean get() = state == TelephonyManager.EXTRA_STATE_IDLE
    val isRinging: Boolean get() = state == TelephonyManager.EXTRA_STATE_RINGING
    val isOffHook: Boolean get() = state == TelephonyManager.EXTRA_STATE_OFFHOOK

    fun update(ctx: Context, newState: String?, newNumber: String?) {
        if (newState == null) return
        val changed = newState != state
        if (!newNumber.isNullOrBlank()) number = newNumber
        if (changed) {
            when (newState) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> offHookSince = System.currentTimeMillis()
                TelephonyManager.EXTRA_STATE_IDLE -> { number = null; offHookSince = 0L }
            }
        }
        state = newState
        if (changed || !newNumber.isNullOrBlank()) {
            ctx.sendBroadcast(Intent(ACTION_CHANGED).setPackage(ctx.packageName))
        }
    }

    /** True while a call is ringing, dialing, active or on hold. */
    fun isInCall(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).isInCall
    } catch (e: SecurityException) {
        !isIdle
    }

    /** Answer the ringing call. Returns false when the permission is missing. */
    fun answer(ctx: Context): Boolean = try {
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        @Suppress("DEPRECATION")
        tm.acceptRingingCall()
        true
    } catch (e: SecurityException) {
        false
    }

    /** Reject a ringing call or hang up the active one. Returns false when not possible. */
    fun hangUp(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            tm.endCall()
        } catch (e: SecurityException) {
            false
        }
    }
}
