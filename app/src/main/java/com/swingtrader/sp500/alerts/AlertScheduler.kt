package com.swingtrader.sp500.alerts

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import com.swingtrader.sp500.data.JournalStore
import com.swingtrader.sp500.data.Settings

object AlertScheduler {

    private const val JOB_ID = 1001
    private const val INTERVAL_MS = 30 * 60 * 1000L // JobScheduler floor is 15 min

    /** Schedule the periodic check when there's something to watch, cancel otherwise. */
    fun sync(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val needed = Settings(context).alertsEnabled &&
            JournalStore(context).openPositions().isNotEmpty()
        if (!needed) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, AlertJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(INTERVAL_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }
}
