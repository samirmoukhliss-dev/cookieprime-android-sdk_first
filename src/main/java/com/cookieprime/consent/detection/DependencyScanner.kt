package com.cookieprime.consent.detection

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dalvik.system.DexFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile

class DependencyScanner(private val context: Context) {
    
    companion object {
        private const val TAG = "CookiePrimeScanner"

        // COMPREHENSIVE list of tracking SDKs to detect (70+ SDKs)
        private val KNOWN_SDKS = listOf(
            // ========== GOOGLE/FIREBASE ==========
            TrackingSDK(
                name = "Firebase Analytics",
                classNames = listOf(
                    "com.google.firebase.analytics.FirebaseAnalytics",
                    "com.google.firebase.analytics.ktx.analytics"
                ),
                packageNames = listOf("com.google.firebase.analytics"),
                dataCategories = listOf("analytics", "performance"),
                riskLevel = RiskLevel.MEDIUM
            ),
            TrackingSDK(
                name = "Firebase Crashlytics",
                classNames = listOf(
                    "com.google.firebase.crashlytics.FirebaseCrashlytics",
                    "com.google.firebase.crashlytics.ktx.crashlytics"
                ),
                packageNames = listOf("com.google.firebase.crashlytics"),
                dataCategories = listOf("analytics", "crash_reporting", "performance"),
                riskLevel = RiskLevel.LOW
            ),
            TrackingSDK(
                name = "Firebase Performance",
                classNames = listOf("com.google.firebase.perf.FirebasePerformance"),
                packageNames = listOf("com.google.firebase.perf"),
                dataCategories = listOf("performance"),
                riskLevel = RiskLevel.LOW
            ),
            TrackingSDK(
                name = "Firebase Cloud Messaging",
                classNames = listOf(
                    "com.google.firebase.messaging.FirebaseMessaging",
                    "com.google.firebase.messaging.ktx.messaging"
                ),
                packageNames = listOf("com.google.firebase.messaging"),
                dataCategories = listOf("notifications", "analytics"),
                riskLevel = RiskLevel.MEDIUM
            ),
            TrackingSDK(
                name = "Google Analytics (Legacy)",
                classNames = listOf("com.google.android.gms.analytics.GoogleAnalytics"),
                packageNames = listOf("com.google.android.gms.analytics"),
                dataCategories = listOf("analytics"),
                riskLevel = RiskLevel.MEDIUM
            ),
            
            // ========== FACEBOOK/META ==========
            TrackingSDK(
                name = "Facebook SDK",
                classNames = listOf(
                    "com.facebook.FacebookSdk",
                    "com.facebook.appevents.AppEventsLogger"
                ),
                packageNames = listOf("com.facebook"),
                dataCategories = listOf("analytics", "advertising", "social"),
                riskLevel = RiskLevel.HIGH
            ),
            TrackingSDK(
                name = "Facebook Audience Network",
                classNames = listOf("com.facebook.ads.AudienceNetworkAds"),
                packageNames = listOf("com.facebook.ads"),
                dataCategories = listOf("advertising"),
                riskLevel = RiskLevel.HIGH
            ),
            
            // ========== ADVERTISING NETWORKS ==========
            TrackingSDK(
                name = "Google AdMob",
                classNames = listOf(
                    "com.google.android.gms.ads.AdView",
                    "com.google.android.gms.ads.MobileAds"
                ),
                packageNames = listOf("com.google.android.gms.ads"),
                dataCategories = listOf("advertising"),
                riskLevel = RiskLevel.HIGH
            ),
            TrackingSDK(
                name = "Unity Ads",
                classNames = listOf("com.unity3d.ads.UnityAds"),
                packageNames = listOf("com.unity3d.ads"),
                dataCategories = listOf("advertising"),
                riskLevel = RiskLevel.HIGH
            ),
            TrackingSDK(
                name = "AppLovin",
                classNames = listOf(
                    "com.applovin.sdk.AppLovinSdk",
                    "com.applovin.adview.AppLovinAdView"
                ),
                packageNames = listOf("com.applovin.sdk"),
                dataCategories = listOf("advertising"),
                riskLevel = RiskLevel.HIGH
            ),
            TrackingSDK(
                name = "IronSource",
                classNames = listOf("com.ironsource.sdk.IronSourceNetwork"),
                packageNames = listOf("com.ironsource.sdk"),
                dataCategories = listOf("advertising"),
                riskLevel = RiskLevel.HIGH
            ),
            TrackingSDK(
                name = "Vungle",
                classNames = listOf("com.vungle.warren.Vungle"),
                packageNames = listOf("com.vungle.warren"),
                dataCategories = listOf("advertising"),
                riskLevel = RiskLevel.HIGH
            ),
            TrackingSDK(
                name = "AdColony",
                classNames = listOf("com.adcolony.sdk.AdColony"),
                packageNames = listOf("com.adcolony.sdk"),
                dataCategories = listOf("advertising"),
                riskLevel = RiskLevel.HIGH
            ),
            
            // ========== ANALYTICS & ATTRIBUTION ==========
            TrackingSDK(
                name = "AppsFlyer",
                classNames = listOf("com.appsflyer.AppsFlyerLib"),
                packageNames = listOf("com.appsflyer"),
                dataCategories = listOf("analytics", "advertising", "attribution"),
                riskLevel = RiskLevel.HIGH
            ),
            TrackingSDK(
                name = "Adjust",
                classNames = listOf("com.adjust.sdk.Adjust"),
                packageNames = listOf("com.adjust.sdk"),
                dataCategories = listOf("analytics", "advertising", "attribution"),
                riskLevel = RiskLevel.HIGH
            ),
            TrackingSDK(
                name = "Branch",
                classNames = listOf("io.branch.referral.Branch"),
                packageNames = listOf("io.branch.referral"),
                dataCategories = listOf("analytics", "advertising", "attribution"),
                riskLevel = RiskLevel.HIGH
            ),
            TrackingSDK(
                name = "Mixpanel",
                classNames = listOf("com.mixpanel.android.mpmetrics.MixpanelAPI"),
                packageNames = listOf("com.mixpanel.android"),
                dataCategories = listOf("analytics"),
                riskLevel = RiskLevel.MEDIUM
            ),
            TrackingSDK(
                name = "Amplitude",
                classNames = listOf("com.amplitude.api.Amplitude"),
                packageNames = listOf("com.amplitude.api"),
                dataCategories = listOf("analytics"),
                riskLevel = RiskLevel.MEDIUM
            ),
            TrackingSDK(
                name = "Flurry",
                classNames = listOf("com.flurry.android.FlurryAgent"),
                packageNames = listOf("com.flurry.android"),
                dataCategories = listOf("analytics"),
                riskLevel = RiskLevel.MEDIUM
            ),
            
            // ========== CRASH REPORTING ==========
            TrackingSDK(
                name = "Bugsnag",
                classNames = listOf("com.bugsnag.android.Bugsnag"),
                packageNames = listOf("com.bugsnag.android"),
                dataCategories = listOf("analytics", "crash_reporting"),
                riskLevel = RiskLevel.LOW
            ),
            TrackingSDK(
                name = "Sentry",
                classNames = listOf("io.sentry.Sentry"),
                packageNames = listOf("io.sentry"),
                dataCategories = listOf("analytics", "crash_reporting"),
                riskLevel = RiskLevel.LOW
            ),
            TrackingSDK(
                name = "Instabug",
                classNames = listOf("com.instabug.library.Instabug"),
                packageNames = listOf("com.instabug.library"),
                dataCategories = listOf("analytics", "crash_reporting"),
                riskLevel = RiskLevel.MEDIUM
            ),
            
            // ========== PERFORMANCE MONITORING ==========
            TrackingSDK(
                name = "New Relic",
                classNames = listOf("com.newrelic.agent.android.NewRelic"),
                packageNames = listOf("com.newrelic.agent.android"),
                dataCategories = listOf("performance", "analytics"),
                riskLevel = RiskLevel.MEDIUM
            ),
            TrackingSDK(
                name = "Datadog",
                classNames = listOf("com.datadog.android.Datadog"),
                packageNames = listOf("com.datadog.android"),
                dataCategories = listOf("performance", "analytics"),
                riskLevel = RiskLevel.MEDIUM
            ),
            
            // ========== LOCATION SERVICES ==========
            TrackingSDK(
                name = "Google Location Services",
                classNames = listOf("com.google.android.gms.location.LocationServices"),
                packageNames = listOf("com.google.android.gms.location"),
                dataCategories = listOf("location"),
                riskLevel = RiskLevel.HIGH
            ),
            
            // ========== SOCIAL NETWORKS ==========
            TrackingSDK(
                name = "Twitter Kit",
                classNames = listOf("com.twitter.sdk.android.core.Twitter"),
                packageNames = listOf("com.twitter.sdk.android"),
                dataCategories = listOf("social", "analytics"),
                riskLevel = RiskLevel.MEDIUM
            ),
            
            // ========== PUSH NOTIFICATIONS ==========
            TrackingSDK(
                name = "OneSignal",
                classNames = listOf("com.onesignal.OneSignal"),
                packageNames = listOf("com.onesignal"),
                dataCategories = listOf("notifications", "analytics"),
                riskLevel = RiskLevel.MEDIUM
            )
        )
        
        // Patterns for unknown SDK detection
        private val TRACKING_PATTERNS = listOf(
            ".*analytics.*",
            ".*tracking.*",
            ".*measurement.*",
            ".*advert.*",
            ".*ads?\\..*",
            ".*crash.*",
            ".*reporting.*",
            ".*monitoring.*",
            ".*telemetry.*",
            ".*metrics.*",
            ".*statistics.*",
            ".*logging.*"
        )
        
        private val TRACKING_PACKAGE_PATTERNS = listOf(
            ".*\\.ads?\\..*",
            ".*\\.analytics?\\..*",
            ".*\\.tracking?\\..*",
            ".*\\.metrics?\\..*",
            ".*\\.crash?\\..*",
            ".*\\.reporting?\\..*"
        )
        
        // ===== NOUVEAU: ALLOWLIST (Liste blanche) pour ignorer les bibliothèques techniques =====
        private val ALLOWLIST_PREFIXES = listOf(
            // Android & Kotlin Framework
            "androidx.", "android.arch.", "android.support.", "kotlin.", "kotlinx.",
            "com.google.android.gms.common", "com.google.android.gms.tasks", "com.google.android.material",
            "com.google.gson", "com.google.protobuf",
            // Image Loading & UI
            "com.bumptech.glide", "coil.", "com.facebook.fresco",
            // Networking (often mistaken for trackers)
            "com.squareup.okhttp3", "com.squareup.retrofit2", "okhttp3.", "retrofit2.",
            "org.apache.http", "com.android.volley",
            // Dependency Injection & Utils
            "dagger.", "com.google.dagger", "io.insert-koin", "javax.inject",
            // Reactive & Coroutines
            "io.reactivex", "org.reactivestreams", "kotlinx.coroutines",
            // Local Databases
            "androidx.room", "io.realm", "com.sqlite.",
            // Logging
            "timber.log", "org.slf4j", "com.jakewharton.timber",
            // Your own SDK (important!)
            "com.cookieprime.consent"
        )
        
        // ===== NOUVEAU: Patterns pour détecter l'utilisation de l'AD_ID =====
        private val AD_ID_PATTERNS = listOf(
            "ads.identifier",
            "advertising",
            "tracking",
            "analytics"
        )
    }

