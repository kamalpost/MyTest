package com.swingtrader.sp500.data

import android.content.Context
import com.swingtrader.sp500.model.Idea
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lightweight trade journal. Tapping TAKE opens a paper position at the
 * idea's current price (with its ATR stop/target frozen in); un-taking or
 * skipping a taken idea closes it at the latest known price and records the
 * result. Everything lives in one JSON file in app storage.
 */
class JournalStore(private val context: Context) {

    data class Position(
        val symbol: String,
        val entry: Double,
        val stop: Double,
        val target: Double,
        val signal: String,
        val openedAt: Long
    )

    data class ClosedTrade(
        val symbol: String,
        val entry: Double,
        val exit: Double,
        val openedAt: Long,
        val closedAt: Long
    ) {
        val plPct: Double get() = if (entry != 0.0) (exit - entry) / entry * 100.0 else 0.0
    }

    data class Stats(val open: Int, val closed: Int, val winRate: Int, val avgPl: Double)

    private val file = File(context.filesDir, "journal.json")
    private var open = LinkedHashMap<String, Position>()
    private var closed = ArrayList<ClosedTrade>()
    private var loaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val o = root.optJSONArray("open") ?: JSONArray()
            for (i in 0 until o.length()) {
                val p = o.getJSONObject(i)
                val pos = Position(
                    p.getString("sym"), p.getDouble("entry"), p.getDouble("stop"),
                    p.getDouble("target"), p.getString("signal"), p.getLong("at")
                )
                open[pos.symbol] = pos
            }
            val c = root.optJSONArray("closed") ?: JSONArray()
            for (i in 0 until c.length()) {
                val t = c.getJSONObject(i)
                closed.add(
                    ClosedTrade(
                        t.getString("sym"), t.getDouble("entry"), t.getDouble("exit"),
                        t.getLong("at"), t.getLong("closedAt")
                    )
                )
            }
        } catch (_: Exception) {
            open = LinkedHashMap()
            closed = ArrayList()
        }
    }

    @Synchronized
    private fun persist() {
        try {
            val root = JSONObject()
            val o = JSONArray()
            open.values.forEach { p ->
                o.put(JSONObject().apply {
                    put("sym", p.symbol); put("entry", p.entry); put("stop", p.stop)
                    put("target", p.target); put("signal", p.signal); put("at", p.openedAt)
                })
            }
            val c = JSONArray()
            closed.forEach { t ->
                c.put(JSONObject().apply {
                    put("sym", t.symbol); put("entry", t.entry); put("exit", t.exit)
                    put("at", t.openedAt); put("closedAt", t.closedAt)
                })
            }
            root.put("open", o)
            root.put("closed", c)
            file.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun openPosition(idea: Idea) {
        ensureLoaded()
        if (open.containsKey(idea.constituent.symbol)) return
        open[idea.constituent.symbol] = Position(
            idea.constituent.symbol, idea.price, idea.stopSuggestion,
            idea.targetSuggestion, idea.signal.name, System.currentTimeMillis()
        )
        persist()
    }

    @Synchronized
    fun closePosition(symbol: String, exitPrice: Double) {
        ensureLoaded()
        val pos = open.remove(symbol) ?: return
        closed.add(ClosedTrade(symbol, pos.entry, exitPrice, pos.openedAt, System.currentTimeMillis()))
        persist()
    }

    @Synchronized
    fun positionFor(symbol: String): Position? {
        ensureLoaded()
        return open[symbol]
    }

    @Synchronized
    fun openPositions(): List<Position> {
        ensureLoaded()
        return open.values.toList()
    }

    @Synchronized
    fun closedTrades(): List<ClosedTrade> {
        ensureLoaded()
        return closed.toList()
    }

    @Synchronized
    fun stats(): Stats {
        ensureLoaded()
        val wins = closed.count { it.plPct > 0 }
        val winRate = if (closed.isNotEmpty()) wins * 100 / closed.size else 0
        val avg = if (closed.isNotEmpty()) closed.sumOf { it.plPct } / closed.size else 0.0
        return Stats(open.size, closed.size, winRate, avg)
    }

    @Synchronized
    fun clearAll() {
        ensureLoaded()
        open.clear()
        closed.clear()
        persist()
    }
}
