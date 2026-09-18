package app.focusphone.phone.screens

import app.focusphone.phone.ScreenHost

class MenuScreen(host: ScreenHost) : ListScreen(host, digitShortcuts = true) {
    override val title = "Menu"

    private val entries = listOf("Phone", "Messages", "Contacts")

    override fun items() = entries

    override fun onSelect(index: Int) {
        when (index) {
            0 -> host.push(PhoneMenuScreen(host))
            1 -> host.push(MessagesScreen(host))
            2 -> host.push(ContactsScreen(host))
        }
    }
}
