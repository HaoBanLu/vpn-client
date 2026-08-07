package com.vpn.member.vpn

import android.net.TrafficStats
import android.os.Build
import android.os.Process
import com.github.kr328.clash.core.Clash
import java.net.NetworkInterface

data class VpnSessionStats(
    val uploadBytes: Long,
    val downloadBytes: Long,
    val durationMs: Long,
)

data class VpnTrafficRates(
    val uploadBps: Long,
    val downloadBps: Long,
)

interface MihomoTrafficReader {
    fun queryTotal(): Long

    fun queryNow(): Long
}

object VpnSessionStatsTracker {
    /** 连接后前几秒不展示速率，避免内核残余造成虚高。 */
    private const val RATE_WARMUP_MS = 3_000L

    /**
     * 采样间隔过短时不算速率。
     * TrafficNow 若被误用，短间隔会把「几百 KB 区间流量」放大成数 Gbps。
     */
    private const val MIN_SAMPLE_MS = 400L

    /** 展示速率上限（200 Mbps，单位 bytes/s）。消费级线路几乎到不了更高，超限视为采样异常。 */
    private const val MAX_DISPLAY_BPS = 25_000_000L

    private val productionMihomoReader =
        object : MihomoTrafficReader {
            override fun queryTotal(): Long = Clash.queryTrafficTotal()

            override fun queryNow(): Long = Clash.queryTrafficNow()
        }

    private var mihomoReaderOverride: MihomoTrafficReader? = null

    private var source: TrafficStatsSource = TrafficStatsSource.NONE
    private var sessionStartMs: Long = 0L

    private var baselineMihomoUpload: Long = 0L
    private var baselineMihomoDownload: Long = 0L

    private var baselineRx: Long = TrafficStats.UNSUPPORTED.toLong()
    private var baselineTx: Long = TrafficStats.UNSUPPORTED.toLong()
    private var vpnInterfaceName: String? = null

    private var prevUploadBytes: Long = 0L
    private var prevDownloadBytes: Long = 0L
    private var prevSampleMs: Long = 0L
    private var emaUploadBps: Long = 0L
    private var emaDownloadBps: Long = 0L

    fun reset() {
        sessionStartMs = System.currentTimeMillis()
        prevUploadBytes = 0L
        prevDownloadBytes = 0L
        prevSampleMs = 0L
        emaUploadBps = 0L
        emaDownloadBps = 0L

        // 冲掉内核「自上次查询」的残余 TrafficNow（仅清缓冲，速率改走累计差）
        runCatching { activeMihomoReader().queryNow() }
        runCatching { activeMihomoReader().queryNow() }

        if (resetMihomoBaseline()) {
            source = TrafficStatsSource.MIHOMO
            return
        }

        source = TrafficStatsSource.SYSTEM
        resetSystemBaseline()
    }

    /** 由前台 Service 定时调用：采样一次并返回快照（同时供通知栏与 UI 使用）。 */
    fun tick(): VpnTrafficSnapshot {
        val stats = snapshot()
        val rates = sampleRates(stats)
        return VpnTrafficSnapshot(stats = stats, rates = rates, source = source)
    }

    fun snapshot(): VpnSessionStats {
        val duration =
            if (sessionStartMs > 0L) {
                (System.currentTimeMillis() - sessionStartMs).coerceAtLeast(0L)
            } else {
                0L
            }
        return when (source) {
            TrafficStatsSource.MIHOMO -> snapshotMihomo(duration)
            TrafficStatsSource.SYSTEM -> snapshotSystem(duration)
            TrafficStatsSource.NONE -> VpnSessionStats(0L, 0L, duration)
        }
    }

    /**
     * 用「会话累计字节差 / 时间差」算速率，不用 TrafficNow。
     * TrafficNow 是「距上次 query 的区间字节」，若被别处消费或间隔极短，会算出上万 Mbps。
     */
    fun sampleRates(stats: VpnSessionStats): VpnTrafficRates {
        val nowMs = System.currentTimeMillis()
        val deltaMs = if (prevSampleMs > 0L) (nowMs - prevSampleMs) else 0L

        if (deltaMs >= MIN_SAMPLE_MS) {
            val upDelta = (stats.uploadBytes - prevUploadBytes).coerceAtLeast(0L)
            val downDelta = (stats.downloadBytes - prevDownloadBytes).coerceAtLeast(0L)
            val rawUploadBps = upDelta * 1000L / deltaMs
            val rawDownloadBps = downDelta * 1000L / deltaMs
            // 超上限视为脏采样（短间隔放大 / 编码异常），不喂给 EMA
            emaUploadBps =
                TrafficRateEma.smooth(
                    emaUploadBps,
                    if (rawUploadBps > MAX_DISPLAY_BPS) emaUploadBps else rawUploadBps,
                )
            emaDownloadBps =
                TrafficRateEma.smooth(
                    emaDownloadBps,
                    if (rawDownloadBps > MAX_DISPLAY_BPS) emaDownloadBps else rawDownloadBps,
                )
            prevUploadBytes = stats.uploadBytes
            prevDownloadBytes = stats.downloadBytes
            prevSampleMs = nowMs
        } else if (prevSampleMs <= 0L) {
            prevUploadBytes = stats.uploadBytes
            prevDownloadBytes = stats.downloadBytes
            prevSampleMs = nowMs
        }

        return VpnTrafficRates(
            uploadBps = capDisplayRate(emaUploadBps),
            downloadBps = capDisplayRate(emaDownloadBps),
        )
    }

