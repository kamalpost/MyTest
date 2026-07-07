package com.swingtrader.sp500

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.swingtrader.sp500.data.CacheStore
import com.swingtrader.sp500.data.DecisionStore
import com.swingtrader.sp500.data.StockRepository
import com.swingtrader.sp500.model.Decision
import com.swingtrader.sp500.model.Idea
import com.swingtrader.sp500.model.MarketSnapshot
import com.swingtrader.sp500.model.Signal
import com.swingtrader.sp500.ui.DetailSheet
import com.swingtrader.sp500.ui.FilterSheet
import com.swingtrader.sp500.ui.FilterState
import com.swingtrader.sp500.ui.Format
import com.swingtrader.sp500.ui.IdeaAdapter
import com.swingtrader.sp500.ui.SparklineView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private enum class ChipFilter(val label: String) {
        ALL("All"),
        BUYS("Buy Setups"),
        OVERSOLD("Oversold"),
        PULLBACK("Pullbacks"),
        BREAKOUT("Breakouts"),
        OVERBOUGHT("Overbought"),
        BEARISH("Bearish"),
        SELECTED("✓ Selected"),
        SKIPPED("✕ Skipped")
    }

    companion object {
        /** Auto-rescan on launch only if the cached scan is older than this. */
        private const val CACHE_FRESH_MS = 60 * 60 * 1000L
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var repository: StockRepository
    private lateinit var decisions: DecisionStore
    private lateinit var adapter: IdeaAdapter
    private lateinit var header: View
    private lateinit var progress: ProgressBar
    private lateinit var txtProgress: TextView
    private lateinit var txtEmpty: TextView
    private lateinit var btnRescan: TextView
    private lateinit var btnFilters: TextView
    private val chips = LinkedHashMap<ChipFilter, TextView>()

    private var allIdeas: List<Idea> = emptyList()
    private var lastScanAt = 0L
    private var universeSize = 0
    private var activeChip = ChipFilter.ALL
    private val filterState = FilterState()

    @Volatile
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = StockRepository(applicationContext)
        decisions = DecisionStore(applicationContext)
        adapter = IdeaAdapter(
            decisions,
            onClick = { idea -> DetailSheet.show(this, idea, ::onDecision) },
            onDecision = ::onDecision
        )

        progress = findViewById(R.id.progress)
        txtProgress = findViewById(R.id.txtProgress)
        txtEmpty = findViewById(R.id.txtEmpty)
        btnRescan = findViewById(R.id.btnRescan)
        btnFilters = findViewById(R.id.btnFilters)

        val list = findViewById<ListView>(R.id.list)
        header = layoutInflater.inflate(R.layout.header_market, list, false)
        list.addHeaderView(header, null, false)
        list.adapter = adapter
        buildChips(header.findViewById(R.id.chipRow))

        btnRescan.setOnClickListener { startScan() }
        btnFilters.setOnClickListener { openFilters() }

        // Restore the previous scan instantly, then only hit the network if stale.
        worker.execute {
            val cached = CacheStore.load(applicationContext)
            val universe = repository.loadConstituents().size
            main.post {
                universeSize = universe
                if (cached != null) {
                    allIdeas = cached.ideas
                    lastScanAt = cached.savedAt
                    cached.snapshot?.let { bindMarket(it) }
                    bindBreadth()
                    applyFilter()
                }
                val stale = cached == null ||
                    System.currentTimeMillis() - cached.savedAt > CACHE_FRESH_MS
                if (stale) startScan()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    private fun onDecision(idea: Idea, decision: Decision) {
        decisions.toggle(idea, decision)
        applyFilter()
    }

    private fun openFilters() {
        val sectors = allIdeas.map { it.constituent.sector }.distinct().sorted()
            .ifEmpty { repository.loadConstituents().map { it.sector }.distinct().sorted() }
        FilterSheet.show(
            this, filterState, sectors,
            onApply = {
                renderFilterButton()
                applyFilter()
            },
            onClearDecisions = {
                decisions.clearAll()
                applyFilter()
                Toast.makeText(this, "All ✓/✕ marks cleared", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun renderFilterButton() {
        val n = filterState.activeCount()
        btnFilters.text = if (n == 0) getString(R.string.filters) else "☰ FILTERS ($n)"
    }

    private fun buildChips(row: LinearLayout) {
        val pad = (12 * resources.displayMetrics.density).toInt()
        val padV = (7 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()
        ChipFilter.values().forEach { f ->
            val chip = TextView(this)
            chip.text = f.label
            chip.textSize = 12f
            chip.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            chip.setBackgroundResource(R.drawable.bg_chip)
            chip.setPadding(pad, padV, pad, padV)
            chip.isSelected = f == activeChip
            chip.setTextColor(getColor(if (chip.isSelected) R.color.accent else R.color.text_secondary))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = margin
            chip.layoutParams = lp
            chip.setOnClickListener { selectChip(f) }
            row.addView(chip)
            chips[f] = chip
        }
    }

    private fun selectChip(f: ChipFilter) {
        activeChip = f
        chips.forEach { (filter, chip) ->
            chip.isSelected = filter == f
            chip.setTextColor(getColor(if (chip.isSelected) R.color.accent else R.color.text_secondary))
        }
        applyFilter()
    }

    private fun startScan() {
        if (scanning) return
        scanning = true
        btnRescan.alpha = 0.4f
        progress.visibility = View.VISIBLE
        progress.progress = 0
        txtProgress.visibility = View.VISIBLE
        txtProgress.text = getString(R.string.scanning)
        txtEmpty.visibility = View.GONE

        worker.execute {
            // Index snapshot first (fast) so the header fills in early.
            val snapshot = repository.fetchMarketSnapshot()
            if (snapshot != null) main.post { bindMarket(snapshot) }

            val constituents = repository.loadConstituents()
            main.post {
                universeSize = constituents.size
                progress.max = constituents.size
            }

            val fresh = try {
                repository.scan(constituents) { done, total ->
                    if (done % 5 == 0 || done == total) {
                        main.post {
                            progress.progress = done
                            txtProgress.text = "$done / $total"
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }

            // Merge: symbols that failed this round keep their previous data
            // instead of vanishing from the dashboard.
            val freshSymbols = fresh.map { it.constituent.symbol }.toHashSet()
            val merged = fresh + allIdeas.filter { it.constituent.symbol !in freshSymbols }

            val scannedAt = System.currentTimeMillis()
            if (fresh.isNotEmpty()) {
                CacheStore.save(applicationContext, snapshot, merged)
            }

            main.post {
                if (fresh.isNotEmpty()) {
                    allIdeas = merged
                    lastScanAt = scannedAt
                }
                bindBreadth()
                applyFilter()

                progress.visibility = View.GONE
                txtProgress.visibility = View.GONE
                btnRescan.alpha = 1f
                scanning = false

                if (fresh.isEmpty()) {
                    if (allIdeas.isEmpty()) txtEmpty.visibility = View.VISIBLE
                    Toast.makeText(
                        this,
                        "Couldn't fetch data — check your connection (Yahoo may be rate-limiting)." +
                            if (allIdeas.isNotEmpty()) " Showing previous scan." else " Tap ⟳ RESCAN to retry.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun bindMarket(s: MarketSnapshot) {
        header.findViewById<TextView>(R.id.txtIndexPrice).text = Format.price(s.price)
        header.findViewById<TextView>(R.id.txtIndexChange).apply {
            text = Format.pct(s.changePct1d)
            setTextColor(getColor(if (s.changePct1d >= 0) R.color.gain else R.color.loss))
        }
        header.findViewById<TextView>(R.id.txtIndexRsi).text = "RSI ${Format.one(s.rsi14)}"
        header.findViewById<TextView>(R.id.txtIndexMa20).text = "MA20 ${Format.price(s.sma20)}"
        header.findViewById<TextView>(R.id.txtIndexMa50).text = "MA50 ${Format.price(s.sma50)}"
        header.findViewById<SparklineView>(R.id.sparkIndex).setData(s.closes, up = s.changePct1d >= 0)
    }

    private fun bindBreadth() {
        val ideas = allIdeas
        val buys = ideas.count { it.signal.bullish == true && it.signal != Signal.UPTREND }
        val bear = ideas.count { it.signal.bullish == false }
        val above50 = ideas.count { it.aboveSma50 }
        val pctAbove = if (ideas.isNotEmpty()) above50 * 100 / ideas.size else 0
        val scanInfo = if (lastScanAt > 0) " · scanned ${Format.ago(lastScanAt)}" else ""
        header.findViewById<TextView>(R.id.txtBreadth).text =
            "${ideas.size}/$universeSize stocks · $buys buy setups · $bear bearish · $pctAbove% above 50d MA$scanInfo"
    }

    private fun applyFilter() {
        val base = when (activeChip) {
            ChipFilter.ALL -> allIdeas
            ChipFilter.BUYS -> allIdeas.filter { it.signal.bullish == true && it.signal != Signal.UPTREND }
            ChipFilter.OVERSOLD -> allIdeas.filter { it.signal == Signal.OVERSOLD_BOUNCE }
            ChipFilter.PULLBACK -> allIdeas.filter { it.signal == Signal.PULLBACK_BUY }
            ChipFilter.BREAKOUT -> allIdeas.filter {
                it.signal == Signal.BREAKOUT || it.signal == Signal.GOLDEN_CROSS
            }
            ChipFilter.OVERBOUGHT -> allIdeas.filter { it.signal == Signal.OVERBOUGHT }
            ChipFilter.BEARISH -> allIdeas.filter { it.signal.bullish == false }
            ChipFilter.SELECTED -> allIdeas.filter { decisions.get(it) == Decision.TAKEN }
            ChipFilter.SKIPPED -> allIdeas.filter { decisions.get(it) == Decision.SKIPPED }
        }
        // Skipped ideas stay out of sight everywhere except their own tab.
        val visible = if (activeChip == ChipFilter.SKIPPED) base
        else base.filter { decisions.get(it) != Decision.SKIPPED }

        val filtered = visible.filter { filterState.matches(it) }

        adapter.submitList(sortForDisplay(filtered))
        if (allIdeas.isNotEmpty() && filtered.isEmpty()) {
            txtEmpty.text = "No matches for this filter"
            txtEmpty.visibility = View.VISIBLE
        } else if (!scanning || filtered.isNotEmpty()) {
            txtEmpty.visibility = View.GONE
        }
    }

    /** Actionable long setups first, then trend/neutral, bearish last — score breaks ties. */
    private fun sortForDisplay(ideas: List<Idea>): List<Idea> {
        fun bucket(s: Signal) = when (s) {
            Signal.OVERSOLD_BOUNCE, Signal.PULLBACK_BUY, Signal.BREAKOUT, Signal.GOLDEN_CROSS -> 0
            Signal.UPTREND -> 1
            Signal.NEUTRAL -> 2
            Signal.OVERBOUGHT, Signal.BREAKDOWN, Signal.DEATH_CROSS -> 3
        }
        return ideas.sortedWith(compareBy({ bucket(it.signal) }, { -it.score }, { it.constituent.symbol }))
    }
}
