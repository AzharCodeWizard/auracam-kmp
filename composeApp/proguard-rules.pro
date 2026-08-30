-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **$*
-keepclassmembers class <1>$<2> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

-keep class androidx.camera.camera2.Camera2Config { *; }
-keep class androidx.camera.camera2.internal.** { *; }
-keep class androidx.camera.core.impl.** { *; }
-keep class * implements androidx.camera.core.CameraXConfig$Provider { *; }
-dontwarn androidx.camera.**

-keep class com.auracam.camera.domain.** { *; }
-keep class com.auracam.processing.** { *; }
-keep class com.auracam.settings.** { *; }
-keep class com.auracam.location.** { *; }

-dontwarn java.lang.invoke.**
-dontwarn org.slf4j.**
