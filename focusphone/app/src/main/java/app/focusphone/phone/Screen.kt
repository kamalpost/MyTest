package app.focusphone.phone

import android.view.View

/** One "page" on the LCD. Screens are stacked by [ScreenHost]. */
abstract class Screen(val host: ScreenHost) {
    open val title: String = ""
    /** Label for the left soft key; null hides it. Default "Select" maps to [Key.OK]. */
    open val leftSoft: String? = "Select"
    /** Label for the right soft key; null hides it. Default "Back" pops the screen. */
    open val rightSoft: String? = "Back"

    val ctx get() = host.activity

    private var built: View? = null
    val view: View
        get() = built ?: createView().also { built = it }

    protected abstract fun createView(): View

    /** Re-render mutable state into the existing view. */
    open fun refresh() {}

    /** Return true if the key was consumed. */
    open fun onKey(key: Key): Boolean = false

    open fun onShow() {}
    open fun onHide() {}

    /** Called once a second while visible (for clocks and countdowns). */
    open fun onTick() {}
}
