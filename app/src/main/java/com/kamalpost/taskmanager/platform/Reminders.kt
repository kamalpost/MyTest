package com.kamalpost.taskmanager.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kamalpost.taskmanager.data.Task
import com.kamalpost.taskmanager.data.provideTaskStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/** Schedules a due-date notification per task via AlarmManager. */
object ReminderScheduler {

    private const val EXTRA_ID = "task_id"
    private const val EXTRA_NAME = "task_name"

    private fun pending(context: Context, task: Task): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .putExtra(EXTRA_ID, task.id)
                .putExtra(EXTRA_NAME, task.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Schedule, replace, or cancel this task's reminder based on its current state. */
    fun sync(context: Context, task: Task) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, task)
        am.cancel(pi)

        if (task.completed || task.dueAt.isEmpty()) return
        val dueMillis = runCatching { Instant.parse(task.dueAt).toEpochMilli() }.getOrNull() ?: return
        if (dueMillis <= System.currentTimeMillis()) return

        val canExact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueMillis, pi)
        } else {
            // Without the exact-alarm special permission, fire within a 10-minute window
            am.setWindow(AlarmManager.RTC_WAKEUP, dueMillis, 10 * 60 * 1000L, pi)
        }
    }

    fun syncAll(context: Context, tasks: List<Task>) {
        tasks.forEach { sync(context, it) }
    }

    fun taskIdFrom(intent: Intent): String? = intent.getStringExtra(EXTRA_ID)
    fun taskNameFrom(intent: Intent): String = intent.getStringExtra(EXTRA_NAME) ?: "Task"
}

/** Fires at a task's due time and posts the reminder notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = ReminderScheduler.taskIdFrom(intent) ?: return
        Notifications.ensureChannels(context)
        Notifications.taskDue(context, id, ReminderScheduler.taskNameFrom(intent))
    }
}

/** Re-registers all pending reminders after a reboot (alarms don't survive restarts). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val data = provideTaskStore(context.applicationContext).load()
                ReminderScheduler.syncAll(context.applicationContext, data.tasks)
            } finally {
                result.finish()
            }
        }
    }
}
