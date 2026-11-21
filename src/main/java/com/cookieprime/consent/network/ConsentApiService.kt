package com.cookieprime.consent.network

// Simple version without Retrofit for now
interface ConsentApiService {
    // We'll implement these later
    suspend fun uploadConsent(request: UploadConsentRequest): Boolean
    suspend fun uploadDetectionResults(request: UploadDetectionRequest): Boolean
    suspend fun getRemoteConfig(appId: String): RemoteConfig?
}

data class UploadConsentRequest(
    val id: String,
    val appId: String,
    val timestamp: Long,
    val actorId: String?,
    val consentJson: String,
    val proofHash: String,
    val platform: String = "android",
    val appVersion: String? = null,
    val sdkVersion: String? = null,
    val deviceId: String? = null,
    val sessionId: String? = null
)

data class UploadDetectionRequest(
    val appId: String,
    val detectedSDKs: List<DetectedSDKDto>,
    val dataCategories: List<String>,
    val overallRisk: String,
    val confidence: Float,
    val timestamp: Long,
    val deviceInfo: Map<String, Any>? = null
)

data class DetectedSDKDto(
    val name: String,
    val categories: List<String>,
    val riskLevel: String,
    val detectionMethod: String,
    val confidence: Float,
    val evidence: List<String>
)

data class RemoteConfig(
    val appId: String,
    val banner: BannerConfig,
    val categories: List<CategoryConfig>,
    val version: String = "1.0"
)

data class BannerConfig(
    val title: String,
    val message: String,
    val style: String,
    val position: String,
    val showCloseButton: Boolean,
    val hideTTLHours: Int
)

data class CategoryConfig(
    val id: String,
    val label: String,
    val description: String,
    val required: Boolean,
    val default: Boolean
)