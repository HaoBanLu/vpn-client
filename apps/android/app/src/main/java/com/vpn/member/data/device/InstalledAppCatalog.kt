package com.vpn.member.data.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

/** 枚举本机已安装应用，供应用直连选择（对齐 LibChecker：getInstalledApplications + 完整包可见性）。 */
class InstalledAppCatalog(
    private val context: Context,
) {
    /** 全部已安装应用（排除跨云自身；含无桌面图标、分身大师等）。 */
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

    /**
     * 仅桌面可启动应用（旧逻辑，保留供对比/单测）。
     * 注意：MATCH_DEFAULT_ONLY 会漏掉 Launcher 未声明 DEFAULT 的应用（如 com.qihoo.magic）。
     */
    fun listLaunchableAppsOnly(): List<LaunchableApp> {
        val pm = context.packageManager
        val launcherIntent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved =
            runCatching {
                pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }.onFailure { err ->
                Log.w(TAG, "listLaunchableAppsOnly failed", err)
            }.getOrElse { emptyList() }
        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName?.trim().orEmpty()
                if (pkg.isBlank() || pkg == context.packageName) {
                    return@mapNotNull null
                }
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
                LaunchableApp(packageName = pkg, label = label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
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
