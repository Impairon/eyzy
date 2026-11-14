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
        MainActivity.instance?.let {
            it.finish()
            return
        }
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
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
        if (Prefs.tileAction(this) == Prefs.TileAction.START_APP_AND_PAW &&
            Prefs.isEnabled(this)
        ) {
            svc.summonManualHideControl()
        }
    }

    private fun update() {
        val t = qsTile ?: return
        t.state = if (Prefs.isEnabled(this) && AvatarService.instance != null)
            Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
