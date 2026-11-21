// FILE: cookieprime-android-sdk/src/main/java/com/cookieprime/consent/ConsentSDK.kt
package com.cookieprime.consent

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.cookieprime.consent.compliance.ComplianceManager
import com.cookieprime.consent.compliance.ConsentMode
import com.cookieprime.consent.configuration.SDKConfigurator
import com.cookieprime.consent.detection.DependencyScanner
import com.cookieprime.consent.license.LicenseManager
import com.cookieprime.consent.license.LicenseValidator
import com.cookieprime.consent.models.ConsentRecord
import com.cookieprime.consent.models.ConsentStatus
import com.cookieprime.consent.models.SdkLicenseInfo
import com.cookieprime.consent.network.ApiClient
import com.cookieprime.consent.network.DetectedSDKDto
import com.cookieprime.consent.network.UploadDetectionRequest
import com.cookieprime.consent.storage.ConsentStorage
import com.cookieprime.consent.storage.RoomConsentStorage
import com.cookieprime.consent.ui.ConsentActivity
import com.cookieprime.consent.utils.DebugOverlay
import com.cookieprime.consent.utils.ProofGenerator
import com.cookieprime.consent.utils.RegionDetector
import com.cookieprime.consent.work.ConsentUploadWorker
import com.cookieprime.consent.work.DetectionUploadWorker
import com.cookieprime.consent.work.ConfigSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

class ConsentSDK private constructor() {
    
