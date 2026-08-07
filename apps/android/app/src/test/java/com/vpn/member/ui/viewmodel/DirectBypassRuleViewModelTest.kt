package com.vpn.member.ui.viewmodel

import com.vpn.member.vpn.DirectBypassRule
import com.vpn.member.vpn.DirectBypassRuleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectBypassRuleViewModelTest {
    @Test
    fun isDuplicate_detectsSameTypeAndValue() {
        val existing =
            listOf(
                DirectBypassRule(
                    id = "1",
                    type = DirectBypassRuleType.DOMAIN_SUFFIX,
                    value = "example.com",
                ),
            )
        val duplicate =
            DirectBypassRule(
                id = "2",
                type = DirectBypassRuleType.DOMAIN_SUFFIX,
                value = "Example.com",
            )
        assertTrue(DirectBypassRuleViewModel.isDuplicate(duplicate, existing))
    }

    @Test
    fun isDuplicate_allowsDifferentType() {
        val existing =
            listOf(
                DirectBypassRule(
                    id = "1",
                    type = DirectBypassRuleType.DOMAIN,
                    value = "example.com",
                ),
            )
        val other =
            DirectBypassRule(
                id = "2",
                type = DirectBypassRuleType.DOMAIN_SUFFIX,
                value = "example.com",
            )
        assertFalse(DirectBypassRuleViewModel.isDuplicate(other, existing))
    }
}
