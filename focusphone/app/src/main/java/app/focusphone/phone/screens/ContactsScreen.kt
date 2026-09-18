package app.focusphone.phone.screens

import app.focusphone.phone.Contact
import app.focusphone.phone.Key
import app.focusphone.phone.ScreenHost

/**
 * Address book with T9-style filtering: typing 5-6 shows names whose words start with J/K/L then M/N/O.
 * In pick mode (compose message) the chosen contact is handed back instead of opened.
 */
class ContactsScreen(host: ScreenHost, private val onPick: ((Contact) -> Unit)? = null) : ListScreen(host) {
    override val title: String get() = if (filter.isEmpty()) "Contacts" else "Find: $filter"
    override val rightSoft: String? get() = if (filter.isEmpty()) "Back" else "Clear"
    override val emptyText: String
        get() = when {
            !ctx.contacts.hasPermission() -> "No contacts access.\nPress OK to allow."
            filter.isNotEmpty() -> "No match"
            else -> "No contacts"
        }

    private var all: List<Contact> = emptyList()
    private var shown: List<Contact> = emptyList()
    private val filter = StringBuilder()

    override fun items(): List<String> {
        shown = if (filter.isEmpty()) all else all.filter { matches(it.name) }
        return shown.map { it.name }
    }

    override fun onShow() {
        all = ctx.contacts.all()
        super.onShow()
    }

    override fun onSelect(index: Int) {
        if (shown.isEmpty()) {
            if (!ctx.contacts.hasPermission()) ctx.ensureContactsPermission {
                all = ctx.contacts.all()
                invalidateItems()
            }
            return
        }
        val c = shown[index]
        if (onPick != null) {
            host.pop()
            onPick.invoke(c)
        } else {
            host.push(ContactDetailScreen(host, c))
        }
    }

    override fun onKey(key: Key): Boolean {
        if (key.isDigit) {
            filter.append(key.char)
            selected = 0
            invalidateItems()
            return true
        }
        if (key == Key.SOFT_RIGHT && filter.isNotEmpty()) {
            filter.setLength(filter.length - 1)
            selected = 0
            invalidateItems()
            return true
        }
        if (key == Key.CALL && shown.isNotEmpty() && onPick == null) {
            ctx.placeCall(shown[selected].number)
            return true
        }
        return super.onKey(key)
    }

    private fun matches(name: String): Boolean {
        val f = filter.toString()
        return name.split(' ', '-', '.').any { word ->
            word.length >= f.length && word.take(f.length).all { it.isLetterOrDigit() } &&
                word.take(f.length).mapIndexed { i, ch -> t9(ch) == f[i] }.all { it }
        }
    }

    private fun t9(ch: Char): Char {
        val c = ch.lowercaseChar()
        if (c.isDigit()) return c
        return when (c) {
            in "abc" -> '2'; in "def" -> '3'; in "ghi" -> '4'; in "jkl" -> '5'
            in "mno" -> '6'; in "pqrs" -> '7'; in "tuv" -> '8'; in "wxyz" -> '9'
            else -> '1'
        }
    }
}

class ContactDetailScreen(host: ScreenHost, private val contact: Contact) : ListScreen(host) {
    override val title: String get() = contact.name
    override fun items() = listOf("Call", "Send message", contact.number)

    override fun onSelect(index: Int) {
        when (index) {
            0, 2 -> ctx.placeCall(contact.number)
            1 -> host.push(ComposeScreen(host, contact.number))
        }
    }

    override fun onKey(key: Key): Boolean {
        if (key == Key.CALL) {
            ctx.placeCall(contact.number)
            return true
        }
        return super.onKey(key)
    }
}
