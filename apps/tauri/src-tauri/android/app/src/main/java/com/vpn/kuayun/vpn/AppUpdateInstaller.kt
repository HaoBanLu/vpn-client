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
    private var pollRunnable: Runnable? = null
    private var pollTicksForActive = 0

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
        cancelActiveDownload(silent = true)
        clearPendingInstall()
        val fileName = "kuayun-${sanitize(versionLabel)}.apk"
        pendingFileName = fileName
        // 避免目标文件已存在时 DownloadManager 改写成 kuayun-xxx-1.apk，导致路径对不上
        runCatching {
            val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@runCatching
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("kuayun-") && f.name.endsWith(".apk")) {
                    f.delete()
                }
            }
        }
        val request =
            DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("跨云 App 更新")
                setDescription("正在下载 $versionLabel")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setMimeType("application/vnd.android.package-archive")
                setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
        val downloadId = downloadManager.enqueue(request)
        android.util.Log.i("AppUpdateInstaller", "enqueue downloadId=$downloadId url=$url file=$fileName")
        saveActiveDownload(downloadId, fileName, versionLabel, versionCode)
        registerReceiver(downloadId)
        startDownloadPoll(downloadId)
        emitEvent(EVENT_DOWNLOAD_STARTED, JSObject())
        toast("已开始下载，完成后将提示安装")
    }

    /** 取消当前 DownloadManager 任务，避免 VPN 下挂死后无法退出更新浮层 */
    fun cancelActiveDownload(silent: Boolean = false) {
        stopDownloadPoll()
        cancelReceiver()
        val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId >= 0L) {
            runCatching { downloadManager.remove(downloadId) }
        }
        clearActiveDownload()
        if (!silent) {
            toast("已取消下载")
        }
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
        // 广播偶发丢失：回前台时补查一次 DownloadManager 状态
        val activeId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (activeId >= 0L) {
            pollDownloadStatus(activeId, fromResume = true)
        }
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

    /** 广播不可靠时轮询 DownloadManager，避免 APK 已下完却不提示安装 */
    private fun startDownloadPoll(downloadId: Long) {
        stopDownloadPoll()
        pollTicksForActive = 0
        val runnable =
            object : Runnable {
                override fun run() {
                    if (prefs.getLong(KEY_DOWNLOAD_ID, -1L) != downloadId) return
                    pollTicksForActive += 1
                    if (pollDownloadStatus(downloadId, fromResume = false)) return
                    mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        pollRunnable = runnable
        mainHandler.postDelayed(runnable, POLL_INTERVAL_MS)
    }

    private fun stopDownloadPoll() {
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
    }

    /**
     * @return true 表示已结束（成功或失败），应停止轮询
     */
    private fun pollDownloadStatus(downloadId: Long, fromResume: Boolean): Boolean {
        if (downloadId != prefs.getLong(KEY_DOWNLOAD_ID, -1L)) return true
        val fileName = pendingFileName ?: prefs.getString(KEY_ACTIVE_FILE_NAME, null)
        val apkFile = if (!fileName.isNullOrBlank()) resolveApkFile(fileName) else null
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = runCatching { downloadManager.query(query) }.getOrNull()
        if (cursor == null) {
            // 查询失败时：文件已完整则按成功收尾
            if (apkFile != null && apkFile.exists() && apkFile.length() > 1024L) {
                handleDownloadComplete(downloadId)
                return true
            }
            return false
        }
        cursor.use {
            if (!it.moveToFirst()) {
                // 系统可能已清理 DownloadManager 记录，但 APK 已落盘
                val resolved = resolveDownloadedApk(downloadId, fileName ?: "")
                if (resolved != null) {
                    handleDownloadComplete(downloadId)
                    return true
                }
                return false
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            android.util.Log.i(
                "AppUpdateInstaller",
                "poll id=$downloadId status=$status ticks=$pollTicksForActive fileLen=${apkFile?.length() ?: -1}",
            )
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    handleDownloadComplete(downloadId)
                    true
                }
                DownloadManager.STATUS_FAILED -> {
                    stopDownloadPoll()
                    cancelReceiver()
                    emitFailed("下载失败，请稍后重试")
                    true
                }
                else -> {
                    // 模拟器上 DownloadManager 偶发一直 RUNNING；用目录扫描找已写完的 APK
                    val resolved =
                        if (pollTicksForActive >= 4) {
                            resolveDownloadedApk(downloadId, fileName ?: "")
                        } else {
                            null
                        }
                    if (resolved != null) {
                        android.util.Log.w(
                            "AppUpdateInstaller",
                            "poll treat as done: status=$status file=${resolved.name} len=${resolved.length()}",
                        )
                        handleDownloadComplete(downloadId)
                        return true
                    }
                    false
                }
            }
        }
    }

    /** 解析实际 APK 路径：优先下载记录里的 local URI，再回退到约定文件名 / 目录扫描 */
    private fun resolveDownloadedApk(downloadId: Long, expectedFileName: String): File? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = runCatching { downloadManager.query(query) }.getOrNull()
        cursor?.use {
            if (it.moveToFirst()) {
                val uriIdx = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                if (uriIdx >= 0) {
                    val localUri = it.getString(uriIdx)?.trim().orEmpty()
                    if (localUri.isNotBlank()) {
                        val path =
                            when {
                                localUri.startsWith("file://") -> Uri.parse(localUri).path
                                localUri.startsWith("/") -> localUri
                                else -> null
                            }
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists() && f.length() > 1024L) return f
                        }
                    }
                }
            }
        }
        val expected = resolveApkFile(expectedFileName)
        if (expected.exists() && expected.length() > 1024L) return expected
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("kuayun-") && it.name.endsWith(".apk") && it.length() > 1024L }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun handleDownloadComplete(downloadId: Long) {
        if (downloadId != prefs.getLong(KEY_DOWNLOAD_ID, -1L)) return
        stopDownloadPoll()
        cancelReceiver()
        val expectedName = pendingFileName ?: prefs.getString(KEY_ACTIVE_FILE_NAME, null)
        if (expectedName.isNullOrBlank()) {
            emitFailed("安装包路径无效")
            return
        }
        val apkFile = resolveDownloadedApk(downloadId, expectedName)
        if (apkFile == null || !apkFile.exists() || apkFile.length() < 1024L) {
            emitFailed("安装包不存在，请重新下载")
            return
        }
        val fileName = apkFile.name
        val versionLabel =
            prefs.getString(KEY_ACTIVE_VERSION_LABEL, null)?.trim().orEmpty().ifBlank { fileName }
        val versionCode = prefs.getInt(KEY_ACTIVE_VERSION_CODE, 0)
        savePendingInstall(fileName, versionLabel, versionCode)
        clearActiveDownload()
        val pending = PendingInstallInfo(versionLabel = versionLabel, versionCode = versionCode, apkFile = apkFile)
        val (currentCode, currentName) = currentVersionInfo()
        if (isPendingInstallObsolete(pending.versionCode, pending.versionLabel, currentCode, currentName)) {
            clearPendingInstall(deleteApk = true)
            return
        }
        emitDownloadComplete(pending)
        android.util.Log.i("AppUpdateInstaller", "download complete pending=${pending.versionLabel} size=${apkFile.length()} path=${apkFile.absolutePath}")
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

        private const val POLL_INTERVAL_MS = 1500L
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
            // APP_VERSION_CODE（如 159）与 Android versionCode（如 1002038）量级不同，不能直接比大小
            val sameScale =
                (pendingVersionCode < 1_000_000 && currentVersionCode < 1_000_000) ||
                    (pendingVersionCode >= 1_000_000 && currentVersionCode >= 1_000_000)
            if (sameScale && pendingVersionCode > 0 && pendingVersionCode <= currentVersionCode) {
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
