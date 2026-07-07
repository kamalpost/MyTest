package com.swingtrader.sp500.ui

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import com.swingtrader.sp500.R
import com.swingtrader.sp500.model.Decision
import com.swingtrader.sp500.model.Idea

/** Bottom-sheet style dialog built on the plain framework Dialog. */
object DetailSheet {

    fun show(context: Context, idea: Idea, onDecision: (Idea, Decision) -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.sheet_detail)
        dialog.window?.apply {
            setBackgroundDrawable(context.getDrawable(R.drawable.bg_sheet))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }

        fun color(id: Int) = context.getColor(id)
        fun tv(id: Int) = dialog.findViewById<TextView>(id)!!

        tv(R.id.dSymbol).text = idea.constituent.symbol
        tv(R.id.dName).text = "${idea.constituent.name} · ${idea.constituent.sector}" +
            " · $${Format.capB(idea.constituent.capB)}"
        tv(R.id.dPrice).text = "$" + Format.price(idea.price)
        tv(R.id.dChange).apply {
            text = Format.pct(idea.changePct1d) + " today"
            setTextColor(color(if (idea.changePct1d >= 0) R.color.gain else R.color.loss))
        }

        val (fg, bg) = IdeaAdapter.signalColors(idea.signal)
        tv(R.id.dBadge).apply {
            text = "${idea.signal.label.uppercase()} · conviction ${idea.score}/100"
            setTextColor(color(fg))
            background.mutate().setTint(color(bg))
        }
        tv(R.id.dReason).text = idea.reason

        dialog.findViewById<SparklineView>(R.id.dSpark)!!
            .setData(idea.closes, idea.sma20Series, idea.sma50Series, up = idea.changePct1d >= 0)

        tv(R.id.dStats).text = buildString {
            appendLine("RSI(14)        ${Format.one(idea.rsi14)}")
            appendLine("MA 20d         $${Format.price(idea.sma20)}  (price ${if (idea.aboveSma20) "above ▲" else "below ▼"})")
            appendLine("MA 50d         $${Format.price(idea.sma50)}  (price ${if (idea.aboveSma50) "above ▲" else "below ▼"})")
            appendLine("Volume         ${Format.volume(idea.lastVol.toDouble())}  (${Format.one(idea.rvol)}× 20d avg)")
            appendLine("20d avg vol    ${Format.volume(idea.avgVol20)}")
            appendLine("Vol trend 5/20 ${Format.two(idea.volTrend)}×")
            appendLine("ATR(14)        $${Format.two(idea.atr14)}")
            append("3mo range      $${Format.price(idea.lo3m)} – $${Format.price(idea.hi3m)}")
        }

        tv(R.id.dPlan).text = if (idea.signal.bullish == true) buildString {
            appendLine("SWING PLAN (ATR-based)")
            appendLine("Entry   ~$${Format.price(idea.price)}")
            appendLine("Stop     $${Format.price(idea.stopSuggestion)}  (1.5 × ATR)")
            appendLine("Target   $${Format.price(idea.targetSuggestion)}  (2.5 × ATR)")
            append("R:R      1 : 1.67")
        } else buildString {
            appendLine("SWING PLAN")
            append(
                when (idea.signal.bullish) {
                    false -> "Bearish/extended setup — not a long entry. If holding, consider tightening stops near $${Format.price(idea.stopSuggestion)}."
                    else -> "No edge right now — keep on watchlist and wait for a pullback toward the 20-day MA ($${Format.price(idea.sma20)})."
                }
            )
        }

        tv(R.id.dBtnTake).setOnClickListener {
            onDecision(idea, Decision.TAKEN)
            dialog.dismiss()
        }
        tv(R.id.dBtnSkip).setOnClickListener {
            onDecision(idea, Decision.SKIPPED)
            dialog.dismiss()
        }

        dialog.show()
    }
}
