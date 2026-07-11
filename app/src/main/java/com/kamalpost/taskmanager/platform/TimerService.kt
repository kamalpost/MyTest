package com.kamalpost.taskmanager.platform

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.kamalpost.taskmanager.TimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process (and TimerEngine) alive while a
 * pomodoro runs in the background, mirroring the countdown in a notification.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        startForeground(
            Notifications.TIMER_ONGOING_ID,
            Notifications.timerRunning(this, TimerEngine.state.value.secondsLeft)
        )
        if (watcher == null) {
            watcher = scope.launch {
                TimerEngine.state.collect { st ->
                    if (st.running) {
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(
                            Notifications.TIMER_ONGOING_ID,
                            Notifications.timerRunning(this@TimerService, st.secondsLeft)
                        )
                    } else {
                        if (st.secondsLeft <= 0) Notifications.timerDone(this@TimerService)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, TimerService::class.java))
        }
    }
}
