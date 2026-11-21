// FILE: cookieprime-android-sdk/src/main/java/com/cookieprime/CookiePrime.kt
package com.cookieprime

import android.app.Application
import android.content.Context
import com.cookieprime.consent.ConsentSDK
import com.cookieprime.consent.license.LicenseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import android.util.Log

object CookiePrime {
    
    private var isInitialized = false
    
    @JvmStatic
    fun init(app: Application, licenseKey: String) {
        if (isInitialized) return
        
        Log.d("CookiePrime", "🚀 Starting CookiePrime initialization...")
        
        MainScope().launch(Dispatchers.IO) {
            try {
                ConsentSDK.initializeAndWait(
                    context = app,
                    appId = app.packageName,
                    apiKey = licenseKey,
                    enableDebug = false,
                    offlineMode = false
                )
                Log.d("CookiePrime", "✅ CookiePrime initialized successfully")
            } catch (e: Exception) {
                Log.e("CookiePrime", "❌ CookiePrime initialization failed", e)
            }
        }
        
        isInitialized = true
    }
    
    @JvmStatic
    fun init(app: Application, licenseKey: String, config: CookiePrimeConfig.() -> Unit) {
        if (isInitialized) return
        
        val cookiePrimeConfig = CookiePrimeConfig().apply(config)
        
        Log.d("CookiePrime", "🚀 Starting CookiePrime initialization with config...")
        
        MainScope().launch(Dispatchers.IO) {
            try {
                ConsentSDK.initializeAndWait(
                    context = app,
                    appId = cookiePrimeConfig.appId ?: app.packageName,
                    apiKey = licenseKey,
                    enableDebug = cookiePrimeConfig.enableDebug ?: false,
                    offlineMode = cookiePrimeConfig.offlineMode ?: false,
                    bypassLicenseCheck = cookiePrimeConfig.bypassLicenseCheck ?: false
                )
                Log.d("CookiePrime", "✅ CookiePrime initialized with config successfully")
            } catch (e: Exception) {
                Log.e("CookiePrime", "❌ CookiePrime initialization failed", e)
            }
        }
        
        isInitialized = true
    }
    
    @JvmStatic
    fun initAsync(app: Application, licenseKey: String, onComplete: (Boolean) -> Unit = {}) {
        if (isInitialized) {
            onComplete(true)
            return
        }
        
        MainScope().launch(Dispatchers.IO) {
            try {
                ConsentSDK.initialize(
                    context = app,
                    appId = app.packageName,
                    apiKey = licenseKey,
                    enableDebug = false,
                    offlineMode = false
                )
                isInitialized = true
                onComplete(true)
            } catch (e: Exception) {
                Log.e("CookiePrime", "Async initialization failed", e)
                onComplete(false)
            }
        }
    }
    
    @JvmStatic
    fun isInitialized(): Boolean = isInitialized
    
    @JvmStatic
    fun isConsentGiven(category: String = "analytics"): Boolean {
        return try {
            ConsentSDK.getInstanceOrNull()?.getConsentBooleanMap()?.get(category) ?: false
        } catch (e: Exception) {
            Log.d("CookiePrime", "Consent check failed: ${e.message}")
            false
        }
    }
    
    @JvmStatic
    fun getLicenseInfo(): Map<String, Any> {
        return try {
            val sdk = ConsentSDK.getInstanceOrNull()
            if (sdk != null) {
                val licenseInfo = sdk.getLicenseInfo()
                mapOf<String, Any>(
                    "valid" to LicenseManager.isLicenseValid(),
                    "error" to (LicenseManager.getLicenseError() ?: "none"),
                    "isTrial" to licenseInfo.isTrial,
                    "trialDaysLeft" to licenseInfo.trialDaysLeft,
                    "state" to licenseInfo.state.name,
                    "initialized" to true
                )
            } else {
                mapOf("initialized" to false, "error" to "SDK not initialized")
            }
        } catch (e: Exception) {
            mapOf<String, Any>(
                "error" to (e.message ?: "unknown"),
                "initialized" to false
            )
        }
    }
    
    @JvmStatic
    fun getConsentMap(): Map<String, Boolean> {
        return try {
            ConsentSDK.getConsentMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    @JvmStatic
    fun getInitializationStatus(): Map<String, Any> {
        return try {
            ConsentSDK.getInitializationStatus()
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "unknown"))
        }
    }
    
    @JvmStatic
    fun resetForTesting(_context: Context) {
        try {
            ConsentSDK.getInstanceOrNull()?.resetForTesting()
            Log.d("CookiePrime", "✅ Consent reset for testing")
        } catch (e: Exception) {
            Log.d("CookiePrime", "Reset failed: ${e.message}")
        }
    }
    
    @JvmStatic
    fun showConsentDialog(context: Context) {
        if (context is android.app.Activity) {
            // Use the extension function from ConsentSDK.kt
            MainScope().launch {
                try {
                    ConsentSDK.getInstance().showConsentIfNeeded(context, force = true)
                } catch (e: Exception) {
                    Log.e("CookiePrime", "Failed to show consent dialog", e)
                }
            }
        } else {
            Log.e("CookiePrime", "Context must be an Activity to show consent dialog")
        }
    }
    
    @JvmStatic
    val isAnalyticsConsentGiven: Boolean
        get() = isConsentGiven("analytics")
    
    @JvmStatic
    val isAdvertisingConsentGiven: Boolean
        get() = isConsentGiven("advertising")
    
    @JvmStatic
    val isEssentialConsentGiven: Boolean
        get() = true
}

data class CookiePrimeConfig(
    var appId: String? = null,
    var enableDebug: Boolean? = null,
    var offlineMode: Boolean? = null,
    var bypassLicenseCheck: Boolean? = null,
    var themeResId: Int? = null
)