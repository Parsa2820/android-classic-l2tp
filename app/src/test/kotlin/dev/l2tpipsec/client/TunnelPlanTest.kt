package dev.l2tpipsec.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelPlanTest {
    @Test
    fun formatsNegotiatedIpv4() {
        assertEquals("10.8.0.2", TunnelPlan.formatIpv4(intArrayOf(10, 8, 0, 2)))
    }

    @Test
    fun rejectsEmptyOrGarbageAddresses() {
        assertNull(TunnelPlan.clientAddress(intArrayOf(0, 0, 0, 0)))
        assertNull(TunnelPlan.clientAddress(intArrayOf(10, 0, 0)))
        assertNull(TunnelPlan.clientAddress(intArrayOf(10, 0, 0, 256)))
    }

    @Test
    fun usesIpCpDnsWhenTheServerSendsIt() {
        val choice = TunnelPlan.dnsServers(intArrayOf(1, 1, 1, 1), intArrayOf(9, 9, 9, 9))
        assertEquals(listOf("1.1.1.1", "9.9.9.9"), choice.servers)
        assertFalse(choice.usedFallback)
    }

    @Test
    fun fallsBackWhenIpCpOmitsDns() {
        val choice = TunnelPlan.dnsServers(intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0))
        assertEquals(TunnelPlan.FALLBACK_DNS, choice.servers)
        assertTrue(choice.usedFallback)
    }

    @Test
    fun exitCodesMatchTheNativeEngine() {
        assertEquals(0, TunnelExit.OK)
        assertEquals(1, TunnelExit.IKE_FAILED)
        assertEquals(2, TunnelExit.L2TP_FAILED)
        assertEquals(3, TunnelExit.PPP_FAILED)
        assertEquals(12, TunnelExit.STOPPED)
        assertTrue(TunnelExit.message(TunnelExit.IKE_FAILED).contains("IKEv1"))
        assertTrue(TunnelExit.message(TunnelExit.PPP_FAILED).contains("PPP"))
    }
}
