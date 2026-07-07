package com.swingtrader.sp500.data

import com.swingtrader.sp500.model.History
import org.json.JSONObject
import java.io.BufferedReader
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
}
