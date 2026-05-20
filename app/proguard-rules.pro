-keep class net.sqlcipher.** { *; }
-keep class com.google.mlkit.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Google Identity and Credentials
-dontwarn com.google.android.libraries.identity.**
-keep class com.google.android.libraries.identity.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.gms.common.api.Scope { *; }

# JSON Parsing (Crucial for Token photo extraction)
-keep class org.json.** { *; }
-keep interface org.json.** { *; }

# Coil - Comprehensive Image Loading Rules
-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn coil.**
-keep public class * implements coil.decode.Decoder$Factory
-keep public class * implements coil.fetch.Fetcher$Factory
-keep public class * implements coil.key.Keyer
-keep public class * implements coil.map.Mapper

# OkHttp/Okio (Networking for Coil)
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class javax.annotation.** { *; }
-keep class org.conscrypt.** { *; }

# Prevent Coil from shrinking its own components
-keep public class * implements coil.decode.Decoder$Factory
-keep public class * implements coil.fetch.Fetcher$Factory

# Kotlin Coroutines (Used by Coil)
-keep class kotlinx.coroutines.** { *; }

# BouncyCastle (Used for ePassport NFC and Security)
-keep class org.bouncycastle.** { *; }
-keep interface org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# JMRTD + Scuba (ePassport NFC reader — loaded via ServiceLoader SPI, stripped by R8)
-keep class net.sf.scuba.** { *; }
-keep interface net.sf.scuba.** { *; }
-dontwarn net.sf.scuba.**
-keep class org.jmrtd.** { *; }
-keep interface org.jmrtd.** { *; }
-dontwarn org.jmrtd.**

-keep class javax.net.ssl.** { *; }
-dontwarn javax.net.ssl.**

# Our Auth Classes
-keep class com.kryptos.vault.security.** { *; }
-keepclassmembers class com.kryptos.vault.security.** { *; }

# If photo is STILL empty, we may need to disable obfuscation for these specific types
-keepnames class com.kryptos.vault.security.AuthManager$Account