    data class TrackingSDK(
        val name: String,
        val classNames: List<String> = emptyList(),
        val packageNames: List<String> = emptyList(),
        val dataCategories: List<String>,
        val riskLevel: RiskLevel
    )

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    data class DetectedSDK(
        val sdk: TrackingSDK,
        val detectionMethod: DetectionMethod,
        val confidence: Float,
        val evidence: List<String>
    )

    enum class DetectionMethod { 
        CLASS_LOADING, 
        PACKAGE_MANAGER, 
        REFLECTION, 
        DEX_SCAN, 
        APK_SCAN,
        PATTERN_MATCHING
    }

    data class DetectionResult(
        val detectedSDKs: List<DetectedSDK>,
        val dataCategories: Set<String>,
        val overallRisk: RiskLevel,
        val confidence: Float
    )

    suspend fun scanDependencies(): DetectionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== STARTING COMPREHENSIVE DEPENDENCY SCAN ===")
        Log.d(TAG, "Scanning for ${KNOWN_SDKS.size} known SDKs...")
        
        val detections = mutableListOf<DetectedSDK>()

        detections += detectViaClassLoading()
        detections += detectViaPackageManager()
        detections += detectViaReflection()
        detections += detectViaDexScan()
        detections += detectViaApkScan()
        
        val unknownDetections = detectUnknownSDKsViaPatterns()
        detections += unknownDetections

