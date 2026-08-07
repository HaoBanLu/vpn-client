# Tauri plugin / annotations
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class app.tauri.** { *; }

# VPN overlay
-keep class com.vpn.tauri.vpn.** { *; }

# Mihomo / CMFA core JNI
-keep class com.github.kr328.clash.core.bridge.** { *; }
-keep class com.github.kr328.clash.core.model.** { *; }

# Kotlin coroutines / flows used by VpnConnectionBus
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
