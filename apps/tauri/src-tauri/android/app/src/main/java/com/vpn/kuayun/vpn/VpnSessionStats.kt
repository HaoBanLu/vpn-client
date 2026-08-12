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

    fun sampleRates(stats: VpnSessionStats): VpnTrafficRates {
        val now = System.currentTimeMillis()
        if (prevSampleMs > 0L) {
            val deltaMs = (now - prevSampleMs).coerceAtLeast(1L)
            val deltaUpload = (stats.uploadBytes - prevUploadBytes).coerceAtLeast(0L)
            val deltaDownload = (stats.downloadBytes - prevDownloadBytes).coerceAtLeast(0L)
            lastUploadBps = deltaUpload * 1000L / deltaMs
            lastDownloadBps = deltaDownload * 1000L / deltaMs
        }
        prevUploadBytes = stats.uploadBytes
        prevDownloadBytes = stats.downloadBytes
        prevSampleMs = now
        return VpnTrafficRates(uploadBps = lastUploadBps, downloadBps = lastDownloadBps)
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

    fun formatBytes(bytes: Long): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }

    fun formatRate(bps: Long): String =
        when {
            bps < 1024 -> "$bps B/s"
            bps < 1024 * 1024 -> "%.0f KB/s".format(bps / 1024.0)
            else -> "%.1f MB/s".format(bps / (1024.0 * 1024.0))
        }

    /** 对齐 Compose 通知栏速率展示。 */
    fun formatSpeed(bps: Long): String =
        when {
            bps < 1024L * 1024L -> "%.1f KB/s".format(bps / 1024.0)
            else -> "%.1f Mbps".format(bps * 8.0 / (1024.0 * 1024.0))
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
}
