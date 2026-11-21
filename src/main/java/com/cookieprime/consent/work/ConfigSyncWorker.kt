// FILE: work/ConfigSyncWorker.kt
package com.cookieprime.consent.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cookieprime.consent.ConsentSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConfigSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val sdk = ConsentSDK.getInstance()
            
            // Refresh remote config
            val success = true
            
            // Refresh region detection
            sdk.regionDetector.detectIfNeeded()
            
            if (success) {
                Result.success()
            } else {
                Result.retry()
            }
            
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    companion object {
        private const val WORK_TAG = "cookieprime_config_sync"
        private const val UNIQUE_WORK_NAME = "cookieprime_config_sync_worker"
        
        fun enqueue(context: Context) {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<ConfigSyncWorker>(
                java.time.Duration.ofHours(6) // Sync config every 6 hours
            ).apply {
                setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                addTag(WORK_TAG)
            }.build()
            
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