    companion object {
        private const val TAG = "ConsentSDK"
        
        // ===== GOOGLE CMP ID =====
        const val GOOGLE_CMP_ID = "dMjJjND"
        const val CMP_VERSION = 2
        // ========================
        
        @Volatile
        private var instance: ConsentSDK? = null
        private val isInitializing = AtomicBoolean(false)

        @MainThread
        suspend fun initialize(
            context: Context,
            appId: String,
            apiKey: String,
            enableDebug: Boolean = false,
            offlineMode: Boolean = false,
            bypassLicenseCheck: Boolean = false
        ) {
            if (instance != null) return
            
            if (isInitializing.compareAndSet(false, true)) {
                try {
                    withContext(Dispatchers.Main) {
                        synchronized(this) {
                            instance ?: ConsentSDK().also { sdk ->
                                sdk.context = context.applicationContext
                                sdk.appId = appId
                                sdk.apiKey = apiKey
                                sdk.enableDebug = enableDebug
                                sdk.offlineMode = offlineMode
                                sdk.bypassLicenseCheck = bypassLicenseCheck
                                
                                // Run configuration check BEFORE initialization
                                if (enableDebug) {
                                    sdk.runConfigurationCheck(context)
                                }
                                
                                // Validate license first
                                val licenseValid = sdk.validateLicense(context, apiKey)
                                if (!licenseValid && !bypassLicenseCheck) {
                                    throw IllegalStateException(
                                        "License validation failed: ${LicenseManager.getLicenseError() ?: "Invalid license key"}"
                                    )
                                }
                                
                                sdk.init()
                                instance = sdk
                            }
                        }
                    }
                } finally {
                    isInitializing.set(false)
                }
            } else {
                // Wait for initialization to complete
                while (instance == null) {
                    delay(10)
                }
            }
        }

        /**
         * One-liner initialization with automatic waiting for scanning completion
         */
        @MainThread
        suspend fun initializeAndWait(
            context: Context,
            appId: String,
            apiKey: String,
            enableDebug: Boolean = false,
            offlineMode: Boolean = false,
            bypassLicenseCheck: Boolean = false,
            timeoutMillis: Long = 5000
        ) {
            // Call existing initialize
            initialize(context, appId, apiKey, enableDebug, offlineMode, bypassLicenseCheck)
            
            // Wait for scanning completion
            getInstance().waitForInitialization(timeoutMillis)
        }

        suspend fun getInstance(): ConsentSDK {
            return instance ?: throw IllegalStateException("ConsentSDK not initialized. Call initialize() first.")
        }

        fun isInitialized(): Boolean = instance != null
        
        fun getInstanceOrNull(): ConsentSDK? = instance
        
        // Check if license is valid
        fun isLicenseValid(): Boolean = LicenseManager.isLicenseValid()
        
        // Get license error if any
        fun getLicenseError(): String? = LicenseManager.getLicenseError()
        
        // ==================== One-Line Helper Methods ====================
        
        fun getConsentStatusQuick(category: String = "analytics"): Boolean {
            return try {
                getInstanceOrNull()?.getConsentBooleanMap()?.get(category) ?: false
            } catch (e: Exception) {
                false
            }
        }
        
        fun getConsentMap(): Map<String, Boolean> {
            return try {
                getInstanceOrNull()?.getConsentBooleanMap() ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
        
        val isAnalyticsConsentGiven: Boolean
            get() = getConsentStatusQuick("analytics")
        
        val isAdvertisingConsentGiven: Boolean
            get() = getConsentStatusQuick("advertising")
        
        val isEssentialConsentGiven: Boolean
            get() = true
            
        fun getInitializationStatus(): Map<String, Any> {
            return mapOf(
                "initialized" to isInitialized(),
                "licenseValid" to isLicenseValid(),
                "licenseError" to (getLicenseError() ?: "none"),
                "hasInstance" to (getInstanceOrNull() != null),
                "scanningComplete" to (getInstanceOrNull()?.initializationComplete?.value ?: false)
            )
        }
        
        fun getCmpInfo(): Map<String, Any> {
            return mapOf(
                "cmp_id" to GOOGLE_CMP_ID,
                "cmp_version" to CMP_VERSION,
                "tcf_version" to "2.2",
                "is_google_certified" to false
            )
        }
    }

    internal lateinit var context: Context
    internal lateinit var appId: String
    internal lateinit var apiKey: String
    internal var enableDebug: Boolean = false
    internal var offlineMode: Boolean = false
    internal var bypassLicenseCheck: Boolean = false

    internal lateinit var apiClient: ApiClient
    internal lateinit var storage: ConsentStorage
    internal lateinit var regionDetector: RegionDetector
    internal lateinit var dependencyScanner: DependencyScanner
    internal lateinit var sdkConfigurator: SDKConfigurator
    internal lateinit var complianceManager: ComplianceManager
    internal lateinit var licenseValidator: LicenseValidator
    
    internal var detectedSDKs: List<DependencyScanner.DetectedSDK> = emptyList()
    internal var requiredCategories: Set<String> = emptySet()
    
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _consentStatusFlow = MutableStateFlow<Map<String, ConsentStatus>>(emptyMap())
    val consentStatusFlow: StateFlow<Map<String, ConsentStatus>> = _consentStatusFlow.asStateFlow()
    
    private val _iabTcfString = MutableStateFlow<String?>(null)
    val iabTcfStringFlow: StateFlow<String?> = _iabTcfString.asStateFlow()
    
    private val _licenseState = MutableStateFlow<LicenseState>(LicenseState.VALIDATING)
    val licenseStateFlow: StateFlow<LicenseState> = _licenseState.asStateFlow()
    
    private val _initializationComplete = MutableStateFlow(false)
    val initializationComplete: StateFlow<Boolean> = _initializationComplete.asStateFlow()
    
    private val isScanning = AtomicBoolean(false)
    
    private val prefs by lazy {
        context.getSharedPreferences("cookieprime_settings_test", Context.MODE_PRIVATE)
    }

    // ===== Configuration Check for Proactive Notifications =====
    private fun runConfigurationCheck(context: Context) {
        if (!enableDebug) return
        
        Log.w(TAG, "🔍 === PROACTIVE CONFIGURATION CHECK ===")
        
        // Check 1: Is SDK initialized in Application class?
        // (Simplified - just log that it's running)
        Log.d(TAG, "✅ SDK initialized - configuration check running")
        
        // Check 2: All required permissions present?
        val hasInternetPermission = try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PERMISSION_GRANTED)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            
            packageInfo.requestedPermissions?.contains("android.permission.INTERNET") == true
        } catch (e: Exception) {
            false
        }
        
        if (!hasInternetPermission) {
            Log.w(TAG, "⚠️ WARNING: INTERNET permission is required for SDK to function properly")
            if (enableDebug) {
                // Show toast on main thread
                Handler(context.mainLooper).post {
                    Toast.makeText(context, "CookiePrime: INTERNET permission missing", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Log.d(TAG, "✅ INTERNET permission present")
        }
        
        // Check 3: Debug mode recommendations
        Log.i(TAG, "ℹ️ TIP: Debug mode enabled - proactive notifications active")
        
        // Check 4: Google signals
        Log.d(TAG, "✅ Google Consent Mode v2 signals will be added automatically")
        
        Log.w(TAG, "🔍 === CONFIGURATION CHECK COMPLETE ===")
    }

    // License validation method
    private fun validateLicense(context: Context, licenseKey: String): Boolean {
        return try {
            licenseValidator = LicenseValidator(context, licenseKey)
            
            val isTrialValid = licenseValidator.isInTrialPeriod()
            
            if (isTrialValid) {
                Log.i(TAG, "✅ SDK is in trial period (30 days)")
                LicenseManager.setLicenseValid(true)
                _licenseState.value = LicenseState.TRIAL
                return true
            }
            
            scope.launch {
                try {
                    val validationResult = licenseValidator.validate()
                    val isValid = validationResult.valid
                    LicenseManager.setLicenseValid(isValid)
                    
                    if (isValid) {
                        _licenseState.value = LicenseState.VALID
                        Log.i(TAG, "✅ License validated successfully")
                    } else {
                        _licenseState.value = LicenseState.INVALID
                        LicenseManager.setLicenseError(validationResult.error)
                        Log.e(TAG, "❌ License validation failed: ${validationResult.error}")
                        
                        if (!bypassLicenseCheck) {
                            showLicenseWarning()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "License validation error, using grace period", e)
                    _licenseState.value = LicenseState.GRACE_PERIOD
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "License validation setup failed", e)
            LicenseManager.setLicenseError(e.message)
            false
        }
    }
    
    private fun showLicenseWarning() {
        if (enableDebug) {
            Log.w(TAG, """
            ⚠️ LICENSE WARNING ⚠️
            Your CookiePrime license appears to be invalid or expired.
            
            Please:
            1. Visit https://cookieprime.com/dashboard
            2. Check your license status
            3. Renew if necessary
            
            Contact: support@cookieprime.com
            """.trimIndent())
        }
    }
    
    private fun checkLicenseOrThrow() {
        if (bypassLicenseCheck) return
        
        if (!LicenseManager.isLicenseValid()) {
            val error = LicenseManager.getLicenseError() ?: "License validation required"
            throw SecurityException("CookiePrime license invalid: $error")
        }
    }

    private fun init() {
        try {
            checkLicenseOrThrow()
            
            apiClient = ApiClient(apiKey, offlineMode)
            storage = RoomConsentStorage(context)
            regionDetector = RegionDetector(context)
            dependencyScanner = DependencyScanner(context)
            sdkConfigurator = SDKConfigurator(context)
            complianceManager = ComplianceManager(context)

            if (offlineMode) {
                Log.d(TAG, "📡 OFFLINE MODE ENABLED - Network calls will be skipped")
            }
            
            Log.d(TAG, "🔑 License State: ${_licenseState.value}")

            if (isScanning.compareAndSet(false, true)) {
                scope.launch {
                    try {
                        Log.d(TAG, "🔍 Starting dependency scanning...")
                        val scanResult = dependencyScanner.scanDependencies()
                        detectedSDKs = scanResult.detectedSDKs
                        requiredCategories = scanResult.dataCategories

                        Log.d(TAG, "✅ Scanning completed - Detected ${detectedSDKs.size} tracking SDKs")
                        Log.d(TAG, "Required consent categories: $requiredCategories")
                        
                        _initializationComplete.value = true

                        if (!offlineMode) {
                            try {
                                val uploadSuccess = apiClient.uploadDetectionResults(
                                    UploadDetectionRequest(
                                        appId = appId,
                                        detectedSDKs = detectedSDKs.map { detectedSDK ->
                                            DetectedSDKDto(
                                                name = detectedSDK.sdk.name,
                                                categories = detectedSDK.sdk.dataCategories,
                                                riskLevel = detectedSDK.sdk.riskLevel.name,
                                                detectionMethod = detectedSDK.detectionMethod.name,
                                                confidence = detectedSDK.confidence,
                                                evidence = detectedSDK.evidence
                                            )
                                        },
                                        dataCategories = requiredCategories.toList(),
                                        overallRisk = scanResult.overallRisk.name,
                                        confidence = scanResult.confidence,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )

                                if (!uploadSuccess) {
                                    DetectionUploadWorker.enqueueImmediate(context)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Detection upload failed, queuing worker", e)
                                DetectionUploadWorker.enqueueImmediate(context)
                            }
                        } else {
                            Log.d(TAG, "📡 OFFLINE MODE: Skipping detection upload")
                        }

                        regionDetector.detectIfNeeded()
                        
                        loadConsentStatus()
                        
                        scheduleAllWorkers()

                        if (storage.getPendingCount() > 0) {
                            ConsentUploadWorker.enqueueImmediate(context)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "SDK initialization failed", e)
                        scheduleAllWorkers()
                        _initializationComplete.value = true
                    } finally {
                        isScanning.set(false)
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Scanning already in progress, skipping duplicate")
                scope.launch {
                    delay(1000)
                    _initializationComplete.value = true
                }
            }

            if (enableDebug) {
                DebugOverlay.install(context)
                Log.i(TAG, "🔧 DEBUG MODE ACTIVE - All warnings will be shown")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "SDK setup failed completely", e)
            _initializationComplete.value = true
        }
    }
    
    suspend fun waitForInitialization(timeoutMillis: Long = 5000): Boolean {
        return try {
            withTimeout(timeoutMillis) {
                initializationComplete.first { it }
            }
            Log.d(TAG, "✅ SDK initialization completed successfully")
            true
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "⚠️ SDK initialization timeout after ${timeoutMillis}ms - proceeding anyway")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for SDK initialization", e)
            false
        }
    }
    
    private suspend fun loadConsentStatus() = withContext(Dispatchers.IO) {
        val statusMap = requiredCategories.associateWith { category ->
            storage.getConsentStatus(category)
        }
        _consentStatusFlow.value = statusMap
    }

    suspend fun showConsentIfNeeded(activity: Activity, force: Boolean = false): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                checkLicenseOrThrow()
                
                if (force || shouldShowConsent()) {
                    showConsent(activity)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show consent flow", e)
                false
            }
        }
    }

    @MainThread
    private fun showConsent(activity: Activity) {
        val intent = ConsentActivity.createIntent(activity, appId)
        activity.startActivity(intent)
    }

    suspend fun shouldShowConsent(): Boolean = withContext(Dispatchers.IO) {
        if (_licenseState.value == LicenseState.INVALID) {
            Log.w(TAG, "License invalid, not showing consent")
            return@withContext false
        }
        
        if (isTestingMode()) {
            return@withContext true
        }
        
        val lastSeen = storage.getLastSeenTimestamp()
        val ttl = 24
        val shouldShow = lastSeen == null || (System.currentTimeMillis() - lastSeen) > ttl * 3600_000L
        return@withContext shouldShow
    }

    suspend fun getConsentStatus(category: String): ConsentStatus = withContext(Dispatchers.IO) {
        return@withContext storage.getConsentStatus(category)
    }

    suspend fun setConsentStatus(category: String, status: ConsentStatus) {
        checkLicenseOrThrow()
        
        val consentMap = mapOf(category to (status == ConsentStatus.GRANTED))
        saveConsent(consentMap, null)
    }

    suspend fun getAllConsentStatuses(): Map<String, ConsentStatus> = withContext(Dispatchers.IO) {
        return@withContext requiredCategories.associateWith { category ->
            storage.getConsentStatus(category)
        }
    }

    fun getConsentBooleanMap(): Map<String, Boolean> {
        return _consentStatusFlow.value.mapValues { (_, status) ->
            status == ConsentStatus.GRANTED
        }
    }
    
    fun isConsentGranted(category: String = "analytics"): Boolean {
        return getConsentBooleanMap()[category] ?: false
    }
    
    val isAnalyticsAllowed: Boolean
        get() = isConsentGranted("analytics")
    
    val isAdvertisingAllowed: Boolean
        get() = isConsentGranted("advertising")
    
    fun needsConsent(): Boolean {
        return getConsentBooleanMap().isEmpty() || 
               getConsentBooleanMap().all { !it.value }
    }

    suspend fun saveConsent(consent: Map<String, Boolean>, actorId: String? = null) = withContext(Dispatchers.IO) {
        try {
            if (_licenseState.value == LicenseState.INVALID) {
                Log.e(TAG, "Cannot save consent: License invalid")
                return@withContext
            }
            
            val googleCompliantConsent = consent.toMutableMap()
            
            val googleKeys = listOf(
                "ad_storage",
                "analytics_storage",
                "ad_user_data",
                "ad_personalization"
            )
            
            googleKeys.forEach { key ->
                if (!googleCompliantConsent.containsKey(key)) {
                    when (key) {
                        "ad_storage" -> googleCompliantConsent[key] = consent["advertising"] ?: false
                        "ad_user_data" -> googleCompliantConsent[key] = consent["advertising"] ?: false
                        "ad_personalization" -> googleCompliantConsent[key] = consent["advertising"] ?: false
                        "analytics_storage" -> googleCompliantConsent[key] = consent["analytics"] ?: false
                    }
                }
            }
            
            Log.d("CookiePrimeGoogle", "✅ Google Consent Mode v2 signals:")
            googleKeys.forEach { key ->
                val status = if (googleCompliantConsent[key] == true) "GRANTED" else "DENIED"
                Log.d("CookiePrimeGoogle", "   $key: $status")
            }
            
            val timestamp = System.currentTimeMillis()
            val json = ConsentJsonConverter.toJson(googleCompliantConsent)
            
            Log.d("CookiePrimeVerify", "🎯 === SAVING CONSENT ===")
            Log.d("CookiePrimeVerify", "📋 Original consent: $consent")
            Log.d("CookiePrimeVerify", "📋 Google-compliant: $googleCompliantConsent")
            Log.d("CookiePrimeVerify", "📄 JSON: $json")
            Log.d("CookiePrimeVerify", "🕒 Timestamp: $timestamp")
            Log.d("CookiePrimeVerify", "📡 Offline mode: $offlineMode")
            
            googleCompliantConsent.forEach { (category, allowed) ->
                val emoji = if (allowed) "✅" else "❌"
                Log.d("CookiePrimeVerify", "   $emoji $category: ${if (allowed) "ALLOWED" else "DENIED"}")
            }
            
            val proofHash = ProofGenerator.hashConsent(json, timestamp, actorId ?: "anonymous")
            val recordId = ProofGenerator.generateId(appId, timestamp, proofHash)
            
            val record = ConsentRecord(
                id = recordId,
                appId = appId,
                timestamp = timestamp,
                actorId = actorId,
                consentJson = json,
                proofHash = proofHash
            )
            
            Log.d("CookiePrimeVerify", "🔐 Proof hash: $proofHash")
            Log.d("CookiePrimeVerify", "🆔 Record ID: $recordId")
            
            try {
                complianceManager.generateTcfString(googleCompliantConsent)
                val tcfString = complianceManager.getTcfString()
                _iabTcfString.value = tcfString
                
                // ===== NOUVEAU: Écriture dans les SharedPreferences par défaut pour IAB =====
                if (!tcfString.isNullOrEmpty()) {
                    complianceManager.writeIabTcfToDefaultSharedPreferences(tcfString)
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate TCF string", e)
            }
            
            storage.saveConsentRecord(record)
            
            val pendingCount = storage.getPendingCount()
            Log.d("CookiePrimeVerify", "💾 Storage stats: $pendingCount pending")
            Log.d("CookiePrimeVerify", "✅ Consent saved to local database")
            
            try {
                sdkConfigurator.configureAllSDKs(googleCompliantConsent)
                Log.d("CookiePrimeVerify", "🛡️ SDKs configured with consent settings")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to configure SDKs", e)
            }
            
            val updatedStatus = googleCompliantConsent.mapValues { (_, granted) ->
                if (granted) ConsentStatus.GRANTED else ConsentStatus.DENIED
            }
            _consentStatusFlow.value = _consentStatusFlow.value + updatedStatus
            
            if (!offlineMode) {
                try {
                    val uploadSuccess = apiClient.uploadConsent(record)
                    
                    if (uploadSuccess) {
                        storage.markRecordSynced(record.id)
                        Log.d(TAG, "Consent saved and uploaded with proof: $proofHash")
                    } else {
                        storage.markSyncAttempt(record.id)
                        ConsentUploadWorker.enqueueImmediate(context)
                        Log.d(TAG, "Consent saved locally, queued for upload")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Consent upload failed, queuing worker", e)
                    storage.markSyncAttempt(record.id)
                    ConsentUploadWorker.enqueueImmediate(context)
                }
            } else {
                Log.d("CookiePrimeVerify", "📡 OFFLINE MODE: Consent saved locally only")
                storage.markSyncAttempt(record.id)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save consent", e)
            if (!offlineMode) {
                ConsentUploadWorker.enqueueImmediate(context)
            }
            Unit
        }
    }

    suspend fun debugPrintStoredConsent() = withContext(Dispatchers.IO) {
        try {
            if (_licenseState.value == LicenseState.INVALID) {
                Log.w(TAG, "Cannot debug: License invalid")
                return@withContext
            }
            
            Log.d("CookiePrimeDebug", "🔍 === CHECKING STORED CONSENT ===")
            
            val pending = storage.getPendingCount()
            
            Log.d("CookiePrimeDebug", "📊 Database stats: $pending pending")
            
            val statuses = getAllConsentStatuses()
            Log.d("CookiePrimeDebug", "🎛️ Current consent statuses: $statuses")
            
            val lastSeen = storage.getLastSeenTimestamp()
            val lastSeenStr = if (lastSeen == null) "Never" else "${(System.currentTimeMillis() - lastSeen) / 1000} seconds ago"
            Log.d("CookiePrimeDebug", "🕒 Last consent seen: $lastSeenStr")
            
            val categories = getRecommendedConsentCategories()
            Log.d("CookiePrimeDebug", "📋 Required categories: ${categories.keys}")
            
            Log.d("CookiePrimeDebug", "🔍 Detected ${detectedSDKs.size} SDKs:")
            detectedSDKs.forEach { sdk ->
                Log.d("CookiePrimeDebug", "   • ${sdk.sdk.name} (${sdk.sdk.riskLevel})")
            }
            
            Log.d("CookiePrimeDebug", "🔑 License State: ${_licenseState.value}")
            
            Log.d("CookiePrimeDebug", "✅ Debug check complete")
            
        } catch (e: Exception) {
            Log.e("CookiePrimeDebug", "❌ Debug check failed", e)
        }
    }

    suspend fun resetConsent() = withContext(Dispatchers.IO) {
        checkLicenseOrThrow()
        
        storage.clearAllConsent()
        requiredCategories = emptySet()
        _consentStatusFlow.value = emptyMap()
        
        try {
            complianceManager.clearTcfString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear TCF string", e)
        }
        
        Log.d(TAG, "Consent data fully reset")
    }

    fun getRecommendedConsentCategories(): Map<String, String> {
        val categories = mutableMapOf<String, String>()
        
        categories["essential"] = "Essential (Required)"
        
        requiredCategories.forEach { category ->
            val label = when (category) {
                "analytics" -> "Analytics & Performance"
                "advertising" -> "Personalized Advertising"
                "social" -> "Social Features"
                "location" -> "Location Services"
                "personalization" -> "Personalized Content"
                "performance" -> "Performance Monitoring"
                "crash_reporting" -> "Crash Reporting"
                "notifications" -> "Push Notifications"
                "attribution" -> "App Attribution"
                "unknown" -> "Other Tracking Technologies"
                else -> category.replaceFirstChar { it.uppercase() }
            }
            categories[category] = label
        }
        
        if (categories.size <= 1) {
            categories.putAll(mapOf(
                "analytics" to "Analytics & Performance",
                "advertising" to "Personalized Advertising",
                "social" to "Social Features",
                "performance" to "Performance Monitoring"
            ))
        }
        
        return categories
    }

    fun observeConsentStatus(lifecycleOwner: LifecycleOwner): Flow<Map<String, ConsentStatus>> {
        return consentStatusFlow
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .distinctUntilChanged()
    }
    
    fun observeConsentForCategory(category: String, lifecycleOwner: LifecycleOwner): Flow<ConsentStatus> {
        return consentStatusFlow
            .map { it[category] ?: ConsentStatus.UNKNOWN }
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .distinctUntilChanged()
    }
    
    fun observeLicenseState(lifecycleOwner: LifecycleOwner): Flow<LicenseState> {
        return licenseStateFlow
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .distinctUntilChanged()
    }
    
    suspend fun getIabTcfString(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            complianceManager.getTcfString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get TCF string", e)
            null
        }
    }
    
    suspend fun setConsentMode(consentMode: ConsentMode) = withContext(Dispatchers.IO) {
        try {
            checkLicenseOrThrow()
            complianceManager.applyConsentMode(consentMode)
            val consentMap = consentMode.toConsentMap()
            saveConsent(consentMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set consent mode", e)
        }
    }
    
    fun getDetectionSummary(): String {
        return """
            Detected ${detectedSDKs.size} Tracking SDKs:
            ${detectedSDKs.joinToString("\n") { "• ${it.sdk.name} (${it.detectionMethod}, ${(it.confidence * 100).toInt()}% confidence)" }}
            
            Required Consent Categories:
            ${requiredCategories.joinToString("\n") { "• $it" }}
            
            Overall Risk: ${getOverallRisk()}
        """.trimIndent()
    }

    suspend fun getSyncStatus(): SyncStatus = withContext(Dispatchers.IO) {
        val pendingCount = storage.getPendingCount()
        val lastSeen = storage.getLastSeenTimestamp()
        
        return@withContext SyncStatus(
            pending = pendingCount,
            lastSync = lastSeen,
            isSyncing = isWorkActive("cookieprime_consent_upload_worker"),
            offlineMode = offlineMode,
            licenseState = _licenseState.value
        )
    }

    data class SyncStatus(
        val pending: Int,
        val lastSync: Long?,
        val isSyncing: Boolean,
        val offlineMode: Boolean = false,
        val licenseState: LicenseState = LicenseState.UNKNOWN
    )

    private fun getOverallRisk(): String {
        val hasHigh = detectedSDKs.any { it.sdk.riskLevel == DependencyScanner.RiskLevel.HIGH }
        val hasMedium = detectedSDKs.any { it.sdk.riskLevel == DependencyScanner.RiskLevel.MEDIUM }
        
        return when {
            hasHigh -> "HIGH"
            hasMedium -> "MEDIUM"
            else -> "LOW"
        }
    }

    private fun scheduleAllWorkers() {
        ConsentUploadWorker.enqueue(context)
        DetectionUploadWorker.enqueue(context)
        ConfigSyncWorker.enqueue(context)
    }

    private fun isWorkActive(workName: String): Boolean {
        return try {
            val workManager = androidx.work.WorkManager.getInstance(context)
            val workInfo = workManager.getWorkInfosForUniqueWork(workName).get()
            workInfo.any { it.state == androidx.work.WorkInfo.State.RUNNING }
        } catch (e: Exception) {
            false
        }
    }
    
    fun isTestingMode(): Boolean {
        return prefs.getBoolean("testing_mode", false)
    }
    
    fun resetForTesting() {
        prefs.edit().remove("consent_last_seen_ts").apply()
        
        context.getSharedPreferences("cookieprime_consent_simple", Context.MODE_PRIVATE)
                .edit().clear().apply()
     
        Log.d(TAG, "SDK reset for testing")
    }
    
    fun destroy() {
        scope.cancel("SDK destroyed")
        instance = null
        LicenseManager.clear()
    }
    
    fun getDetectedSDKs(): List<DependencyScanner.DetectedSDK> {
        if (!_initializationComplete.value) {
            Log.w(TAG, "⚠️ SDK scanning not complete yet. Results may be incomplete.")
        }
        return detectedSDKs
    }
    
    fun getDetectedSDKsSafe(): Flow<List<DependencyScanner.DetectedSDK>> {
        return initializationComplete.map { complete ->
            if (complete) detectedSDKs else emptyList()
        }
    }
    
    fun runConsentProofTest() {
        com.cookieprime.consent.proof.ProofManager.runConsentProofTest(context)
    }
    
    suspend fun generateProofMessage(): String {
        return com.cookieprime.consent.proof.ProofManager.generateProofForUI(context)
    }
   
    fun getLicenseInfo(): SdkLicenseInfo {
        val currentState: LicenseState = _licenseState.value
        val isTrialValue: Boolean = (currentState == LicenseState.TRIAL)
        val trialDays: Int = licenseValidator.getTrialDaysLeft()
        val isValidValue: Boolean = LicenseManager.isLicenseValid()
        val errorMsgValue: String? = LicenseManager.getLicenseError()
    
        return SdkLicenseInfo(
            state = currentState,
            isTrial = isTrialValue,
            trialDaysLeft = trialDays,
            isValid = isValidValue,
            errorMessage = errorMsgValue
        )
    }
   
    suspend fun validateLicenseNow(): Boolean {
        return try {
            val validationResult = licenseValidator.validate()
            val isValid = validationResult?.valid ?: false
            LicenseManager.setLicenseValid(isValid)
            
            if (isValid) {
                _licenseState.value = LicenseState.VALID
            } else {
                _licenseState.value = LicenseState.INVALID
            }
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "License validation error", e)
            false
        }
    }
    
    fun getConsentSummary(): Map<String, Any> {
        val licenseInfo = getLicenseInfo()
        return mapOf<String, Any>(
            "categories" to getConsentBooleanMap(),
            "license_state" to licenseInfo.state.name,
            "is_trial" to licenseInfo.isTrial,
            "trial_days_left" to licenseInfo.trialDaysLeft,
            "is_valid" to licenseInfo.isValid,
            "detectedSDKs" to detectedSDKs.size,
            "needsConsent" to needsConsent(),
            "initialized" to true,
            "scanning_complete" to _initializationComplete.value
        )
    }
    
    fun shouldShowConsentUI(): Boolean {
        return try {
            val hasDecisions = getConsentBooleanMap().isNotEmpty()
            val allDenied = getConsentBooleanMap().all { !it.value }
            
            !hasDecisions || allDenied
        } catch (e: Exception) {
            true
        }
    }
    
    fun getCmpInfo(): Map<String, Any> {
        return mapOf(
            "cmp_id" to GOOGLE_CMP_ID,
            "cmp_version" to CMP_VERSION,
            "tcf_version" to "2.2",
            "is_google_certified" to false
        )
    }
}

enum class LicenseState {
    VALIDATING,
    VALID,
    INVALID,
    TRIAL,
    GRACE_PERIOD,
    UNKNOWN
}

object ConsentJsonConverter {
    private val gson = com.google.gson.Gson()
    
    fun toJson(consent: Map<String, Boolean>): String {
        return gson.toJson(consent)
    }
    
    fun fromJson(json: String): Map<String, Boolean> {
        return try {
            gson.fromJson(json, Map::class.java) as Map<String, Boolean>
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

fun ConsentStatus.isGranted(): Boolean = this == ConsentStatus.GRANTED
fun ConsentStatus.isDenied(): Boolean = this == ConsentStatus.DENIED
fun ConsentStatus.isUnknown(): Boolean = this == ConsentStatus.UNKNOWN

fun Map<String, ConsentStatus>.toBooleanMap(): Map<String, Boolean> {
    return this.mapValues { it.value == ConsentStatus.GRANTED }
}

fun Activity.showCookiePrimeConsent() {
    kotlinx.coroutines.MainScope().launch {
        try {
            ConsentSDK.getInstance().showConsentIfNeeded(this@showCookiePrimeConsent, force = true)
        } catch (e: Exception) {
            android.util.Log.e("CookiePrime", "Failed to show consent", e)
        }
    }
}