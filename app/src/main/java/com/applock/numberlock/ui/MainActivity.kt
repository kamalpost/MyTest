package com.applock.numberlock.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.applock.numberlock.data.AppInfo
import com.applock.numberlock.data.PrefsManager
import com.applock.numberlock.databinding.ActivityMainBinding
import com.applock.numberlock.service.AppLockAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager.getInstance(this)

        if (!prefsManager.isPinSet()) {
            startActivity(Intent(this, SetupPinActivity::class.java))
        }

        binding.recyclerApps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        adapter = AppListAdapter { app, locked ->
            prefsManager.setAppLocked(app.packageName, locked)
        }
        binding.recyclerApps.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnChangePin.setOnClickListener {
            startActivity(Intent(this, SetupPinActivity::class.java).apply {
                putExtra(SetupPinActivity.EXTRA_CHANGE_PIN, true)
            })
        }

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        updateAccessibilityBanner()
    }

    private fun updateAccessibilityBanner() {
        val enabled = isAccessibilityServiceEnabled()
        binding.accessibilityBanner.visibility = if (enabled) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityManager.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name == AppLockAccessibilityService::class.java.name }
    }

    private fun loadApps() {
        val pm = packageManager
        val lockedPackages = prefsManager.getLockedPackages()

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, 0)

        val apps = resolved
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .map { appInfo: ApplicationInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    label = appInfo.loadLabel(pm).toString(),
                    icon = appInfo.loadIcon(pm),
                    locked = appInfo.packageName in lockedPackages
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

        adapter.submitList(apps)
    }
}
