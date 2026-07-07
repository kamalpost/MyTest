package com.swingtrader.sp500.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.swingtrader.sp500.R

/**
 * Lightweight sparkline: price line with soft gradient fill, plus optional
 * MA20/MA50 overlay lines. Series are oldest-first; NaN entries are skipped.
 */
class SparklineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var prices: DoubleArray = DoubleArray(0)
    private var ma20: DoubleArray? = null
    private var ma50: DoubleArray? = null
    private var lineColor: Int = context.getColor(R.color.gain)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val maPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val path = Path()
    private val fillPath = Path()

    fun setData(prices: DoubleArray, ma20: DoubleArray? = null, ma50: DoubleArray? = null, up: Boolean = true) {
        this.prices = prices
        this.ma20 = ma20
        this.ma50 = ma50
        lineColor = context.getColor(if (up) R.color.gain else R.color.loss)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (prices.size < 2) return

        var min = Double.MAX_VALUE
        var max = Double.MIN_VALUE
        val all = ArrayList<DoubleArray>(3)
        all.add(prices)
        ma20?.let { all.add(it) }
        ma50?.let { all.add(it) }
        for (arr in all) for (v in arr) {
            if (v.isNaN()) continue
            if (v < min) min = v
            if (v > max) max = v
        }
        if (min >= max) { min -= 1.0; max += 1.0 }

        val w = width.toFloat()
        val h = height.toFloat()
        val pad = dp(2f)
        val span = (max - min)

        fun x(i: Int, n: Int) = pad + (w - 2 * pad) * i / (n - 1)
        fun y(v: Double) = (pad + (h - 2 * pad) * (1.0 - (v - min) / span)).toFloat()

        // MA overlays behind the price line
        ma50?.let { drawSeries(canvas, it, context.getColor(R.color.warn), ::x, ::y) }
        ma20?.let { drawSeries(canvas, it, context.getColor(R.color.accent), ::x, ::y) }

        path.reset()
        fillPath.reset()
        val n = prices.size
        var started = false
        for (i in 0 until n) {
            val v = prices[i]
            if (v.isNaN()) continue
            val px = x(i, n)
            val py = y(v)
            if (!started) {
                path.moveTo(px, py)
                fillPath.moveTo(px, h)
                fillPath.lineTo(px, py)
                started = true
            } else {
                path.lineTo(px, py)
                fillPath.lineTo(px, py)
            }
        }
        fillPath.lineTo(x(n - 1, n), h)
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            (lineColor and 0x00FFFFFF) or 0x33000000, (lineColor and 0x00FFFFFF),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        linePaint.color = lineColor
        canvas.drawPath(path, linePaint)
    }

    private inline fun drawSeries(
        canvas: Canvas, series: DoubleArray, color: Int,
        x: (Int, Int) -> Float, y: (Double) -> Float
    ) {
        val n = series.size
        if (n < 2) return
        val p = Path()
        var started = false
        for (i in 0 until n) {
            val v = series[i]
            if (v.isNaN()) continue
            val px = x(i, n)
            val py = y(v)
            if (!started) { p.moveTo(px, py); started = true } else p.lineTo(px, py)
        }
        maPaint.color = (color and 0x00FFFFFF) or 0xB3000000.toInt()
        canvas.drawPath(p, maPaint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
