# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.stickertransfer.app.**$$serializer { *; }
-keepclassmembers class com.stickertransfer.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.stickertransfer.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Coil
-keep class coil.** { *; }

# Content Provider
-keep class com.stickertransfer.app.provider.** { *; }
