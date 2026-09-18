package app.focusphone.phone

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.focusphone.R

/** Tiny view factory so every screen shares the same monochrome LCD look. */
object Lcd {
    fun ink(ctx: Context) = ctx.getColor(R.color.lcd_ink)
    fun paper(ctx: Context) = ctx.getColor(R.color.lcd_paper)

    fun text(
        ctx: Context,
        text: CharSequence = "",
        sp: Float = 16f,
        bold: Boolean = false,
        inverted: Boolean = false,
        center: Boolean = false,
        singleLine: Boolean = false,
    ): TextView = TextView(ctx).apply {
        this.text = text
        textSize = sp
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (inverted) paper(ctx) else ink(ctx))
        if (inverted) setBackgroundColor(ink(ctx))
        gravity = if (center) Gravity.CENTER_HORIZONTAL else Gravity.START
        includeFontPadding = false
        if (singleLine) {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val pad = dp(ctx, 2)
        setPadding(pad, pad, pad, pad)
    }

    fun column(ctx: Context, fill: Boolean = true): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (fill) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun scroll(ctx: Context, content: View): ScrollView = ScrollView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(content)
    }

    fun spacer(ctx: Context, weight: Float = 1f): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight)
    }

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
}
