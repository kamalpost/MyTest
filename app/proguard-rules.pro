# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kamalpost.taskmanager.**$$serializer { *; }
-keepclassmembers class com.kamalpost.taskmanager.** { *** Companion; }
-keepclasseswithmembers class com.kamalpost.taskmanager.** { kotlinx.serialization.KSerializer serializer(...); }
