package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDirectConnectStoreTest {
    @Test
    fun normalizePackages_doesNotForceSelfPackage() {
        val normalized = AppDirectConnectStore.normalizePackages(setOf("com.example.app"))
        assertTrue(normalized.contains("com.example.app"))
        assertFalse(normalized.contains("com.vpn.member"))
        assertEquals(1, normalized.size)
    }

    @Test
    fun normalizePackages_deduplicatesAndIgnoresBlank() {
        val normalized =
            AppDirectConnectStore.normalizePackages(
                setOf("com.bank.app", "com.bank.app", " ", ""),
            )
        assertEquals(1, normalized.size)
        assertTrue(normalized.contains("com.bank.app"))
    }

    @Test
    fun normalizePackages_includesCloneMasterWhenUserSelected() {
        val normalized = AppDirectConnectStore.normalizePackages(setOf("com.qihoo.magic"))
        assertTrue(normalized.contains("com.qihoo.magic"))
        assertFalse(normalized.contains("com.vpn.member"))
    }

    @Test
    fun mergeWithSelf_noLongerForcesSelf() {
        val merged = AppDirectConnectStore.mergeWithSelf(setOf("com.example.app"), "com.vpn.member")
        assertTrue(merged.contains("com.example.app"))
        assertFalse(merged.contains("com.vpn.member"))
    }
}
