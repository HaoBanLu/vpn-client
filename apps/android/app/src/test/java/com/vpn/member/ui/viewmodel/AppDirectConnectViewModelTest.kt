package com.vpn.member.ui.viewmodel

import com.vpn.member.data.device.LaunchableApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDirectConnectViewModelTest {
    private val apps =
        listOf(
            LaunchableApp("com.bank.app", "Bank"),
            LaunchableApp("com.chat.app", "Chat"),
        )

    @Test
    fun filterLaunchableAppsMatchesLabelOrPackage() {
        val filtered = AppDirectConnectViewModel.filterLaunchableApps(apps, "bank")
        assertEquals(1, filtered.size)
        assertEquals("com.bank.app", filtered.first().packageName)
    }

    @Test
    fun filterLaunchableAppsBlankQueryReturnsAll() {
        val filtered = AppDirectConnectViewModel.filterLaunchableApps(apps, "  ")
        assertEquals(2, filtered.size)
    }
}
