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

    fun matches(idea: Idea): Boolean {
        if (caps.isNotEmpty() && idea.constituent.capCategory !in caps) return false
        if (sectors.isNotEmpty() && idea.constituent.sector !in sectors) return false
        if (idea.rsi14 < rsiMin || idea.rsi14 > rsiMax) return false
        if (rvolMin > 0 && idea.rvol < rvolMin) return false
        if (priceMin > 0 && idea.price < priceMin) return false
        if (priceMax > 0 && idea.price > priceMax) return false
        return true
    }

    fun activeCount(): Int {
        var n = 0
        if (caps.isNotEmpty()) n++
        if (sectors.isNotEmpty()) n++
        if (rsiMin > 0 || rsiMax < 100) n++
        if (rvolMin > 0) n++
        if (priceMin > 0 || priceMax > 0) n++
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
    }
}
