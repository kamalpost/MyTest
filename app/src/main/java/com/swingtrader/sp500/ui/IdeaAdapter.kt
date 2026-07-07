package com.swingtrader.sp500.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.swingtrader.sp500.R
import com.swingtrader.sp500.data.DecisionStore
import com.swingtrader.sp500.data.JournalStore
import com.swingtrader.sp500.model.Decision
import com.swingtrader.sp500.model.Idea
import com.swingtrader.sp500.model.Signal

class IdeaAdapter(
    private val decisions: DecisionStore,
    private val journal: JournalStore,
    private val onClick: (Idea) -> Unit,
    private val onDecision: (Idea, Decision) -> Unit,
    private val onLongClick: (Idea) -> Unit
) : BaseAdapter() {

    companion object {
        fun signalColors(signal: Signal): Pair<Int, Int> = when (signal.bullish) {
            true -> R.color.gain to R.color.gain_dim
            false -> R.color.loss to R.color.loss_dim
            null -> R.color.neutral to R.color.neutral_dim
        }

        fun rsiColor(rsi: Double): Int = when {
            rsi <= 30 -> R.color.gain
            rsi >= 70 -> R.color.loss
            rsi >= 60 -> R.color.warn
            else -> R.color.text_primary
        }
    }

    private var items: List<Idea> = emptyList()

    fun submitList(list: List<Idea>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(position: Int): Idea = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_idea, parent, false)
            .also { it.tag = VH(it) }
        (view.tag as VH).bind(getItem(position), decisions, journal, onClick, onDecision, onLongClick)
        return view
    }

    private class VH(item: View) {
        val root = item
        val symbol: TextView = item.findViewById(R.id.txtSymbol)
        val badge: TextView = item.findViewById(R.id.txtBadge)
        val name: TextView = item.findViewById(R.id.txtName)
        val price: TextView = item.findViewById(R.id.txtPrice)
        val change: TextView = item.findViewById(R.id.txtChange)
        val spark: SparklineView = item.findViewById(R.id.spark)
        val rsi: TextView = item.findViewById(R.id.txtRsi)
        val ma20: TextView = item.findViewById(R.id.txtMa20)
        val ma50: TextView = item.findViewById(R.id.txtMa50)
        val vol: TextView = item.findViewById(R.id.txtVol)
        val reason: TextView = item.findViewById(R.id.txtReason)
        val decision: TextView = item.findViewById(R.id.txtDecision)
        val btnTake: TextView = item.findViewById(R.id.btnTake)
        val btnSkip: TextView = item.findViewById(R.id.btnSkip)

        fun bind(
            idea: Idea,
            decisions: DecisionStore,
            journal: JournalStore,
            onClick: (Idea) -> Unit,
            onDecision: (Idea, Decision) -> Unit,
            onLongClick: (Idea) -> Unit
        ) {
            val ctx = root.context
            fun color(id: Int) = ctx.getColor(id)

            symbol.text = idea.constituent.symbol
            name.text = "${idea.constituent.name} · ${idea.constituent.sector}" +
                " · $${Format.capB(idea.constituent.capB)}" +
                if (idea.pe > 0) " · P/E ${Format.one(idea.pe)}" else ""
            price.text = "$" + Format.price(idea.price)
            change.text = Format.pct(idea.changePct1d)
            change.setTextColor(color(if (idea.changePct1d >= 0) R.color.gain else R.color.loss))

            val (fg, bg) = signalColors(idea.signal)
            badge.text = "${idea.signal.label.uppercase()} · ${idea.score}"
            badge.setTextColor(color(fg))
            badge.background.mutate().setTint(color(bg))

            spark.setData(idea.closes, idea.sma20Series, idea.sma50Series, up = idea.changePct1d >= 0)

            rsi.text = "RSI ${Format.one(idea.rsi14)}"
            rsi.setTextColor(color(rsiColor(idea.rsi14)))

            ma20.text = "MA20 ${if (idea.aboveSma20) "▲" else "▼"}"
            ma20.setTextColor(color(if (idea.aboveSma20) R.color.gain else R.color.loss))

            ma50.text = "MA50 ${if (idea.aboveSma50) "▲" else "▼"}"
            ma50.setTextColor(color(if (idea.aboveSma50) R.color.gain else R.color.loss))

            vol.text = "VOL ${Format.one(idea.rvol)}×"
            vol.setTextColor(
                color(
                    when {
                        idea.rvol >= 2.0 -> R.color.warn
                        idea.rvol >= 1.5 -> R.color.gain
                        else -> R.color.text_primary
                    }
                )
            )

            reason.text = idea.reason

            when (decisions.get(idea)) {
                Decision.TAKEN -> {
                    val pos = journal.positionFor(idea.constituent.symbol)
                    if (pos != null && pos.entry > 0) {
                        val pl = (idea.price - pos.entry) / pos.entry * 100.0
                        decision.text = "✓ TAKEN @ $${Format.price(pos.entry)} · ${Format.pct(pl)}"
                        decision.setTextColor(color(if (pl >= 0) R.color.gain else R.color.loss))
                    } else {
                        decision.text = "✓ IN YOUR SELECTIONS"
                        decision.setTextColor(color(R.color.gain))
                    }
                }
                Decision.SKIPPED -> {
                    decision.text = "✕ SKIPPED"
                    decision.setTextColor(color(R.color.loss))
                }
                Decision.NONE -> decision.text = ""
            }

            btnTake.setOnClickListener { onDecision(idea, Decision.TAKEN) }
            btnSkip.setOnClickListener { onDecision(idea, Decision.SKIPPED) }
            root.setOnClickListener { onClick(idea) }
            root.setOnLongClickListener { onLongClick(idea); true }
        }
    }
}
