-keep class com.github.kr328.clash.core.bridge.** { *; }
-keep class com.github.kr328.clash.core.model.** { *; }
-keep class com.github.kr328.clash.common.** { *; }

# libbridge JNI_OnLoad 会 FindClass("kotlin/Unit") 等；Release R8 剥离后 loadLibrary 直接 SIGABRT
-keep class kotlin.Unit { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.jvm.functions.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.CompletableDeferred { *; }

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.github.kr328.clash.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.github.kr328.clash.core.**$$serializer { *; }
-keepclassmembers class com.github.kr328.clash.core.** {
    *** Companion;
}