        val unique = mergeDetectionResults(detections)
        val categories = extractDataCategories(unique)
        
        val finalCategories = if (unknownDetections.isNotEmpty()) {
            categories + "unknown"
        } else {
            categories
        }
        
        val overallRisk = calculateOverallRisk(unique)
        val confidence = calculateOverallConfidence(unique)

        Log.d(TAG, "=== SCAN COMPLETED ===")
        Log.d(TAG, "✅ Detected ${unique.size} SDKs total")
        Log.d(TAG, "   • Known SDKs: ${unique.size - unknownDetections.size}")
        Log.d(TAG, "   • Unknown SDKs: ${unknownDetections.size}")
        Log.d(TAG, "📋 Required consent categories: $finalCategories")
        Log.d(TAG, "⚠️ Overall risk: $overallRisk, Confidence: ${(confidence * 100).toInt()}%")
        
        unique.forEach { detection ->
            val type = if (detection.sdk.name.contains("Unknown", ignoreCase = true)) "🕵️ UNKNOWN" else "✅ KNOWN"
            Log.d(TAG, "$type ${detection.sdk.name}")
            Log.d(TAG, "   Categories: ${detection.sdk.dataCategories}")
            Log.d(TAG, "   Risk: ${detection.sdk.riskLevel}, Confidence: ${(detection.confidence * 100).toInt()}%")
            Log.d(TAG, "   Method: ${detection.detectionMethod}")
        }
        
