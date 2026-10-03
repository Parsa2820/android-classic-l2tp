package dev.l2tpipsec.client

/**
 * Pure decisions shared by the VpnService and the unit tests.
 * Exit codes match app/src/main/cpp/engine.h.
 */
object TunnelExit {
    const val OK = 0
    const val IKE_FAILED = 1
    const val L2TP_FAILED = 2
    const val PPP_FAILED = 3
    const val POLL_ERROR = 4
    const val BAD_ARGS = 10
    const val STOPPED = 12

    fun message(code: Int): String = when (code) {
        OK -> "IKE, L2TP, and PPP finished."
        IKE_FAILED -> "IKEv1 did not finish. Check the server, UDP 500 and 4500, and the pre-shared key."
        L2TP_FAILED -> "IPsec came up, but the L2TP control connection did not."
        PPP_FAILED -> "L2TP is up, but PPP authentication or IPCP failed. Check the username and password."
        POLL_ERROR -> "The tunnel stopped because of a socket error."
        BAD_ARGS -> "The connection settings were rejected by the engine."
        STOPPED -> "Connection cancelled."
        else -> "Tunnel engine exited with code $code."
    }
}

data class DnsChoice(
    val servers: List<String>,
    val usedFallback: Boolean,
)

object TunnelPlan {
    const val MTU = 1400
    val FALLBACK_DNS = listOf("1.1.1.1", "9.9.9.9")

    fun formatIpv4(octets: IntArray?): String? {
        if (octets == null || octets.size < 4) return null
        val parts = octets.take(4)
        if (parts.any { it !in 0..255 }) return null
        if (parts.all { it == 0 }) return null
        return parts.joinToString(".")
    }

    fun clientAddress(octets: IntArray?): String? = formatIpv4(octets)

    fun dnsServers(primary: IntArray?, secondary: IntArray?): DnsChoice {
        val negotiated = listOfNotNull(formatIpv4(primary), formatIpv4(secondary)).distinct()
        return if (negotiated.isEmpty()) {
            DnsChoice(FALLBACK_DNS, usedFallback = true)
        } else {
            DnsChoice(negotiated, usedFallback = false)
        }
    }
}
