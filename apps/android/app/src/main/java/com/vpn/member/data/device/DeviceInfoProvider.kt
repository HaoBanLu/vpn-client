package com.vpn.member.data.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.vpn.member.BuildConfig
import java.util.UUID

data class LoginDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "android",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceBrand: String = Build.MANUFACTURER,
    val deviceModel: String = Build.MODEL,
    val osName: String = "Android",
    val osVersion: String = Build.VERSION.RELEASE,
    val clientPlatform: String = "android",
    val loginSource: String = "android_app",
)

object DeviceInfoProvider {
    private const val PREFS_NAME = "vpn_member_device"
    private const val KEY_DEVICE_ID = "device_id"

    fun get(context: Context): LoginDeviceInfo {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId =
                runCatching {
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                }.getOrNull()?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                    ?: UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        val deviceName = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
        return LoginDeviceInfo(
            deviceId = deviceId,
            deviceName = deviceName.ifBlank { "Android 设备" },
        )
    }
}
