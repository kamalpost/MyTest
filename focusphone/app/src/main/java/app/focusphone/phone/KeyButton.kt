package app.focusphone.phone

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import app.focusphone.R

/** A keypad key: big label with an optional small sub-label (the "ABC" under the 2). */
class KeyButton(ctx: Context, main: String, sub: String?, background: Int, mainSp: Int = 20) : TextView(ctx) {
    init {
        gravity = Gravity.CENTER
        setBackgroundResource(background)
        isClickable = true
        isFocusable = true
        isHapticFeedbackEnabled = true
        includeFontPadding = false
        val density = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, ctx.resources.displayMetrics)
        val ssb = SpannableStringBuilder()
        ssb.append(main)
        ssb.setSpan(AbsoluteSizeSpan((mainSp * density).toInt()), 0, main.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(StyleSpan(Typeface.BOLD), 0, main.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(ForegroundColorSpan(ctx.getColor(R.color.key_text)), 0, main.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (!sub.isNullOrEmpty()) {
            val start = ssb.length
            ssb.append("\n").append(sub)
            ssb.setSpan(AbsoluteSizeSpan((10 * density).toInt()), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(ForegroundColorSpan(ctx.getColor(R.color.key_sub)), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text = ssb
        setLineSpacing(0f, 0.95f)
    }
}
