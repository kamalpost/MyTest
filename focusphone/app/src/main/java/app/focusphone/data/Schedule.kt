package app.focusphone.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** A recurring focus window. Days are a bitmask: bit 0 = Monday … bit 6 = Sunday. */
data class Schedule(
    val id: Long,
    val startMinutes: Int,   // minutes after midnight
    val endMinutes: Int,     // minutes after midnight; <= start means "ends next day"
    val days: Int,
    val enabled: Boolean = true,
) {
    data class Window(val start: Long, val end: Long, val schedule: Schedule)

    fun hasDay(mondayBasedIndex: Int) = (days shr mondayBasedIndex) and 1 == 1

    fun durationMinutes(): Int =
        if (endMinutes > startMinutes) endMinutes - startMinutes else endMinutes + 24 * 60 - startMinutes

    /** Occurrences whose start day lies within [-1, +7] days of [now]. */
    fun windowsAround(now: Long): List<Window> {
        if (!enabled || days == 0) return emptyList()
        val out = ArrayList<Window>()
        val cal = Calendar.getInstance()
        for (offset in -1..7) {
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, offset)
            val dow = cal.get(Calendar.DAY_OF_WEEK) // SUNDAY=1 … SATURDAY=7
            val mondayIdx = (dow + 5) % 7           // MONDAY -> 0 … SUNDAY -> 6
            if (!hasDay(mondayIdx)) continue
            cal.set(Calendar.HOUR_OF_DAY, startMinutes / 60)
            cal.set(Calendar.MINUTE, startMinutes % 60)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            val end = start + durationMinutes() * 60_000L
            out.add(Window(start, end, this))
        }
        return out
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("start", startMinutes).put("end", endMinutes)
        .put("days", days).put("enabled", enabled)

    companion object {
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0011111
        val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        fun fromJson(o: JSONObject) = Schedule(
            id = o.getLong("id"),
            startMinutes = o.getInt("start"),
            endMinutes = o.getInt("end"),
            days = o.getInt("days"),
            enabled = o.optBoolean("enabled", true),
        )

        fun listToJson(list: List<Schedule>): String =
            JSONArray().also { arr -> list.forEach { arr.put(it.toJson()) } }.toString()

        fun listFromJson(s: String?): List<Schedule> {
            if (s.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(s)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /** The window that contains [now], if any (earliest-ending wins). */
        fun currentWindow(list: List<Schedule>, now: Long): Window? =
            list.flatMap { it.windowsAround(now) }
                .filter { now >= it.start && now < it.end }
                .minByOrNull { it.end }

        /** The next window that starts strictly after [now]. */
        fun nextWindow(list: List<Schedule>, now: Long): Window? =
            list.flatMap { it.windowsAround(now) }
                .filter { it.start > now }
                .minByOrNull { it.start }

        fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

        fun formatDays(days: Int): String = when (days) {
            ALL_DAYS -> "Every day"
            WEEKDAYS -> "Weekdays"
            0b1100000 -> "Weekends"
            else -> DAY_NAMES.filterIndexed { i, _ -> (days shr i) and 1 == 1 }.joinToString(" ")
        }
    }
}
