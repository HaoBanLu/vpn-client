package com.vpn.kuayun.vpn

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
import java.io.File
import java.lang.ref.WeakReference

/**
 * 应用内更新：DownloadManager 拉 APK → FileProvider 调起安装。
 * 对齐归档 Compose [AppUpdateInstaller]，精简为 Tauri 插件可调用。
 */
class AppUpdateInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null
    private var downloadReceiver: BroadcastReceiver? = null

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun startDownload(url: String, versionLabel: String, versionCode: Int) {
        if (url.isBlank()) {
            toast("下载地址无效，请稍后重试")
            return
        }
        cancelReceiver()
        val fileName = "kuayun-${sanitize(versionLabel)}.apk"
        val request =
            DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("跨云 App 更新")
                setDescription("正在下载 $versionLabel")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setMimeType("application/vnd.android.package-archive")
                setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
            }
        val downloadId = downloadManager.enqueue(request)
        prefs.edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .putString(KEY_FILE, fileName)
            .putString(KEY_VERSION, versionLabel)
            .putInt(KEY_VERSION_CODE, versionCode)
            .apply()
        registerReceiver(downloadId)
        toast("已开始下载，完成后将提示安装")
    }

    private fun registerReceiver(downloadId: Long) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
                    if (id != downloadId) return
                    cancelReceiver()
                    onDownloadComplete(downloadId)
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

    private fun onDownloadComplete(downloadId: Long) {
        if (downloadId != prefs.getLong(KEY_DOWNLOAD_ID, -1L)) return
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) {
                toast("下载失败，请稍后重试")
                return
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                toast("下载失败，请稍后重试")
                return
            }
        }
        val fileName = prefs.getString(KEY_FILE, null)?.trim().orEmpty()
        if (fileName.isBlank()) {
            toast("安装包路径无效")
            return
        }
        val apk = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (!apk.exists()) {
            toast("安装包不存在，请重新下载")
            return
        }
        runOnMain {
            if (!canInstallPackages()) {
                toast("下载完成，请允许安装未知应用后再试")
                openInstallPermissionSettings()
                return@runOnMain
            }
            if (launchInstall(apk)) {
                toast("请按提示完成安装")
            } else {
                toast("无法打开安装程序，请稍后重试")
            }
        }
    }

    private fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    private fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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

    companion object {
        private const val PREFS_NAME = "kuayun_app_update"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_FILE = "file_name"
        private const val KEY_VERSION = "version_label"
        private const val KEY_VERSION_CODE = "version_code"

        fun sanitize(value: String): String {
            val cleaned = value.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
            return cleaned.ifBlank { "latest" }
        }
    }
}
