package com.swingtrader.sp500.data

import com.swingtrader.sp500.model.History
import org.json.JSONObject
import java.io.BufferedReader
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * Minimal client for Yahoo Finance's v8 chart endpoint — the same API the
 * Python yfinance library reads. No API key required; a browser-like
 * User-Agent header is enough.
 */
object YahooFinanceClient {

    private val HOSTS = listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    /**
     * Fetch ~6 months of daily bars. Tries query1 then query2.
     * Returns null when the symbol can't be fetched or parsed.
     */
    fun fetchDailyHistory(symbol: String, range: String = "6mo"): History? {
        val enc = URLEncoder.encode(symbol, "UTF-8")
        for (host in HOSTS) {
            val url = "https://$host/v8/finance/chart/$enc?range=$range&interval=1d&events=div%2Csplit"
            try {
                val body = get(url) ?: continue
                val parsed = parseChart(symbol, body)
                if (parsed != null) return parsed
            } catch (_: Exception) {
                // fall through to the next host
            }
        }
        return null
    }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            if (conn.responseCode != 200) return null
            val raw = if (conn.contentEncoding == "gzip") GZIPInputStream(conn.inputStream) else conn.inputStream
            raw.bufferedReader().use(BufferedReader::readText)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseChart(symbol: String, body: String): History? {
        val result = JSONObject(body)
            .optJSONObject("chart")
            ?.optJSONArray("result")
            ?.optJSONObject(0) ?: return null

        val tsArr = result.optJSONArray("timestamp") ?: return null
        val quote = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0) ?: return null

        val opens = quote.optJSONArray("open") ?: return null
        val highs = quote.optJSONArray("high") ?: return null
        val lows = quote.optJSONArray("low") ?: return null
        val closes = quote.optJSONArray("close") ?: return null
        val volumes = quote.optJSONArray("volume") ?: return null

        // Drop rows with null closes (halts/holidays) so indicator math stays clean.
        val n = tsArr.length()
        val ts = ArrayList<Long>(n)
        val o = ArrayList<Double>(n)
        val h = ArrayList<Double>(n)
        val l = ArrayList<Double>(n)
        val c = ArrayList<Double>(n)
        val v = ArrayList<Long>(n)
        for (i in 0 until n) {
            val close = closes.optDouble(i, Double.NaN)
            if (close.isNaN()) continue
            ts.add(tsArr.optLong(i))
            c.add(close)
            o.add(highsOr(opens.optDouble(i, Double.NaN), close))
            h.add(highsOr(highs.optDouble(i, Double.NaN), close))
            l.add(highsOr(lows.optDouble(i, Double.NaN), close))
            v.add(volumes.optLong(i, 0L))
        }
        if (c.size < 2) return null

