package com.cookieprime.consent.compliance

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class ComplianceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ComplianceManager"
        
        // IAB TCF SharedPreferences keys
        private const val IAB_TCF_PREFS = "IABTCF_"
        private const val IAB_TCF_TCSTRING = "IABTCF_TCString"
        private const val IAB_TCF_POLICY_VERSION = "IABTCF_PolicyVersion"
        private const val IAB_TCF_CMP_ID = "IABTCF_CmpId"
        private const val IAB_TCF_CMP_VERSION = "IABTCF_CmpVersion"
        private const val IAB_TCF_VENDOR_CONSENTS = "IABTCF_VendorConsents"
        private const val IAB_TCF_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"
        private const val IAB_TCF_SPECIAL_FEATURES_OPTINS = "IABTCF_SpecialFeaturesOptIns"
        private const val IAB_TCF_PUBLISHER_RESTRICTIONS = "IABTCF_PublisherRestrictions"
        private const val IAB_TCF_PUBLISHER_CC = "IABTCF_PublisherCC"
        private const val IAB_TCF_USE_NON_STANDARD_STACKS = "IABTCF_UseNonStandardStacks"
        
        // Google Consent Mode keys
        private const val GCM_AD_STORAGE = "ad_storage"
        private const val GCM_ANALYTICS_STORAGE = "analytics_storage"
        private const val GCM_AD_USER_DATA = "ad_user_data"
        private const val GCM_AD_PERSONALIZATION = "ad_personalization"
        
        // CookiePrime CMP details
        private const val COOKIEPRIME_CMP_ID = "dMjJjND"
        private const val COOKIEPRIME_CMP_VERSION = 2
        private const val IAB_TCF_POLICY_VERSION_NUMBER = 4
    }
    
    private val gcmPrefs by lazy {
        context.getSharedPreferences("cookieprime_gcm_simple", Context.MODE_PRIVATE)
    }
    
    private val iabPrefs = context.getSharedPreferences("IABTCF", Context.MODE_PRIVATE)
    
    suspend fun initializeIabTcf() = withContext(Dispatchers.IO) {
        try {
            initializeDefaultTcfValues()
            Log.d(TAG, "IAB TCF initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize IAB TCF", e)
        }
    }
    
    private fun initializeDefaultTcfValues() {
        iabPrefs.edit()
            .putString(IAB_TCF_CMP_ID, COOKIEPRIME_CMP_ID)
            .putInt(IAB_TCF_CMP_VERSION, COOKIEPRIME_CMP_VERSION)
            .putInt(IAB_TCF_POLICY_VERSION, IAB_TCF_POLICY_VERSION_NUMBER)
            .putString(IAB_TCF_TCSTRING, "")
            .putString(IAB_TCF_VENDOR_CONSENTS, "")
            .putString(IAB_TCF_PURPOSE_CONSENTS, "0".repeat(10))
            .putString(IAB_TCF_SPECIAL_FEATURES_OPTINS, "0".repeat(2))
            .putString(IAB_TCF_PUBLISHER_RESTRICTIONS, "")
            .putString(IAB_TCF_PUBLISHER_CC, "US")
            .putInt(IAB_TCF_USE_NON_STANDARD_STACKS, 0)
            .apply()
    }
    
    // ===== NOUVEAU: Écriture des clés IAB TCF dans les SharedPreferences par défaut =====
    fun writeIabTcfToDefaultSharedPreferences(tcString: String) {
        try {
            val defaultPrefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            defaultPrefs.edit()
                .putString("IABTCF_TCString", tcString)
                .putInt("IABTCF_gdprApplies", 1)
                .apply()
            
            Log.d(TAG, "✅ IAB TCF keys written to DEFAULT SharedPreferences")
            Log.d(TAG, "   TCString: ${tcString.take(50)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write IAB TCF keys to default prefs", e)
        }
    }
    
    fun generateTcfString(consent: Map<String, Boolean>) {
        try {
            val purposeConsents = mapCookiePrimeToIabPurposes(consent)
            val tcString = buildTcString(purposeConsents)
            
            iabPrefs.edit()
                .putString(IAB_TCF_TCSTRING, tcString)
                .putString(IAB_TCF_PURPOSE_CONSENTS, purposeConsents)
                .apply()
            
            // ===== NOUVEAU: Écrit également dans les préférences par défaut =====
            writeIabTcfToDefaultSharedPreferences(tcString)
            
            Log.d(TAG, "Generated TC String: $tcString")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate TC String", e)
        }
    }
    
    private fun mapCookiePrimeToIabPurposes(consent: Map<String, Boolean>): String {
        val purposes = CharArray(10) { '0' }
        
        consent.forEach { (category, granted) ->
            when (category) {
                "analytics" -> {
                    purposes[0] = if (granted) '1' else '0'
                    purposes[6] = if (granted) '1' else '0'
                }
                "advertising" -> {
                    purposes[1] = if (granted) '1' else '0'
                    purposes[2] = if (granted) '1' else '0'
                    purposes[3] = if (granted) '1' else '0'
                }
                "personalization" -> {
                    purposes[4] = if (granted) '1' else '0'
                    purposes[5] = if (granted) '1' else '0'
                }
            }
        }
        
        return String(purposes)
    }
    
    private fun buildTcString(purposeConsents: String): String {
        val cmpId = iabPrefs.getString(IAB_TCF_CMP_ID, COOKIEPRIME_CMP_ID) ?: COOKIEPRIME_CMP_ID
        val cmpVersion = iabPrefs.getInt(IAB_TCF_CMP_VERSION, COOKIEPRIME_CMP_VERSION)
        val policyVersion = iabPrefs.getInt(IAB_TCF_POLICY_VERSION, IAB_TCF_POLICY_VERSION_NUMBER)
        val publisherCC = iabPrefs.getString(IAB_TCF_PUBLISHER_CC, "US") ?: "US"
        
        return Base64.getUrlEncoder().encodeToString(
            "$cmpId.$cmpVersion.$policyVersion.$purposeConsents.$publisherCC".toByteArray()
        )
    }
    
    fun getTcfString(): String? {
        return iabPrefs.getString(IAB_TCF_TCSTRING, null)
    }
    
    fun clearTcfString() {
        iabPrefs.edit()
            .remove(IAB_TCF_TCSTRING)
            .remove(IAB_TCF_PURPOSE_CONSENTS)
            .apply()
    }
    
    fun applyConsentMode(consentMode: ConsentMode) {
        val consentMap = consentMode.toConsentMap()
        updateGoogleConsentMode(consentMap)
        generateTcfString(consentMap)
    }
    
    private fun updateGoogleConsentMode(consent: Map<String, Boolean>) {
        val analyticsAllowed = consent["analytics"] == true
        val advertisingAllowed = consent["advertising"] == true
        
        try {
            gcmPrefs.edit()
                .putString(GCM_AD_STORAGE, if (advertisingAllowed) "granted" else "denied")
                .putString(GCM_ANALYTICS_STORAGE, if (analyticsAllowed) "granted" else "denied")
                .putString(GCM_AD_USER_DATA, if (advertisingAllowed) "granted" else "denied")
                .putString(GCM_AD_PERSONALIZATION, if (advertisingAllowed) "granted" else "denied")
                .apply()
            
            Log.d(TAG, "Google Consent Mode updated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Google Consent Mode", e)
        }
    }
    
    private fun isTcfRequiredRegion(region: String): Boolean {
        val tcfRegions = setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", 
            "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", 
            "RO", "SK", "SI", "ES", "SE", "GB", "IS", "LI", "NO"
        )
        return tcfRegions.contains(region)
    }
    
    private fun isCcpaRequiredRegion(region: String): Boolean {
        return region == "US-CA" || region == "CA"
    }
    
    private fun updateRegionCompliance(region: String) {
        iabPrefs.edit()
            .putString(IAB_TCF_PUBLISHER_CC, region)
            .apply()
        
        Log.d(TAG, "Compliance updated for region: $region")
        Log.d(TAG, "IAB TCF required: ${isTcfRequiredRegion(region)}")
        Log.d(TAG, "CCPA required: ${isCcpaRequiredRegion(region)}")
    }
    
    fun getGoogleConsentModeParams(): Map<String, String> {
        return try {
            mapOf(
                GCM_AD_STORAGE to (gcmPrefs.getString(GCM_AD_STORAGE, "denied") ?: "denied"),
                GCM_ANALYTICS_STORAGE to (gcmPrefs.getString(GCM_ANALYTICS_STORAGE, "denied") ?: "denied"),
                GCM_AD_USER_DATA to (gcmPrefs.getString(GCM_AD_USER_DATA, "denied") ?: "denied"),
                GCM_AD_PERSONALIZATION to (gcmPrefs.getString(GCM_AD_PERSONALIZATION, "denied") ?: "denied")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Google Consent Mode params", e)
            emptyMap()
        }
    }
    
    fun toConsentMap(): Map<String, Boolean> {
        return try {
            val tcfString = getTcfString() ?: return emptyMap()
            parseTcfString(tcfString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TCF string to consent map", e)
            emptyMap()
        }
    }
    
    private fun parseTcfString(tcfString: String): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        
        tcfString.split("&").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                val key = parts[0]
                val value = parts[1].toIntOrNull() ?: 0
                map[key] = value == 1
            }
        }
        
        return map
    }
}