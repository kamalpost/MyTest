package app.focusphone.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog

data class CallEntry(val number: String, val cachedName: String?, val date: Long, val type: Int, val durationSec: Long) {
    val isMissed: Boolean get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
    val isOutgoing: Boolean get() = type == CallLog.Calls.OUTGOING_TYPE
    val glyph: String get() = if (isMissed) "✗" else if (isOutgoing) "↗" else "↙"
}

/** Recent calls from the system call log. */
class CallLogRepo(private val ctx: Context) {
    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun recent(limit: Int = 60): List<CallEntry> {
        if (!hasPermission()) return emptyList()
        val out = ArrayList<CallEntry>()
        val proj = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.DURATION)
        try {
            ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, proj, null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val number = c.getString(0)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    out.add(CallEntry(number, c.getString(1)?.takeIf { it.isNotBlank() }, c.getLong(2), c.getInt(3), c.getLong(4)))
                }
            }
        } catch (e: Exception) {
            // provider unavailable on this device
        }
        return out
    }
}