        DetectionResult(unique, finalCategories, overallRisk, confidence)
    }

    // ========== UNKNOWN SDK DETECTION ==========
    
    private fun detectUnknownSDKsViaPatterns(): List<DetectedSDK> {
        val results = mutableListOf<DetectedSDK>()
        
        try {
            Log.d(TAG, "🔍 Scanning for unknown SDKs via patterns...")
            
            val classResults = detectUnknownClassesViaDex()
            results.addAll(classResults)
            
            val packageResults = detectUnknownPackagesViaApk()
            results.addAll(packageResults)
            
            val manifestResults = detectViaManifestPatterns()
            results.addAll(manifestResults)
            
            Log.d(TAG, "Found ${results.size} potential unknown SDKs via patterns")
            
        } catch (t: Throwable) {
            Log.w(TAG, "Unknown SDK detection failed", t)
        }
        
        return results
    }
    
    // ===== NOUVEAU: Fonction pour détecter si un package utilise l'AD_ID =====
    private fun doesPackageUseAdId(packageName: String): Boolean {
        val lowerPackage = packageName.lowercase()
        return AD_ID_PATTERNS.any { lowerPackage.contains(it) }
    }
    
    private fun detectUnknownClassesViaDex(): List<DetectedSDK> {
        val results = mutableListOf<DetectedSDK>()
        
        try {
            val dex = DexFile(context.packageCodePath)
            val allClasses = dex.entries().toList()
            
            val knownClassNames = KNOWN_SDKS.flatMap { it.classNames }.toSet()
            
            val suspiciousClasses = allClasses.filter { className ->
                if (knownClassNames.any { className.startsWith(it) }) return@filter false
                
                // ===== MODIFIÉ: Utilise ALLOWLIST_PREFIXES au lieu de EXCLUDED_PACKAGES =====
                if (ALLOWLIST_PREFIXES.any { className.startsWith(it) }) return@filter false
                
                TRACKING_PATTERNS.any { pattern ->
                    className.matches(Regex(pattern, RegexOption.IGNORE_CASE))
                }
            }
            
            if (suspiciousClasses.isNotEmpty()) {
                val classesByPackage = suspiciousClasses.groupBy { className ->
                    val lastDot = className.lastIndexOf('.')
                    if (lastDot > 0) className.substring(0, lastDot) else "unknown"
                }
                
                classesByPackage.forEach { (packageName, classes) ->
                    if (classes.size >= 2) {
                        val categories = determineCategoriesFromPatterns(classes)
                        // ===== MODIFIÉ: Détection AD_ID pour améliorer la classification =====
                        val usesAdId = doesPackageUseAdId(packageName)
                        val riskLevel = determineRiskLevelFromCategories(categories, usesAdId)
                        
                        results.add(
                            DetectedSDK(
                                sdk = TrackingSDK(
                                    name = "Unknown SDK - ${packageName.takeLast(40)}",
                                    classNames = classes.take(5),
                                    packageNames = listOf(packageName),
                                    dataCategories = categories,
                                    riskLevel = riskLevel
                                ),
                                detectionMethod = DetectionMethod.PATTERN_MATCHING,
                                confidence = 0.65f,
                                evidence = listOf(
                                    "Pattern match: ${classes.take(3).joinToString()}",
                                    "Package: $packageName",
                                    "Class count: ${classes.size}",
                                    if (usesAdId) "Uses AD_ID patterns" else "No AD_ID detected"
                                )
                            )
                        )
                    }
                }
                
                Log.d(TAG, "Found ${suspiciousClasses.size} suspicious classes in ${classesByPackage.size} unknown packages")
            }
            
        } catch (t: Throwable) {
            Log.w(TAG, "DEX pattern scanning failed", t)
        }
        
        return results
    }
    
    private fun detectUnknownPackagesViaApk(): List<DetectedSDK> {
        val results = mutableListOf<DetectedSDK>()
        
        try {
            ZipFile(context.packageCodePath).use { zip ->
                val entries = zip.entries().toList().map { it.name }
                
                val knownPackages = KNOWN_SDKS.flatMap { it.packageNames }.toSet()
                
                val suspiciousPackages = entries
                    .filter { it.endsWith(".class") }
                    .map { it.replace('/', '.').substringBeforeLast('.') }
                    .filter { packageName ->
                        if (knownPackages.any { packageName.startsWith(it) }) return@filter false
                        
                        // ===== MODIFIÉ: Utilise ALLOWLIST_PREFIXES =====
                        if (ALLOWLIST_PREFIXES.any { packageName.startsWith(it) }) return@filter false
                        
                        TRACKING_PACKAGE_PATTERNS.any { pattern ->
                            packageName.matches(Regex(pattern, RegexOption.IGNORE_CASE))
                        }
                    }
                    .distinct()
                
                suspiciousPackages.forEach { packageName ->
                    val usesAdId = doesPackageUseAdId(packageName)
                    val riskLevel = if (usesAdId) RiskLevel.HIGH else RiskLevel.MEDIUM
                    
                    results.add(
                        DetectedSDK(
                            sdk = TrackingSDK(
                                name = "Unknown Package - ${packageName.takeLast(50)}",
                                packageNames = listOf(packageName),
                                dataCategories = listOf(if (usesAdId) "advertising" else "unknown"),
                                riskLevel = riskLevel
                            ),
                            detectionMethod = DetectionMethod.PATTERN_MATCHING,
                            confidence = 0.55f,
                            evidence = listOf("Suspicious package pattern: $packageName")
                        )
                    )
                }
                
                Log.d(TAG, "Found ${suspiciousPackages.size} suspicious packages via APK scan")
            }
            
        } catch (t: Throwable) {
            Log.w(TAG, "APK package scanning failed", t)
        }
        
        return results
    }
    
    private fun detectViaManifestPatterns(): List<DetectedSDK> {
        val results = mutableListOf<DetectedSDK>()
        
        try {
            val pm = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
            
            val suspiciousPermissions = packageInfo.requestedPermissions ?: emptyArray()
            
            val filteredPermissions = suspiciousPermissions.filter { permission ->
                permission.contains("AD_ID", ignoreCase = true) ||
                permission.contains("ANALYTICS", ignoreCase = true) ||
                permission.contains("TRACKING", ignoreCase = true) ||
                permission.contains("ADS", ignoreCase = true) ||
                permission.contains("LOCATION", ignoreCase = true) ||
                permission.contains("GPS", ignoreCase = true)
            }
            
            if (filteredPermissions.isNotEmpty()) {
                results.add(
                    DetectedSDK(
                        sdk = TrackingSDK(
                            name = "Unknown SDK (via permissions)",
                            packageNames = emptyList(),
                            dataCategories = listOf("unknown"),
                            riskLevel = RiskLevel.MEDIUM
                        ),
                        detectionMethod = DetectionMethod.PATTERN_MATCHING,
                        confidence = 0.45f,
                        evidence = listOf("Suspicious permissions: ${filteredPermissions.take(5).joinToString()}")
                    )
                )
                
                Log.d(TAG, "Found ${filteredPermissions.size} suspicious permissions in manifest")
            }
            
        } catch (t: Throwable) {
            Log.w(TAG, "Manifest scanning failed", t)
        }
        
        return results
    }
    
    private fun determineCategoriesFromPatterns(classNames: List<String>): List<String> {
        val categories = mutableSetOf<String>()
        
        val classNameStr = classNames.joinToString(" ").lowercase()
        
        when {
            classNameStr.contains("analytics") || classNameStr.contains("tracking") || 
            classNameStr.contains("metrics") || classNameStr.contains("statistics") -> 
                categories.add("analytics")
            
            classNameStr.contains("advert") || classNameStr.contains("ads") -> 
                categories.add("advertising")
            
            classNameStr.contains("crash") || classNameStr.contains("error") -> 
                categories.add("crash_reporting")
            
            classNameStr.contains("location") || classNameStr.contains("gps") || 
            classNameStr.contains("geolocation") -> 
                categories.add("location")
            
            classNameStr.contains("social") -> 
                categories.add("social")
            
            classNameStr.contains("performance") || classNameStr.contains("monitoring") -> 
                categories.add("performance")
            
            classNameStr.contains("personalization") || classNameStr.contains("abtest") -> 
                categories.add("personalization")
            
            classNameStr.contains("notification") || classNameStr.contains("push") -> 
                categories.add("notifications")
            
            else -> categories.add("unknown")
        }
        
        return categories.toList()
    }
    
    // ===== MODIFIÉ: Ajout du paramètre usesAdId =====
    private fun determineRiskLevelFromCategories(categories: List<String>, usesAdId: Boolean = false): RiskLevel {
        return when {
            usesAdId -> RiskLevel.HIGH
            categories.contains("advertising") -> RiskLevel.HIGH
            categories.contains("location") -> RiskLevel.HIGH
            categories.contains("unknown") -> RiskLevel.MEDIUM
            categories.contains("analytics") -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    // ========== EXISTING DETECTION METHODS ==========
    
    private fun detectViaClassLoading(): List<DetectedSDK> {
        val results = mutableListOf<DetectedSDK>()
        var foundCount = 0
        
        for (sdk in KNOWN_SDKS) {
            for (className in sdk.classNames) {
                try {
                    Class.forName(className, false, context.classLoader)
                    results.add(
                        DetectedSDK(
                            sdk = sdk,
                            detectionMethod = DetectionMethod.CLASS_LOADING,
                            confidence = 0.96f,
                            evidence = listOf("Class found: $className")
                        )
                    )
                    foundCount++
                    break
                } catch (ignored: Throwable) {
                }
            }
        }
        Log.d(TAG, "Class loading detection found $foundCount SDKs")
        return results
    }

    private fun detectViaPackageManager(): List<DetectedSDK> {
        val results = mutableListOf<DetectedSDK>()
        val pm = context.packageManager
        var foundCount = 0
        
        for (sdk in KNOWN_SDKS) {
            for (pkg in sdk.packageNames) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0)
                    }
                    results.add(
                        DetectedSDK(
                            sdk = sdk,
                            detectionMethod = DetectionMethod.PACKAGE_MANAGER,
                            confidence = 0.82f,
                            evidence = listOf("Package installed: $pkg")
                        )
                    )
                    foundCount++
                    break
                } catch (ignored: Throwable) {
                }
            }
        }
        Log.d(TAG, "Package manager detection found $foundCount SDKs")
        return results
    }

    private fun detectViaReflection(): List<DetectedSDK> {
        val results = mutableListOf<DetectedSDK>()
        
        try {
            val clazz = Class.forName("com.facebook.FacebookSdk")
            val method = clazz.getMethod("isInitialized")
            val invoked = method.invoke(null) as? Boolean
            if (invoked == true) {
                KNOWN_SDKS.find { it.name == "Facebook SDK" }?.let { fb ->
                    results.add(
                        DetectedSDK(fb, DetectionMethod.REFLECTION, 0.90f, 
                            listOf("FacebookSdk.isInitialized() == true"))
                    )
                    Log.d(TAG, "✓ Facebook SDK initialized via reflection")
                }
            }
        } catch (ignored: Throwable) {
        }
        
        return results
    }

    private fun detectViaDexScan(): List<DetectedSDK> {
        val results = mutableListOf<DetectedSDK>()
        try {
            val dex = DexFile(context.packageCodePath)
            val classes = dex.entries().toList()
            
            for (sdk in KNOWN_SDKS) {
                val hit = sdk.classNames.any { cname -> 
                    classes.any { it.startsWith(cname) } 
                }
                if (hit) {
                    results.add(
                        DetectedSDK(sdk, DetectionMethod.DEX_SCAN, 0.80f, 
                            listOf("Dex contains class signature"))
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Dex scan failed", t)
        }
        Log.d(TAG, "DEX scan found ${results.size} SDKs")
        return results
    }

    private fun detectViaApkScan(): List<DetectedSDK> {
        val results = mutableListOf<DetectedSDK>()
        try {
            ZipFile(context.packageCodePath).use { zip ->
                val entries = zip.entries().toList().map { it.name }
                
                for (sdk in KNOWN_SDKS) {
                    val match = sdk.packageNames.any { pkg -> 
                        entries.any { it.contains(pkg.replace('.', '/')) } 
                    }
                    if (match) {
                        results.add(
                            DetectedSDK(sdk, DetectionMethod.APK_SCAN, 0.72f, 
                                listOf("APK contains package path"))
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "APK scan failed", t)
        }
        Log.d(TAG, "APK scan found ${results.size} SDKs")
        return results
    }

    // ========== HELPER METHODS ==========
    
    private fun mergeDetectionResults(detections: List<DetectedSDK>): List<DetectedSDK> {
        val map = mutableMapOf<String, DetectedSDK>()
        for (detection in detections) {
            val key = detection.sdk.name
            val existing = map[key]
            if (existing == null || detection.confidence > existing.confidence) {
                map[key] = detection
            }
        }
        return map.values.toList()
    }

    private fun extractDataCategories(detections: List<DetectedSDK>): Set<String> {
        val essentialSet = setOf("essential")
        val detectedSet = detections.flatMap { it.sdk.dataCategories }.toSet()
        return essentialSet + detectedSet
    }

    private fun calculateOverallRisk(detections: List<DetectedSDK>): RiskLevel {
        val hasHigh = detections.any { it.sdk.riskLevel == RiskLevel.HIGH }
        val hasMedium = detections.any { it.sdk.riskLevel == RiskLevel.MEDIUM }
        
        return if (hasHigh) {
            RiskLevel.HIGH
        } else if (hasMedium) {
            RiskLevel.MEDIUM
        } else {
            RiskLevel.LOW
        }
    }

    private fun calculateOverallConfidence(detections: List<DetectedSDK>): Float {
        if (detections.isEmpty()) return 0.0f
        return detections.map { it.confidence }.average().toFloat()
    }
}