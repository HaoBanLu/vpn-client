package com.vpn.kuayun.vpn

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** 国内 ROM（MIUI 等）完整应用列表权限。 */
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
            PackageManager.PERMISSION_GRANTED
    }

    /** 声明了 OEM 权限且未授予时始终提示，避免 Launcher 可见数 ≥8 就藏掉授权入口。 */
    fun needsUserGrant(context: Context): Boolean {
        if (!isPermissionDeclared(context)) return false
        return !isGranted(context)
    }
}
