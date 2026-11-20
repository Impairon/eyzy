package com.abo.avatarblackout

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * Quick Settings is deliberately a service switch, not an app launcher.
 * Android owns accessibility-service permission; this tile can only control
 * Swizy after the user has enabled that service in system Settings.
 */
class BlackoutTile : TileService() {

    override fun onStartListening() = update()

    override fun onClick() {
        val service = AvatarService.instance
        if (service == null) {
            Toast.makeText(
                this,
                "Enable Swizy in Android Accessibility settings first.",
                Toast.LENGTH_LONG
            ).show()
            update()
            return
        }

        val shouldEnable = !Prefs.isEnabled(this)
        Prefs.set(this, shouldEnable)
        service.refresh()

        if (shouldEnable &&
            Prefs.tileAction(this) == Prefs.TileAction.START_APP_AND_PAW
        ) {
            service.summonManualHideControl()
        }

        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val serviceConnected = AvatarService.instance != null
        val enabled = serviceConnected && Prefs.isEnabled(this)
        tile.label = if (enabled) "Swizy on" else "Swizy"
        tile.subtitle = if (serviceConnected) {
            if (enabled) "Protection active" else "Protection paused"
        } else {
            "Enable accessibility service"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
