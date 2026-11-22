# CookiePrime Android SDK

A lightweight (~380 KB) on-device consent management SDK for Android that scans for third-party tracking SDKs in your app, presents a customizable consent UI, and enforces user privacy choices directly in app code.

> **Status Note:** This repository is published for developer evaluation and UX testing. If you plan to deploy it in a production app subject to strict GDPR/ePrivacy enforcement, please review the **Legal Compliance & Production Checklist** below before shipping.

## Features

- **Lightweight On-Device Engine** — ~380 KB AAR with local enforcement and zero heavy WebView dependencies
- **Automatic SDK Detection** — Scans DEX/classes for Firebase, Facebook, Google Ads, AppsFlyer, Adjust, Mixpanel, Amplitude, and more
- **Consent UI** — Native Android dialog with first-layer Accept All, Reject All, and per-category toggles
- **Runtime Configuration** — Toggles collection flags on supported SDKs via reflection and exposes `ConsentSDK.isConsentGiven(...)` for call-site gating
- **Offline-First Storage** — Room database stores consent records locally; optional WorkManager sync when online
- **Consent Mode & TCF Scaffolding** — Includes basic structures for Google Consent Mode v2 and IAB TCF signals *(see production notes below)*
- **Debug Overlay** — Optional on-screen debug panel in development builds

## Requirements

- Android 5.0 (API 21) or higher
- Kotlin 1.9+
- Java 17 for building

## 📦 Installation

Build the AAR from source:

```bash
./gradlew assembleRelease
```

The output is at `build/outputs/aar/cookieprime-android-sdk-release.aar`.

Copy it into your app's `libs/` folder and reference it in `build.gradle`:

```groovy
dependencies {
    implementation files('libs/cookieprime-android-sdk-release.aar')
}
```

## Quick Start

### 1. Add permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 2. Initialize in your Application class

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        CookiePrime.init(this, "YOUR_LICENSE_KEY") { 
            enableDebug = true
            offlineMode = true // Recommended for pure on-device storage
        }
    }
}
```

### 3. Show the consent dialog

```kotlin
// In your first Activity
lifecycleScope.launch {
    ConsentSDK.getInstance().showConsentIfNeeded(this@MainActivity)
}
```

### 4. Check consent before using an SDK

```kotlin
if (ConsentSDK.isAnalyticsConsentGiven) {
    FirebaseAnalytics.getInstance(context).logEvent("app_open", null)
}
```

---

## ⚖️ Legal Compliance & Production Checklist (GDPR / ePrivacy)

If you are using this SDK solely to evaluate the UI/UX flow, the Quick Start above works out of the box. However, if you want to deploy this codebase in production for **strict legal compliance** (GDPR, ePrivacy, CNIL/EDPB guidelines), apply the following adjustments (estimated ~2–4 days of engineering work):

1. **Disable Pre-Ticked Non-Essential Toggles (Required by GDPR)**
   - In `CategoryConfig.kt` (`createDefaultCategories()`) and `ConsentActivity.kt` (`getDefaultForCategory()`), `analytics`, `personalization`, and `performance` currently default to `true`.
   - **Fix:** Change all non-essential defaults to `false` so only `essential` is enabled prior to affirmative user opt-in.

2. **Prevent Pre-Consent SDK Auto-Initialization**
   - Runtime reflection setters cannot undo network pings sent before `ConsentSDK` runs.
   - **Fix:** Disable auto-init in your host app's `AndroidManifest.xml` (e.g., `firebase_analytics_collection_deactivated = true`, `com.facebook.sdk.AutoInitEnabled = false`), and remove `tryInitializeFacebook()` in `SDKConfigurator.kt` when consent is denied.

3. **Equalize Button Visual Weight & Handle System Back Gesture**
   - In `ConsentActivity.kt`, `Accept All` uses a high-contrast background while `Reject All` uses default styling, and pressing the hardware Back button dismisses the dialog without recording a choice.
   - **Fix:** Give `Accept All` and `Reject All` identical visual prominence (avoiding "dark pattern" nudging) and intercept `onBackPressedDispatcher` to record an explicit refusal (`ALL_DENIED`) if dismissed.

4. **Data Minimization & Transport Security (`offlineMode` / `LicenseValidator.kt`)**
   - By default, `LicenseValidator.kt` points to a raw `http://` endpoint and attaches `X-Device-ID` headers before consent is granted, while `UploadConsentRequest` includes device/session identifiers.
   - **Fix:** Run with `offlineMode = true` for pure on-device Room audit logs, or update the endpoint to `https://` and strip `X-Device-ID` from pre-consent requests.

5. **Note on Google Consent Mode v2 & IAB TCF v2.2**
   - **Legal vs. Commercial:** Google CMP certification and IAB TCF strings are commercial ad-tech industry standards, not legal requirements under GDPR. For non-ad-supported apps (e.g., analytics/SaaS), they are not legally required.
   - **Current Code State:** `ComplianceManager.kt` currently contains placeholder/stub logic (writing Google Consent Mode flags to a local `cookieprime_gcm_simple` file rather than calling `FirebaseAnalytics.setConsent(...)`, and generating a non-binary string in `buildTcString()`). If you monetize with **AdMob or Google Ads in the EEA/UK**, remove `buildTcString()` and pair your app with Google's official UMP SDK instead.

---

## Project Structure

```text
src/main/java/com/cookieprime/
├── CookiePrime.kt                    # Public entry point
└── consent/
    ├── ConsentSDK.kt                 # Core SDK
    ├── compliance/                   # ConsentMode + ComplianceManager stubs
    ├── configuration/                # CategoryConfig + SDKConfigurator
    ├── detection/                    # DependencyScanner
    ├── initializer/                  # Auto-init via androidx.startup
    ├── license/                      # License validation
    ├── models/                       # Data classes
    ├── network/                      # OkHttp client & DTOs
    ├── proof/                        # Consent proof hashing
    ├── storage/                      # Room database
    ├── ui/                           # ConsentActivity + adapter
    ├── utils/                        # Helpers
    ├── work/                         # WorkManager workers
    └── wrappers/                     # Safe SDK wrappers
```

## License

Proprietary. See `LICENSE.md` for details.

## Support

For issues and questions, open a GitHub issue or email `samir.moukhliss@cookieprime.com`.
