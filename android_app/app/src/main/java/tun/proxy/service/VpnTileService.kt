package tun.proxy.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import tun.proxy.MainActivity
import tun.proxy.R

@RequiresApi(Build.VERSION_CODES.N)
class VpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (Tun2SocksVpnService.isActive) {
            // Stop the VPN
            val stopIntent = Intent(this, Tun2SocksVpnService::class.java).apply {
                action = Tun2SocksVpnService.ACTION_STOP_SERVICE
            }
            startService(stopIntent)
        } else {
            // Open the app — VPN requires user interaction for VpnService.prepare()
            val openIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(openIntent)
            }
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        if (Tun2SocksVpnService.isActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.tile_connected)
            tile.contentDescription = getString(R.string.tile_connected)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.app_name)
            tile.contentDescription = getString(R.string.tile_disconnected)
        }
        tile.updateTile()
    }
}
