package dev.l2tpipsec.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class TunnelPhase {
    IDLE,
    CONNECTING,
    CONNECTED,
    FAILED,
}

data class TunnelSnapshot(
    val phase: TunnelPhase = TunnelPhase.IDLE,
    val detail: String = "Not connected",
    val clientIpv4: String? = null,
    val dns: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
) {
    val busy: Boolean
        get() = phase == TunnelPhase.CONNECTING || phase == TunnelPhase.CONNECTED
}

/**
 * Process-wide connection state. The activity collects it; the VpnService writes it.
 * Credentials never go in here.
 */
object TunnelSession {
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val mutable = MutableStateFlow(TunnelSnapshot())
    val state: StateFlow<TunnelSnapshot> = mutable.asStateFlow()

    fun update(
        phase: TunnelPhase,
        detail: String,
        clientIpv4: String? = null,
        dns: List<String> = emptyList(),
    ) {
        mutable.update { current ->
            current.copy(
                phase = phase,
                detail = detail,
                clientIpv4 = clientIpv4,
                dns = dns,
            )
        }
    }

    fun appendLog(message: String) {
        val line = "${time.format(LocalTime.now())}  $message"
        mutable.update { current ->
            current.copy(logs = (current.logs + line).takeLast(120))
        }
    }

    fun clearLogs() {
        mutable.update { it.copy(logs = emptyList()) }
    }
}

data class ConnectRequest(
    val server: String,
    val psk: String,
    val username: String,
    val password: String,
    val mtu: Int = TunnelPlan.MTU,
)

/** In-memory handoff so the PSK and password are not stuffed into a sticky Intent. */
object PendingConnect {
    @Volatile
    var request: ConnectRequest? = null
}
