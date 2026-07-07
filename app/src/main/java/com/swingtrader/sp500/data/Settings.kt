package com.swingtrader.sp500.data

import android.content.Context

/** Portfolio settings used for position sizing and alerts. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var accountSize: Double
        get() = Double.fromBits(prefs.getLong("accountSize", 10_000.0.toRawBits()))
        set(v) { prefs.edit().putLong("accountSize", v.toRawBits()).apply() }

    var riskPct: Double
        get() = Double.fromBits(prefs.getLong("riskPct", 1.0.toRawBits()))
        set(v) { prefs.edit().putLong("riskPct", v.toRawBits()).apply() }

    var alertsEnabled: Boolean
        get() = prefs.getBoolean("alertsEnabled", true)
        set(v) { prefs.edit().putBoolean("alertsEnabled", v).apply() }

    /** Shares to buy so that (entry − stop) × shares ≈ account × risk%. */
    fun positionSize(entry: Double, stop: Double): Int {
        val riskDollars = accountSize * riskPct / 100.0
        val perShare = entry - stop
        if (perShare <= 0) return 0
        return (riskDollars / perShare).toInt()
    }
}
