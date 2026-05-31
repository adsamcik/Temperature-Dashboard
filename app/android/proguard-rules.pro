# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Compose-related metadata
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*,EnclosingMethod,Signature,SourceFile,LineNumberTable

# Koin
-keep class org.koin.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.* class * { *; }
