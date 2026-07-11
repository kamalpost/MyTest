package com.kamalpost.taskmanager.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kamalpost.taskmanager.MainActivity
import com.kamalpost.taskmanager.R

object Notifications {

    const val CHANNEL_TIMER = "timer"
    const val CHANNEL_REMINDERS = "reminders"
    const val TIMER_ONGOING_ID = 1
    const val TIMER_DONE_ID = 2

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIMER, "Pomodoro timer", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Ongoing countdown and completion alerts" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Due-date reminders" }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun timerRunning(context: Context, secondsLeft: Int): android.app.Notification {
        val m = secondsLeft / 60
        val s = secondsLeft % 60
        return NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Pomodoro running")
            .setContentText("%02d:%02d remaining".format(m, s))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .build()
    }

    fun timerDone(context: Context) {
        if (!canPost(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            TIMER_DONE_ID,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⏰ Timer complete!")
                .setContentText("Your pomodoro session has finished.")
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
        )
    }

    fun taskDue(context: Context, taskId: String, taskName: String) {
        if (!canPost(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            taskId.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Task due")
                .setContentText(taskName)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
        )
    }
}
