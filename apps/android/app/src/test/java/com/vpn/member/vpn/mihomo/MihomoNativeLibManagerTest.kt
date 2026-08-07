package com.vpn.member.vpn.mihomo

import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoNativeLibManagerTest {
    @Test
    fun resolveAbi_prefersArm64() {
        assertEquals("arm64-v8a", MihomoNativeLibManager.resolveAbi())
    }
}