        return History(
            symbol = symbol,
            timestamps = ts.toLongArray(),
            open = o.toDoubleArray(),
            high = h.toDoubleArray(),
            low = l.toDoubleArray(),
            close = c.toDoubleArray(),
            volume = v.toLongArray()
        )
    }

    private fun highsOr(value: Double, fallback: Double) = if (value.isNaN()) fallback else value

    // ---------------------------------------------------------------------
    // Fundamentals (EPS / P/E / P/B) via the v7 quote API. Unlike the chart
    // endpoint this one requires Yahoo's cookie + crumb handshake — the same
    // dance yfinance performs. All failures degrade to an empty map.
    // ---------------------------------------------------------------------

    data class Fundamentals(val eps: Double, val pe: Double, val pb: Double)

    @Volatile
    private var crumb: String? = null

    @Synchronized
    private fun ensureCrumb(): String? {
        crumb?.let { return it }
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
        }
        // Seed session cookies (fc.yahoo.com's 404 still sets them; the full
        // site is the backup), then ask either query host for the crumb.
        for (seed in listOf("https://fc.yahoo.com", "https://finance.yahoo.com")) {
            try { get(seed) } catch (_: Exception) {}
            for (host in HOSTS) {
                val c = try { get("https://$host/v1/test/getcrumb")?.trim() } catch (_: Exception) { null }
                if (!c.isNullOrEmpty() && c.length <= 32 && !c.contains('{')) {
                    crumb = c
                    return c
                }
            }
        }
        return null
    }

    /**
     * Batched quote lookup with a crumb-free per-symbol fallback. Returns
     * whatever subset Yahoo answers for.
     */
    fun fetchFundamentals(symbols: List<String>): Map<String, Fundamentals> {
        val quick = quoteBatch(symbols)
        if (quick.isNotEmpty()) return quick
        return timeseriesFallback(symbols)
    }

    private fun quoteBatch(symbols: List<String>): Map<String, Fundamentals> {
        val out = HashMap<String, Fundamentals>(symbols.size)
        if (symbols.isEmpty()) return out
        val cr = ensureCrumb()
        val fields = "epsTrailingTwelveMonths,trailingPE,priceToBook"
        symbols.chunked(100).forEach { batch ->
            val syms = URLEncoder.encode(batch.joinToString(","), "UTF-8")
            for (host in HOSTS) {
                try {
                    val url = "https://$host/v7/finance/quote?symbols=$syms&fields=$fields" +
                        (cr?.let { "&crumb=${URLEncoder.encode(it, "UTF-8")}" } ?: "")
                    val body = get(url) ?: continue
                    val arr = JSONObject(body)
                        .optJSONObject("quoteResponse")
                        ?.optJSONArray("result") ?: continue
                    for (i in 0 until arr.length()) {
                        val q = arr.getJSONObject(i)
                        val sym = q.optString("symbol")
                        if (sym.isNotEmpty()) {
                            out[sym] = Fundamentals(
                                eps = q.optDouble("epsTrailingTwelveMonths", Double.NaN),
                                pe = q.optDouble("trailingPE", Double.NaN),
                                pb = q.optDouble("priceToBook", Double.NaN)
                            )
                        }
                    }
                    break
                } catch (_: Exception) {
                    // try next host; a failed crumb may also be stale
                    crumb = null
                }
            }
        }
        return out
    }

    /**
     * Crumb-free fallback: the fundamentals-timeseries endpoint serves
     * trailing P/E and P/B per symbol. Slower (one call per symbol, 10-way
     * concurrent) but works when the quote API rejects the crumb. EPS is
     * derived later as price / P/E.
     */
    private fun timeseriesFallback(symbols: List<String>): Map<String, Fundamentals> {
        val out = java.util.concurrent.ConcurrentHashMap<String, Fundamentals>()
        if (symbols.isEmpty()) return out
        val pool = java.util.concurrent.Executors.newFixedThreadPool(10)
        try {
            val now = System.currentTimeMillis() / 1000
            val from = now - 450L * 24 * 3600
            val futures = symbols.map { sym ->
                pool.submit {
                    try {
                        val enc = URLEncoder.encode(sym, "UTF-8")
                        for (host in HOSTS) {
                            val url = "https://$host/ws/fundamentals-timeseries/v1/finance/timeseries/$enc" +
                                "?symbol=$enc&type=trailingPeRatio,trailingPbRatio" +
                                "&period1=$from&period2=$now&merge=false&padTimeSeries=true"
                            val body = get(url) ?: continue
                            val res = JSONObject(body)
                                .optJSONObject("timeseries")
                                ?.optJSONArray("result") ?: continue
                            var pe = Double.NaN
                            var pb = Double.NaN
                            for (i in 0 until res.length()) {
                                val r = res.getJSONObject(i)
                                lastReported(r, "trailingPeRatio")?.let { pe = it }
                                lastReported(r, "trailingPbRatio")?.let { pb = it }
                            }
                            if (!pe.isNaN() || !pb.isNaN()) {
                                out[sym] = Fundamentals(Double.NaN, pe, pb)
                            }
                            break
                        }
                    } catch (_: Exception) {
                        // symbol stays unmapped
                    }
                }
            }
            futures.forEach { f -> try { f.get() } catch (_: Exception) {} }
        } finally {
            pool.shutdown()
        }
        return out
    }

    private fun lastReported(result: JSONObject, key: String): Double? {
        val arr = result.optJSONArray(key) ?: return null
        for (i in arr.length() - 1 downTo 0) {
            val v = arr.optJSONObject(i)
                ?.optJSONObject("reportedValue")
                ?.optDouble("raw", Double.NaN)
            if (v != null && !v.isNaN()) return v
        }
        return null
    }
}
