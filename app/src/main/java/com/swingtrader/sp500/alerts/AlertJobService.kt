package com.swingtrader.sp500.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import com.swingtrader.sp500.MainActivity
import com.swingtrader.sp500.R
import com.swingtrader.sp500.data.JournalStore
import com.swingtrader.sp500.data.Settings
import com.swingtrader.sp500.data.YahooFinanceClient
import com.swingtrader.sp500.ui.Format
import kotlin.concurrent.thread

/**
 * Periodic background check (via JobScheduler) of every open journal position
 * against its frozen ATR stop/target. Fires a notification the first time a
 * level is breached; deduped per position so you're not re-notified every run.
 */
class AlertJobService : JobService() {

    companion object {
        const val CHANNEL_ID = "swing_alerts"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "Price alerts", NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "Stop/target alerts for taken swing trades" }
                )
            }
        }
    }

    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val settings = Settings(applicationContext)
        if (!settings.alertsEnabled) return false
        val journal = JournalStore(applicationContext)
        val positions = journal.openPositions()
        if (positions.isEmpty()) return false

        worker = thread {
            val prefs = getSharedPreferences("alerts_sent", Context.MODE_PRIVATE)
            positions.forEach { pos ->
                try {
                    val h = YahooFinanceClient.fetchDailyHistory(pos.symbol, range = "5d") ?: return@forEach
                    val price = h.close.last()
                    val key = "${pos.symbol}:${pos.openedAt}"
                    when {
                        price <= pos.stop && !prefs.getBoolean("$key:stop", false) -> {
                            notify(
                                pos.symbol.hashCode(),
                                "${pos.symbol} hit its stop",
                                "Price $${Format.price(price)} ≤ stop $${Format.price(pos.stop)} " +
                                    "(entry $${Format.price(pos.entry)}). Review the position."
                            )
                            prefs.edit().putBoolean("$key:stop", true).apply()
                        }
                        price >= pos.target && !prefs.getBoolean("$key:target", false) -> {
                            notify(
                                pos.symbol.hashCode(),
                                "${pos.symbol} hit its target 🎯",
                                "Price $${Format.price(price)} ≥ target $${Format.price(pos.target)} " +
                                    "(entry $${Format.price(pos.entry)}). Consider taking profit."
                            )
                            prefs.edit().putBoolean("$key:target", true).apply()
                        }
                    }
                } catch (_: Exception) {
                    // skip this symbol; next periodic run retries
                }
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        worker?.interrupt()
        return true
    }

    private fun notify(id: Int, title: String, text: String) {
        ensureChannel(this)
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
    }
}
