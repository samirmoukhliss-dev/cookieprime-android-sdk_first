# 🍪 CookiePrime Android SDK

A consent management SDK for Android that automatically detects tracking SDKs
in your app, presents a GDPR/CCPA-compliant consent UI, and configures those
SDKs at runtime based on the user's choices.

## Features

- **Automatic SDK detection** — scans for Firebase, Facebook, Google Ads,
  AppsFlyer, Adjust, Mixpanel, Amplitude, and more
- **Consent UI** — Material Design dialog with per-category toggles
- **Runtime configuration** — SDKs are enabled/disabled based on consent
- **Offline support** — Room database stores consent locally; WorkManager
  syncs when the network is available
- **IAB TCF 2.2** — generates a TC string compatible with the IAB framework
- **Google Consent Mode v2** — sets `ad_storage`, `analytics_storage`,
  `ad_user_data`, `ad_personalization` signals
- **Debug overlay** — optional on-screen debug panel in development builds

## Requirements

- Android 5.0 (API 21) or higher
- Kotlin 1.9+
- Java 17 for building

## Installation

Build the AAR from source:

```bash
./gradlew assembleRelease



The output is at build/outputs/aar/cookieprime-android-sdk-release.aar.


Copy it into your app's libs/ folder and reference it in build.gradle:

groovy
dependencies {
    implementation files('libs/cookieprime-android-sdk-release.aar')
}



Quick Start



1. Add permissions

xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

2. Initialize in your Application class

kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        CookiePrime.init(this, "YOUR_LICENSE_KEY") { 
            enableDebug = true 
        }
    }
}

3. Show the consent dialog

kotlin
// In your first Activity
lifecycleScope.launch {
    ConsentSDK.getInstance().showConsentIfNeeded(this@MainActivity)
}

4. Check consent before using an SDK

kotlin
if (ConsentSDK.isAnalyticsConsentGiven) {
    FirebaseAnalytics.getInstance(context).logEvent("app_open", null)
}




Project Structure

src/main/java/com/cookieprime/
├── CookiePrime.kt                    # Public entry point
└── consent/
    ├── ConsentSDK.kt                 # Core SDK
    ├── compliance/                   # IAB TCF + Google Consent Mode
    ├── configuration/                # Category config + remote config
    ├── detection/                    # SDK scanner
    ├── initializer/                  # Auto-init via androidx.startup
    ├── license/                      # License validation
    ├── models/                       # Data classes
    ├── network/                      # Retrofit/OkHttp client
    ├── proof/                        # Consent proof hashing
    ├── storage/                      # Room database
    ├── ui/                           # Consent Activity + adapter
    ├── utils/                        # Helpers
    ├── work/                         # WorkManager workers
    └── wrappers/                     # Safe SDK wrappers


License

Proprietary. See LICENSE.md for details.

Support
For issues and questions, open a GitHub issue or email samir.moukhliss@cookieprime.com.