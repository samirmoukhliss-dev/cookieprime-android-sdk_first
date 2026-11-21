// FILE: src/main/java/com/cookieprime/consent/initializer/CookiePrimeInitializer.kt
package com.cookieprime.consent.initializer

import android.content.Context
import androidx.startup.Initializer
import com.cookieprime.consent.ConsentSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Auto-initializes CookiePrime SDK on app startup
 * Reads license key from AndroidManifest meta-data
 * 
 * Developers can add this to their manifest:
 * <meta-data
 *     android:name="cookieprime.license_key"
 *     android:value="CP-12345-ABCDE-67890" />
 */
class CookiePrimeInitializer : Initializer<Unit> {
    
    override fun create(context: Context) {
        val appContext = context.applicationContext
        val packageName = appContext.packageName
        
        // Try to read license from manifest
        val licenseKey = try {
            val appInfo = appContext.packageManager
                .getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            appInfo.metaData?.getString("cookieprime.license_key") ?: "TRIAL"
        } catch (e: Exception) {
            "TRIAL" // Start in trial mode
        }
        
        // Auto-initialize in background
        MainScope().launch(Dispatchers.IO) {
            ConsentSDK.initialize(
                context = appContext,
                appId = packageName,
                apiKey = licenseKey,
                enableDebug = com.cookieprime.consent.BuildConfig.DEBUG,
                offlineMode = false
            )
            
            if (licenseKey == "TRIAL") {
                android.util.Log.i("CookiePrime", "🍪 Started 30-day free trial")
            } else {
                android.util.Log.i("CookiePrime", "✅ SDK auto-initialized with license")
            }
        }
    }
    
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}