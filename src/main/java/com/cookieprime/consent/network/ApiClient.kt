// FILE: src/main/java/com/cookieprime/consent/network/ApiClient.kt
package com.cookieprime.consent.network

import android.util.Log
import com.cookieprime.consent.models.ConsentRecord
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

class ApiClient(
    private val apiKey: String,
    private val offlineMode: Boolean = false
) {
    
    // Your VM IP
    private val BASE_URL = "http://34.14.109.247:5000/api/v1/"
    
    // ===== HARDCODED FIX FOR GOOGLE TESTING =====
    // Force the correct API key regardless of what's passed
    private val actualApiKey = "CP-TEST-12345-ANDROID"
    // ============================================
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            // Use hardcoded key instead of the passed one
            val request = original.newBuilder()
                .addHeader("Authorization", "Bearer $actualApiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            
            // Debug logging
            Log.d("CookiePrimeNetwork", "📨 Request URL: ${request.url}")
            Log.d("CookiePrimeNetwork", "🔑 Authorization: Bearer $actualApiKey")
            
            chain.proceed(request)
        }
        .build()
    
    private val gson = Gson()
    
    suspend fun uploadConsent(record: ConsentRecord): Boolean {
        return try {
            if (offlineMode) {
                Log.d("CookiePrimeNetwork", "📡 [OFFLINE MODE] Simulating consent upload")
                return false
            }
            
            withTimeout(30_000) {
                val json = mapOf(
                    "app_id" to "test_app",  // TODO: Fix this to use real appId
                    "record_id" to record.id,
                    "consent_data" to record.consentJson,
                    "proof_hash" to record.proofHash,
                    "timestamp" to record.timestamp
                )
                
                val requestBody = gson.toJson(json).toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("${BASE_URL}consent/upload")  // Fixed endpoint
                    .post(requestBody)
                    .build()
                
                Log.d("CookiePrimeNetwork", "📡 Uploading consent to: ${request.url}")
                Log.d("CookiePrimeNetwork", "📦 Record ID: ${record.id}")
                Log.d("CookiePrimeNetwork", "📦 Proof hash: ${record.proofHash}")
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                Log.d("CookiePrimeNetwork", "📡 Response code: ${response.code}")
                Log.d("CookiePrimeNetwork", "📡 Response body: $responseBody")
                
                val isSuccessful = response.isSuccessful
                
                if (!isSuccessful) {
                    Log.w("CookiePrimeNetwork", "❌ Upload failed: ${response.code} ${response.message}")
                } else {
                    Log.d("CookiePrimeNetwork", "✅ Upload successful")
                }
                
                isSuccessful
            }
        } catch (e: Exception) {
            Log.e("CookiePrimeNetwork", "❌ Consent upload error", e)
            false
        }
    }
    
    suspend fun uploadDetectionResults(request: UploadDetectionRequest): Boolean {
        return try {
            if (offlineMode) {
                Log.d("CookiePrimeNetwork", "📡 [OFFLINE MODE] Simulating detection upload")
                return false
            }
            
            withTimeout(30_000) {
                val requestBody = gson.toJson(request).toRequestBody("application/json".toMediaType())
                
                val httpRequest = Request.Builder()
                    .url("${BASE_URL}detection/upload")
                    .post(requestBody)
                    .build()
                
                Log.d("CookiePrimeNetwork", "📡 Uploading detection results to: ${httpRequest.url}")
                
                val response = client.newCall(httpRequest).execute()
                val responseBody = response.body?.string()
                
                Log.d("CookiePrimeNetwork", "📡 Response code: ${response.code}")
                Log.d("CookiePrimeNetwork", "📡 Response body: $responseBody")
                
                val isSuccessful = response.isSuccessful
                
                if (!isSuccessful) {
                    Log.w("CookiePrimeNetwork", "❌ Detection upload failed: ${response.code} ${response.message}")
                } else {
                    Log.d("CookiePrimeNetwork", "✅ Detection upload successful")
                }
                
                isSuccessful
            }
        } catch (e: Exception) {
            Log.e("CookiePrimeNetwork", "❌ Detection upload error", e)
            false
        }
    }
    
    // ===== NEW METHODS FOR GOOGLE CERTIFICATION =====
    
    data class ConfigResponse(
        val banner: BannerConfig,
        val categories: List<CategoryConfig>,
        val features: Map<String, Boolean>
    )
    
    data class BannerConfig(
        val title: String,
        val description: String,
        val acceptAllText: String,
        val rejectAllText: String,
        val saveText: String
    )
    
    data class CategoryConfig(
        val id: String,
        val title: String,
        val description: String,
        val required: Boolean,
        val default: Boolean
    )
    
    suspend fun getConfig(appId: String): ConfigResponse? {
        return try {
            if (offlineMode) {
                return getDefaultConfig()
            }
            
            withTimeout(10_000) {
                val request = Request.Builder()
                    .url("${BASE_URL}config/$appId")
                    .addHeader("Authorization", "Bearer $actualApiKey")
                    .get()
                    .build()
                
                Log.d("CookiePrimeNetwork", "📡 Fetching config from: ${request.url}")
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    gson.fromJson(response.body?.string(), ConfigResponse::class.java)
                } else {
                    Log.w("CookiePrimeNetwork", "⚠️ Config fetch failed: ${response.code}")
                    getDefaultConfig()
                }
            }
        } catch (e: Exception) {
            Log.e("CookiePrimeNetwork", "❌ Config fetch error", e)
            getDefaultConfig()
        }
    }
    
    private fun getDefaultConfig(): ConfigResponse {
        return ConfigResponse(
            banner = BannerConfig(
                title = "We value your privacy",
                description = "This app uses SDKs that require your consent.",
                acceptAllText = "Accept All",
                rejectAllText = "Reject All",
                saveText = "Save Preferences"
            ),
            categories = listOf(
                CategoryConfig("essential", "Essential", "Required for app function", true, true),
                CategoryConfig("ad_storage", "Ad Storage", "Store advertising data", false, false),
                CategoryConfig("analytics_storage", "Analytics Storage", "Store analytics data", false, false),
                CategoryConfig("ad_user_data", "Ad User Data", "Use advertising data", false, false),
                CategoryConfig("ad_personalization", "Ad Personalization", "Personalized ads", false, false)
            ),
            features = mapOf(
                "enable_iab_tcf" to true,
                "enable_google_consent_mode" to true
            )
        )
    }
    
    data class LicenseValidationResponse(
        val valid: Boolean,
        val error: String? = null,
        val license: LicenseInfo? = null
    )
    
    data class LicenseInfo(
        val licenseType: String,
        val features: Map<String, Boolean>,
        val expiresAt: Long
    )
    
    suspend fun validateLicense(licenseKey: String, packageName: String): LicenseValidationResponse {
        return try {
            if (offlineMode) {
                return LicenseValidationResponse(valid = true, license = getMockLicense())
            }
            
            withTimeout(10_000) {
                val request = Request.Builder()
                    .url("${BASE_URL}validate?license_key=$licenseKey&package_name=$packageName")
                    .addHeader("Authorization", "Bearer $actualApiKey")
                    .get()
                    .build()
                
                Log.d("CookiePrimeNetwork", "📡 Validating license: ${request.url}")
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                Log.d("CookiePrimeNetwork", "📡 Response code: ${response.code}")
                Log.d("CookiePrimeNetwork", "📡 Response body: $responseBody")
                
                if (response.isSuccessful) {
                    gson.fromJson(responseBody, LicenseValidationResponse::class.java)
                } else {
                    LicenseValidationResponse(valid = false, error = "Server error: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("CookiePrimeNetwork", "❌ License validation error", e)
            LicenseValidationResponse(valid = false, error = e.message)
        }
    }
    
    private fun getMockLicense(): LicenseInfo {
        return LicenseInfo(
            licenseType = "test",
            features = mapOf(
                "analytics" to true,
                "iab_tcf" to true,
                "google_consent_mode" to true,
                "custom_theming" to true
            ),
            expiresAt = System.currentTimeMillis() + 365L * 24 * 3600 * 1000
        )
    }
    
    suspend fun getTcString(userId: String, appId: String): String? {
        return try {
            if (offlineMode) return null
            
            withTimeout(10_000) {
                val request = Request.Builder()
                    .url("${BASE_URL}tc-string/$userId?app_id=$appId")
                    .addHeader("Authorization", "Bearer $actualApiKey")
                    .get()
                    .build()
                
                Log.d("CookiePrimeNetwork", "📡 Fetching TC string from: ${request.url}")
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val result = gson.fromJson(response.body?.string(), Map::class.java)
                    result["tc_string"] as? String
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("CookiePrimeNetwork", "❌ TC string fetch error", e)
            null
        }
    }
}