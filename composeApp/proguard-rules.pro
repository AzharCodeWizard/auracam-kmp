# AuraCam ProGuard & R8 Configuration for Play Store Release

# Kotlin Multiplatform Coroutines & Serialization
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# AndroidX CameraX / Camera2 Keep rules
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keepclassmembers class androidx.camera.core.** { *; }
-keepclassmembers class androidx.camera.camera2.** { *; }

# Compose Multiplatform runtime and animations
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Computational Photography Native & Image Processing
-keep class com.auracam.processing.** { *; }
-keep class com.auracam.camera.domain.** { *; }
