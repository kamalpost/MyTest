package com.swingtrader.sp500.data

import android.content.Context
import com.swingtrader.sp500.model.Constituent
import com.swingtrader.sp500.model.Idea
import com.swingtrader.sp500.model.MarketSnapshot
import com.swingtrader.sp500.model.Signal
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the last completed scan (index snapshot + all ideas) as JSON in
 * app-private storage so the dashboard is instantly available on relaunch and
 * survives without network. NaN values in MA series are stored as JSON nulls
 * (optDouble reads them back as NaN).
 */
object CacheStore {

    private const val FILE_NAME = "scan_cache.json"
    private const val VERSION = 3

    data class Cached(
        val savedAt: Long,
        val snapshot: MarketSnapshot?,
        val ideas: List<Idea>
    )

    fun save(context: Context, snapshot: MarketSnapshot?, ideas: List<Idea>) {
        try {
            val root = JSONObject()
            root.put("version", VERSION)
            root.put("savedAt", System.currentTimeMillis())
            snapshot?.let { s ->
                root.put("snapshot", JSONObject().apply {
                    put("price", s.price)
                    put("chg", s.changePct1d)
                    put("rsi", s.rsi14)
                    put("sma20", s.sma20)
                    put("sma50", s.sma50)
                    put("closes", arr(s.closes))
                })
            }
            val list = JSONArray()
            ideas.forEach { i ->
                list.put(JSONObject().apply {
                    put("sym", i.constituent.symbol)
                    put("name", i.constituent.name)
                    put("sector", i.constituent.sector)
                    put("capB", i.constituent.capB)
                    put("price", i.price)
                    put("chg", i.changePct1d)
                    put("rsi", i.rsi14)
                    put("sma20", i.sma20)
                    put("sma50", i.sma50)
                    put("avgVol20", i.avgVol20)
                    put("lastVol", i.lastVol)
                    put("rvol", i.rvol)
                    put("volTrend", i.volTrend)
                    put("atr", nz(i.atr14))
                    put("hi3m", i.hi3m)
                    put("lo3m", i.lo3m)
                    put("macdHist", nz(i.macdHist))
                    put("macdBull", i.macdBullCross)
                    put("bbU", nz(i.bbUpper))
                    put("bbL", nz(i.bbLower))
                    put("bbSq", i.bbSqueeze)
                    put("signal", i.signal.name)
                    put("score", i.score)
                    put("reason", i.reason)
                    put("closes", arr(i.closes))
                    put("s20", arr(i.sma20Series))
                    put("s50", arr(i.sma50Series))
                })
            }
            root.put("ideas", list)
            File(context.filesDir, FILE_NAME).writeText(root.toString())
        } catch (_: Exception) {
            // cache is best-effort; never crash the app for it
        }
    }

    fun load(context: Context): Cached? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null
            val root = JSONObject(file.readText())
            if (root.optInt("version") != VERSION) return null

            val snapshot = root.optJSONObject("snapshot")?.let { s ->
                MarketSnapshot(
                    price = s.getDouble("price"),
                    changePct1d = s.getDouble("chg"),
                    rsi14 = s.getDouble("rsi"),
                    sma20 = s.getDouble("sma20"),
                    sma50 = s.getDouble("sma50"),
                    closes = darr(s.getJSONArray("closes"))
                )
            }

            val ideasJson = root.getJSONArray("ideas")
            val ideas = ArrayList<Idea>(ideasJson.length())
            for (k in 0 until ideasJson.length()) {
                val o = ideasJson.getJSONObject(k)
                ideas.add(
                    Idea(
                        constituent = Constituent(
                            o.getString("sym"), o.getString("name"),
                            o.getString("sector"), o.getDouble("capB")
                        ),
                        price = o.getDouble("price"),
                        changePct1d = o.getDouble("chg"),
                        rsi14 = o.getDouble("rsi"),
                        sma20 = o.getDouble("sma20"),
                        sma50 = o.getDouble("sma50"),
                        avgVol20 = o.getDouble("avgVol20"),
                        lastVol = o.getLong("lastVol"),
                        rvol = o.getDouble("rvol"),
                        volTrend = o.getDouble("volTrend"),
                        atr14 = o.optDouble("atr"),
                        hi3m = o.getDouble("hi3m"),
                        lo3m = o.getDouble("lo3m"),
                        macdHist = o.optDouble("macdHist"),
                        macdBullCross = o.optBoolean("macdBull"),
                        bbUpper = o.optDouble("bbU"),
                        bbLower = o.optDouble("bbL"),
                        bbSqueeze = o.optBoolean("bbSq"),
                        signal = Signal.valueOf(o.getString("signal")),
                        score = o.getInt("score"),
                        reason = o.getString("reason"),
                        closes = darr(o.getJSONArray("closes")),
                        sma20Series = darr(o.getJSONArray("s20")),
                        sma50Series = darr(o.getJSONArray("s50"))
                    )
                )
            }
            Cached(root.getLong("savedAt"), snapshot, ideas)
        } catch (_: Exception) {
            null
        }
    }

    private fun arr(values: DoubleArray): JSONArray {
        val a = JSONArray()
        values.forEach { v -> if (v.isNaN()) a.put(JSONObject.NULL) else a.put(v) }
        return a
    }

    private fun darr(a: JSONArray): DoubleArray =
        DoubleArray(a.length()) { i -> a.optDouble(i) } // null -> NaN

    private fun nz(v: Double): Any = if (v.isNaN()) JSONObject.NULL else v
}
