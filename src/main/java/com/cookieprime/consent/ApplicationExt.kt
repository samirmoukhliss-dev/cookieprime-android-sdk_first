// FILE: src/main/java/com/cookieprime/consent/ApplicationExt.kt
package com.cookieprime.consent

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Extension functions for easier integration
 */

/**
 * Initialize CookiePrime with just the license key
 * 
 * @param licenseKey Your CookiePrime license key
 */
fun Application.initCookiePrime(licenseKey: String) {
    MainScope().launch(Dispatchers.IO) {
        ConsentSDK.initialize(
            context = this@initCookiePrime,
            appId = packageName,
            apiKey = licenseKey,
            enableDebug = BuildConfig.DEBUG,
            offlineMode = false
        )
    }
}

/**
 * Initialize CookiePrime with configuration
 */
fun Application.initCookiePrime(licenseKey: String, config: CookiePrimeConfig.() -> Unit) {
    val cookiePrimeConfig = CookiePrimeConfig().apply(config)
    
    MainScope().launch(Dispatchers.IO) {
        ConsentSDK.initialize(
            context = this@initCookiePrime,
            appId = cookiePrimeConfig.appId ?: packageName,
            apiKey = licenseKey,
            enableDebug = cookiePrimeConfig.enableDebug ?: BuildConfig.DEBUG,
            offlineMode = cookiePrimeConfig.offlineMode ?: false
        )
    }
}

/**
 * Simplified configuration DSL
 */
data class CookiePrimeConfig(
    var appId: String? = null,
    var enableDebug: Boolean? = null,
    var offlineMode: Boolean? = null,
    var themeResId: Int? = null
)

/**
 * Quick access to consent status
 */
val Application.isAnalyticsConsentGiven: Boolean
    get() = try {
        ConsentSDK.getInstanceOrNull()?.getConsentBooleanMap()?.get("analytics") ?: false
    } catch (e: Exception) {
        false
    }

/**
 * Quick access to advertising consent
 */
val Application.isAdvertisingConsentGiven: Boolean
    get() = try {
        ConsentSDK.getInstanceOrNull()?.getConsentBooleanMap()?.get("advertising") ?: false
    } catch (e: Exception) {
        false
    }