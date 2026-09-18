package app.focusphone

import app.focusphone.data.Schedule
import app.focusphone.phone.Key
import app.focusphone.phone.MultiTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class LogicTest {
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test
    fun weekdayWindowContainsNow() {
        val now = at(2026, Calendar.SEPTEMBER, 16, 10, 30) // a Wednesday
        val s = Schedule(1, 9 * 60, 12 * 60, Schedule.WEEKDAYS)
        val cur = Schedule.currentWindow(listOf(s), now)
        assertNotNull(cur)
        assertEquals(90 * 60_000L, cur!!.end - now)
        val next = Schedule.nextWindow(listOf(s), now)
        assertEquals((24 * 60 - 90) * 60_000L, next!!.start - now)
    }

    @Test
    fun weekendScheduleIsNotActiveMidweek() {
        val now = at(2026, Calendar.SEPTEMBER, 16, 10, 30)
        val s = Schedule(2, 20 * 60, 22 * 60, 0b1100000)
        assertNull(Schedule.currentWindow(listOf(s), now))
        val next = Schedule.nextWindow(listOf(s), now)!!
        val c = Calendar.getInstance().apply { timeInMillis = next.start }
        assertEquals(Calendar.SATURDAY, c.get(Calendar.DAY_OF_WEEK))
        assertEquals(20, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun overnightWindowSpansMidnight() {
        val oneAm = at(2026, Calendar.SEPTEMBER, 16, 1, 0)
        val s = Schedule(3, 22 * 60, 2 * 60, Schedule.ALL_DAYS)
        val cur = Schedule.currentWindow(listOf(s), oneAm)
        assertNotNull(cur)
        assertEquals(60 * 60_000L, cur!!.end - oneAm)
    }

    @Test
    fun dayFormatting() {
        assertEquals("Weekdays", Schedule.formatDays(Schedule.WEEKDAYS))
        assertEquals("Weekends", Schedule.formatDays(0b1100000))
        assertEquals("Mon Wed", Schedule.formatDays(0b0000101))
        assertEquals("09:05", Schedule.formatTime(9 * 60 + 5))
    }

    @Test
    fun multiTapTypesASentence() {
        val t = MultiTap()
        var time = 0L
        fun p(k: Key) { t.press(k, time); time += 100 }
        p(Key.D4); p(Key.D4)
        assertEquals("H", t.text)
        time += 2000
        p(Key.D4); p(Key.D4); p(Key.D4)
        assertEquals("Hi", t.text)
        p(Key.D0)
        time += 2000
        p(Key.D6); p(Key.D6); p(Key.D6)
        time += 2000
        p(Key.D5); p(Key.D5)
        assertEquals("Hi ok", t.text)
        t.backspace()
        assertEquals("Hi o", t.text)
        t.toggleMode(); t.toggleMode(); t.toggleMode()
        assertEquals(MultiTap.Mode.NUM, t.mode)
        p(Key.D7); p(Key.D7)
        assertEquals("Hi o77", t.text)
    }
}
