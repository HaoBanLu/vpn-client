package com.vpn.member.data.device

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** 国内 ROM（MIUI 等）完整应用列表权限，LibChecker 同款。 */
object InstalledAppsPermission {
    const val GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"

    fun isPermissionDeclared(context: Context): Boolean =
        runCatching {
            context.packageManager.getPermissionInfo(GET_INSTALLED_APPS, 0)
            true
        }.getOrDefault(false)

    fun isGranted(context: Context): Boolean {
        if (!isPermissionDeclared(context)) return true
        return ContextCompat.checkSelfPermission(context, GET_INSTALLED_APPS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
