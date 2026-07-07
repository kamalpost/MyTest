package com.swingtrader.sp500.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.Window
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.swingtrader.sp500.R
import com.swingtrader.sp500.model.CapCategory
import java.util.Locale

/** Bottom sheet with the advanced screening filters. */
object FilterSheet {

    fun show(
        context: Context,
        state: FilterState,
        sectors: List<String>,
        onApply: () -> Unit,
        onClearDecisions: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.sheet_filters)
        dialog.window?.apply {
            setBackgroundDrawable(context.getDrawable(R.drawable.bg_sheet))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }

        // working copies so Cancel (dismiss) doesn't mutate live state
        val caps = LinkedHashSet(state.caps)
        val secs = LinkedHashSet(state.sectors)

        val capRow = dialog.findViewById<LinearLayout>(R.id.capRow)!!
        val sectorRow = dialog.findViewById<LinearLayout>(R.id.sectorRow)!!
        val txtRsi = dialog.findViewById<TextView>(R.id.txtRsiRange)!!
        val sbRsiMin = dialog.findViewById<SeekBar>(R.id.sbRsiMin)!!
        val sbRsiMax = dialog.findViewById<SeekBar>(R.id.sbRsiMax)!!
        val txtRvol = dialog.findViewById<TextView>(R.id.txtRvol)!!
        val sbRvol = dialog.findViewById<SeekBar>(R.id.sbRvol)!!
        val etMin = dialog.findViewById<EditText>(R.id.etPriceMin)!!
        val etMax = dialog.findViewById<EditText>(R.id.etPriceMax)!!

        fun chip(label: String, selected: Boolean, onToggle: (TextView) -> Unit): TextView {
            val c = TextView(context)
            c.text = label
            c.textSize = 12f
            c.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            c.setBackgroundResource(R.drawable.bg_chip)
            val p = (11 * context.resources.displayMetrics.density).toInt()
            val pv = (7 * context.resources.displayMetrics.density).toInt()
            c.setPadding(p, pv, p, pv)
            c.isSelected = selected
            c.setTextColor(context.getColor(if (selected) R.color.accent else R.color.text_secondary))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = (6 * context.resources.displayMetrics.density).toInt()
            c.layoutParams = lp
            c.setOnClickListener {
                onToggle(c)
                c.setTextColor(context.getColor(if (c.isSelected) R.color.accent else R.color.text_secondary))
            }
            return c
        }

        CapCategory.values().forEach { cat ->
            capRow.addView(chip(cat.label, cat in caps) { c ->
                if (cat in caps) caps.remove(cat) else caps.add(cat)
                c.isSelected = cat in caps
            })
        }
        sectors.forEach { s ->
            sectorRow.addView(chip(s, s in secs) { c ->
                if (s in secs) secs.remove(s) else secs.add(s)
                c.isSelected = s in secs
            })
        }

        fun renderRsi() {
            txtRsi.text = context.getString(R.string.filters_rsi) +
                "  ${sbRsiMin.progress} – ${sbRsiMax.progress}"
        }

        fun renderRvol() {
            val v = sbRvol.progress / 10.0
            txtRvol.text = context.getString(R.string.filters_rvol) +
                if (v <= 0.0) "  off" else String.format(Locale.US, "  ≥ %.1f×", v)
        }

        sbRsiMin.progress = state.rsiMin
        sbRsiMax.progress = state.rsiMax
        sbRvol.progress = (state.rvolMin * 10).toInt()
        if (state.priceMin > 0) etMin.setText(Format.two(state.priceMin))
        if (state.priceMax > 0) etMax.setText(Format.two(state.priceMax))
        renderRsi()
        renderRvol()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                // keep min <= max
                if (sb == sbRsiMin && value > sbRsiMax.progress) sbRsiMax.progress = value
                if (sb == sbRsiMax && value < sbRsiMin.progress) sbRsiMin.progress = value
                renderRsi()
                renderRvol()
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        }
        sbRsiMin.setOnSeekBarChangeListener(listener)
        sbRsiMax.setOnSeekBarChangeListener(listener)
        sbRvol.setOnSeekBarChangeListener(listener)

        dialog.findViewById<TextView>(R.id.btnApply)!!.setOnClickListener {
            state.caps.clear(); state.caps.addAll(caps)
            state.sectors.clear(); state.sectors.addAll(secs)
            state.rsiMin = sbRsiMin.progress
            state.rsiMax = sbRsiMax.progress
            state.rvolMin = sbRvol.progress / 10.0
            state.priceMin = etMin.text.toString().toDoubleOrNull() ?: 0.0
            state.priceMax = etMax.text.toString().toDoubleOrNull() ?: 0.0
            dialog.dismiss()
            onApply()
        }

        dialog.findViewById<TextView>(R.id.btnReset)!!.setOnClickListener {
            state.reset()
            dialog.dismiss()
            onApply()
        }

        dialog.findViewById<TextView>(R.id.btnClearDecisions)!!.setOnClickListener {
            dialog.dismiss()
            onClearDecisions()
        }

        dialog.show()
    }
}
