package com.swingtrader.sp500.ui

import com.swingtrader.sp500.model.CapCategory
import com.swingtrader.sp500.model.Idea

/**
 * Advanced screening criteria applied on top of the signal chips.
 * Empty sets mean "no restriction".
 */
class FilterState {
    val caps = LinkedHashSet<CapCategory>()
    val sectors = LinkedHashSet<String>()
    var rsiMin = 0
    var rsiMax = 100
    var rvolMin = 0.0        // 0 = off
    var priceMin = 0.0       // 0 = off
    var priceMax = 0.0       // 0 = off
    var macdBullOnly = false
    var squeezeOnly = false
    var peMax = 0.0          // 0 = off; when set, requires 0 < P/E <= peMax
    var pbMax = 0.0          // 0 = off; when set, requires 0 < P/B <= pbMax

    fun matches(idea: Idea): Boolean {
        if (caps.isNotEmpty() && idea.constituent.capCategory !in caps) return false
        if (sectors.isNotEmpty() && idea.constituent.sector !in sectors) return false
        if (idea.rsi14 < rsiMin || idea.rsi14 > rsiMax) return false
        if (rvolMin > 0 && idea.rvol < rvolMin) return false
        if (priceMin > 0 && idea.price < priceMin) return false
        if (priceMax > 0 && idea.price > priceMax) return false
        if (macdBullOnly && !(idea.macdBullCross || idea.macdHist > 0)) return false
        if (squeezeOnly && !idea.bbSqueeze) return false
        // Valuation caps also exclude unknown (NaN) and negative values —
        // "P/E under 25" shouldn't surface loss-makers or unpriced symbols.
        if (peMax > 0 && !(idea.pe > 0 && idea.pe <= peMax)) return false
        if (pbMax > 0 && !(idea.pb > 0 && idea.pb <= pbMax)) return false
        return true
    }

    fun activeCount(): Int {
        var n = 0
        if (caps.isNotEmpty()) n++
        if (sectors.isNotEmpty()) n++
        if (rsiMin > 0 || rsiMax < 100) n++
        if (rvolMin > 0) n++
        if (priceMin > 0 || priceMax > 0) n++
        if (macdBullOnly) n++
        if (squeezeOnly) n++
        if (peMax > 0) n++
        if (pbMax > 0) n++
        return n
    }

    fun reset() {
        caps.clear()
        sectors.clear()
        rsiMin = 0
        rsiMax = 100
        rvolMin = 0.0
        priceMin = 0.0
        priceMax = 0.0
        macdBullOnly = false
        squeezeOnly = false
        peMax = 0.0
        pbMax = 0.0
    }
}
