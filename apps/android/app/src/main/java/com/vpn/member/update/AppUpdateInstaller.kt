package com.vpn.member.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vpn.member.BuildConfig
import java.io.File
import java.lang.ref.WeakReference

/** 应用内更新：下载 APK 并引导安装；待安装状态持久化，授权后可继续。 */
class AppUpdateInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activityRef: WeakReference<Activity>? = null
    private var listener: Listener? = null
    private var pendingFileName: String? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private var awaitingPermissionReturn = false

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
        /** 无待安装包 */
        NoPending,
        /** 安装包文件丢失 */
        MissingApk,
        /** 需要先授权「安装未知应用」 */
        NeedPermission,
        /** 已拉起系统安装界面 */
        Launched,
        /** 拉起安装失败 */
        Failed,
    }

    interface Listener {
        fun onDownloadStarted()

        fun onDownloadCompleted(pending: PendingInstallInfo)

        fun onDownloadFailed(message: String)

        fun onInstallPermissionRequired(pending: PendingInstallInfo)
    }

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun startDownload(url: String, versionLabel: String, versionCode: Int) {
        if (url.isBlank()) {
            notifyFailed("下载地址无效，请稍后重试")
            return
        }

        cancelPendingReceiver()
        clearPendingInstall()

        val fileName = "kuayun-${sanitizeFileName(versionLabel)}.apk"
        pendingFileName = fileName

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("跨云 App 更新")
            setDescription("正在下载 $versionLabel")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                fileName,
            )
        }

        val downloadId = downloadManager.enqueue(request)
        saveActiveDownload(downloadId, fileName, versionLabel, versionCode)
        registerDownloadReceiver(downloadId)
        notifyStarted()
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun hasPendingInstall(): Boolean = readPendingInstall() != null

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
            prefs.getString(KEY_PENDING_INSTALL_VERSION, null)?.trim().orEmpty()
                .ifBlank { fileName }
        val versionCode = prefs.getInt(KEY_PENDING_INSTALL_VERSION_CODE, 0)
        val pending = PendingInstallInfo(versionLabel = versionLabel, versionCode = versionCode, apkFile = apkFile)
        if (isPendingInstallObsolete(pending.versionCode, pending.versionLabel, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
            clearPendingInstall(deleteApk = true)
            return null
        }
        return pending
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        awaitingPermissionReturn = true
        runOnMain {
            runCatching {
                val intent = Intent(
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

    /** 从授权页返回或用户点击「立即安装」时调用（需在主线程）。 */
    fun tryInstallPendingApk(): InstallAttemptResult {
        val pending = readPendingInstall()
            ?: return InstallAttemptResult.NoPending

        if (!pending.apkFile.exists()) {
            clearPendingInstall()
            return InstallAttemptResult.MissingApk
        }

        if (!canInstallPackages()) {
            runOnMain { listener?.onInstallPermissionRequired(pending) }
            return InstallAttemptResult.NeedPermission
        }

        return if (launchInstallIntent(pending.apkFile)) {
            InstallAttemptResult.Launched
        } else {
            InstallAttemptResult.Failed
        }
    }

    fun dismissPendingInstall() {
        clearPendingInstall()
    }

    fun handleDownloadComplete(downloadId: Long) {
        if (downloadId != prefs.getLong(KEY_DOWNLOAD_ID, -1L)) return

        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        cursor.use {
            if (!it.moveToFirst()) {
                notifyFailed("下载失败，请稍后重试")
                return
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                notifyFailed("下载失败，请稍后重试")
                return
            }
        }

        val fileName = pendingFileName ?: prefs.getString(KEY_ACTIVE_FILE_NAME, null)
        if (fileName.isNullOrBlank()) {
            notifyFailed("安装包路径无效")
            return
        }

        val apkFile = resolveApkFile(fileName)
        if (!apkFile.exists()) {
            notifyFailed("安装包不存在，请重新下载")
            return
        }

        val versionLabel =
            prefs.getString(KEY_ACTIVE_VERSION_LABEL, null)?.trim().orEmpty()
                .ifBlank { fileName }
        val versionCode = prefs.getInt(KEY_ACTIVE_VERSION_CODE, 0)

        savePendingInstall(fileName, versionLabel, versionCode)
        clearActiveDownload()
        cancelPendingReceiver()

        val pending = PendingInstallInfo(versionLabel = versionLabel, versionCode = versionCode, apkFile = apkFile)
        if (isPendingInstallObsolete(pending.versionCode, pending.versionLabel, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
            clearPendingInstall(deleteApk = true)
            return
        }
        notifyCompleted(pending)

        runOnMain {
            when (tryInstallPendingApk()) {
                InstallAttemptResult.NeedPermission -> {
                    showToast("下载完成，请允许安装未知应用后点击「立即安装」")
                    openInstallPermissionSettings()
                }
                InstallAttemptResult.Launched -> {
                    showToast("请按提示完成安装")
                }
                InstallAttemptResult.Failed -> {
                    notifyFailed("无法打开安装程序，请点击「立即安装」重试")
                }
                else -> Unit
            }
        }
    }

    private fun registerDownloadReceiver(downloadId: Long) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
                if (id != downloadId) return
                cancelPendingReceiver()
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

    private fun launchInstallIntent(apkFile: File): Boolean {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val activity = activityRef?.get()
        return runCatching {
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            }
            true
        }.getOrElse { false }
    }

    private fun notifyStarted() {
        runOnMain {
            listener?.onDownloadStarted()
            showToast("已开始下载，完成后将提示安装")
        }
    }

    private fun notifyCompleted(pending: PendingInstallInfo) {
        runOnMain {
            listener?.onDownloadCompleted(pending)
        }
    }

    private fun notifyFailed(message: String) {
        clearActiveDownload()
        runOnMain {
            listener?.onDownloadFailed(message)
            showToast(message)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun cancelPendingReceiver() {
        downloadReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        downloadReceiver = null
    }

    private fun resolveApkFile(fileName: String): File =
        File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName,
        )

    private fun sanitizeFileName(value: String): String = sanitizeFileNameForTest(value)

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
        val legacyFile = prefs.getString(LEGACY_KEY_FILE_NAME, null)?.trim().orEmpty()
        if (legacyFile.isBlank()) return
        if (!prefs.getString(KEY_PENDING_INSTALL_FILE, null).isNullOrBlank()) {
            prefs.edit().remove(LEGACY_KEY_FILE_NAME).apply()
            return
        }
        val apkFile = resolveApkFile(legacyFile)
        if (apkFile.exists()) {
            savePendingInstall(legacyFile, legacyFile, versionCode = 0)
        }
        prefs.edit().remove(LEGACY_KEY_FILE_NAME).remove(LEGACY_KEY_DOWNLOAD_ID).apply()
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
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_ACTIVE_FILE_NAME)
            .remove(KEY_ACTIVE_VERSION_LABEL)
            .remove(KEY_ACTIVE_VERSION_CODE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "app_update_download"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_ACTIVE_FILE_NAME = "active_file_name"
        private const val KEY_ACTIVE_VERSION_LABEL = "active_version_label"
        private const val KEY_ACTIVE_VERSION_CODE = "active_version_code"
        private const val KEY_PENDING_INSTALL_FILE = "pending_install_file"
        private const val KEY_PENDING_INSTALL_VERSION = "pending_install_version"
        private const val KEY_PENDING_INSTALL_VERSION_CODE = "pending_install_version_code"
        private const val LEGACY_KEY_FILE_NAME = "file_name"
        private const val LEGACY_KEY_DOWNLOAD_ID = "download_id"

        /** 待安装包版本不高于当前已安装版本时，视为过期并清除。 */
        internal fun isPendingInstallObsolete(
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

        /** 从待安装记录或 APK 文件名提取可比较的版本名。 */
        internal fun normalizePendingVersionLabel(label: String): String {
            val trimmed = label.trim()
            val fromFile =
                Regex("""kuayun-(.+)\.apk""", RegexOption.IGNORE_CASE)
                    .find(trimmed)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
            return fromFile?.takeIf { it.isNotBlank() } ?: trimmed
        }

        /** 供单元测试校验文件名消毒逻辑。 */
        internal fun sanitizeFileNameForTest(value: String): String {
            val cleaned = value.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
            return cleaned.ifBlank { "latest" }
        }
    }
}
