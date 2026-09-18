package app.focusphone.phone.screens

import app.focusphone.phone.ScreenHost

/**
 * The Break button. Lets the user step out of focus mode for a fixed time
 * (the smartphone comes back, focus resumes automatically) or end the session.
 */
class BreakScreen(host: ScreenHost) : ListScreen(host, digitShortcuts = true) {
    override val title = "Break"

    private val options = listOf(
        5 to "5 min break",
        15 to "15 min break",
        30 to "30 min break",
        60 to "1 hour break",
    )

    override fun items() = options.map { it.second } + listOf("End focus session", "Cancel")

    override fun onSelect(index: Int) {
        when {
            index < options.size -> {
                val minutes = options[index].first
                guarded {
                    host.push(
                        ConfirmScreen(
                            host, "Take a break?",
                            "Leave the focus phone for $minutes minutes?\n\nYour normal phone comes back. Focus mode resumes automatically afterwards."
                        ) { ctx.takeBreak(minutes) }
                    )
                }
            }
            index == options.size -> guarded {
                host.push(
                    ConfirmScreen(host, "End session?", "End this focus session now and return to your normal phone?") { ctx.endSession() }
                )
            }
            else -> host.pop()
        }
    }

    /** Runs [action] directly, or after the Break PIN when one is set. */
    private fun guarded(action: () -> Unit) {
        if (ctx.prefs.hasBreakPin) host.push(PinScreen(host, action)) else action()
    }
}
