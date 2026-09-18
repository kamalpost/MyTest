package app.focusphone.phone.screens

import android.text.format.DateFormat
import android.text.format.DateUtils
import app.focusphone.phone.CallEntry
import app.focusphone.phone.Contact
import app.focusphone.phone.Key
import app.focusphone.phone.ScreenHost

/** Menu → Phone. */
class PhoneMenuScreen(host: ScreenHost) : ListScreen(host, digitShortcuts = true) {
    override val title = "Phone"
    override fun items() = listOf("Recent calls", "Favourites", "Contacts", "Dial number")

    override fun onSelect(index: Int) {
        when (index) {
            0 -> host.push(RecentCallsScreen(host))
            1 -> host.push(FavouritesScreen(host))
            2 -> host.push(ContactsScreen(host))
            3 -> host.push(DialerScreen(host))
        }
    }

    override fun onKey(key: Key): Boolean {
        if (key == Key.CALL) {
            host.push(RecentCallsScreen(host))
            return true
        }
        return super.onKey(key)
    }
}

/** The green key on the home screen: the call log with names, direction and time. */
class RecentCallsScreen(host: ScreenHost) : ListScreen(host) {
    override val title = "Recent calls"
    override val leftSoft: String? get() = if (entries.isEmpty()) "OK" else "Call"
    override val emptyText: String
        get() = if (ctx.callLog.hasPermission()) "No recent calls" else "No call log access.\nAllow it in FocusPhone setup."

    private var entries: List<CallEntry> = emptyList()

    override fun items(): List<String> {
        entries = ctx.callLog.recent()
        val now = System.currentTimeMillis()
        return entries.map { e ->
            val who = e.cachedName ?: ctx.contacts.nameFor(e.number) ?: e.number
            val stamp = if (DateUtils.isToday(e.date)) DateFormat.format("HH:mm", e.date)
            else if (now - e.date < 6 * DateUtils.DAY_IN_MILLIS) DateFormat.format("EEE", e.date)
            else DateFormat.format("d MMM", e.date)
            "${e.glyph} ${who.take(13)} $stamp"
        }
    }

    override fun onSelect(index: Int) {
        if (entries.isEmpty()) return
        val e = entries[index]
        ctx.placeCall(e.number)
    }

    override fun onKey(key: Key): Boolean {
        if (entries.isNotEmpty()) {
            val e = entries[selected]
            when (key) {
                Key.CALL -> { ctx.placeCall(e.number); return true }
                Key.RIGHT -> {
                    val name = e.cachedName ?: ctx.contacts.nameFor(e.number) ?: e.number
                    host.push(ContactDetailScreen(host, Contact(name, e.number)))
                    return true
                }
                else -> {}
            }
        }
        return super.onKey(key)
    }
}

/** Contacts starred in the phone's Contacts app. */
class FavouritesScreen(host: ScreenHost) : ListScreen(host) {
    override val title = "Favourites"
    override val emptyText: String
        get() = if (ctx.contacts.hasPermission()) "No favourites yet.\nStar contacts in your\nphone's Contacts app."
        else "No contacts access.\nAllow it in FocusPhone setup."

    private var list: List<Contact> = emptyList()

    override fun items(): List<String> {
        list = ctx.contacts.favourites()
        return list.map { it.name }
    }

    override fun onSelect(index: Int) {
        if (list.isNotEmpty()) host.push(ContactDetailScreen(host, list[index]))
    }

    override fun onKey(key: Key): Boolean {
        if (key == Key.CALL && list.isNotEmpty()) {
            ctx.placeCall(list[selected].number)
            return true
        }
        return super.onKey(key)
    }
}
