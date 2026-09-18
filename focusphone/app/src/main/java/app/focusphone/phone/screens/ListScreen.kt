package app.focusphone.phone.screens

import android.view.View
import android.widget.LinearLayout
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost

/** A scrolling, highlight-driven menu list — the workhorse of every feature phone UI. */
abstract class ListScreen(host: ScreenHost, private val digitShortcuts: Boolean = false) : Screen(host) {
    abstract fun items(): List<String>
    open fun onSelect(index: Int) {}
    open val emptyText: String = "Empty"
    open val visibleRows: Int = 6

    var selected = 0
        protected set
    private var scrollTop = 0
    private lateinit var column: LinearLayout
    private var cached: List<String> = emptyList()

    override fun createView(): View {
        column = Lcd.column(ctx)
        return column
    }

    /** Subclasses call this when their data changed. */
    fun invalidateItems() {
        cached = items()
        if (selected >= cached.size) selected = maxOf(0, cached.size - 1)
        refresh()
    }

    override fun onShow() {
        cached = items()
    }

    override fun refresh() {
        column.removeAllViews()
        val list = cached
        if (list.isEmpty()) {
            column.addView(Lcd.spacer(ctx))
            column.addView(Lcd.text(ctx, emptyText, 14f, center = true))
            column.addView(Lcd.spacer(ctx))
            return
        }
        if (selected < scrollTop) scrollTop = selected
        if (selected >= scrollTop + visibleRows) scrollTop = selected - visibleRows + 1
        val end = minOf(list.size, scrollTop + visibleRows)
        for (i in scrollTop until end) {
            val sel = i == selected
            val prefix = if (digitShortcuts) "${i + 1} " else if (sel) "▸" else " "
            column.addView(Lcd.text(ctx, prefix + list[i], 15f, bold = sel, inverted = sel, singleLine = true))
        }
        if (list.size > visibleRows) {
            column.addView(Lcd.spacer(ctx))
            val up = if (scrollTop > 0) "▲" else " "
            val down = if (end < list.size) "▼" else " "
            column.addView(Lcd.text(ctx, "$up ${selected + 1}/${list.size} $down", 11f, center = true))
        }
    }

    override fun onKey(key: Key): Boolean {
        val n = cached.size
        when (key) {
            Key.UP -> {
                if (n > 0) selected = (selected - 1 + n) % n
                refresh(); return true
            }
            Key.DOWN -> {
                if (n > 0) selected = (selected + 1) % n
                refresh(); return true
            }
            Key.OK -> {
                if (n > 0) onSelect(selected)
                return true
            }
            else -> {
                if (digitShortcuts && key.isDigit && key != Key.D0) {
                    val idx = key.char!!.digitToInt() - 1
                    if (idx < n) {
                        selected = idx
                        refresh()
                        onSelect(idx)
                    }
                    return true
                }
            }
        }
        return false
    }
}
