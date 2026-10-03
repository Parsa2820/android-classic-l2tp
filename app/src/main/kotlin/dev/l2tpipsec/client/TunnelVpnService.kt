package dev.l2tpipsec.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stock VpnService. Negotiation runs before establish() so the TUN address comes from IPCP.
 * UDP sockets used by the engine are passed through [protectSocketFd], which calls [protect].
 */
@Keep
class TunnelVpnService : VpnService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cancel = AtomicBoolean(false)
    private val attemptIds = AtomicInteger(0)
    private val sessionLock = Any()
    private var activeAttempt = 0
    private var tunInterface: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var loop: Thread? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onRevoke() {
        requestStop()
        super.onRevoke()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> requestStop()
            ACTION_CONNECT -> startSession()
        }
        return START_NOT_STICKY
    }

    private fun startSession() {
        val alreadyRunning = synchronized(sessionLock) {
            worker?.isAlive == true || loop?.isAlive == true
        }
        if (alreadyRunning) {
            TunnelSession.appendLog("A session is already running")
            return
        }
        val request = PendingConnect.request
        startForegroundWith(getString(R.string.vpn_notification_connecting))
        if (request == null) {
            fail("The connection request was empty. Try again from the app.")
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            stopSelf()
            return
        }
        val id = attemptIds.incrementAndGet()
        synchronized(sessionLock) {
            activeAttempt = id
            cancel.set(false)
        }
        TunnelSession.clearLogs()
        TunnelSession.update(TunnelPhase.CONNECTING, "Starting IKEv1 Main Mode")
        TunnelSession.appendLog("Connecting to ${request.server}")
        val thread = Thread({ runNegotiation(id, request) }, "l2tp-negotiate")
        worker = thread
        thread.start()
    }

    private fun runNegotiation(id: Int, request: ConnectRequest) {
        var handedToLoop = false
        try {
            if (!isCurrent(id) || cancel.get()) {
                return
            }
            VpnBridge.nativeSetSocketProtectionEnabled(true)
            val client = IntArray(4)
            val dns1 = IntArray(4)
            val dns2 = IntArray(4)
            TunnelSession.update(TunnelPhase.CONNECTING, "IKEv1, then L2TP, then PPP MS-CHAPv2")
            val code = VpnBridge.nativeNegotiate(
                request.server,
                request.username,
                request.password,
                request.psk,
                request.mtu,
                client,
                dns1,
                dns2,
            )
            if (!isCurrent(id)) {
                return
            }
            if (code != TunnelExit.OK || cancel.get()) {
                if (code == TunnelExit.OK) {
                    VpnBridge.nativeDisconnect()
                }
                if (cancel.get() || code == TunnelExit.STOPPED) {
                    TunnelSession.update(TunnelPhase.IDLE, "Not connected")
                } else {
                    fail(TunnelExit.message(code))
                }
                return
            }
            val address = TunnelPlan.clientAddress(client)
            if (address == null) {
                VpnBridge.nativeDisconnect()
                fail("PPP finished without an IPv4 address.")
                return
            }
            val dns = TunnelPlan.dnsServers(dns1, dns2)
            if (dns.usedFallback) {
                TunnelSession.appendLog("PPP did not offer DNS. Using ${dns.servers.joinToString()}")
            }
            if (cancel.get() || !isCurrent(id)) {
                VpnBridge.nativeDisconnect()
                TunnelSession.update(TunnelPhase.IDLE, "Not connected")
                return
            }
            val tun = establishTun(request, address, dns.servers)
            if (tun == null) {
                VpnBridge.nativeDisconnect()
                fail("Android did not create the VPN interface. Grant the VPN permission and try again.")
                return
            }
            synchronized(sessionLock) {
                if (!isCurrent(id) || cancel.get()) {
                    tun.close()
                    VpnBridge.nativeDisconnect()
                    return
                }
                tunInterface = tun
            }
            TunnelSession.update(
                TunnelPhase.CONNECTING,
                "Tunnel interface is up. Waiting for the data path.",
                clientIpv4 = address,
                dns = dns.servers,
            )
            handedToLoop = true
            val loopThread = Thread({
                var loopCode = TunnelExit.POLL_ERROR
                try {
                    loopCode = VpnBridge.nativeStartLoop(tun.fd)
                } catch (error: Throwable) {
                    TunnelSession.appendLog(error.message ?: "Data path crashed")
                } finally {
                    finishLoop(id, loopCode)
                }
            }, "l2tp-dataplane")
            loop = loopThread
            loopThread.start()
        } catch (error: Throwable) {
            fail(error.message ?: "Connection failed")
        } finally {
            if (!handedToLoop) {
                teardown(id, keepFailure = TunnelSession.state.value.phase == TunnelPhase.FAILED)
            }
        }
    }

    private fun establishTun(
        request: ConnectRequest,
        address: String,
        dns: List<String>,
    ): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.vpn_session_name))
            .setMtu(request.mtu)
            .addAddress(address, 32)
            .addRoute("0.0.0.0", 0)
            .allowFamily(OsConstants.AF_INET)
            .setMetered(false)
        for (server in dns) {
            builder.addDnsServer(server)
        }
        try {
            val v4 = InetAddress.getAllByName(request.server).filterIsInstance<Inet4Address>().firstOrNull()
            if (v4 != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.excludeRoute(IpPrefix(v4, 32))
                TunnelSession.appendLog("Excluded ${v4.hostAddress}/32 from the tunnel route")
            }
        } catch (error: Exception) {
            TunnelSession.appendLog("Could not exclude the server route: ${error.message}")
        }
        return builder.establish()
    }

    private fun finishLoop(id: Int, code: Int) {
        if (!isCurrent(id)) {
            return
        }
        if (cancel.get() || code == TunnelExit.STOPPED) {
            TunnelSession.update(TunnelPhase.IDLE, "Not connected")
        } else if (code == TunnelExit.OK) {
            TunnelSession.update(TunnelPhase.IDLE, "Tunnel closed")
            TunnelSession.appendLog("Tunnel closed")
        } else {
            fail(TunnelExit.message(code))
        }
        teardown(id, keepFailure = TunnelSession.state.value.phase == TunnelPhase.FAILED)
    }

    private fun teardown(id: Int, keepFailure: Boolean) {
        val tun = synchronized(sessionLock) {
            if (activeAttempt != id) {
                return
            }
            activeAttempt = 0
            val open = tunInterface
            tunInterface = null
            open
        }
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        PendingConnect.request = null
        if (!keepFailure && TunnelSession.state.value.phase != TunnelPhase.FAILED) {
            if (TunnelSession.state.value.phase != TunnelPhase.IDLE) {
                TunnelSession.update(TunnelPhase.IDLE, "Not connected")
            }
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun fail(detail: String) {
        TunnelSession.update(TunnelPhase.FAILED, detail)
        TunnelSession.appendLog(detail)
    }

    private fun requestStop() {
        cancel.set(true)
        TunnelSession.appendLog("Disconnect requested")
        if (TunnelSession.state.value.phase == TunnelPhase.CONNECTING ||
            TunnelSession.state.value.phase == TunnelPhase.CONNECTED
        ) {
            TunnelSession.update(
                TunnelPhase.CONNECTING,
                "Disconnecting",
                clientIpv4 = TunnelSession.state.value.clientIpv4,
                dns = TunnelSession.state.value.dns,
            )
        }
        try {
            VpnBridge.nativeStopTunnel()
        } catch (_: UnsatisfiedLinkError) {
        } catch (_: Throwable) {
        }
        val running = synchronized(sessionLock) {
            (worker?.isAlive == true) || (loop?.isAlive == true)
        }
        if (!running) {
            TunnelSession.update(TunnelPhase.IDLE, "Not connected")
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            stopSelf()
        }
    }

    private fun isCurrent(id: Int): Boolean = synchronized(sessionLock) { activeAttempt == id }

    private fun startForegroundWith(text: String) {
        val notification = notification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(text: String): Notification {
        val disconnect = Intent(this, TunnelVpnService::class.java).setAction(ACTION_DISCONNECT)
        val pending = PendingIntent.getService(
            this,
            1,
            disconnect,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.vpn_action_disconnect), pending)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "l2tp_ipsec"
        private const val NOTIFICATION_ID = 42
        const val ACTION_CONNECT = "dev.l2tpipsec.client.CONNECT"
        const val ACTION_DISCONNECT = "dev.l2tpipsec.client.DISCONNECT"

        @Volatile
        private var instance: TunnelVpnService? = null

        @JvmStatic
        fun protectSocketFd(fd: Int): Boolean {
            val service = instance ?: return false
            return try {
                service.protect(fd)
            } catch (_: Exception) {
                false
            }
        }

        @JvmStatic
        fun onNativeTunnelReady(detail: String?) {
            val service = instance ?: return
            val address = TunnelSession.state.value.clientIpv4
            val text = if (address.isNullOrBlank()) {
                detail ?: "Connected"
            } else {
                service.getString(R.string.vpn_notification_connected, address)
            }
            TunnelSession.update(
                TunnelPhase.CONNECTED,
                if (address.isNullOrBlank()) "Connected" else "Connected, $address",
                clientIpv4 = address,
                dns = TunnelSession.state.value.dns,
            )
            TunnelSession.appendLog(detail ?: "Data path is up")
            service.mainHandler.post {
                try {
                    service.startForegroundWith(text)
                } catch (_: Exception) {
                }
            }
        }
    }
}
