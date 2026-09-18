package app.focusphone.phone.screens

import app.focusphone.phone.ScreenHost

class MenuScreen(host: ScreenHost) : ListScreen(host, digitShortcuts = true) {
    override val title = "Menu"

    private val entries = listOf("Phone", "Messages", "Contacts", "Clock", "Calculator", "Snake", "About")

    override fun items() = entries

    override fun onSelect(index: Int) {
        when (index) {
            0 -> host.push(DialerScreen(host))
            1 -> host.push(MessagesScreen(host))
            2 -> host.push(ContactsScreen(host))
            3 -> host.push(ClockScreen(host))
            4 -> host.push(CalculatorScreen(host))
            5 -> host.push(SnakeScreen(host))
            6 -> host.push(AboutScreen(host))
        }
    }
}
