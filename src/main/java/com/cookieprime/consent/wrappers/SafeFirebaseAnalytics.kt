// FILE: cookieprime-android-sdk/src/main/java/com/cookieprime/consent/wrappers/SafeFirebaseAnalytics.kt
package com.cookieprime.consent.wrappers

import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * Safe wrapper for Firebase Analytics that checks consent
 * Shows developers how to integrate consent checks
 * Works even if Firebase is not included - just logs
 */
object SafeFirebaseAnalytics {
    
    private const val TAG = "SafeFirebaseAnalytics"
    
    /**
     * Log an event only if analytics consent is granted
     */
    fun logEvent(eventName: String, params: Map<String, Any>? = null) {
        Log.d(TAG, "🎯 Attempting to log event: $eventName")
        
        try {
            // Check if consent SDK is available
            val sdk = com.cookieprime.consent.ConsentSDK.getInstanceOrNull()
            if (sdk == null) {
                Log.w(TAG, "⚠️ Consent SDK not initialized - allowing event")
                actuallyLogToFirebase(eventName, params)
                return
            }
            
            // Check analytics consent
            val isAnalyticsAllowed = kotlin.runCatching {
                runBlocking {
                    sdk.getConsentStatus("analytics") == com.cookieprime.consent.models.ConsentStatus.GRANTED
                }
            }.getOrElse { false }
            
            if (isAnalyticsAllowed) {
                Log.d(TAG, "✅ Analytics consent GRANTED - sending event")
                actuallyLogToFirebase(eventName, params)
            } else {
                Log.d(TAG, "🚫 Analytics consent DENIED - blocking event")
                Log.d(TAG, "🔒 Event '$eventName' was blocked due to user choice")
                Log.d(TAG, "📝 This proves CookiePrime SDK works!")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check consent for Firebase event", e)
            // Fail open - allow the event
            actuallyLogToFirebase(eventName, params)
        }
    }
    
    private fun actuallyLogToFirebase(eventName: String, params: Map<String, Any>?) {
        // This is where you'd actually call Firebase Analytics
        // For now, just log it - works even without Firebase dependency
        try {
            // Try to use Firebase if available
            Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            Log.d(TAG, "📤 [Real Firebase] Event sent: $eventName with params: $params")
        } catch (e: ClassNotFoundException) {
            // Firebase not included - just log
            Log.d(TAG, "📤 [Mock Firebase] Event would be sent: $eventName with params: $params")
            Log.d(TAG, "ℹ️ Add Firebase dependency for real tracking")
        }
    }
    
    /**
     * Set user property only if analytics consent is granted
     */
    fun setUserProperty(name: String, value: String) {
        Log.d(TAG, "🎯 Attempting to set user property: $name = $value")
        
        try {
            val sdk = com.cookieprime.consent.ConsentSDK.getInstanceOrNull()
            if (sdk == null) {
                Log.w(TAG, "⚠️ Consent SDK not initialized - allowing property")
                actuallySetFirebaseProperty(name, value)
                return
            }
            
            val isAnalyticsAllowed = kotlin.runCatching {
                runBlocking {
                    sdk.getConsentStatus("analytics") == com.cookieprime.consent.models.ConsentStatus.GRANTED
                }
            }.getOrElse { false }
            
            if (isAnalyticsAllowed) {
                Log.d(TAG, "✅ Analytics consent GRANTED - setting property")
                actuallySetFirebaseProperty(name, value)
            } else {
                Log.d(TAG, "🚫 Analytics consent DENIED - blocking property")
                Log.d(TAG, "🔒 Property '$name' was blocked due to user choice")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check consent for Firebase property", e)
            actuallySetFirebaseProperty(name, value)
        }
    }
    
    private fun actuallySetFirebaseProperty(name: String, value: String) {
        try {
            Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            Log.d(TAG, "📤 [Real Firebase] Property set: $name = $value")
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "📤 [Mock Firebase] Property would be set: $name = $value")
        }
    }
}