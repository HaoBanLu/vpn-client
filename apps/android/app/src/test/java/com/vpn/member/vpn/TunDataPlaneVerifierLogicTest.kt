package com.vpn.member.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunDataPlaneVerifierLogicTest {
    @Test
    fun fullTunnel_overseas_vpnNetworkOk_isPass() {
        assertTrue(
            TunDataPlaneVerifier.evaluateDataplanePass(
                splitDomesticDirect = false,
                domesticReturn = false,
                domesticOk = false,
                overseasOk = false,
                tunTcpSeen = false,
                trafficGrew = false,
                vpnNetworkOk = true,
            ),
        )
    }

    @Test
    fun fullTunnel_domesticReturn_vpnNetworkOk_isPass() {
        assertTrue(
            TunDataPlaneVerifier.evaluateDataplanePass(
                splitDomesticDirect = false,
                domesticReturn = true,
                domesticOk = false,
                overseasOk = false,
                tunTcpSeen = false,
                trafficGrew = false,
                vpnNetworkOk = true,
            ),
        )
    }

    @Test
    fun fullTunnel_mixedOnlyWithoutVpnNetwork_isNotPass() {
        assertFalse(
            TunDataPlaneVerifier.evaluateDataplanePass(
                splitDomesticDirect = false,
                domesticReturn = false,
                domesticOk = true,
                overseasOk = true,
                tunTcpSeen = false,
                trafficGrew = false,
                tunDownloadGrew = false,
                vpnNetworkOk = false,
            ),
        )
    }

    @Test
    fun fullTunnel_domesticReturn_mixedAndTunWithoutVpnNetwork_isNotPass() {
        // 回归：禁止 mixed + TUN 字节在无系统 VPN 成功时放行（假连根因）
        assertFalse(
            TunDataPlaneVerifier.evaluateDataplanePass(
                splitDomesticDirect = false,
                domesticReturn = true,
                domesticOk = true,
                overseasOk = false,
                tunTcpSeen = true,
                trafficGrew = true,
                tunDownloadGrew = true,
                vpnNetworkOk = false,
            ),
        )
    }

    @Test
    fun fullTunnel_strongTunEvidenceWithoutVpnNetwork_isNotPass() {
        assertFalse(
            TunDataPlaneVerifier.evaluateDataplanePass(
                splitDomesticDirect = false,
                domesticReturn = false,
                domesticOk = false,
                overseasOk = false,
                tunTcpSeen = true,
                trafficGrew = true,
                tunDownloadGrew = true,
                vpnNetworkOk = false,
            ),
        )
    }

    @Test
    fun fullTunnel_overseasOkWithTunButNoVpnNetwork_isNotPass() {
        assertFalse(
            TunDataPlaneVerifier.evaluateDataplanePass(
                splitDomesticDirect = false,
                domesticReturn = false,
                domesticOk = false,
                overseasOk = true,
                tunTcpSeen = true,
                trafficGrew = false,
                vpnNetworkOk = false,
            ),
        )
    }

    @Test
    fun split_domesticOrTun_isPass() {
        assertTrue(
            TunDataPlaneVerifier.evaluateDataplanePass(
                splitDomesticDirect = true,
                domesticReturn = false,
                domesticOk = true,
                overseasOk = false,
                tunTcpSeen = false,
                trafficGrew = false,
                vpnNetworkOk = false,
            ),
        )
        assertTrue(
            TunDataPlaneVerifier.evaluateDataplanePass(
                splitDomesticDirect = true,
                domesticReturn = false,
                domesticOk = false,
                overseasOk = false,
                tunTcpSeen = false,
                trafficGrew = false,
                tunDownloadGrew = true,
                vpnNetworkOk = false,
            ),
        )
    }

    @Test
    fun fullTunnel_nothing_isNotPass() {
        assertFalse(
            TunDataPlaneVerifier.evaluateDataplanePass(
                splitDomesticDirect = false,
                domesticReturn = false,
                domesticOk = false,
                overseasOk = false,
                tunTcpSeen = false,
                trafficGrew = false,
                vpnNetworkOk = false,
            ),
        )
    }
}
