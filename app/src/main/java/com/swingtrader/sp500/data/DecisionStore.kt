package com.swingtrader.sp500.data

import android.content.Context
import com.swingtrader.sp500.model.Decision
import com.swingtrader.sp500.model.Idea

/**
 * Remembers which ideas the user took or skipped. A decision is stored per
 * symbol together with the signal it was made on, so it automatically expires
 * when the setup changes (e.g. a skipped Pullback Buy that later turns into a
 * Volume Breakout shows up again as undecided).
 */
class DecisionStore(context: Context) {

    private val prefs = context.getSharedPreferences("decisions", Context.MODE_PRIVATE)

    fun get(idea: Idea): Decision {
        val stored = prefs.getString(idea.constituent.symbol, null) ?: return Decision.NONE
        val parts = stored.split('|')
        if (parts.size != 2 || parts[1] != idea.signal.name) return Decision.NONE
        return try {
            Decision.valueOf(parts[0])
        } catch (_: Exception) {
            Decision.NONE
        }
    }

    fun set(idea: Idea, decision: Decision) {
        if (decision == Decision.NONE) {
            prefs.edit().remove(idea.constituent.symbol).apply()
        } else {
            prefs.edit()
                .putString(idea.constituent.symbol, "${decision.name}|${idea.signal.name}")
                .apply()
        }
    }

    /** Toggle helper: pressing the active decision again clears it. */
    fun toggle(idea: Idea, decision: Decision) {
        set(idea, if (get(idea) == decision) Decision.NONE else decision)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
