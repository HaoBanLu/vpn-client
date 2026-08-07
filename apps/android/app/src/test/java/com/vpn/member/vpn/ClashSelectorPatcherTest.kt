package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashSelectorPatcherTest {
    @Test
    fun apply_patchesManualAndProxyGroup() {
        val calls = mutableListOf<Pair<String, String>>()
        val ok =
            ClashSelectorPatcher.apply("武汉") { group, selection ->
                calls += group to selection
                group == "手动选择" || group == "Proxy"
            }
        assertTrue(ok)
        assertEquals(
            listOf(
                "手动选择" to "武汉",
                "回国专线" to "武汉",
                "GLOBAL" to "武汉",
                "Proxy" to "手动选择",
            ),
            calls.take(4),
        )
    }
}
