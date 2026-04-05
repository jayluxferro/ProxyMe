package tun.proxy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RotationReceiver : BroadcastReceiver() {
    private val TAG = "RotationReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ProxyRotationManager.ACTION_ROTATE) return

        val manager = ProxyRotationManager(context)
        val state = manager.getState()
        if (!state.enabled) return

        val nextConfig = manager.getNextConfig()
        if (nextConfig == null) {
            Log.w(TAG, "No configs available for rotation")
            manager.disable()
            return
        }

        Log.d(TAG, "Rotating to: ${nextConfig.name}")

        // Start VPN with the next config
        val vpnIntent = Intent(context, Tun2SocksVpnService::class.java).apply {
            putExtra("data", nextConfig.proxyAddress)
        }
        context.startService(vpnIntent)

        // Schedule the next rotation
        manager.scheduleNextRotation(state.intervalMinutes)
    }
}
