package com.swingtrader.sp500.data

import android.content.Context
import com.swingtrader.sp500.analysis.Indicators
import com.swingtrader.sp500.analysis.SignalEngine
import com.swingtrader.sp500.model.Constituent
import com.swingtrader.sp500.model.Idea
import com.swingtrader.sp500.model.MarketSnapshot
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Blocking data access — every method here must be called off the main thread.
 * The scan fans out over a fixed thread pool (10 concurrent Yahoo requests).
 */
class StockRepository(private val context: Context) {

    companion object {
        private const val CONCURRENCY = 10
        const val INDEX_SYMBOL = "^GSPC"
    }

    fun loadConstituents(): List<Constituent> {
        val out = ArrayList<Constituent>(510)
        context.assets.open("sp500.csv").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val parts = line.split(',', limit = 3)
                if (parts.size == 3) out.add(Constituent(parts[0].trim(), parts[1].trim(), parts[2].trim()))
            }
        }
        return out
    }

    fun fetchMarketSnapshot(): MarketSnapshot? {
        val h = YahooFinanceClient.fetchDailyHistory(INDEX_SYMBOL) ?: return null
        val n = h.size
        if (n < 55) return null
        val rsi = Indicators.rsi(h.close, 14)[n - 1]
        val sma20 = Indicators.sma(h.close, 20)[n - 1]
        val sma50 = Indicators.sma(h.close, 50)[n - 1]
        val price = h.close[n - 1]
        val prev = h.close[n - 2]
        return MarketSnapshot(
            price = price,
            changePct1d = (price - prev) / prev * 100.0,
            rsi14 = rsi,
            sma20 = sma20,
            sma50 = sma50,
            closes = h.close.copyOfRange(n - min(n, 60), n)
        )
    }

    /** Blocking scan of all constituents. [onProgress] fires on worker threads. */
    fun scan(constituents: List<Constituent>, onProgress: (Int, Int) -> Unit): List<Idea> {
        val pool = Executors.newFixedThreadPool(CONCURRENCY)
        val done = AtomicInteger(0)
        val total = constituents.size
        try {
            val futures = constituents.map { c ->
                pool.submit(Callable {
                    val idea = try {
                        YahooFinanceClient.fetchDailyHistory(c.symbol)?.let { SignalEngine.analyze(c, it) }
                    } catch (_: Exception) {
                        null
                    }
                    onProgress(done.incrementAndGet(), total)
                    idea
                })
            }
            return futures.mapNotNull { f ->
                try { f.get() } catch (_: Exception) { null }
            }
        } finally {
            pool.shutdown()
        }
    }
}
