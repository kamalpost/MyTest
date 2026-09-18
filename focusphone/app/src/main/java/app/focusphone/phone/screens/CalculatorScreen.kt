package app.focusphone.phone.screens

import android.view.Gravity
import android.view.View
import android.widget.TextView
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost

/** Four-function calculator: digits, # = decimal point, * cycles + − × ÷, OK = equals. */
class CalculatorScreen(host: ScreenHost) : Screen(host) {
    override val title = "Calculator"
    override val leftSoft: String? = "="
    override val rightSoft: String? get() = if (input.isEmpty() && acc == null) "Back" else "Clear"

    private val ops = listOf('+', '−', '×', '÷')
    private var input = StringBuilder()
    private var acc: Double? = null
    private var op: Char? = null
    private var justEvaluated = false

    private lateinit var upper: TextView
    private lateinit var display: TextView

    override fun createView(): View {
        val col = Lcd.column(ctx)
        col.addView(Lcd.spacer(ctx))
        upper = Lcd.text(ctx, "", 14f).apply { gravity = Gravity.END }
        display = Lcd.text(ctx, "0", 30f, bold = true).apply { gravity = Gravity.END }
        col.addView(upper)
        col.addView(display)
        col.addView(Lcd.spacer(ctx))
        col.addView(Lcd.text(ctx, "* operator  # point  OK =", 11f, center = true))
        return col
    }

    override fun refresh() {
        upper.text = if (acc != null && op != null) "${fmt(acc!!)} $op" else ""
        display.text = if (input.isEmpty()) (if (acc != null && op == null) fmt(acc!!) else "0") else input.toString()
    }

    override fun onKey(key: Key): Boolean {
        when {
            key.isDigit -> {
                if (justEvaluated) { acc = null; op = null; input.setLength(0); justEvaluated = false }
                if (input.toString() == "0") input.setLength(0)
                input.append(key.char)
            }
            key == Key.HASH -> {
                if (justEvaluated) { acc = null; op = null; input.setLength(0); justEvaluated = false }
                if (!input.contains('.')) input.append(if (input.isEmpty()) "0." else ".")
            }
            key == Key.STAR -> {
                justEvaluated = false
                if (input.isNotEmpty()) {
                    val v = input.toString().toDoubleOrNull() ?: 0.0
                    acc = if (acc != null && op != null) apply(acc!!, op!!, v) else v
                    input.setLength(0)
                    op = ops[0]
                } else if (acc != null) {
                    op = ops[(ops.indexOf(op ?: ops.last()) + 1) % ops.size]
                }
            }
            key == Key.UP || key == Key.DOWN -> {
                if (input.isNotEmpty()) {
                    if (input.startsWith("-")) input.deleteCharAt(0) else input.insert(0, '-')
                }
            }
            key == Key.OK || key == Key.SOFT_LEFT -> {
                if (acc != null && op != null && input.isNotEmpty()) {
                    val v = input.toString().toDoubleOrNull() ?: 0.0
                    acc = apply(acc!!, op!!, v)
                    op = null
                    input.setLength(0)
                    justEvaluated = true
                }
            }
            key == Key.SOFT_RIGHT -> {
                if (input.isNotEmpty()) input.setLength(input.length - 1)
                else if (acc != null || op != null) { acc = null; op = null }
                else return false
            }
            else -> return false
        }
        refresh()
        return true
    }

    private fun apply(a: Double, o: Char, b: Double): Double = when (o) {
        '+' -> a + b
        '−' -> a - b
        '×' -> a * b
        '÷' -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    private fun fmt(v: Double): String {
        if (v.isNaN()) return "Error"
        if (v.isInfinite()) return "∞"
        val s = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else "%.8f".format(v).trimEnd('0').trimEnd('.')
        return s
    }
}
