package com.vpn.kuayun.vpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

/** 枚举本机已安装应用，供应用直连勾选。 */
class InstalledAppCatalog(
    private val context: Context,
) {
    fun listInstalledApps(): List<LaunchableApp> {
        val pm = context.packageManager
        val installed =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                }
            }.onFailure { err ->
                Log.w(TAG, "listInstalledApps failed", err)
            }.getOrElse { emptyList() }

        return installed
            .asSequence()
            .filter { info -> info.packageName != context.packageName }
            .filter { info -> info.enabled }
            .mapNotNull { info -> toLaunchableApp(pm, info) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun toLaunchableApp(pm: PackageManager, info: ApplicationInfo): LaunchableApp? {
        val pkg = info.packageName.trim()
        if (pkg.isBlank()) return null
        val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
        return LaunchableApp(packageName = pkg, label = label)
    }

    companion object {
        private const val TAG = "InstalledAppCatalog"
    }
}
