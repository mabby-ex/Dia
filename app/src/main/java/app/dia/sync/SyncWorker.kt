package app.dia.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.dia.DiaApp
import java.util.concurrent.TimeUnit

/** Pulls LibreLinkUp readings in the background (Android's minimum period is 15 minutes). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as DiaApp).repository
        val s = repo.settings.current()
        if (!s.autoSync || !s.isLibreConnected) return Result.success()
        // Errors are recorded in settings and shown in the app; don't hammer the API with retries.
        repo.syncLibre()
        return Result.success()
    }

    companion object {
        private const val NAME = "librelinkup-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
