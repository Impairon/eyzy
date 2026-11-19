package app.katib.audio

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import app.katib.R
import app.katib.data.KatibDb
import app.katib.data.Status
import app.katib.ui.MainActivity
import app.katib.work.Jobs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

data class RecState(
    val meetingId: Long? = null,
    val recording: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0,
    val levelDb: Float = -90f,
    val levels: List<Float> = emptyList(),
)

/** Foreground microphone service: survives screen lock; writes encrypted 16 kHz mono PCM. */
class RecorderService : Service() {
    companion object {
        const val CHANNEL = "recording"
        private const val NOTIF = 1
        const val ACTION_START = "start"; const val ACTION_STOP = "stop"
        const val ACTION_PAUSE = "pause"; const val ACTION_RESUME = "resume"
        const val EXTRA_MEETING = "meeting"

        /** Meetings whose capture manifest should now be sealed with the user's biometric. */
        val sealRequests = kotlinx.coroutines.flow.MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 4)

        private val _state = MutableStateFlow(RecState())
        val state: StateFlow<RecState> = _state

        fun start(ctx: Context, meetingId: Long) = ctx.startForegroundService(
            Intent(ctx, RecorderService::class.java).setAction(ACTION_START).putExtra(EXTRA_MEETING, meetingId)
        )
        fun send(ctx: Context, action: String) = ctx.startService(Intent(ctx, RecorderService::class.java).setAction(action))

        fun audioFile(ctx: Context, meetingId: Long) =
            File(ctx.noBackupFilesDir, "audio").apply { mkdirs() }.let { File(it, "m$meetingId.eca") }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var paused = false
    private var wake: PowerManager.WakeLock? = null
    @Volatile private var writerRef: EncryptedAudioWriter? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(intent.getLongExtra(EXTRA_MEETING, -1))
            ACTION_PAUSE -> { paused = true; _state.value = _state.value.copy(paused = true); notifyState() }
            ACTION_RESUME -> { paused = false; _state.value = _state.value.copy(paused = false); notifyState() }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        fun action(a: String, req: Int) = PendingIntent.getService(
            this, req, Intent(this, RecorderService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE
        )
        val s = _state.value
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(if (s.paused) R.string.notif_paused else R.string.notif_recording))
            .setContentText(getString(R.string.notif_on_device))
            .setOngoing(true)
            .setUsesChronometer(!s.paused)
            .setWhen(System.currentTimeMillis() - s.elapsedMs)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, getString(if (s.paused) R.string.resume else R.string.pause),
                action(if (s.paused) ACTION_RESUME else ACTION_PAUSE, 1))
            .addAction(0, getString(R.string.stop), action(ACTION_STOP, 2))
            .build()
    }

    private fun notifyState() =
        getSystemService(NotificationManager::class.java).notify(NOTIF, notification())

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun begin(meetingId: Long) {
        if (job != null || meetingId < 0) return
        _state.value = RecState(meetingId = meetingId, recording = true)
        val n = notification()
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIF, n)
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "katib:rec").apply { acquire() }

        job = scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, SAMPLE_RATE),
            )
            val writer = EncryptedAudioWriter(audioFile(this@RecorderService, meetingId)).also { writerRef = it }
            val buf = ShortArray(SAMPLE_RATE / 10) // 100 ms
            val levels = ArrayDeque<Float>()
            var activeMs = 0L
            var last = SystemClock.elapsedRealtime()
            try {
                rec.startRecording()
                while (isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    val now = SystemClock.elapsedRealtime()
                    if (n <= 0) { delay(10); continue }
                    if (paused) { last = now; continue }
                    writer.write(buf, n)
                    activeMs += now - last; last = now
                    var peak = 0
                    for (i in 0 until n) peak = max(peak, abs(buf[i].toInt()))
                    val db = if (peak == 0) -90f else 20 * log10(peak / 32768f)
                    levels.addLast(db); if (levels.size > 60) levels.removeFirst()
                    _state.value = _state.value.copy(elapsedMs = writer.samplesWritten * 1000 / SAMPLE_RATE, levelDb = db, levels = levels.toList())
                }
            } finally {
                runCatching { rec.stop() }; rec.release()
                writer.close()
            }
        }
    }

    private fun stopRecording() {
        val s = _state.value
        val j = job
        scope.launch {
            j?.cancelAndJoin()
            job = null
            s.meetingId?.let { id ->
                val dao = KatibDb.get(this@RecorderService).dao()
                val hash = writerRef?.sha256Hex
                dao.meeting(id)?.let { m ->
                    dao.updateMeeting(m.copy(status = Status.QUEUED, durationSec = s.elapsedMs / 1000f, audioSha256 = hash))
                }
                writerRef = null
                sealRequests.tryEmit(id)
                Jobs.transcribe(this@RecorderService, id)
            }
            _state.value = RecState()
            wake?.let { if (it.isHeld) it.release() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
