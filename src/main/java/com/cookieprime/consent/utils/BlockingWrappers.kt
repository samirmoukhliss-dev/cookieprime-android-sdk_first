package com.cookieprime.consent.utils

import com.cookieprime.consent.ConsentSDK
import com.cookieprime.consent.models.ConsentStatus
import kotlinx.coroutines.runBlocking

/**
 * Utility functions for blocking SDK operations based on consent
 * These are synchronous wrappers for convenience in non-coroutine contexts
 */
object BlockingWrappers {
    
    /**
     * Only execute analytics tracking if analytics consent is granted
     * Synchronous blocking version
     */
    fun trackAnalytics(eventName: String, block: () -> Unit) {
        try {
            val sdk = runBlocking { ConsentSDK.getInstanceOrNull() }
            if (sdk != null) {
                val status = runBlocking { sdk.getConsentStatus("analytics") }
                if (status == ConsentStatus.GRANTED) {
                    block()
                }
            }
        } catch (e: Exception) {
            // SDK not initialized or error getting status
            // You might want to log this or handle differently
        }
    }
    
    /**
     * Only execute analytics tracking if analytics consent is granted
     * Asynchronous version
     */
    suspend fun trackAnalyticsSuspend(eventName: String, block: suspend () -> Unit) {
        try {
            val sdk = ConsentSDK.getInstance()
            val status = sdk.getConsentStatus("analytics")
            if (status == ConsentStatus.GRANTED) {
                block()
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    /**
     * Only execute advertising tracking if advertising consent is granted
     * Synchronous blocking version
     */
    fun trackAdvertising(eventName: String, block: () -> Unit) {
        try {
            val sdk = runBlocking { ConsentSDK.getInstanceOrNull() }
            if (sdk != null) {
                val status = runBlocking { sdk.getConsentStatus("advertising") }
                if (status == ConsentStatus.GRANTED) {
                    block()
                }
            }
        } catch (e: Exception) {
            // SDK not initialized or error getting status
        }
    }
    
    /**
     * Only personalize content if personalization consent is granted
     * Synchronous blocking version
     */
    fun personalizeContent(block: () -> Unit) {
        try {
            val sdk = runBlocking { ConsentSDK.getInstanceOrNull() }
            if (sdk != null) {
                val status = runBlocking { sdk.getConsentStatus("personalization") }
                if (status == ConsentStatus.GRANTED) {
                    block()
                }
            }
        } catch (e: Exception) {
            // SDK not initialized or error getting status
        }
    }
    
    /**
     * Only access location if location consent is granted
     * Synchronous blocking version
     */
    fun accessLocation(block: () -> Unit) {
        try {
            val sdk = runBlocking { ConsentSDK.getInstanceOrNull() }
            if (sdk != null) {
                val status = runBlocking { sdk.getConsentStatus("location") }
                if (status == ConsentStatus.GRANTED) {
                    block()
                }
            }
        } catch (e: Exception) {
            // SDK not initialized or error getting status
        }
    }
    
    /**
     * Execute block with specific consent category check
     * Synchronous blocking version
     */
    fun withConsent(category: String, block: () -> Unit) {
        try {
            val sdk = runBlocking { ConsentSDK.getInstanceOrNull() }
            if (sdk != null) {
                val status = runBlocking { sdk.getConsentStatus(category) }
                if (status == ConsentStatus.GRANTED) {
                    block()
                }
            }
        } catch (e: Exception) {
            // SDK not initialized or error getting status
        }
    }
    
    /**
     * Execute block with specific consent category check
     * Asynchronous version
     */
    suspend fun withConsentSuspend(category: String, block: suspend () -> Unit) {
        try {
            val sdk = ConsentSDK.getInstance()
            val status = sdk.getConsentStatus(category)
            if (status == ConsentStatus.GRANTED) {
                block()
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
}