package com.cookieprime.consent.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cookieprime.consent.ConsentSDK
import com.cookieprime.consent.network.DetectedSDKDto
import com.cookieprime.consent.network.UploadDetectionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DetectionUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("DetectionUploadWorker", "Starting detection upload work")
            
            val sdk = ConsentSDK.getInstance()
            
            // Perform fresh scan
            Log.d("DetectionUploadWorker", "Performing dependency scan...")
            val scanResult = sdk.dependencyScanner.scanDependencies()
            
            Log.d("DetectionUploadWorker", "Scan completed: ${scanResult.detectedSDKs.size} SDKs found")
            
            // Prepare DTOs for upload
            val detectedSDKDtos = scanResult.detectedSDKs.map { detectedSDK ->
                DetectedSDKDto(
                    name = detectedSDK.sdk.name,
                    categories = detectedSDK.sdk.dataCategories,
                    riskLevel = detectedSDK.sdk.riskLevel.name,
                    detectionMethod = detectedSDK.detectionMethod.name,
                    confidence = detectedSDK.confidence,
                    evidence = detectedSDK.evidence
                )
            }
            
            // Create upload request
            val request = UploadDetectionRequest(
                appId = sdk.appId,
                detectedSDKs = detectedSDKDtos,
                dataCategories = scanResult.dataCategories.toList(),
                overallRisk = scanResult.overallRisk.name,
                confidence = scanResult.confidence,
                timestamp = System.currentTimeMillis(),
                deviceInfo = mapOf(
                    "sdkVersion" to "1.0.0",
                    "platform" to "android",
                    "timestamp" to System.currentTimeMillis()
                )
            )
            
            Log.d("DetectionUploadWorker", "Uploading detection results...")
            
            // Upload results
            val success = sdk.apiClient.uploadDetectionResults(request)
            
            if (success) {
                Log.d("DetectionUploadWorker", "Detection results uploaded successfully")
                Result.success()
            } else {
                Log.w("DetectionUploadWorker", "Failed to upload detection results, will retry")
                Result.retry()
            }
            
        } catch (e: Exception) {
            Log.e("DetectionUploadWorker", "Error in detection upload worker", e)
            Result.retry()
        }
    }
    
    companion object {
        private const val WORK_TAG = "cookieprime_detection_upload"
        private const val UNIQUE_WORK_NAME = "cookieprime_detection_upload_worker"
        
        fun enqueue(context: Context) {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<DetectionUploadWorker>(
                java.time.Duration.ofDays(1) // Daily detection upload
            ).apply {
                setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                addTag(WORK_TAG)
                setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    java.time.Duration.ofHours(1)
                )
            }.build()
            
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            
            Log.d("DetectionUploadWorker", "Scheduled daily detection upload")
        }
        
        fun enqueueImmediate(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<DetectionUploadWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    java.time.Duration.ofMinutes(2)
                )
                .addTag(WORK_TAG)
                .build()
            
            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
            
            Log.d("DetectionUploadWorker", "Enqueued immediate detection upload")
        }
    }
}