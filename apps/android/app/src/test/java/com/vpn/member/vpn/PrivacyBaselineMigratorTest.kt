package com.vpn.member.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyBaselineMigratorTest {
    @Test
    fun migratesKillSwitchWhenUserNeverModified() {
        assertTrue(PrivacyBaselineMigrator.shouldMigrateKillSwitchToEnabled(hasUserModifiedKillSwitch = false))
    }

    @Test
    fun keepsKillSwitchWhenUserExplicitlyModified() {
        assertFalse(PrivacyBaselineMigrator.shouldMigrateKillSwitchToEnabled(hasUserModifiedKillSwitch = true))
    }

    @Test
    fun baselineVersionIncludesBlockOnConnectFailureOff() {
        assertTrue(PrivacyBaselineMigrator.CURRENT_VERSION >= 2)
    }
}
