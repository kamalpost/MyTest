package com.applock.numberlock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.applock.numberlock.data.LockSessionManager
import com.applock.numberlock.data.PrefsManager
import com.applock.numberlock.ui.LockActivity

/**
 * Watches for foreground app changes and shows [LockActivity] on top of any
 * app the user has locked. This is the standard, reliable way for a
 * non-system app to detect "app X just came to the foreground" on Android.
 */
class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var prefsManager: PrefsManager
    private var lastForegroundPackage: String? = null
    private var launcherPackageName: String? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Re-lock everything once the screen turns off.
            LockSessionManager.clearAll()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefsManager = PrefsManager.getInstance(applicationContext)
        launcherPackageName = resolveLauncherPackageName()
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOffReceiver) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == lastForegroundPackage) return
        lastForegroundPackage = packageName

        when {
            packageName == applicationContext.packageName -> return
            packageName == launcherPackageName -> LockSessionManager.clearAll()
            prefsManager.isAppLocked(packageName) && !LockSessionManager.isUnlocked(packageName) -> {
                showLockScreen(packageName)
            }
        }
    }

    private fun showLockScreen(packageName: String) {
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(LockActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        startActivity(intent)
    }

    private fun resolveLauncherPackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName
    }

    override fun onInterrupt() {
        // No-op: required by AccessibilityService.
    }
}
