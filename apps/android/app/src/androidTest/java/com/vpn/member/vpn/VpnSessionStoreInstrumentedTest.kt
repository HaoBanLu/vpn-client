package com.vpn.member.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnSessionStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: VpnSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vpn_session_store", Context.MODE_PRIVATE).edit().clear().apply()
        store = VpnSessionStore(context)
    }

    @Test
    fun saveAndReadSnapshot() {
        store.saveSnapshot(
            VpnSessionSnapshot(
                wasUserConnected = true,
                nodeName = "新加坡1",
                region = "sg",
                profile = ConnectionScenario.PROFILE_OVERSEAS_WEAK,
                routeMode = AppRouteMode.FULL,
                connectionScenario = ConnectionScenario.AUTO,
            ),
        )
        val snapshot = store.readSnapshot()
        assertNotNull(snapshot)
        assertEquals("新加坡1", snapshot?.nodeName)
    }

    @Test
    fun clearSnapshotRemovesReconnectAttempts() {
        store.saveSnapshot(
            VpnSessionSnapshot(
                wasUserConnected = true,
                nodeName = "n1",
                region = null,
                profile = ConnectionScenario.PROFILE_OVERSEAS_WEAK,
                routeMode = AppRouteMode.FULL,
                connectionScenario = ConnectionScenario.AUTO,
            ),
        )
        store.incrementReconnectAttempts()
        store.clearSnapshot()
        assertNull(store.readSnapshot())
        assertEquals(0, store.getReconnectAttempts())
    }
}
