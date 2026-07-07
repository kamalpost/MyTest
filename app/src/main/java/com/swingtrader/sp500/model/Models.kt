package com.swingtrader.sp500.model

/** Size buckets relative to the S&P 500 universe (approx cap in $B). */
enum class CapCategory(val label: String) {
    MEGA("Mega ≥200B"),
    LARGE("Large 50–200B"),
    MID("Mid 20–50B"),
    SMALL("Small <20B");

    companion object {
        fun of(capB: Double): CapCategory = when {
            capB >= 200 -> MEGA
            capB >= 50 -> LARGE
            capB >= 20 -> MID
            else -> SMALL
        }
    }
}

/** User's decision on a presented trade idea. */
enum class Decision { NONE, TAKEN, SKIPPED }

/** Static info about an index constituent, loaded from assets/sp500.csv. */
data class Constituent(
    val symbol: String,
    val name: String,
    val sector: String,
    val capB: Double
) {
    val capCategory: CapCategory get() = CapCategory.of(capB)
}

/** Daily OHLCV history for one symbol (oldest first). */
data class History(
    val symbol: String,
    val timestamps: LongArray,
    val open: DoubleArray,
    val high: DoubleArray,
    val low: DoubleArray,
    val close: DoubleArray,
    val volume: LongArray
) {
    val size: Int get() = close.size
}

enum class Signal(val label: String, val bullish: Boolean?) {
    OVERSOLD_BOUNCE("Oversold Bounce", true),
    PULLBACK_BUY("Pullback Buy", true),
    BREAKOUT("Volume Breakout", true),
    GOLDEN_CROSS("Golden Cross", true),
    UPTREND("Uptrend Momentum", true),
    OVERBOUGHT("Overbought", false),
    BREAKDOWN("Breakdown", false),
    DEATH_CROSS("Death Cross", false),
    NEUTRAL("Neutral", null)
}

/** Full analysis result for one stock. */
data class Idea(
    val constituent: Constituent,
    val price: Double,
    val changePct1d: Double,
    val rsi14: Double,
    val sma20: Double,
    val sma50: Double,
    val avgVol20: Double,
    val lastVol: Long,
    val rvol: Double,           // last volume / 20d avg volume
    val volTrend: Double,       // 5d avg vol / 20d avg vol
    val atr14: Double,
    val hi3m: Double,
    val lo3m: Double,
    val signal: Signal,
    val score: Int,             // 0..100 conviction for the primary signal
    val reason: String,
    val closes: DoubleArray,    // trailing closes for sparkline (oldest first)
    val sma20Series: DoubleArray,
    val sma50Series: DoubleArray
) {
    val aboveSma20: Boolean get() = price >= sma20
    val aboveSma50: Boolean get() = price >= sma50
    val stopSuggestion: Double get() = price - 1.5 * atr14
    val targetSuggestion: Double get() = price + 2.5 * atr14
}

/** Snapshot of the index itself for the dashboard header. */
data class MarketSnapshot(
    val price: Double,
    val changePct1d: Double,
    val rsi14: Double,
    val sma20: Double,
    val sma50: Double,
    val closes: DoubleArray
)
