package com.applock.numberlock.util

import android.view.View
import com.applock.numberlock.R

/**
 * Wires the numeric keypad buttons (shared by the setup and lock screens) to
 * simple digit/backspace callbacks so both activities can reuse one layout.
 */
object PinKeypadBinder {

    fun bind(root: View, onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
        val digitButtonIds = intArrayOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )
        for (id in digitButtonIds) {
            val button = root.findViewById<View>(id)
            val digit = (button as? android.widget.TextView)?.text?.firstOrNull() ?: continue
            button.setOnClickListener { onDigit(digit) }
        }
        root.findViewById<View>(R.id.btnBackspace).setOnClickListener { onBackspace() }
    }
}
