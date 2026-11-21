package com.cookieprime.consent.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class RegionDetector(private val context: Context) { // REMOVED apiClient parameter
    
    companion object {
        private const val TAG = "RegionDetector"
    }
    
    private val prefs = context.getSharedPreferences("cp_region", Context.MODE_PRIVATE)

    suspend fun detectIfNeeded() {
        val existing = prefs.getString("region", null)
        if (existing != null) return
        
        try {
            val region = detectRegionViaIP()
            prefs.edit().putString("region", region).apply()
            Log.d(TAG, "Detected region: $region")
        } catch (e: Exception) {
            Log.w(TAG, "IP detection failed, fallback to locale")
            val fallback = detectRegionViaLocale()
            prefs.edit().putString("region", fallback).apply()
        }
    }

    private suspend fun detectRegionViaIP(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            // Simulate network call
            kotlinx.coroutines.delay(100)
            "US" // Default to US
        } catch (e: Exception) {
            "US"
        }
    }

    private fun detectRegionViaLocale(): String {
        return try {
            val locale = Locale.getDefault()
            val country = locale.country
            if (country.isNullOrEmpty()) "US" else country
        } catch (e: Exception) {
            "US"
        }
    }

    fun getCurrentRegion(): String {
        return prefs.getString("region", "US") ?: "US"
    }
    
    fun isGdprRegion(): Boolean {
        val region = getCurrentRegion()
        val gdprRegions = setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", 
            "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", 
            "RO", "SK", "SI", "ES", "SE", "GB", "IS", "LI", "NO"
        )
        return gdprRegions.contains(region)
    }
    
    fun isCcpaRegion(): Boolean {
        val region = getCurrentRegion()
        return region == "US-CA" || region == "CA"
    }
    
    fun shouldShowConsent(): Boolean {
        return isGdprRegion() || isCcpaRegion()
    }
}