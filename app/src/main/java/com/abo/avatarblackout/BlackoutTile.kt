package com.abo.avatarblackout

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class BlackoutTile : TileService() {

    override fun onStartListening() = update()

    override fun onClick() {
        val svc = AvatarService.instance
        if (svc == null) {
            Toast.makeText(this, "Turn on the Avatar Blackout accessibility service first", Toast.LENGTH_LONG).show()
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(i)
            }
            return
        }
        Prefs.set(this, !Prefs.isEnabled(this))
        svc.refresh()
        update()
    }

    private fun update() {
        val t = qsTile ?: return
        t.state = if (Prefs.isEnabled(this) && AvatarService.instance != null)
            Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
