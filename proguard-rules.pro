# proguard-rules.pro
# Keep all public classes and methods
-keep public class * {
    public *;
}

# Keep CookiePrime entry points
-keep class com.cookieprime.CookiePrime { *; }
-keep class com.cookieprime.consent.ConsentSDK { *; }
-keep class com.cookieprime.consent.CookiePrimeBuilder { *; }

# Keep license validation classes
-keep class com.cookieprime.consent.license.** { *; }

# Keep Room database
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep WorkManager
-keep class androidx.work.impl.WorkManagerInitializer { *; }

# Keep Retrofit
-keep class com.cookieprime.consent.network.** { *; }

# Keep extension functions
-keep class com.cookieprime.consent.ApplicationExtKt { *; }

# Keep ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding { *; }

# Keep annotations
-keepattributes *Annotation*

# Keep generic signatures
-keepattributes Signature

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# OkHttp
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* *;
}

# Coroutines
-dontwarn kotlinx.coroutines.**

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.examples.android.model.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ===== FIX: Keep Java 9+ StringConcatFactory =====
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn java.lang.invoke.MethodHandles
-keep class java.lang.invoke.** { *; }
# ================================================