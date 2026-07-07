package com.swingtrader.sp500.ui

import java.util.Locale

object Format {
    fun price(v: Double): String = when {
        v >= 1000 -> String.format(Locale.US, "%,.2f", v)
        else -> String.format(Locale.US, "%.2f", v)
    }

    fun pct(v: Double): String =
        String.format(Locale.US, "%s%.2f%%", if (v >= 0) "+" else "", v)

    fun volume(v: Double): String = when {
        v >= 1e9 -> String.format(Locale.US, "%.2fB", v / 1e9)
        v >= 1e6 -> String.format(Locale.US, "%.1fM", v / 1e6)
        v >= 1e3 -> String.format(Locale.US, "%.0fK", v / 1e3)
        else -> String.format(Locale.US, "%.0f", v)
    }

    fun one(v: Double): String = String.format(Locale.US, "%.1f", v)
    fun two(v: Double): String = String.format(Locale.US, "%.2f", v)
}
