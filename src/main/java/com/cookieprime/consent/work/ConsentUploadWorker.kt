package com.cookieprime.consent.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cookieprime.consent.ConsentSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConsentUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val sdk = ConsentSDK.getInstance()
            val storage = sdk.storage
            val apiClient = sdk.apiClient
            
            val pendingRecords = storage.getPendingRecords()
            
            if (pendingRecords.isEmpty()) {
                Log.d("ConsentUploadWorker", "No pending records to upload")
                return@withContext Result.success()
            }
            
            var allSuccessful = true
            var anySuccessful = false
            
            Log.d("ConsentUploadWorker", "Uploading ${pendingRecords.size} pending records")
            
            for (record in pendingRecords) {
                try {
                    Log.d("ConsentUploadWorker", "Uploading record: ${record.id}")
                    val success = apiClient.uploadConsent(record)
                    
                    if (success) {
                        storage.markRecordSynced(record.id)
                        anySuccessful = true
                        Log.d("ConsentUploadWorker", "Record ${record.id} uploaded successfully")
                    } else {
                        storage.markSyncAttempt(record.id)
                        allSuccessful = false
                        Log.w("ConsentUploadWorker", "Failed to upload record: ${record.id}")
                    }
                    
                    kotlinx.coroutines.delay(100)
                    
                } catch (e: Exception) {
                    Log.e("ConsentUploadWorker", "Error uploading record ${record.id}", e)
                    storage.markSyncAttempt(record.id)
                    allSuccessful = false
                }
            }
            
            if (anySuccessful) {
                storage.cleanup()
            }
            
            return@withContext when {
                allSuccessful -> {
                    Log.d("ConsentUploadWorker", "All records uploaded successfully")
                    Result.success()
                }
                anySuccessful -> {
                    Log.d("ConsentUploadWorker", "Some records uploaded, retrying for failures")
                    Result.retry()
                }
                else -> {
                    Log.w("ConsentUploadWorker", "All uploads failed, retrying")
                    Result.retry()
                }
            }
            
        } catch (e: Exception) {
            Log.e("ConsentUploadWorker", "Critical failure in worker", e)
            return@withContext Result.retry()
        }
    }
    
    companion object {
        private const val WORK_TAG = "cookieprime_consent_upload"
        private const val UNIQUE_WORK_NAME = "cookieprime_consent_upload_worker"
        
        fun enqueue(context: Context) {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<ConsentUploadWorker>(
                java.time.Duration.ofHours(1)
            ).apply {
                setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                addTag(WORK_TAG)
                setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    java.time.Duration.ofMinutes(5)
                )
            }.build()
            
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
        
        fun enqueueImmediate(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<ConsentUploadWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    java.time.Duration.ofMinutes(1)
                )
                .addTag(WORK_TAG)
                .build()
            
            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
