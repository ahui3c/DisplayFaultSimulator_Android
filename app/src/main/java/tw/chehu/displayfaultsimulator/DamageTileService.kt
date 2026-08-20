package tw.chehu.displayfaultsimulator

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class DamageTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshState()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val settingsStore = LineSettings(this)
        if (settingsStore.serviceEnabled) {
            startService(Intent(this, LineOverlayService::class.java).setAction(LineOverlayService.ACTION_STOP))
        } else if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, LineOverlayService::class.java).setAction(LineOverlayService.ACTION_START))
        } else {
            Toast.makeText(this, R.string.tile_permission_required, Toast.LENGTH_LONG).show()
            val pendingIntent = PendingIntent.getActivity(
                this,
                20,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        refreshState()
    }

    private fun refreshState() {
        qsTile?.apply {
            state = if (LineSettings(this@DamageTileService).serviceEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(if (state == Tile.STATE_ACTIVE) R.string.tile_active else R.string.tile_stopped)
            }
            updateTile()
        }
    }
}
