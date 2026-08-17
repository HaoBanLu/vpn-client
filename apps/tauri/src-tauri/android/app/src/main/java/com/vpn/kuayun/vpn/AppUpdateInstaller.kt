package com.vpn.kuayun.vpn

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.tauri.plugin.JSObject
import java.io.File
import java.lang.ref.WeakReference

/**
 * 应用内更新：DownloadManager 拉 APK，pending 持久化，授权后可继续安装。
 * 对齐归档 Compose AppUpdateInstaller，并通过 [eventEmitter] 通知 WebView。
 */
class AppUpdateInstaller private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private var pendingFileName: String? = null
    private var awaitingPermissionReturn = false

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun consumeAwaitingPermissionReturn(): Boolean {
        val pending = awaitingPermissionReturn
        awaitingPermissionReturn = false
        return pending
    }

    data class PendingInstallInfo(
        val versionLabel: String,
        val versionCode: Int,
        val apkFile: File,
    )

    enum class InstallAttemptResult {
        NoPending,
        MissingApk,
        NeedPermission,
        Launched,
        Failed,
    }

    fun startDownload(url: String, versionLabel: String, versionCode: Int) {
        if (url.isBlank()) {
            emitFailed("下载地址无效，请稍后重试")
            return
        }
        cancelReceiver()
        clearPendingInstall()
        val fileName = "kuayun-${sanitize(versionLabel)}.apk"
        pendingFileName = fileName
        val request =
            DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("跨云 App 更新")
                setDescription("正在下载 $versionLabel")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setMimeType("application/vnd.android.package-archive")
                setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
            }
        val downloadId = downloadManager.enqueue(request)
        saveActiveDownload(downloadId, fileName, versionLabel, versionCode)
        registerReceiver(downloadId)
        emitEvent(EVENT_DOWNLOAD_STARTED, JSObject())
        toast("已开始下载，完成后将提示安装")
    }

    fun hasPendingInstall(): Boolean = readPendingInstall() != null

    fun needsInstallPermission(): Boolean = !canInstallPackages()

    fun readPendingInstall(): PendingInstallInfo? {
        migrateLegacyPendingPrefs()
        val fileName = prefs.getString(KEY_PENDING_INSTALL_FILE, null)?.trim().orEmpty()
        if (fileName.isBlank()) return null
        val apkFile = resolveApkFile(fileName)
        if (!apkFile.exists()) {
            clearPendingInstall()
            return null
        }
        val versionLabel =
            prefs.getString(KEY_PENDING_INSTALL_VERSION, null)?.trim().orEmpty().ifBlank { fileName }
        val versionCode = prefs.getInt(KEY_PENDING_INSTALL_VERSION_CODE, 0)
        val pending = PendingInstallInfo(versionLabel = versionLabel, versionCode = versionCode, apkFile = apkFile)
        val (currentCode, currentName) = currentVersionInfo()
        if (isPendingInstallObsolete(pending.versionCode, pending.versionLabel, currentCode, currentName)) {
            clearPendingInstall(deleteApk = true)
            return null
        }
        return pending
    }

    fun tryInstallPendingApk(): InstallAttemptResult {
        val pending = readPendingInstall() ?: return InstallAttemptResult.NoPending
        if (!pending.apkFile.exists()) {
            clearPendingInstall()
            return InstallAttemptResult.MissingApk
        }
        if (!canInstallPackages()) {
            return InstallAttemptResult.NeedPermission
        }
        return if (launchInstall(pending.apkFile)) {
            emitEvent(EVENT_INSTALL_LAUNCHED, JSObject())
            InstallAttemptResult.Launched
        } else {
            InstallAttemptResult.Failed
        }
    }

    fun onResume() {
        emitEvent(EVENT_RESUME, JSObject())
        if (consumeAwaitingPermissionReturn()) {
            when (tryInstallPendingApk()) {
                InstallAttemptResult.NeedPermission -> openInstallPermissionSettings()
                InstallAttemptResult.Launched -> toast("请按提示完成安装")
                else -> Unit
            }
            return
        }
        val pending = readPendingInstall() ?: return
        when (tryInstallPendingApk()) {
            InstallAttemptResult.NeedPermission -> {
                emitDownloadComplete(pending)
            }
            InstallAttemptResult.Launched -> Unit
            else -> emitDownloadComplete(pending)
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        awaitingPermissionReturn = true
        runOnMain {
            runCatching {
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${appContext.packageName}"),
                    )
                val activity = activityRef?.get()
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                }
            }
        }
    }

    private fun handleDownloadComplete(downloadId: Long) {
        if (downloadId != prefs.getLong(KEY_DOWNLOAD_ID, -1L)) return
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        cursor.use {
            if (!it.moveToFirst()) {
                emitFailed("下载失败，请稍后重试")
                return
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                emitFailed("下载失败，请稍后重试")
                return
            }
        }
        val fileName = pendingFileName ?: prefs.getString(KEY_ACTIVE_FILE_NAME, null)
        if (fileName.isNullOrBlank()) {
            emitFailed("安装包路径无效")
            return
        }
        val apkFile = resolveApkFile(fileName)
        if (!apkFile.exists()) {
            emitFailed("安装包不存在，请重新下载")
            return
        }
        val versionLabel =
            prefs.getString(KEY_ACTIVE_VERSION_LABEL, null)?.trim().orEmpty().ifBlank { fileName }
        val versionCode = prefs.getInt(KEY_ACTIVE_VERSION_CODE, 0)
        savePendingInstall(fileName, versionLabel, versionCode)
        clearActiveDownload()
        cancelReceiver()
        val pending = PendingInstallInfo(versionLabel = versionLabel, versionCode = versionCode, apkFile = apkFile)
        val (currentCode, currentName) = currentVersionInfo()
        if (isPendingInstallObsolete(pending.versionCode, pending.versionLabel, currentCode, currentName)) {
            clearPendingInstall(deleteApk = true)
            return
        }
        emitDownloadComplete(pending)
        runOnMain {
            when (tryInstallPendingApk()) {
                InstallAttemptResult.NeedPermission -> {
                    toast("下载完成，请允许安装未知应用后再试")
                    openInstallPermissionSettings()
                }
                InstallAttemptResult.Launched -> toast("请按提示完成安装")
                InstallAttemptResult.Failed -> emitFailed("无法打开安装程序，请稍后重试")
                else -> Unit
            }
        }
    }

    private fun emitDownloadComplete(pending: PendingInstallInfo) {
        val payload = JSObject()
        payload.put("versionLabel", pending.versionLabel)
        payload.put("versionCode", pending.versionCode)
        payload.put("needsInstallPermission", !canInstallPackages())
        emitEvent(EVENT_DOWNLOAD_COMPLETE, payload)
    }

    private fun emitFailed(message: String) {
        clearActiveDownload()
        val payload = JSObject()
        payload.put("message", message)
        emitEvent(EVENT_DOWNLOAD_FAILED, payload)
        toast(message)
    }

    private fun registerReceiver(downloadId: Long) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
                    if (id != downloadId) return
                    cancelReceiver()
                    handleDownloadComplete(downloadId)
                }
            }
        downloadReceiver = receiver
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    private fun launchInstall(apkFile: File): Boolean {
        val uri =
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile,
            )
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return runCatching {
            val activity = activityRef?.get()
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            }
            true
        }.getOrDefault(false)
    }

    private fun cancelReceiver() {
        downloadReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        downloadReceiver = null
    }

    private fun toast(message: String) {
        runOnMain { Toast.makeText(appContext, message, Toast.LENGTH_LONG).show() }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun resolveApkFile(fileName: String): File =
        File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

    private fun saveActiveDownload(downloadId: Long, fileName: String, versionLabel: String, versionCode: Int) {
        prefs.edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .putString(KEY_ACTIVE_FILE_NAME, fileName)
            .putString(KEY_ACTIVE_VERSION_LABEL, versionLabel)
            .putInt(KEY_ACTIVE_VERSION_CODE, versionCode)
            .apply()
    }

    private fun savePendingInstall(fileName: String, versionLabel: String, versionCode: Int) {
        pendingFileName = fileName
        prefs.edit()
            .putString(KEY_PENDING_INSTALL_FILE, fileName)
            .putString(KEY_PENDING_INSTALL_VERSION, versionLabel)
            .putInt(KEY_PENDING_INSTALL_VERSION_CODE, versionCode)
            .apply()
    }

    private fun clearActiveDownload() {
        prefs.edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_ACTIVE_FILE_NAME)
            .remove(KEY_ACTIVE_VERSION_LABEL)
            .remove(KEY_ACTIVE_VERSION_CODE)
            .apply()
    }

    private fun migrateLegacyPendingPrefs() {
        val legacyFile = prefs.getString(LEGACY_KEY_FILE, null)?.trim().orEmpty()
        if (legacyFile.isBlank()) return
        if (!prefs.getString(KEY_PENDING_INSTALL_FILE, null).isNullOrBlank()) {
            prefs.edit().remove(LEGACY_KEY_FILE).apply()
            return
        }
        if (resolveApkFile(legacyFile).exists()) {
            savePendingInstall(legacyFile, legacyFile, 0)
        }
        prefs.edit().remove(LEGACY_KEY_FILE).remove(LEGACY_KEY_DOWNLOAD_ID).apply()
    }

    private fun clearPendingInstall(deleteApk: Boolean = false) {
        if (deleteApk) {
            val fileName = prefs.getString(KEY_PENDING_INSTALL_FILE, null)?.trim().orEmpty()
            if (fileName.isNotBlank()) {
                runCatching { resolveApkFile(fileName).delete() }
            }
        }
        pendingFileName = null
        prefs.edit()
            .remove(KEY_PENDING_INSTALL_FILE)
            .remove(KEY_PENDING_INSTALL_VERSION)
            .remove(KEY_PENDING_INSTALL_VERSION_CODE)
            .apply()
    }

    private fun currentVersionInfo(): Pair<Int, String> {
        return runCatching {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.packageManager.getPackageInfo(
                        appContext.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                }
            val code =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode
                }
            Pair(code, info.versionName.orEmpty())
        }.getOrDefault(Pair(0, ""))
    }

    private fun emitEvent(event: String, payload: JSObject) {
        eventEmitter?.invoke(event, payload)
    }

    companion object {
        const val EVENT_DOWNLOAD_STARTED = "app-update://download-started"
        const val EVENT_DOWNLOAD_COMPLETE = "app-update://download-complete"
        const val EVENT_DOWNLOAD_FAILED = "app-update://download-failed"
        const val EVENT_INSTALL_LAUNCHED = "app-update://install-launched"
        const val EVENT_RESUME = "app-update://resume"

        private const val PREFS_NAME = "kuayun_app_update"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_ACTIVE_FILE_NAME = "active_file_name"
        private const val KEY_ACTIVE_VERSION_LABEL = "active_version_label"
        private const val KEY_ACTIVE_VERSION_CODE = "active_version_code"
        private const val KEY_PENDING_INSTALL_FILE = "pending_install_file"
        private const val KEY_PENDING_INSTALL_VERSION = "pending_install_version"
        private const val KEY_PENDING_INSTALL_VERSION_CODE = "pending_install_version_code"
        private const val LEGACY_KEY_FILE = "file_name"
        private const val LEGACY_KEY_DOWNLOAD_ID = "legacy_download_id"

        @Volatile
        var eventEmitter: ((String, JSObject) -> Unit)? = null

        @Volatile
        private var instance: AppUpdateInstaller? = null

        fun getInstance(context: Context): AppUpdateInstaller {
            return instance ?: synchronized(this) {
                instance ?: AppUpdateInstaller(context.applicationContext).also { instance = it }
            }
        }

        fun sanitize(value: String): String {
            val cleaned = value.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
            return cleaned.ifBlank { "latest" }
        }

        fun isPendingInstallObsolete(
            pendingVersionCode: Int,
            pendingVersionLabel: String,
            currentVersionCode: Int,
            currentVersionName: String,
        ): Boolean {
            if (pendingVersionCode > 0 && pendingVersionCode <= currentVersionCode) {
                return true
            }
            val pendingName = normalizePendingVersionLabel(pendingVersionLabel)
            val currentName = currentVersionName.trim()
            return pendingName.isNotBlank() &&
                currentName.isNotBlank() &&
                pendingName.equals(currentName, ignoreCase = true)
        }

        fun normalizePendingVersionLabel(label: String): String {
            val trimmed = label.trim()
            val fromFile =
                Regex("""kuayun-(.+)\.apk""", RegexOption.IGNORE_CASE)
                    .find(trimmed)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
            return fromFile?.takeIf { it.isNotBlank() } ?: trimmed
        }
    }
}
