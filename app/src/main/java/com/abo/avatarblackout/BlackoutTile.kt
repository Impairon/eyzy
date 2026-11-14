package com.abo.avatarblackout

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class BlackoutTile : TileService() {

    override fun onStartListening() = update()

    override fun onClick() {
        val svc = AvatarService.instance
        if (svc != null && Prefs.isEnabled(this)) {
            Prefs.set(this, false)
            svc.refresh()
            MainActivity.instance?.finish()
            update()
            return
        }

        if (svc == null) {
            Toast.makeText(this, "Turn on the swizy accessibility service first", Toast.LENGTH_LONG).show()
            openApp()
            return
        }

        Prefs.set(this, true)
        svc.refresh()
        if (Prefs.tileAction(this) == Prefs.TileAction.START_APP_AND_PAW) {
            svc.summonManualHideControl()
        }
        update()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }

    private fun update() {
        val t = qsTile ?: return
        t.label = "swizy"
        t.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        t.state = if (Prefs.isEnabled(this) && AvatarService.instance != null)
            Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
