// FILE: CookiePrimeBuilder.kt
package com.cookieprime.consent

import android.content.Context
import androidx.annotation.Keep
import kotlinx.coroutines.runBlocking
import android.util.Log

@Keep
class CookiePrimeBuilder private constructor() {
    private var context: Context? = null
    private var appId: String? = null
    private var apiKey: String? = null
    private var enableDebug: Boolean = false
    private var offlineMode: Boolean = false  // NEW: Offline mode field
    private var testingMode: Boolean = false
    private var regionOverride: String? = null
    private var themeResId: Int? = null
    private var language: String = "en"
    private var autoShowConsent: Boolean = true
    private var tcfEnabled: Boolean = true
    private var googleConsentModeEnabled: Boolean = true
    
    companion object {
        fun with(context: Context): CookiePrimeBuilder {
            return CookiePrimeBuilder().apply {
                this.context = context.applicationContext
            }
        }
    }
    
    fun appId(appId: String): CookiePrimeBuilder {
        this.appId = appId
        return this
    }
    
    fun apiKey(apiKey: String): CookiePrimeBuilder {
        this.apiKey = apiKey
        return this
    }
    
    fun enableDebug(enable: Boolean): CookiePrimeBuilder {
        this.enableDebug = enable
        return this
    }
    
    // NEW: Offline mode for testing without backend
    fun offlineMode(enabled: Boolean): CookiePrimeBuilder {
        this.offlineMode = enabled
        return this
    }
    
    fun testingMode(enabled: Boolean): CookiePrimeBuilder {
        this.testingMode = enabled
        return this
    }
    
    fun regionOverride(region: String): CookiePrimeBuilder {
        this.regionOverride = region
        return this
    }
    
    fun theme(themeResId: Int): CookiePrimeBuilder {
        this.themeResId = themeResId
        return this
    }
    
    fun language(language: String): CookiePrimeBuilder {
        this.language = language
        return this
    }
    
    fun autoShowConsent(enabled: Boolean): CookiePrimeBuilder {
        this.autoShowConsent = enabled
        return this
    }
    
    fun enableIabTcf(enabled: Boolean): CookiePrimeBuilder {
        this.tcfEnabled = enabled
        return this
    }
    
    fun enableGoogleConsentMode(enabled: Boolean): CookiePrimeBuilder {
        this.googleConsentModeEnabled = enabled
        return this
    }
    
    suspend fun initialize() {
        val context = requireNotNull(context) { "Context is required" }
        val appId = requireNotNull(appId) { "App ID is required" }
        val apiKey = requireNotNull(apiKey) { "API Key is required" }
        
        // Use the updated initialize method with offlineMode parameter
        ConsentSDK.initialize(context, appId, apiKey, enableDebug, offlineMode)
        
        if (offlineMode) {
            Log.d("CookiePrimeBuilder", "📡 OFFLINE MODE ENABLED - Network calls will be skipped")
        }
        
        if (testingMode) {
            // Force show consent on next app launch
            val prefs = context.getSharedPreferences("cookieprime_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("testing_mode", true).apply()
        }
        
        if (themeResId != null) {
            // Store theme for UI components
            val prefs = context.getSharedPreferences("cookieprime_settings", Context.MODE_PRIVATE)
            prefs.edit().putInt("theme_res_id", themeResId!!).apply()
        }
    }
    
    // Blocking version for synchronous initialization
    fun initializeBlocking() = runBlocking {
        initialize()
    }
}