package com.swingtrader.sp500.analysis

import kotlin.math.abs
import kotlin.math.max

/**
 * Plain-array technical indicators. All series are oldest-first and every
 * output array is aligned with its input (leading entries are NaN until the
 * indicator has enough data).
 */
object Indicators {

    /** Simple moving average. */
    fun sma(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.size < period) return out
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    /** Wilder-smoothed RSI (the classic 14-period formula yfinance users chart). */
    fun rsi(values: DoubleArray, period: Int = 14): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.size <= period) return out
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val d = values[i] - values[i - 1]
            if (d > 0) avgGain += d else avgLoss -= d
        }
        avgGain /= period
        avgLoss /= period
        out[period] = toRsi(avgGain, avgLoss)
        for (i in period + 1 until values.size) {
            val d = values[i] - values[i - 1]
            val gain = if (d > 0) d else 0.0
            val loss = if (d < 0) -d else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            out[i] = toRsi(avgGain, avgLoss)
        }
        return out
    }

    private fun toRsi(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    /** Wilder ATR from high/low/close. */
    fun atr(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 14): Double {
        val n = close.size
        if (n <= period) return Double.NaN
        var atr = 0.0
        for (i in 1..period) atr += trueRange(high, low, close, i)
        atr /= period
        for (i in period + 1 until n) {
            atr = (atr * (period - 1) + trueRange(high, low, close, i)) / period
        }
        return atr
    }

    private fun trueRange(h: DoubleArray, l: DoubleArray, c: DoubleArray, i: Int): Double {
        val hl = h[i] - l[i]
        val hc = abs(h[i] - c[i - 1])
        val lc = abs(l[i] - c[i - 1])
        return max(hl, max(hc, lc))
    }

    /** Average of the trailing [period] longs ending at the last index (exclusive of NaN handling). */
    fun avgTail(values: LongArray, period: Int, endExclusive: Int = values.size): Double {
        val start = endExclusive - period
        if (start < 0) return Double.NaN
        var sum = 0.0
        for (i in start until endExclusive) sum += values[i]
        return sum / period
    }

    /**
     * Index (bars back from the end) where fast crossed over slow, or -1.
     * [up]=true looks for fast crossing above slow ("golden"), false for below.
     * Only scans the trailing [window] bars.
     */
    fun barsSinceCross(fast: DoubleArray, slow: DoubleArray, up: Boolean, window: Int): Int {
        val n = fast.size
        for (back in 0 until window) {
            val i = n - 1 - back
            if (i < 1) break
            val fPrev = fast[i - 1]; val sPrev = slow[i - 1]
            val fCur = fast[i]; val sCur = slow[i]
            if (fPrev.isNaN() || sPrev.isNaN() || fCur.isNaN() || sCur.isNaN()) continue
            val crossed = if (up) fPrev <= sPrev && fCur > sCur else fPrev >= sPrev && fCur < sCur
            if (crossed) return back
        }
        return -1
    }
}