    private fun capDisplayRate(bps: Long): Long {
        if (sessionStartMs <= 0L) return 0L
        val elapsed = System.currentTimeMillis() - sessionStartMs
        if (elapsed < RATE_WARMUP_MS) return 0L
        return bps.coerceIn(0L, MAX_DISPLAY_BPS)
    }

    private fun resetMihomoBaseline(): Boolean {
        val total = readMihomoTotal() ?: return false
        baselineMihomoUpload = MihomoTrafficCodec.unpackUpload(total)
        baselineMihomoDownload = MihomoTrafficCodec.unpackDownload(total)
        return true
    }

    private fun resetSystemBaseline() {
        vpnInterfaceName = findVpnInterfaceName()
        val interfaceName = vpnInterfaceName
        if (interfaceName != null && supportsInterfaceTrafficStats()) {
            baselineRx = readInterfaceRxBytes(interfaceName).coerceAtLeast(0L)
            baselineTx = readInterfaceTxBytes(interfaceName).coerceAtLeast(0L)
        } else {
            vpnInterfaceName = null
            val uid = Process.myUid()
            baselineRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
            baselineTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        }
    }

    private fun snapshotMihomo(durationMs: Long): VpnSessionStats {
        val total = readMihomoTotal()
        if (total == null) {
            source = TrafficStatsSource.SYSTEM
            resetSystemBaseline()
            return snapshotSystem(durationMs)
        }
        val upload = (MihomoTrafficCodec.unpackUpload(total) - baselineMihomoUpload).coerceAtLeast(0L)
        val download = (MihomoTrafficCodec.unpackDownload(total) - baselineMihomoDownload).coerceAtLeast(0L)
        return VpnSessionStats(uploadBytes = upload, downloadBytes = download, durationMs = durationMs)
    }

    private fun snapshotSystem(durationMs: Long): VpnSessionStats {
        val interfaceName = vpnInterfaceName ?: findVpnInterfaceName().also { vpnInterfaceName = it }
        val currentRx: Long
        val currentTx: Long
        if (interfaceName != null && supportsInterfaceTrafficStats()) {
            currentRx = readInterfaceRxBytes(interfaceName).coerceAtLeast(0L)
            currentTx = readInterfaceTxBytes(interfaceName).coerceAtLeast(0L)
        } else {
            val uid = Process.myUid()
            currentRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
            currentTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        }
        val rx =
            if (baselineRx == TrafficStats.UNSUPPORTED.toLong()) 0L else (currentRx - baselineRx).coerceAtLeast(0L)
        val tx =
            if (baselineTx == TrafficStats.UNSUPPORTED.toLong()) 0L else (currentTx - baselineTx).coerceAtLeast(0L)
        return VpnSessionStats(uploadBytes = tx, downloadBytes = rx, durationMs = durationMs)
    }

    private fun readMihomoTotal(): Long? =
        runCatching {
            activeMihomoReader().queryTotal()
        }.getOrNull()

    private fun activeMihomoReader(): MihomoTrafficReader = mihomoReaderOverride ?: productionMihomoReader

    /** getRxBytes(String)/getTxBytes(String) 自 API 31 才有，低版本会 NoSuchMethodError。 */
    private fun supportsInterfaceTrafficStats(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun readInterfaceRxBytes(interfaceName: String): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TrafficStats.getRxBytes(interfaceName)
        } else {
            TrafficStats.UNSUPPORTED.toLong()
        }

    private fun readInterfaceTxBytes(interfaceName: String): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TrafficStats.getTxBytes(interfaceName)
        } else {
            TrafficStats.UNSUPPORTED.toLong()
        }

    private fun findVpnInterfaceName(): String? =
        runCatching {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .firstOrNull { networkInterface ->
                    networkInterface.isUp &&
                        !networkInterface.isLoopback &&
                        networkInterface.isPointToPoint &&
                        (networkInterface.name.startsWith("tun") || networkInterface.name.startsWith("ppp"))
                }?.name
        }.getOrNull()

    fun formatBytes(bytes: Long): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }

    fun formatRate(bps: Long): String =
        when {
            bps < 1024 -> "$bps B/s"
            bps < 1024 * 1024 -> "%.0f KB/s".format(bps / 1024.0)
            else -> "%.1f MB/s".format(bps / (1024.0 * 1024.0))
        }

    /** 连接页速率：统一 KB/s 一位小数，≥1 Mbps 用兆比特；避免「—」「&lt; 1 KB/s」等碎文案。 */
    fun formatSpeed(bytesPerSecond: Long): String {
        val bytes = bytesPerSecond.coerceAtLeast(0L)
        val bitsPerSecond = bytes * 8L
        if (bitsPerSecond >= 1_000_000) {
            return "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
        }
        return "%.1f KB/s".format(bytes / 1024.0)
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    internal fun setMihomoReaderForTest(reader: MihomoTrafficReader?) {
        mihomoReaderOverride = reader
    }
}
