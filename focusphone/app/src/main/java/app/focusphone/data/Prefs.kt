package app.focusphone.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** All persistent state. Small enough for SharedPreferences. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("focusphone", Context.MODE_PRIVATE)

    // ---- schedules ----
    var schedules: List<Schedule>
        get() = Schedule.listFromJson(sp.getString("schedules", null))
        set(v) = sp.edit().putString("schedules", Schedule.listToJson(v)).apply()

    // ---- live session state ----
    var sessionActive: Boolean
        get() = sp.getBoolean("sessionActive", false)
        set(v) = sp.edit().putBoolean("sessionActive", v).apply()

    var sessionStartedAt: Long
        get() = sp.getLong("sessionStartedAt", 0L)
        set(v) = sp.edit().putLong("sessionStartedAt", v).apply()

    var sessionEndAt: Long
        get() = sp.getLong("sessionEndAt", 0L)
        set(v) = sp.edit().putLong("sessionEndAt", v).apply()

    /** While now < breakUntil the phone is back to normal; focus resumes afterwards. */
    var breakUntil: Long
        get() = sp.getLong("breakUntil", 0L)
        set(v) = sp.edit().putLong("breakUntil", v).apply()

    /** A scheduled window the user ended early must not restart until it is over. */
    var skipWindowUntil: Long
        get() = sp.getLong("skipWindowUntil", 0L)
        set(v) = sp.edit().putLong("skipWindowUntil", v).apply()

    fun isOnBreak(now: Long = System.currentTimeMillis()) = sessionActive && now < breakUntil

    /** True when the feature phone should be on screen and pinned. */
    fun isFocusActive(now: Long = System.currentTimeMillis()) =
        sessionActive && now < sessionEndAt && now >= breakUntil

    // ---- options ----
    var kioskEnabled: Boolean
        get() = sp.getBoolean("kioskEnabled", true)
        set(v) = sp.edit().putBoolean("kioskEnabled", v).apply()

    var dndEnabled: Boolean
        get() = sp.getBoolean("dndEnabled", true)
        set(v) = sp.edit().putBoolean("dndEnabled", v).apply()

    var keyTones: Boolean
        get() = sp.getBoolean("keyTones", true)
        set(v) = sp.edit().putBoolean("keyTones", v).apply()

    // ---- DND state saved so it can be restored ----
    var dndApplied: Boolean
        get() = sp.getBoolean("dndApplied", false)
        set(v) = sp.edit().putBoolean("dndApplied", v).apply()
    var savedInterruptionFilter: Int
        get() = sp.getInt("savedFilter", -1)
        set(v) = sp.edit().putInt("savedFilter", v).apply()
    var savedPolicyCategories: Int
        get() = sp.getInt("savedPolicyCats", -1)
        set(v) = sp.edit().putInt("savedPolicyCats", v).apply()
    var savedPolicyCallSenders: Int
        get() = sp.getInt("savedPolicyCalls", -1)
        set(v) = sp.edit().putInt("savedPolicyCalls", v).apply()
    var savedPolicyMessageSenders: Int
        get() = sp.getInt("savedPolicyMsgs", -1)
        set(v) = sp.edit().putInt("savedPolicyMsgs", v).apply()
    var savedPolicyVisualEffects: Int
        get() = sp.getInt("savedPolicyEffects", 0)
        set(v) = sp.edit().putInt("savedPolicyEffects", v).apply()

    // ---- messaging ----
    var unreadSms: Int
        get() = sp.getInt("unreadSms", 0)
        set(v) = sp.edit().putInt("unreadSms", v).apply()

    /** Messages we sent (non-default SMS apps cannot write to the SMS provider). */
    data class SentSms(val address: String, val body: String, val date: Long)

    var sentMessages: List<SentSms>
        get() = try {
            val arr = JSONArray(sp.getString("sentSms", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SentSms(o.getString("a"), o.getString("b"), o.getLong("d"))
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) {
            val arr = JSONArray()
            v.takeLast(200).forEach { arr.put(JSONObject().put("a", it.address).put("b", it.body).put("d", it.date)) }
            sp.edit().putString("sentSms", arr.toString()).apply()
        }

    fun addSentMessage(address: String, body: String) {
        sentMessages = sentMessages + SentSms(address, body, System.currentTimeMillis())
    }
}
