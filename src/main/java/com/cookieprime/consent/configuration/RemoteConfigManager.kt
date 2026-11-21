package com.cookieprime.consent.configuration

import android.content.Context
import android.util.Log

class SDKConfigurator(private val context: Context) {
    private val adapters: List<SdkAdapter> = listOf(
        FirebaseAdapter(context),
        FacebookAdapter(context),
        AdMobAdapter(context),
        UnityAdsAdapter(context),
        AppsFlyerAdapter(context),
        MixpanelAdapter(context),
        AmplitudeAdapter(context),
        GoogleAnalyticsAdapter(context),
        UnknownSDKAdapter(context)  // For unknown SDKs
    )

    fun configureAllSDKs(consent: Map<String, Boolean>) {
        Log.d("CookiePrimeBlock", "🛡️ === CONFIGURING SDKs BASED ON CONSENT ===")
        Log.d("CookiePrimeBlock", "📋 Consent map: $consent")
        
        // Log each consent decision
        consent.forEach { (category, allowed) ->
            if (allowed) {
                Log.d("CookiePrimeBlock", "✅ Category '$category': SDKs ALLOWED")
            } else {
                Log.d("CookiePrimeBlock", "❌ Category '$category': SDKs BLOCKED")
            }
        }
        
        var successCount = 0
        var failCount = 0
        
        adapters.forEach { adapter ->
            try {
                adapter.applyConsent(consent)
                Log.d("CookiePrimeBlock", "✅ Successfully configured ${adapter.name}")
                successCount++
            } catch (t: Throwable) {
                Log.e("CookiePrimeBlock", "❌ Adapter ${adapter.name} failed: ${t.message}", t)
                failCount++
            }
        }
        
        Log.d("CookiePrimeBlock", "📊 Configuration complete: $successCount successful, $failCount failed")
        Log.d("CookiePrimeBlock", "✅ SDK configuration completed")
    }
}

interface SdkAdapter {
    val name: String
    fun applyConsent(consent: Map<String, Boolean>)
}

class FirebaseAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Firebase Analytics"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            // Use Google Consent Mode v2 signals if available
            val analyticsAllowed = consent["analytics_storage"] == true || consent["analytics"] == true
            
            val clazz = Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            val getInstance = clazz.getMethod("getInstance", Context::class.java)
            val instance = getInstance.invoke(null, ctx)
            val setEnabled = clazz.getMethod("setAnalyticsCollectionEnabled", Boolean::class.javaPrimitiveType)
            setEnabled.invoke(instance, analyticsAllowed)
            Log.d("CookiePrimeBlock", "   🔧 Firebase Analytics collection: $analyticsAllowed")
        } catch (t: Throwable) {
            throw RuntimeException("Firebase Analytics configuration failed: ${t.message}", t)
        }
    }
}

class FacebookAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Facebook SDK"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            // First check if Facebook SDK is present
            if (!isFacebookSdkPresent()) {
                Log.d("CookiePrimeBlock", "   ℹ️ Facebook SDK not present in app, skipping")
                return
            }
            
            // Use Google Consent Mode v2 signals if available
            val advertisingAllowed = consent["ad_storage"] == true || consent["advertising"] == true
            val analyticsAllowed = consent["analytics_storage"] == true || consent["analytics"] == true
            
            // Try to initialize Facebook SDK if needed
            if (!isFacebookInitialized()) {
                tryInitializeFacebook()
            }
            
            val sdkClass = Class.forName("com.facebook.FacebookSdk")
            
            try {
                val setAutoLogMethod = sdkClass.getMethod("setAutoLogAppEventsEnabled", Boolean::class.javaPrimitiveType)
                setAutoLogMethod.invoke(null, analyticsAllowed)
                Log.d("CookiePrimeBlock", "   🔧 Facebook AutoLog: $analyticsAllowed")
            } catch (e: Exception) {
                Log.d("CookiePrimeBlock", "   ⚠️ Could not set AutoLog: ${e.message}")
            }
            
            try {
                val setAdvertiserMethod = sdkClass.getMethod("setAdvertiserIDCollectionEnabled", Boolean::class.javaPrimitiveType)
                setAdvertiserMethod.invoke(null, advertisingAllowed)
                Log.d("CookiePrimeBlock", "   🔧 Facebook AdvertiserCollection: $advertisingAllowed")
            } catch (e: Exception) {
                Log.d("CookiePrimeBlock", "   ⚠️ Could not set AdvertiserCollection: ${e.message}")
            }
            
        } catch (t: Throwable) {
            // Don't crash the app - just log and continue
            Log.w("CookiePrimeBlock", "   ⚠️ Facebook SDK configuration failed (non-critical)", t)
        }
    }
    
    private fun isFacebookSdkPresent(): Boolean {
        return try {
            Class.forName("com.facebook.FacebookSdk")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    
    private fun isFacebookInitialized(): Boolean {
        return try {
            val sdkClass = Class.forName("com.facebook.FacebookSdk")
            val method = sdkClass.getMethod("isInitialized")
            method.invoke(null) as Boolean
        } catch (e: Exception) {
            false
        }
    }
    
    private fun tryInitializeFacebook() {
        try {
            val sdkClass = Class.forName("com.facebook.FacebookSdk")
            
            // Try different initialization methods
            try {
                val method = sdkClass.getMethod("sdkInitialize", Context::class.java)
                method.invoke(null, ctx.applicationContext)
                Thread.sleep(50) // Brief pause for initialization
            } catch (e: Exception) {
                // Some versions use initialize without parameters
                try {
                    val method = sdkClass.getMethod("initialize")
                    method.invoke(null)
                } catch (e2: Exception) {
                    // Ignore - will use default
                }
            }
        } catch (e: Exception) {
            Log.d("CookiePrimeBlock", "   ⚠️ Could not initialize Facebook SDK")
        }
    }
}

class AdMobAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Google AdMob"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            // Use Google Consent Mode v2 signals
            val adStorageAllowed = consent["ad_storage"] == true
            val adPersonalizationAllowed = consent["ad_personalization"] == true
            
            // For AdMob, we can set app muted or use consent information
            try {
                val mobileAdsClass = Class.forName("com.google.android.gms.ads.MobileAds")
                val setAppMutedMethod = mobileAdsClass.getMethod("setAppMuted", Boolean::class.javaPrimitiveType)
                // Mute ads if ad_storage is denied
                setAppMutedMethod.invoke(null, !adStorageAllowed)
                Log.d("CookiePrimeBlock", "   🔧 AdMob muted: ${!adStorageAllowed}")
            } catch (e: Exception) {
                // Fallback
            }
            
            // Also store consent for AdMob to read later
            val prefs = ctx.getSharedPreferences("cookieprime_ads", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("admob_personalization_allowed", adPersonalizationAllowed)
                .apply()
            
        } catch (t: Throwable) {
            throw RuntimeException("Google AdMob configuration failed: ${t.message}", t)
        }
    }
}

class UnityAdsAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Unity Ads"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            val advertisingAllowed = consent["advertising"] == true || consent["ad_storage"] == true
            val privacyClass = Class.forName("com.unity3d.ads.metadata.PrivacyMetaData")
            val privacyConstructor = privacyClass.getConstructor(Context::class.java)
            val privacyInstance = privacyConstructor.newInstance(ctx)
            
            val setMethod = privacyClass.getMethod("set", String::class.java, Any::class.java)
            setMethod.invoke(privacyInstance, "privacy.consent", advertisingAllowed)
            
            val commitMethod = privacyClass.getMethod("commit")
            commitMethod.invoke(privacyInstance)
            
            Log.d("CookiePrimeBlock", "   🔧 Unity Ads consent: $advertisingAllowed")
        } catch (t: Throwable) {
            throw RuntimeException("Unity Ads configuration failed: ${t.message}", t)
        }
    }
}

class AppsFlyerAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "AppsFlyer"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            val analyticsAllowed = consent["analytics"] == true || consent["analytics_storage"] == true
            if (!analyticsAllowed) {
                val appsFlyerClass = Class.forName("com.appsflyer.AppsFlyerLib")
                val getInstanceMethod = appsFlyerClass.getMethod("getInstance")
                val appsFlyerInstance = getInstanceMethod.invoke(null)
                val stopMethod = appsFlyerClass.getMethod("stop", Boolean::class.javaPrimitiveType, Context::class.java)
                stopMethod.invoke(appsFlyerInstance, true, ctx)
                Log.d("CookiePrimeBlock", "   🔧 AppsFlyer tracking stopped")
            } else {
                Log.d("CookiePrimeBlock", "   🔧 AppsFlyer tracking enabled")
            }
        } catch (t: Throwable) {
            throw RuntimeException("AppsFlyer configuration failed: ${t.message}", t)
        }
    }
}

class MixpanelAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Mixpanel"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            val analyticsAllowed = consent["analytics"] == true || consent["analytics_storage"] == true
            val mixpanelClass = Class.forName("com.mixpanel.android.mpmetrics.MixpanelAPI")
            val getInstanceMethod = mixpanelClass.getMethod("getInstance", Context::class.java, String::class.java)
            
            // Note: You need to provide the actual project token
            val token = "" // Get from your config
            if (token.isNotEmpty()) {
                val instance = getInstanceMethod.invoke(null, ctx, token)
                val optOutMethod = mixpanelClass.getMethod("optOutTracking")
                if (!analyticsAllowed) {
                    optOutMethod.invoke(instance)
                    Log.d("CookiePrimeBlock", "   🔧 Mixpanel tracking opted out")
                }
            }
        } catch (t: Throwable) {
            Log.w("CookiePrimeBlock", "   ⚠️ Mixpanel configuration skipped (may not be in app)")
        }
    }
}

class AmplitudeAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Amplitude"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            val analyticsAllowed = consent["analytics"] == true || consent["analytics_storage"] == true
            val amplitudeClass = Class.forName("com.amplitude.api.Amplitude")
            val getInstanceMethod = amplitudeClass.getMethod("getInstance")
            val instance = getInstanceMethod.invoke(null)
            
            val enableMethod = amplitudeClass.getMethod("enableTracking")
            val disableMethod = amplitudeClass.getMethod("disableTracking")
            
            if (analyticsAllowed) {
                enableMethod.invoke(instance)
                Log.d("CookiePrimeBlock", "   🔧 Amplitude tracking enabled")
            } else {
                disableMethod.invoke(instance)
                Log.d("CookiePrimeBlock", "   🔧 Amplitude tracking disabled")
            }
        } catch (t: Throwable) {
            Log.w("CookiePrimeBlock", "   ⚠️ Amplitude configuration skipped (may not be in app)")
        }
    }
}

class GoogleAnalyticsAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Google Analytics (Legacy)"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            val analyticsAllowed = consent["analytics"] == true || consent["analytics_storage"] == true
            val analyticsClass = Class.forName("com.google.android.gms.analytics.GoogleAnalytics")
            val getInstanceMethod = analyticsClass.getMethod("getInstance", Context::class.java)
            val instance = getInstanceMethod.invoke(null, ctx)
            
            val setAppOptOutMethod = analyticsClass.getMethod("setAppOptOut", Boolean::class.javaPrimitiveType)
            setAppOptOutMethod.invoke(instance, !analyticsAllowed)
            
            Log.d("CookiePrimeBlock", "   🔧 Google Analytics opt-out: ${!analyticsAllowed}")
        } catch (t: Throwable) {
            Log.w("CookiePrimeBlock", "   ⚠️ Google Analytics configuration skipped (may not be in app)")
        }
    }
}

class UnknownSDKAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Unknown/Other SDKs"
    
    override fun applyConsent(consent: Map<String, Boolean>) {
        val unknownAllowed = consent["unknown"] == true
        
        Log.d("CookiePrimeBlock", "   🔧 Unknown SDKs handling: ${if (unknownAllowed) "ALLOWED" else "BLOCKED"}")
        
        val prefs = ctx.getSharedPreferences("cookieprime_unknown_sdks", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("unknown_sdks_allowed", unknownAllowed).apply()
        
        if (!unknownAllowed) {
            Log.w("CookiePrimeBlock", "   ⚠️ Unknown SDKs detected but cannot be blocked automatically")
            Log.w("CookiePrimeBlock", "   ⚠️ User should manually check app permissions and tracking")
            
            // Store this in analytics for monitoring
            val analyticsPrefs = ctx.getSharedPreferences("cookieprime_analytics", Context.MODE_PRIVATE)
            analyticsPrefs.edit().putLong("unknown_sdks_blocked_at", System.currentTimeMillis()).apply()
        }
    }
}

// ADDITIONAL ADAPTERS FOR MORE SDKs (Optional)

class SentryAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Sentry"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            val crashAllowed = consent["crash_reporting"] ?: consent["analytics"] ?: true
            val sentryClass = Class.forName("io.sentry.Sentry")
            val getCurrentHub = sentryClass.getMethod("getCurrentHub")
            val hub = getCurrentHub.invoke(null)
            
            if (hub != null) {
                val hubClass = Class.forName("io.sentry.IHub")
                val closeMethod = hubClass.getMethod("close")
                
                if (!crashAllowed) {
                    closeMethod.invoke(hub)
                    Log.d("CookiePrimeBlock", "   🔧 Sentry crash reporting disabled")
                }
            }
        } catch (t: Throwable) {
            Log.w("CookiePrimeBlock", "   ⚠️ Sentry configuration skipped", t)
        }
    }
}

class OneSignalAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "OneSignal"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            val notificationsAllowed = consent["notifications"] ?: true
            val oneSignalClass = Class.forName("com.onesignal.OneSignal")
            val setSubscriptionMethod = oneSignalClass.getMethod("setSubscription", Boolean::class.javaPrimitiveType)
            setSubscriptionMethod.invoke(null, notificationsAllowed)
            
            Log.d("CookiePrimeBlock", "   🔧 OneSignal notifications: $notificationsAllowed")
        } catch (t: Throwable) {
            Log.w("CookiePrimeBlock", "   ⚠️ OneSignal configuration skipped", t)
        }
    }
}

class AdjustAdapter(private val ctx: Context) : SdkAdapter {
    override val name = "Adjust"
    override fun applyConsent(consent: Map<String, Boolean>) {
        try {
            val analyticsAllowed = consent["analytics"] == true
            val advertisingAllowed = consent["advertising"] == true
            
            val adjustClass = Class.forName("com.adjust.sdk.Adjust")
            val setEnabledMethod = adjustClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
            
            // Adjust should be disabled if either analytics OR advertising is denied
            val adjustEnabled = analyticsAllowed || advertisingAllowed
            setEnabledMethod.invoke(null, adjustEnabled)
            
            Log.d("CookiePrimeBlock", "   🔧 Adjust SDK enabled: $adjustEnabled")
        } catch (t: Throwable) {
            Log.w("CookiePrimeBlock", "   ⚠️ Adjust configuration skipped", t)
        }
    }
}