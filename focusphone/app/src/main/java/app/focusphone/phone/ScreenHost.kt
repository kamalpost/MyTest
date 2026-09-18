package app.focusphone.phone

import android.widget.FrameLayout
import android.widget.TextView

/** Navigation stack for [Screen]s plus the LCD chrome (title, soft-key labels). */
class ScreenHost(
    val activity: FeaturePhoneActivity,
    private val container: FrameLayout,
    private val titleView: TextView,
    private val leftSoftView: TextView,
    private val rightSoftView: TextView,
) {
    private val stack = ArrayList<Screen>()

    val current: Screen get() = stack.last()
    val depth: Int get() = stack.size

    fun setRoot(screen: Screen) {
        stack.forEach { it.onHide() }
        stack.clear()
        stack.add(screen)
        show()
    }

    fun push(screen: Screen) {
        if (stack.isNotEmpty()) current.onHide()
        stack.add(screen)
        show()
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1).onHide()
        show()
        return true
    }

    fun popToRoot() {
        while (stack.size > 1) stack.removeAt(stack.size - 1).onHide()
        show()
    }

    /** Pop until a screen of type [T] is on top (or the root). */
    inline fun <reified T : Screen> popTo() {
        while (depth > 1 && current !is T) pop()
    }

    private fun show() {
        val s = current
        container.removeAllViews()
        container.addView(s.view)
        s.onShow()
        s.refresh()
        refreshChrome()
    }

    fun refreshChrome() {
        val s = current
        titleView.text = s.title
        leftSoftView.text = s.leftSoft ?: ""
        rightSoftView.text = s.rightSoft ?: ""
    }

    fun tick() {
        current.onTick()
    }

    fun dispatch(key: Key) {
        val s = current
        val consumed = s.onKey(key)
        if (!consumed) {
            when (key) {
                Key.SOFT_LEFT -> s.onKey(Key.OK)
                Key.SOFT_RIGHT -> pop()
                Key.END -> popToRoot()
                else -> {}
            }
        }
        refreshChrome()
    }
}
