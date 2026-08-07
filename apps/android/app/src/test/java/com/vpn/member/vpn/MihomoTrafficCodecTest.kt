package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoTrafficCodecTest {
    @Test
    fun unpack_readsScaledUploadAndDownload() {
        val packed = encodeScaled(upload = 2048, download = 4096)
        assertEquals(2048L, MihomoTrafficCodec.unpackUpload(packed))
        assertEquals(4096L, MihomoTrafficCodec.unpackDownload(packed))
    }

    @Test
    fun unpack_appliesKibiScaleType() {
        val upload = encodeWithScaleType(type = 1, data = 5)
        val download = encodeWithScaleType(type = 2, data = 3)
        val packed = (upload shl 32) or download
        assertEquals(5L * 1024, MihomoTrafficCodec.unpackUpload(packed))
        assertEquals(3L * 1024 * 1024, MihomoTrafficCodec.unpackDownload(packed))
    }

    @Test
    fun totalBytes_sumsUploadAndDownload() {
        val packed = encodeScaled(upload = 100, download = 200)
        assertEquals(300L, MihomoTrafficCodec.totalBytes(packed))
    }

    private fun encodeScaled(upload: Long, download: Long): Long = (upload shl 32) or download

    private fun encodeWithScaleType(type: Long, data: Long): Long = (type shl 30) or data
}
