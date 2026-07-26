package com.applock.numberlock.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import com.applock.numberlock.data.AppInfo
import com.applock.numberlock.databinding.ItemAppBinding

class AppListAdapter(
    private val onLockToggled: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private var apps: MutableList<AppInfo> = mutableListOf()
    private var filtered: MutableList<AppInfo> = apps
    private var currentQuery: String = ""

    fun submitList(newApps: List<AppInfo>) {
        apps = newApps.toMutableList()
        filter(currentQuery)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(filtered[position])
    }

    override fun getItemCount(): Int = filtered.size

    fun filter(query: String) {
        currentQuery = query
        filtered = if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }.toMutableList()
        }
        notifyDataSetChanged()
    }

    inner class AppViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.appIcon.setImageDrawable(app.icon)
            binding.appLabel.text = app.label
            binding.lockSwitch.setOnCheckedChangeListener(null)
            binding.lockSwitch.isChecked = app.locked
            binding.lockSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                app.locked = isChecked
                onLockToggled(app, isChecked)
            }
        }
    }
}
