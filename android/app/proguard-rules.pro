# Keep the Neo SDK surface if/when the real jar is added.
-keep class kr.neolab.sdk.** { *; }
-dontwarn kr.neolab.sdk.**

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
