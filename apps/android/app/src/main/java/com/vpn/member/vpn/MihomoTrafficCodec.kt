package com.vpn.member.vpn

import com.github.kr328.clash.core.model.Traffic

/** 解析 Mihomo / Clash 内核打包的流量值（与 mihomo-core Traffic.kt 编码一致）。 */
object MihomoTrafficCodec {
    fun unpackUpload(traffic: Traffic): Long = scaleTraffic(traffic ushr 32)

    fun unpackDownload(traffic: Traffic): Long = scaleTraffic(traffic and 0xFFFFFFFFL)

    fun totalBytes(traffic: Traffic): Long = unpackUpload(traffic) + unpackDownload(traffic)

    internal fun scaleTraffic(value: Long): Long {
        val type = (value ushr 30) and 0x3
        val data = value and 0x3FFFFFFF
        return when (type) {
            0L -> data
            1L -> data * 1024
            2L -> data * 1024 * 1024
            3L -> data * 1024 * 1024 * 1024
            else -> 0L
        }
    }
}
