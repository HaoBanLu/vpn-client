package com.vpn.kuayun.vpn

import android.net.TrafficStats
import android.os.Build
import android.os.Process
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

object VpnSessionStatsTracker {
    /** 与前端 session-throughput.ts 对齐，通知与连接页共用。 */
    const val RATE_WARMUP_MS = 3_000L
    const val MIN_SAMPLE_MS = 400L
    const val RATE_CACHE_MS = 300L
    const val MAX_DISPLAY_BPS = 25_000_000L
    const val RATE_EMA_ALPHA = 0.35

    private var baselineRx: Long = TrafficStats.UNSUPPORTED.toLong()
    private var baselineTx: Long = TrafficStats.UNSUPPORTED.toLong()
    private var sessionStartMs: Long = 0L
    private var vpnInterfaceName: String? = null
    private var prevUploadBytes: Long = 0L
    private var prevDownloadBytes: Long = 0L
    private var prevSampleMs: Long = 0L
    private var lastUploadBps: Long = 0L
    private var lastDownloadBps: Long = 0L

    fun reset() {
        vpnInterfaceName = findVpnInterfaceName()
        val interfaceName = vpnInterfaceName
        if (interfaceName != null && supportsInterfaceTrafficStats()) {
            baselineRx = readInterfaceRxBytes(interfaceName).coerceAtLeast(0L)
            baselineTx = readInterfaceTxBytes(interfaceName).coerceAtLeast(0L)
        } else {
            resetUidBaseline()
        }
        sessionStartMs = System.currentTimeMillis()
        prevUploadBytes = 0L
        prevDownloadBytes = 0L
        prevSampleMs = 0L
        lastUploadBps = 0L
        lastDownloadBps = 0L
    }

    private fun resetUidBaseline() {
        val uid = Process.myUid()
        baselineRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        baselineTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
    }

    fun snapshot(): VpnSessionStats {
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
        val duration = if (sessionStartMs > 0L) (System.currentTimeMillis() - sessionStartMs).coerceAtLeast(0L) else 0L
        return VpnSessionStats(uploadBytes = tx, downloadBytes = rx, durationMs = duration)
    }

    /**
     * 展示速率：warmup + EMA + cap。
     * 300ms 内重复调用返回缓存，避免通知循环与 getStats 互相偷采样间隔。
     */
    @Synchronized
    fun sampleRates(stats: VpnSessionStats): VpnTrafficRates {
        val now = System.currentTimeMillis()
        val sessionElapsed =
            if (sessionStartMs > 0L) (now - sessionStartMs).coerceAtLeast(0L) else 0L
        if (sessionElapsed < RATE_WARMUP_MS) {
            lastUploadBps = 0L
            lastDownloadBps = 0L
            prevUploadBytes = stats.uploadBytes
            prevDownloadBytes = stats.downloadBytes
            prevSampleMs = now
            return peekRates()
        }
        if (prevSampleMs > 0L && now - prevSampleMs < RATE_CACHE_MS) {
            return peekRates()
        }
        if (prevSampleMs > 0L) {
            val deltaMs = now - prevSampleMs
            if (deltaMs < MIN_SAMPLE_MS) {
                return peekRates()
            }
            lastUploadBps =
                nextDisplayBps(lastUploadBps, stats.uploadBytes - prevUploadBytes, deltaMs)
            lastDownloadBps =
                nextDisplayBps(lastDownloadBps, stats.downloadBytes - prevDownloadBytes, deltaMs)
        }
        prevUploadBytes = stats.uploadBytes
        prevDownloadBytes = stats.downloadBytes
        prevSampleMs = now
        return peekRates()
    }

    @Synchronized
    fun peekRates(): VpnTrafficRates = VpnTrafficRates(lastUploadBps, lastDownloadBps)

    private fun nextDisplayBps(previous: Long, deltaBytes: Long, deltaMs: Long): Long {
        val instant = deltaBytes.coerceAtLeast(0L) * 1000L / deltaMs.coerceAtLeast(1L)
        if (instant > MAX_DISPLAY_BPS) return previous
        val clamped = instant.coerceAtMost(MAX_DISPLAY_BPS)
        if (previous <= 0L) return clamped
        return (RATE_EMA_ALPHA * clamped + (1.0 - RATE_EMA_ALPHA) * previous).toLong().coerceAtLeast(0L)
    }

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

    fun formatBytes(bytes: Long): String {
        val n = bytes.coerceAtLeast(0L)
        return when {
            n < 1024 -> "$n B"
            n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
            n < 1024L * 1024L * 1024L -> "%.1f MB".format(n / (1024.0 * 1024.0))
            else -> "%.2f GB".format(n / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /** 与前端 formatDisplaySpeed 一致：低于 1e6 bit/s 显示 KB/s，否则十进制 Mbps。空闲为 0.0 KB/s。 */
    fun formatSpeed(bps: Long): String {
        val bytes = bps.coerceAtLeast(0L)
        val bitsPerSecond = bytes * 8.0
        return if (bitsPerSecond >= 1_000_000.0) {
            "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
        } else {
            "%.1f KB/s".format(bytes / 1024.0)
        }
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
