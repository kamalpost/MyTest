package app.focusphone.focus

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import app.focusphone.data.Prefs

/**
 * Turns "focus mode" on/off for the feature-phone activity:
 *  - Lock task mode (screen pinning; a true kiosk when the app is device owner) keeps
 *    every other app, the launcher, recents and notification shade out of reach.
 *  - Do Not Disturb (when granted) silences all notification pop-ups but still lets calls ring.
 */
object FocusModeController {
    private const val TAG = "FocusMode"

    fun isDeviceOwner(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(ctx.packageName)
    }

    fun hasDndAccess(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun hasOverlayPermission(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun isLockTaskActive(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /** Pin the activity and silence the phone. Idempotent. */
    fun enter(activity: Activity) {
        val prefs = Prefs(activity)
        applyDnd(activity, prefs)
        if (!prefs.kioskEnabled) return
        try {
            if (isDeviceOwner(activity)) {
                val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = FocusAdminReceiver.component(activity)
                val packages = mutableListOf(activity.packageName)
                dialerPackage(activity)?.let { packages.add(it) } // so the in-call screen can show
                dpm.setLockTaskPackages(admin, packages.toTypedArray())
                if (Build.VERSION.SDK_INT >= 28) {
                    dpm.setLockTaskFeatures(
                        admin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD or
                            DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                    )
                }
            }
            if (!isLockTaskActive(activity)) activity.startLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "startLockTask failed: ${e.message}")
        }
    }

    /** Unpin and restore the user's previous interruption settings. */
    fun exit(activity: Activity) {
        try {
            if (isLockTaskActive(activity)) activity.stopLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "stopLockTask failed: ${e.message}")
        }
        restoreDnd(activity)
    }

    /** Temporarily unpin so a foreign screen (in-call UI) can appear; pin again on resume. */
    fun suspendForCall(activity: Activity) {
        if (isDeviceOwner(activity)) return // dialer is whitelisted, nothing to do
        try {
            if (isLockTaskActive(activity)) activity.stopLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "stopLockTask failed: ${e.message}")
        }
    }

    fun dialerPackage(ctx: Context): String? = try {
        (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
    } catch (e: Exception) {
        null
    }

    // ---- Do Not Disturb ----

    private fun applyDnd(ctx: Context, prefs: Prefs) {
        if (!prefs.dndEnabled || !hasDndAccess(ctx) || prefs.dndApplied) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            prefs.savedInterruptionFilter = nm.currentInterruptionFilter
            nm.notificationPolicy?.let {
                prefs.savedPolicyCategories = it.priorityCategories
                prefs.savedPolicyCallSenders = it.priorityCallSenders
                prefs.savedPolicyMessageSenders = it.priorityMessageSenders
                prefs.savedPolicyVisualEffects = it.suppressedVisualEffects
            }
            // Priority mode that still lets phone calls through: a feature phone rings.
            // Everything else is silenced; heads-ups are already hidden by lock task mode.
            nm.notificationPolicy = NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS
            )
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            prefs.dndApplied = true
        } catch (e: Exception) {
            Log.w(TAG, "DND apply failed: ${e.message}")
        }
    }

    fun restoreDnd(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.dndApplied) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            if (hasDndAccess(ctx)) {
                if (prefs.savedPolicyCategories >= 0) {
                    nm.notificationPolicy = NotificationManager.Policy(
                        prefs.savedPolicyCategories,
                        prefs.savedPolicyCallSenders,
                        prefs.savedPolicyMessageSenders,
                        prefs.savedPolicyVisualEffects
                    )
                }
                val f = prefs.savedInterruptionFilter
                nm.setInterruptionFilter(if (f > 0) f else NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {
            Log.w(TAG, "DND restore failed: ${e.message}")
        } finally {
            prefs.dndApplied = false
        }
    }

    // ---- settings deep links used by the setup screen ----

    fun dndSettingsIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun overlaySettingsIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    fun exactAlarmSettingsIntent(ctx: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 31)
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
        else null

    fun batterySettingsIntent(ctx: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
}
