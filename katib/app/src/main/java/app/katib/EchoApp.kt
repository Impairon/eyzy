package app.katib

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.katib.audio.RecorderService
import app.katib.data.KatibDb
import app.katib.data.Status
import app.katib.work.Jobs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KatibApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(RecorderService.CHANNEL, getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW)
                .apply { lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE }
        )
        nm.createNotificationChannel(
            NotificationChannel(Jobs.CHANNEL, getString(R.string.channel_processing), NotificationManager.IMPORTANCE_LOW)
        )
        Jobs.scheduleRetention(this)

        // Recover from process death: a meeting left "recording" with no live service is finalized and queued.
        appScope.launch {
            val dao = KatibDb.get(this@KatibApp).dao()
            if (RecorderService.state.value.recording) return@launch
            dao.orphaned().forEach { dao.updateMeeting(it.copy(status = Status.QUEUED)) }
            dao.pending().forEach { Jobs.transcribe(this@KatibApp, it.id) }
        }
    }
}
