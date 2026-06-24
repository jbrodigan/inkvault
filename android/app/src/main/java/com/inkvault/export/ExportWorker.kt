package com.inkvault.export

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.inkvault.di.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * Runs the export off the UI/capture path and survives process death (WorkManager). On failure it
 * returns retry → WorkManager re-runs it with exponential backoff, which is exactly the
 * "unreachable endpoint → queue + retry, never drop" behavior the brief asks for.
 */
class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sl = ServiceLocator.from(applicationContext)
        // Missing/!configured target: keep capturing, don't fail-loop. A later capture (or the user
        // configuring the folder) re-enqueues and drains the still-intact outbox.
        val provider = sl.currentStorageProvider() ?: return Result.success()
        return if (sl.exportEngine.exportPending(provider)) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE = "export"

        /** Enqueue a drain. Coalesced so a burst of strokes doesn't spawn redundant jobs. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ExportWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
