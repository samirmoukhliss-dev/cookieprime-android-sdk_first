// FILE: cookieprime-android-sdk/src/main/java/com/cookieprime/consent/license/LicenseValidator.kt
package com.cookieprime.consent.license

import android.content.Context
import android.util.Log
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LicenseValidator(private val context: Context, private val licenseKey: String) {
    
    companion object {
        private const val TAG = "CookiePrimeLicense"
        private const val TRIAL_DAYS = 30
        private const val GRACE_PERIOD_DAYS = 7
        private const val SDK_VERSION = "1.0.0"  // Added SDK version constant
        
        // Production and development endpoints
        private const val PRODUCTION_SERVER = "http://34.14.109.247:5000"
        private const val DEVELOPMENT_SERVER = "http://localhost:8000"
        
        // License key patterns
        private val LICENSE_PATTERN = Regex("^CP-[A-Z0-9]{5}-[A-Z0-9]{5}-[A-Z0-9]{5}$")
        private val TRIAL_PATTERN = Regex("^TRIAL-[A-Z0-9]{8}$")
        private val DEV_PATTERN = Regex("^(DEV|DEVELOPMENT)-[A-Z0-9]{6}$")
        
        // Cache expiration (1 hour)
        private const val CACHE_EXPIRATION_MS = 3600_000L
        
        // Watermark for binary protection
        private const val WATERMARK = "COOKIEPRIME_PROPRIETARY_SDK_DO_NOT_REVERSE_ENGINEER"
    }
    
    // ===== FIX: Create SEPARATE client for license validation =====
    // This client has the interceptor for license validation only
    private val licenseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", "CookiePrime-SDK/${getSDKVersion()}")
                .addHeader("X-Device-ID", getDeviceId())
                .addHeader("X-App-Package", context.packageName)
                .addHeader("X-License-Key", licenseKey)
                .build()
            chain.proceed(request)
        }
        .build()
    
    // This client is for general API calls (NO interceptor)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    // ============================================================
    
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("cookieprime_license", Context.MODE_PRIVATE)
    
    private var cachedValidation: LicenseValidationResult? = null
    private var lastValidationTime = AtomicLong(0)
    private var isValidationInProgress = AtomicBoolean(false)
    
    data class LicenseValidationResult(
        val valid: Boolean,
        val error: String? = null,
        val errorCode: String? = null,
        val license: LicenseInfo? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class LicenseInfo(
        val licenseType: String,
        val maxMau: Int,
        val currentMau: Int,
        val expiresAt: Long,
        val has_analytics: Boolean,
        val has_iab_tcf: Boolean,
        val has_google_consent_mode: Boolean,
        val has_custom_theming: Boolean,
        val has_priority_support: Boolean,
        val watermark: String = WATERMARK
    ) {
        // Helper method to get features as map
        fun getFeaturesMap(): Map<String, Boolean> {
            return mapOf(
                "analytics" to has_analytics,
                "iab_tcf" to has_iab_tcf,
                "google_consent_mode" to has_google_consent_mode,
                "custom_theming" to has_custom_theming,
                "priority_support" to has_priority_support
            )
        }
    }
    
    data class ValidationRequest(
        val licenseKey: String,
        val appPackage: String,
        val sdkVersion: String,
        val deviceId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val signature: String? = null
    )
    
    data class ValidationResponse(
        val valid: Boolean,
        val error: String? = null,
        val errorCode: String? = null,
        val license: LicenseInfo? = null,
        val serverTime: Long,
        val signature: String? = null
    )
    
    /**
     * Validate the license with server
     */
    suspend fun validate(forceRefresh: Boolean = false): LicenseValidationResult = withContext(Dispatchers.IO) {
        // Check if we're currently validating to prevent multiple simultaneous validations
        if (isValidationInProgress.getAndSet(true)) {
            // Wait for current validation to complete
            var attempts = 0
            while (isValidationInProgress.get() && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            
            // Return cached result if available
            cachedValidation?.let {
                if (System.currentTimeMillis() - it.timestamp < CACHE_EXPIRATION_MS) {
                    return@withContext it
                }
            }
        }
        
        try {
            // Check cache first (unless forced)
            if (!forceRefresh) {
                cachedValidation?.let { cached ->
                    if (System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRATION_MS) {
                        Log.d(TAG, "Using cached license validation (${cached.timestamp})")
                        return@withContext cached
                    }
                }
            }
            
            // Check trial period first (local check, no network needed)
            if (isInTrialPeriod()) {
                val trialResult = LicenseValidationResult(
                    valid = true,
                    license = LicenseInfo(
                        licenseType = "trial",
                        maxMau = 1000,
                        currentMau = 0,
                        expiresAt = getTrialExpiryDate(),
                        has_analytics = true,
                        has_iab_tcf = true,
                        has_google_consent_mode = true,
                        has_custom_theming = false,
                        has_priority_support = false
                    )
                )
                cacheValidation(trialResult)
                return@withContext trialResult
            }
            
            // Validate license format locally
            if (!isValidLicenseFormat(licenseKey)) {
                val errorResult = LicenseValidationResult(
                    valid = false,
                    error = "Invalid license key format",
                    errorCode = "INVALID_FORMAT"
                )
                cacheValidation(errorResult)
                return@withContext errorResult
            }
            
            // Check grace period
            if (isInGracePeriod()) {
                Log.w(TAG, "Using grace period (server unreachable)")
                cachedValidation?.let {
                    return@withContext it.copy(
                        valid = true,
                        error = "Using cached validation during grace period"
                    )
                }
            }
            
            // Validate with server
            return@withContext validateWithServer()
            
        } catch (e: Exception) {
            Log.e(TAG, "License validation error", e)
            
            // Fallback to grace period
            if (isInGracePeriod()) {
                cachedValidation?.let {
                    return@withContext it.copy(
                        valid = true,
                        error = "Server error, using grace period: ${e.message}"
                    )
                }
            }
            
            return@withContext LicenseValidationResult(
                valid = false,
                error = "Validation failed: ${e.message}",
                errorCode = "VALIDATION_ERROR"
            )
        } finally {
            isValidationInProgress.set(false)
            lastValidationTime.set(System.currentTimeMillis())
        }
    }
    
    /**
     * Check if device is in trial period (30 days from first install)
     */
    fun isInTrialPeriod(): Boolean {
        val firstInstall = prefs.getLong("first_install", 0L)
        
        if (firstInstall == 0L) {
            // First time - record installation
            prefs.edit().putLong("first_install", System.currentTimeMillis()).apply()
            Log.i(TAG, "📅 Trial period started (30 days)")
            return true
        }
        
        val daysSinceInstall = (System.currentTimeMillis() - firstInstall) / (24 * 3600_000)
        val trialValid = daysSinceInstall < TRIAL_DAYS
        
        if (trialValid) {
            val daysLeft = TRIAL_DAYS - daysSinceInstall.toInt()
            Log.i(TAG, "📅 Trial period active: $daysLeft days left")
        } else {
            Log.w(TAG, "📅 Trial period expired")
        }
        
        return trialValid
    }
    
    /**
     * Get remaining trial days
     */
    fun getTrialDaysLeft(): Int {
        val firstInstall = prefs.getLong("first_install", 0L)
        if (firstInstall == 0L) return TRIAL_DAYS
        
        val daysSinceInstall = (System.currentTimeMillis() - firstInstall) / (24 * 3600_000)
        val daysLeft = TRIAL_DAYS - daysSinceInstall.toInt()
        return maxOf(0, daysLeft)
    }
    
    /**
     * Get trial expiry timestamp
     */
    private fun getTrialExpiryDate(): Long {
        val firstInstall = prefs.getLong("first_install", System.currentTimeMillis())
        return firstInstall + (TRIAL_DAYS * 24 * 3600_000L)
    }
    
    /**
     * Check if we're in grace period (7 days since last successful validation)
     */
    private fun isInGracePeriod(): Boolean {
        val lastSuccess = prefs.getLong("last_successful_validation", 0L)
        if (lastSuccess == 0L) return false
        
        val daysSinceSuccess = (System.currentTimeMillis() - lastSuccess) / (24 * 3600_000)
        return daysSinceSuccess < GRACE_PERIOD_DAYS
    }
    
    /**
     * Validate license key format
     */
    private fun isValidLicenseFormat(key: String): Boolean {
        return when {
            LICENSE_PATTERN.matches(key) -> {
                Log.d(TAG, "✅ Valid commercial license format")
                true
            }
            TRIAL_PATTERN.matches(key) -> {
                Log.d(TAG, "✅ Valid trial license format")
                true
            }
            DEV_PATTERN.matches(key) -> {
                Log.d(TAG, "⚠️ Development license - for testing only")
                true
            }
            key == "DEVELOPMENT" || key == "DEBUG" -> {
                Log.d(TAG, "⚠️ Development mode - bypassing validation")
                true
            }
            else -> {
                Log.e(TAG, "❌ Invalid license key format: $key")
                false
            }
        }
    }
    
    /**
     * Validate license with server
     */
    private suspend fun validateWithServer(): LicenseValidationResult = withTimeout(30_000) {
        try {
            // Determine which server to use
            val baseUrl = when {
                licenseKey.startsWith("DEV") -> DEVELOPMENT_SERVER
                else -> PRODUCTION_SERVER
            }
            
            val url = "$baseUrl/api/v1/licenses/validate"
            
            // Create validation request
            val requestData = ValidationRequest(
                licenseKey = licenseKey,
                appPackage = context.packageName,
                sdkVersion = getSDKVersion(),
                deviceId = getDeviceId(),
                timestamp = System.currentTimeMillis()
            )
            
            val requestBody = gson.toJson(requestData).toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            
            Log.d(TAG, "Validating license with server: $url")
            
            // ===== FIX: Use licenseClient for validation, NOT the shared client =====
            val response = licenseClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "Server returned error: ${response.code} ${response.message}")
                return@withTimeout LicenseValidationResult(
                    valid = false,
                    error = "Server error: ${response.code}",
                    errorCode = "SERVER_ERROR_${response.code}"
                )
            }
            
            if (responseBody.isNullOrEmpty()) {
                return@withTimeout LicenseValidationResult(
                    valid = false,
                    error = "Empty server response",
                    errorCode = "EMPTY_RESPONSE"
                )
            }
            
            // Parse server response
            val validationResponse = try {
                gson.fromJson(responseBody, ValidationResponse::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Failed to parse server response", e)
                return@withTimeout LicenseValidationResult(
                    valid = false,
                    error = "Invalid server response format",
                    errorCode = "INVALID_RESPONSE"
                )
            }
            
            if (validationResponse.valid) {
                // Record successful validation
                prefs.edit().putLong("last_successful_validation", System.currentTimeMillis()).apply()
                Log.i(TAG, "✅ License validated successfully")
            } else {
                Log.e(TAG, "❌ License validation failed: ${validationResponse.error}")
            }
            
            val result = LicenseValidationResult(
                valid = validationResponse.valid,
                error = validationResponse.error,
                errorCode = validationResponse.errorCode,
                license = validationResponse.license,
                timestamp = System.currentTimeMillis()
            )
            
            cacheValidation(result)
            return@withTimeout result
            
        } catch (e: Exception) {
            Log.e(TAG, "Network validation error", e)
            throw e
        }
    }
    
    /**
     * Cache validation result
     */
    private fun cacheValidation(result: LicenseValidationResult) {
        cachedValidation = result
        lastValidationTime.set(System.currentTimeMillis())
        prefs.edit().putLong("last_validation_time", System.currentTimeMillis()).apply()
        
        // Store license info for offline use
        result.license?.let { license ->
            prefs.edit().apply {
                putString("cached_license_type", license.licenseType)
                putInt("cached_max_mau", license.maxMau)
                putLong("cached_expires_at", license.expiresAt)
                putBoolean("cached_has_analytics", license.has_analytics)
                putBoolean("cached_has_iab_tcf", license.has_iab_tcf)
                putBoolean("cached_has_google_consent_mode", license.has_google_consent_mode)
                apply()
            }
        }
    }
    
    /**
     * Get SDK version
     */
    private fun getSDKVersion(): String {
        return SDK_VERSION
    }
    
    /**
     * Generate a stable device ID
     */
    private fun getDeviceId(): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        
        // Create a hash of Android ID + package name
        val input = "$androidId:${context.packageName}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
    
    /**
     * Get cached license info for debugging
     */
    fun getCachedLicenseInfo(): Map<String, Any> {
        return mapOf(
            "license_key" to licenseKey.take(8) + "..." + licenseKey.takeLast(4),
            "app_package" to context.packageName,
            "is_trial" to isInTrialPeriod(),
            "trial_days_left" to getTrialDaysLeft(),
            "last_validation" to formatTimestamp(lastValidationTime.get()),
            "cached_valid" to (cachedValidation?.valid ?: false),
            "in_grace_period" to isInGracePeriod()
        )
    }
    
    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Never"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
    }
    
    /**
     * Force clear cache and revalidate
     */
    suspend fun forceRevalidate(): LicenseValidationResult {
        prefs.edit().remove("last_successful_validation").apply()
        cachedValidation = null
        return validate(forceRefresh = true)
    }
    
    /**
     * Check if license has specific feature
     */
    suspend fun hasFeature(feature: String): Boolean {
        val validation = validate()
        if (!validation.valid || validation.license == null) return false
        
        return when (feature) {
            "analytics" -> validation.license.has_analytics
            "iab_tcf" -> validation.license.has_iab_tcf
            "google_consent_mode" -> validation.license.has_google_consent_mode
            "custom_theming" -> validation.license.has_custom_theming
            "priority_support" -> validation.license.has_priority_support
            else -> false
        }
    }
}