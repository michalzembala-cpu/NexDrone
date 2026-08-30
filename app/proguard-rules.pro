# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.nexplay.dronepreflight.**$$serializer { *; }
-keepclassmembers class com.nexplay.dronepreflight.** {
    *** Companion;
}
-keepclasseswithmembers class com.nexplay.dronepreflight.** {
    kotlinx.serialization.KSerializer serializer(...);
}
