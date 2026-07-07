package com.swingtrader.sp500.data

import android.content.Context
import com.swingtrader.sp500.model.Constituent

/**
 * User edits to the scan universe: extra tickers added in-app and bundled
 * tickers the user removed. Applied on top of assets/sp500.csv.
 */
class UserUniverseStore(context: Context) {

    private val prefs = context.getSharedPreferences("universe", Context.MODE_PRIVATE)

    fun added(): List<Constituent> =
        (prefs.getStringSet("added", emptySet()) ?: emptySet())
            .sorted()
            .map { Constituent(it, it, "Custom", 15.0) }

    fun removed(): Set<String> = prefs.getStringSet("removed", emptySet()) ?: emptySet()

    fun add(symbol: String) {
        val s = symbol.trim().uppercase()
        if (s.isEmpty()) return
        val set = HashSet(prefs.getStringSet("added", emptySet()) ?: emptySet())
        set.add(s)
        val removed = HashSet(removed())
        removed.remove(s)
        prefs.edit().putStringSet("added", set).putStringSet("removed", removed).apply()
    }

    fun remove(symbol: String) {
        val s = symbol.trim().uppercase()
        val added = HashSet(prefs.getStringSet("added", emptySet()) ?: emptySet())
        if (added.remove(s)) {
            prefs.edit().putStringSet("added", added).apply()
        } else {
            val removed = HashSet(removed())
            removed.add(s)
            prefs.edit().putStringSet("removed", removed).apply()
        }
    }

    fun apply(base: List<Constituent>): List<Constituent> {
        val removed = removed()
        val baseSymbols = base.map { it.symbol }.toHashSet()
        return base.filter { it.symbol !in removed } +
            added().filter { it.symbol !in baseSymbols && it.symbol !in removed }
    }
}
