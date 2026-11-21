\# Changelog



All notable changes to the CookiePrime Android SDK will be documented in this file.



The format is based on \[Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

and this project adheres to \[Semantic Versioning](https://semver.org/spec/v2.0.0.html).



\## \[1.0.0] - 2024-01-15

\### Added

\- Automatic SDK detection (Firebase, Facebook, Google Ads, etc.)

\- GDPR/CCPA compliant consent UI

\- Runtime SDK blocking for detected SDKs

\- Room database for offline consent storage

\- WorkManager for background consent synchronization

\- IAB TCF 2.2 support (preliminary)

\- Google Consent Mode v2 support

\- Configurable themes and localization

\- Debug overlay for development



\### Technical

\- Min SDK: 21 (Android 5.0)

\- Target SDK: 33 (Android 13)

\- Written in Kotlin with Coroutines

\- Uses Room 2.6, Retrofit 2.9, OkHttp 4.11

\- Obfuscated release builds with ProGuard/R8

\- GitHub Actions CI/CD pipeline



\### Known Issues

\- Backend API endpoints are placeholders (update in ApiClient.kt)

\- Some SDK detection methods may require runtime permissions

\- Offline mode uses simulated network calls

