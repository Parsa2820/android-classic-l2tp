package dev.l2tpipsec.client

import android.util.Log
import androidx.annotation.Keep

/** Native logs land here. The C side redacts password, psk, user, and host tokens first. */
object VpnTunnelEvents {
    private const val TAG = "tunnel_engine"

    @Keep
    @JvmStatic
    fun emitEngineLogFromNative(priority: Int, tag: String, message: String) {
        when (priority) {
            Log.ERROR -> Log.e(tag, message)
            Log.WARN -> Log.w(tag, message)
            Log.INFO -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }
        if (priority >= Log.INFO) {
            TunnelSession.appendLog(message)
        }
    }
}
