package com.vpn.member.vpn.mihomo

import android.content.Context
import com.vpn.member.data.local.AppPreferences
import com.vpn.member.data.local.TokenStore
import com.vpn.member.debug.AppDebugLogger
import com.vpn.member.vpn.AppRouteMode
import com.vpn.member.vpn.ClashConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mihomo geodata / ruleset：首启后台下载或按需拉取，避免在点击「连接」时同步解压大文件。
 * Release 包不再内置 geo（见 build.gradle packaging excludes），以缩小 APK。
 */
/** geodata 就绪策略：全流量不阻塞连接；分流才要求本地 geodata/ruleset。 */
enum class GeoAssetPolicy {
    FULL_TUNNEL,
    SPLIT_ROUTING,
}

object MihomoGeoAssetManager {
    private const val ASSET_DIR = "mihomo"
    private const val GEO_CDN_BASE = "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release"
    private const val RULE_CDN_BASE = "https://cdn.jsdelivr.net/gh/Loyalsoldier/clash-rules@release"

    private val GEO_FILES = listOf("geosite.dat", "geoip.metadb")
    private val RULESET_FILES =
        mapOf(
            "reject.yaml" to "providers/ruleset/reject.yaml",
            "cn.yaml" to "providers/ruleset/cn.yaml",
        )

    private val downloadSpecs =
        listOf(
            DownloadSpec("geosite.dat", "geosite.dat", "$GEO_CDN_BASE/geosite.dat"),
            DownloadSpec("geoip.metadb", "geoip.metadb", "$GEO_CDN_BASE/geoip.metadb"),
            DownloadSpec("providers/ruleset/reject.yaml", "ruleset/reject.yaml", "$RULE_CDN_BASE/reject.txt"),
            DownloadSpec("providers/ruleset/cn.yaml", "ruleset/cn.yaml", "$RULE_CDN_BASE/direct.txt"),
        )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installMutex = Mutex()
    private val installRunning = AtomicBoolean(false)

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

    /** 仅分流模式需要本地 geodata；全流量连接不触发 CDN 下载。 */
    fun needsGeoAssets(context: Context): Boolean {
        val routeMode = TokenStore(context.applicationContext).getRouteMode()
        return AppRouteMode.isDomesticDirectEnabled(routeMode)
    }

    /** 在 Application / 登录 / 进主页时调用，后台准备资源（须已同意隐私政策）。 */
    fun scheduleInstall(context: Context) {
        val appContext = context.applicationContext
        if (!AppPreferences(appContext).isPrivacyAccepted()) return
        if (!needsGeoAssets(appContext)) return
        if (isGeoReady(appContext) && areRulesetsReady(appContext)) return
        if (!installRunning.compareAndSet(false, true)) return
        scope.launch {
            runCatching { installInternal(appContext) }
                .onFailure { e ->
                    AppDebugLogger.warn(
                        category = "mihomo",
                        message = "geodata 后台安装失败",
                        context = mapOf("error" to (e.message ?: "unknown")),
                    )
                }
            installRunning.set(false)
        }
    }

    fun policyForRouteMode(routeModeSplit: Boolean): GeoAssetPolicy =
        if (routeModeSplit) GeoAssetPolicy.SPLIT_ROUTING else GeoAssetPolicy.FULL_TUNNEL

    /** 连接前等待资源就绪（全流量模式不等待，仅后台尝试下载）。 */
    suspend fun awaitReady(
        context: Context,
        policy: GeoAssetPolicy = GeoAssetPolicy.SPLIT_ROUTING,
        timeoutMs: Long = DEFAULT_AWAIT_TIMEOUT_MS,
    ): Boolean {
        val appContext = context.applicationContext
        // 全流量不依赖 geodata；弱网地区跳过 CDN 下载，避免误导性告警与无效重试。
        if (policy != GeoAssetPolicy.FULL_TUNNEL) {
            scheduleInstall(appContext)
        }
        if (policy == GeoAssetPolicy.FULL_TUNNEL) {
            return true
        }
        if (isReady(appContext, policy)) return true
        return withTimeoutOrNull(timeoutMs) {
            while (!isReady(appContext, policy)) {
                delay(POLL_MS)
            }
            true
        } == true
    }

    fun isGeoReady(context: Context): Boolean =
        GEO_FILES.all { File(ClashConfigStore.directory(context), it).isFile }

    fun areRulesetsReady(context: Context): Boolean =
        RULESET_FILES.values.all { file ->
            File(ClashConfigStore.directory(context), file).isFile
        }

    fun isReady(
        context: Context,
        policy: GeoAssetPolicy = GeoAssetPolicy.SPLIT_ROUTING,
    ): Boolean =
        when (policy) {
            GeoAssetPolicy.FULL_TUNNEL -> true
            GeoAssetPolicy.SPLIT_ROUTING -> isGeoReady(context) && areRulesetsReady(context)
        }

    private suspend fun installInternal(context: Context) =
        installMutex.withLock {
            val home = ClashConfigStore.directory(context)
            home.mkdirs()
            var installed = 0
            for (spec in downloadSpecs) {
                val dest = File(home, spec.destRelativePath)
                if (dest.isFile && dest.length() > 0L) continue
                val ok =
                    downloadToFile(spec.url, dest) ||
                        copyAssetIfNeeded(context, "$ASSET_DIR/${spec.assetRelativePath}", dest)
                if (ok) installed++
            }
            migrateLegacyRulesets(home)
            if (installed > 0) {
                AppDebugLogger.info(
                    category = "mihomo",
                    message = "geodata 已就绪",
                    context = mapOf("files" to installed.toString(), "source" to "download_or_asset"),
                )
            }
        }

    private fun downloadToFile(url: String, dest: File): Boolean =
        runCatching {
            dest.parentFile?.mkdirs()
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty body")
                val tmp = File(dest.parentFile, "${dest.name}.download")
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            }
            dest.isFile && dest.length() > 0L
        }.getOrElse {
            AppDebugLogger.warn(
                category = "mihomo",
                message = "geodata 下载失败",
                context = mapOf("url" to url, "error" to (it.message ?: "unknown")),
            )
            false
        }

    private fun copyAssetIfNeeded(context: Context, assetPath: String, dest: File): Boolean {
        if (dest.isFile && dest.length() > 0L) return true
        return runCatching {
            context.assets.open(assetPath).use { input ->
                dest.parentFile?.mkdirs()
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
    }

    private fun migrateLegacyRulesets(home: File) {
        val legacyDir = File(home, "ruleset")
        if (!legacyDir.isDirectory) return
        val targetDir = File(home, "providers/ruleset")
        targetDir.mkdirs()
        legacyDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val dest = File(targetDir, file.name)
            if (!dest.isFile || dest.length() == 0L) {
                file.copyTo(dest, overwrite = true)
            }
        }
    }

    private data class DownloadSpec(
        val destRelativePath: String,
        val assetRelativePath: String,
        val url: String,
    )

    private const val DEFAULT_AWAIT_TIMEOUT_MS = 120_000L
    private const val POLL_MS = 200L
}
