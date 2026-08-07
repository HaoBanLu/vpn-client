# Retrofit / OkHttp / Gson
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep class retrofit2.** { *; }
-keep interface com.vpn.member.data.api.VpnApi { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# API models — Gson 反射解析，Release 混淆后必须完整保留
-keep class com.vpn.member.data.api.** { *; }

# 规则直连 — AppPreferences JSON 本地序列化
-keep class com.vpn.member.vpn.DirectBypassRule { *; }
-keep class com.vpn.member.vpn.DirectBypassRuleType { *; }

# BuildConfig
-keep class com.vpn.member.BuildConfig { *; }

# Mihomo / Clash Meta bridge
-keep class com.github.kr328.clash.core.** { *; }
-keep class com.github.kr328.clash.common.** { *; }
-keep class go.** { *; }

# libbridge JNI_OnLoad 依赖 kotlin.Unit / 协程类型，R8 剥离后真机 Release 连接即闪退
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
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.github.kr328.clash.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.github.kr328.clash.core.**$$serializer { *; }
-keepclassmembers class com.github.kr328.clash.core.** { *** Companion; }

# JNI 回调：nativeLoad / startTun 依赖这些接口与协程句柄
-keep class com.github.kr328.clash.core.bridge.TunInterface { *; }
-keep class * implements com.github.kr328.clash.core.bridge.TunInterface { *; }
-keep class com.github.kr328.clash.core.bridge.FetchCallback { *; }
-keep class * implements com.github.kr328.clash.core.bridge.FetchCallback { *; }
-keep class com.github.kr328.clash.core.bridge.LogcatInterface { *; }
-keep class * implements com.github.kr328.clash.core.bridge.LogcatInterface { *; }
-keep class kotlinx.coroutines.CompletableDeferred { *; }

# Tink (androidx.security:security-crypto) — compile-only errorprone annotations
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
