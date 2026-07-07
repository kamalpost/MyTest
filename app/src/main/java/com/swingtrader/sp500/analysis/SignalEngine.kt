package com.swingtrader.sp500.analysis

import com.swingtrader.sp500.model.Constituent
import com.swingtrader.sp500.model.History
import com.swingtrader.sp500.model.Idea
import com.swingtrader.sp500.model.Signal
import java.util.Locale
import kotlin.math.min

/**
 * Turns raw daily history into a swing-trade idea by combining:
 *  - RSI(14) positioning (oversold / pullback / momentum / overbought)
 *  - 20 vs 50 day simple moving averages (trend + crosses)
 *  - Volume analysis (RVOL vs 20-day average, 5d vs 20d volume trend)
 */
object SignalEngine {

    private const val CROSS_WINDOW = 7   // bars to look back for MA crosses
    private const val SPARK_BARS = 60    // trailing bars kept for sparklines

    fun analyze(c: Constituent, h: History): Idea? {
        val n = h.size
        if (n < 55) return null // need SMA50 + a little headroom

        val closes = h.close
        val price = closes[n - 1]
        val prev = closes[n - 2]
        if (price <= 0.0 || prev <= 0.0) return null

        val sma20Series = Indicators.sma(closes, 20)
        val sma50Series = Indicators.sma(closes, 50)
        val rsiSeries = Indicators.rsi(closes, 14)

        val sma20 = sma20Series[n - 1]
        val sma50 = sma50Series[n - 1]
        val rsi = rsiSeries[n - 1]
        if (sma20.isNaN() || sma50.isNaN() || rsi.isNaN()) return null

        val avgVol20 = Indicators.avgTail(h.volume, 20)
        val avgVol5 = Indicators.avgTail(h.volume, 5)
        val lastVol = h.volume[n - 1]
        val rvol = if (avgVol20 > 0) lastVol / avgVol20 else 0.0
        val volTrend = if (avgVol20 > 0) avgVol5 / avgVol20 else 0.0
        val atr = Indicators.atr(h.high, h.low, closes, 14)

        val changePct = (price - prev) / prev * 100.0

        val look = min(n, 63) // ~3 months
        var hi3m = Double.MIN_VALUE
        var lo3m = Double.MAX_VALUE
        for (i in n - look until n) {
            if (closes[i] > hi3m) hi3m = closes[i]
            if (closes[i] < lo3m) lo3m = closes[i]
        }

        val goldenBars = Indicators.barsSinceCross(sma20Series, sma50Series, up = true, window = CROSS_WINDOW)
        val deathBars = Indicators.barsSinceCross(sma20Series, sma50Series, up = false, window = CROSS_WINDOW)
        // Did price reclaim the 20-day MA in the last 3 bars? (breakout trigger)
        val priceCross20 = crossedAboveRecently(closes, sma20Series, 3)

        val (macdLine, macdSignalLine, macdHistSeries) = Indicators.macd(closes)
        val macdHist = macdHistSeries[n - 1]
        val macdBullCross =
            Indicators.barsSinceCross(macdLine, macdSignalLine, up = true, window = 5) >= 0
        val bb = Indicators.bollinger(closes)

        val uptrend = sma20 > sma50
        var (signal, score, reason) = classify(
            price, rsi, sma20, sma50, uptrend, rvol, volTrend,
            goldenBars, deathBars, priceCross20, hi3m, changePct
        )

        // Confirmation layer: MACD and Bollinger refine conviction, not direction.
        if (signal.bullish == true) {
            if (macdBullCross) {
                score += 8
                reason += " MACD just crossed bullish."
            } else if (!macdHist.isNaN() && macdHist > 0) {
                score += 4
            }
            if (bb?.squeeze == true) {
                score += 5
                reason += " Bollinger squeeze — volatility coiled."
            }
        } else if (signal.bullish == false && !macdHist.isNaN() && macdHist < 0) {
            score += 5
        }
        score = score.coerceAtMost(100)

        val sparkFrom = n - min(n, SPARK_BARS)
        return Idea(
            constituent = c,
            price = price,
            changePct1d = changePct,
            rsi14 = rsi,
            sma20 = sma20,
            sma50 = sma50,
            avgVol20 = avgVol20,
            lastVol = lastVol,
            rvol = rvol,
            volTrend = volTrend,
            atr14 = atr,
            hi3m = hi3m,
            lo3m = lo3m,
            macdHist = macdHist,
            macdBullCross = macdBullCross,
            bbUpper = bb?.upper ?: Double.NaN,
            bbLower = bb?.lower ?: Double.NaN,
            bbSqueeze = bb?.squeeze ?: false,
            signal = signal,
            score = score,
            reason = reason,
            closes = closes.copyOfRange(sparkFrom, n),
            sma20Series = sma20Series.copyOfRange(sparkFrom, n),
            sma50Series = sma50Series.copyOfRange(sparkFrom, n)
        )
    }

