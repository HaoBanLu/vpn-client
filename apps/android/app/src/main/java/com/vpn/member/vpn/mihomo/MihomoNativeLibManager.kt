package com.vpn.member.vpn.mihomo

import android.app.Application
import android.os.Build
import com.vpn.member.BuildConfig
import com.vpn.member.debug.AppDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Release 瘦包模式：APK 不内置 libclash/libbridge，首连前从 CMFA Release 拉取并解压。
 * 与 [MihomoGeoAssetManager] 一致，缩小安装包体积（arm64 安装包约 3MB vs ~49MB）。
 */
object MihomoNativeLibManager {
    private const val MIN_LIBCLASH_BYTES = 40_000_000L
    private const val MIN_LIBBRIDGE_BYTES = 10_000L

    private val installMutex = Mutex()
    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()

    suspend fun ensureReady(app: Application): File {
        val abi = resolveAbi()
        val targetDir = File(app.filesDir, "native-libs/$abi").apply { mkdirs() }
        if (isInstalled(targetDir)) return targetDir
        return installMutex.withLock {
            if (isInstalled(targetDir)) return@withLock targetDir
            withContext(Dispatchers.IO) {
                downloadAndExtract(app, abi, targetDir)
            }
            targetDir
        }
    }

    fun resolveAbi(): String {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        return when {
            abis.contains("arm64-v8a") -> "arm64-v8a"
            abis.contains("armeabi-v7a") -> "armeabi-v7a"
            abis.contains("x86_64") -> "x86_64"
            else -> abis.firstOrNull() ?: "arm64-v8a"
        }
    }

    private fun isInstalled(dir: File): Boolean {
        val clash = File(dir, "libclash.so")
        val bridge = File(dir, "libbridge.so")
        return clash.isFile && clash.length() >= MIN_LIBCLASH_BYTES &&
            bridge.isFile && bridge.length() >= MIN_LIBBRIDGE_BYTES
    }

    private fun downloadAndExtract(app: Application, abi: String, targetDir: File) {
        val version = BuildConfig.MIHOMO_NATIVE_VERSION.trim().ifEmpty { "v2.11.30" }
        val versionTag = version.removePrefix("v")
        val apkName =
            when (abi) {
                "arm64-v8a" -> "cmfa-$versionTag-meta-arm64-v8a-release.apk"
                "armeabi-v7a" -> "cmfa-$versionTag-meta-armeabi-v7a-release.apk"
                "x86_64" -> "cmfa-$versionTag-meta-x86_64-release.apk"
                else -> error("不支持的 ABI: $abi")
            }
        val url =
            BuildConfig.MIHOMO_NATIVE_APK_URL.trim().ifEmpty {
                "https://github.com/MetaCubeX/ClashMetaForAndroid/releases/download/$version/$apkName"
            }
        AppDebugLogger.info(
            category = "mihomo",
            message = "开始下载 Mihomo native 库",
            context = mapOf("abi" to abi, "url" to url),
        )
        val tempApk = File(app.cacheDir, "mihomo-native-$abi.apk.download")
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("下载 Mihomo native 失败: HTTP ${response.code}")
            }
            val body = response.body ?: error("下载 Mihomo native 失败: 空响应")
            FileOutputStream(tempApk).use { out -> body.byteStream().copyTo(out) }
        }
        extractNativeLibs(tempApk, abi, targetDir)
        tempApk.delete()
        AppDebugLogger.info(
            category = "mihomo",
            message = "Mihomo native 库已就绪",
            context = mapOf("abi" to abi, "dir" to targetDir.absolutePath),
        )
    }

    private fun extractNativeLibs(apkFile: File, abi: String, targetDir: File) {
        ZipFile(apkFile).use { zip ->
            val entries =
                listOf(
                    "lib/$abi/libclash.so" to "libclash.so",
                    "lib/$abi/libbridge.so" to "libbridge.so",
                )
            for ((zipPath, fileName) in entries) {
                val entry = zip.getEntry(zipPath) ?: error("APK 中缺少 $zipPath")
                val outFile = File(targetDir, fileName)
                val tmp = File(targetDir, "$fileName.part")
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                if (!tmp.renameTo(outFile)) {
                    tmp.copyTo(outFile, overwrite = true)
                    tmp.delete()
                }
            }
        }
        if (!isInstalled(targetDir)) {
            error("Mihomo native 解压校验失败")
        }
    }
}