    private fun crossedAboveRecently(price: DoubleArray, ma: DoubleArray, window: Int): Boolean {
        val n = price.size
        for (back in 0 until window) {
            val i = n - 1 - back
            if (i < 1 || ma[i].isNaN() || ma[i - 1].isNaN()) continue
            if (price[i - 1] <= ma[i - 1] && price[i] > ma[i]) return true
        }
        return false
    }

    private fun classify(
        price: Double, rsi: Double, sma20: Double, sma50: Double, uptrend: Boolean,
        rvol: Double, volTrend: Double, goldenBars: Int, deathBars: Int,
        priceCross20: Boolean, hi3m: Double, changePct: Double
    ): Triple<Signal, Int, String> {
        val f2 = { v: Double -> String.format(Locale.US, "%.1f", v) }

        // Bearish states first — they disqualify long setups regardless of the rest.
        if (rsi >= 70) {
            val s = base(55) + bonus(rsi >= 78, 15) + bonus(rvol > 1.5, 10)
            return Triple(
                Signal.OVERBOUGHT, s,
                "RSI ${f2(rsi)} is overbought — extended above both MAs; take profits or wait for a pullback before entering."
            )
        }
        if (deathBars >= 0) {
            val s = base(60) + bonus(price < sma50, 15) + bonus(rvol > 1.3, 10)
            return Triple(
                Signal.DEATH_CROSS, s,
                "20-day MA crossed below 50-day MA $deathBars session(s) ago — trend turning down; avoid new longs."
            )
        }
        if (price < sma50 && price < sma20 && rsi in 30.0..55.0 && rvol >= 1.4 && changePct < 0) {
            val s = base(55) + bonus(rvol > 2.0, 15) + bonus(volTrend > 1.2, 10)
            return Triple(
                Signal.BREAKDOWN, s,
                "Price broke below both MAs on ${f2(rvol)}x average volume — distribution; stand aside or consider exits."
            )
        }

        // Bullish setups, most actionable first.
        if (rsi <= 30) {
            val s = base(60) + bonus(rsi <= 25, 15) + bonus(rvol >= 1.3, 10) + bonus(changePct > 0, 10)
            return Triple(
                Signal.OVERSOLD_BOUNCE, s,
                "RSI ${f2(rsi)} is oversold — mean-reversion bounce candidate${if (changePct > 0) ", already turning up today" else ""}."
            )
        }
        if (goldenBars >= 0 && price > sma20) {
            val s = base(65) + bonus(rvol >= 1.3, 10) + bonus(rsi in 50.0..65.0, 15)
            return Triple(
                Signal.GOLDEN_CROSS, s,
                "20-day MA crossed above 50-day MA $goldenBars session(s) ago with price above both — fresh uptrend."
            )
        }
        if (uptrend && price > sma50 && price <= sma20 * 1.01 && rsi in 33.0..52.0) {
            val nearMa = price >= sma20 * 0.97
            val s = base(65) + bonus(nearMa, 10) + bonus(rsi in 38.0..48.0, 10) + bonus(volTrend < 0.9, 10)
            return Triple(
                Signal.PULLBACK_BUY, s,
                "Uptrend intact (20d > 50d MA) and price pulled back to the 20-day MA with RSI ${f2(rsi)} — classic buy-the-dip zone."
            )
        }
        if (priceCross20 && uptrend && rvol >= 1.5 && rsi in 50.0..68.0) {
            val nearHigh = price >= hi3m * 0.985
            val s = base(60) + bonus(rvol >= 2.0, 15) + bonus(nearHigh, 15)
            return Triple(
                Signal.BREAKOUT, s,
                "Reclaimed the 20-day MA on ${f2(rvol)}x volume${if (nearHigh) " near 3-month highs" else ""} — momentum breakout."
            )
        }
        if (uptrend && price > sma20 && rsi in 50.0..65.0) {
            val s = base(45) + bonus(volTrend > 1.1, 10) + bonus(rsi < 60, 5)
            return Triple(
                Signal.UPTREND, s,
                "Price > 20d MA > 50d MA with RSI ${f2(rsi)} — healthy trend; buy pullbacks, not chases."
            )
        }
        return Triple(
            Signal.NEUTRAL, 25,
            "No actionable edge: RSI ${f2(rsi)}, price ${if (price >= sma20) "above" else "below"} 20d MA, ${if (uptrend) "up" else "down"}trend bias."
        )
    }

    private fun base(v: Int) = v
    private fun bonus(cond: Boolean, v: Int) = if (cond) v else 0
}
