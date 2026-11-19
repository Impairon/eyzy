package app.katib.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import app.katib.R
import app.katib.asr.Pipeline
import app.katib.audio.EncryptedAudioReader
import app.katib.audio.RecorderService
import app.katib.data.*
import java.io.File
import java.util.concurrent.TimeUnit

object Jobs {
    const val CHANNEL = "processing"

    fun transcribe(ctx: Context, meetingId: Long) {
        // Expedited: run now, not whenever JobScheduler gets round to it (we observed multi-minute deferrals).
        val req = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf("id" to meetingId))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("transcribe-$meetingId", ExistingWorkPolicy.REPLACE, req)
    }

    fun scheduleRetention(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RetentionWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("retention", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun runRetentionNow(ctx: Context) =
        WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<RetentionWorker>().build())
}

class TranscribeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val dao = KatibDb.get(ctx).dao()

    private fun notif(text: String): ForegroundInfo {
        val n: Notification = NotificationCompat.Builder(applicationContext, Jobs.CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(applicationContext.getString(R.string.notif_transcribing))
            .setContentText(text)
            .setOngoing(true).setSilent(true).build()
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                   else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        return ForegroundInfo(2, n, type)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notif(applicationContext.getString(R.string.notif_transcribing))

    override suspend fun doWork(): Result {
        val id = inputData.getLong("id", -1)
        val m = dao.meeting(id) ?: return Result.failure()
        val file = File(m.audioPath ?: return Result.failure())
        if (!file.exists()) {
            dao.updateMeeting(m.copy(status = Status.FAILED, error = "Audio already deleted")); return Result.failure()
        }
        setForeground(notif(m.title))
        dao.updateMeeting(m.copy(status = Status.TRANSCRIBING, progress = 0f, error = null))
        return try {
            val samples = EncryptedAudioReader(file).use { it.readAllFloats() }
            var lastWrite = 0L
            val res = Pipeline(applicationContext).run(samples) { stage, p ->
                val now = System.currentTimeMillis()
                if (now - lastWrite > 700 || p >= 1f) {
                    lastWrite = now
                    kotlinx.coroutines.runBlocking { dao.setProgress(id, p, stage) }
                }
            }
            android.util.Log.i("KatibPipeline", "meeting=$id model=${res.model} segs=${res.segments.size} speakers=${res.clusters.size} rtf=${"%.3f".format(res.rtf)}")
            if (app.katib.BuildConfig.DEBUG) res.segments.forEach { android.util.Log.i("KatibPipeline", "[${"%.1f".format(it.start)}-${"%.1f".format(it.end)}] S${it.speaker}: ${it.text}") }
            dao.deleteSegments(id); dao.deleteSpeakers(id)
            dao.insertSegments(res.segments.map { Segment(meetingId = id, startSec = it.start, endSec = it.end, text = it.text, speakerIdx = it.speaker) })

            // Match each speaker cluster to remembered voices.
            val prefs = Prefs(applicationContext)
            val people = dao.peopleNow().filter { it.embedding != null }
            dao.insertSpeakers(res.clusters.map { c ->
                val best = c.embedding?.let { e ->
                    people.map { it to Pipeline.cosine(e, it.embedding!!.toFloats()) }.maxByOrNull { it.second }
                }
                val hit = best?.takeIf { it.second >= prefs.matchThreshold }
                MeetingSpeaker(meetingId = id, idx = c.idx, personId = hit?.first?.id, score = best?.second ?: 0f,
                    speechSec = c.speechSec, embedding = c.embedding?.toBytes())
            })

            // Personal note: flag any substantial voice that isn't the enrolled owner.
            val ownerId = people.firstOrNull { it.isOwner }?.id
            val others = dao.meeting(id)!!.kind == "note" && dao.speakersNow(id).any { sp ->
                sp.speechSec >= 3f && (ownerId == null || sp.personId != ownerId)
            } && (ownerId != null || res.clusters.size > 1)

            val now = System.currentTimeMillis()
            val keepMs = TimeUnit.DAYS.toMillis(prefs.retentionDays.toLong())
            val dur = EncryptedAudioReader(file).use { it.durationSec }
            dao.updateMeeting(dao.meeting(id)!!.copy(
                status = Status.DONE, progress = 1f, stage = null, model = res.model, rtf = res.rtf,
                durationSec = dur, transcribedAt = now, audioDeleteAt = now + keepMs, otherVoices = others,
            ))
            runCatching { app.katib.security.Provenance.sealTranscript(applicationContext, id, "transcribed") }
                .onFailure { android.util.Log.e("KatibProvenance", "transcript seal failed", it) }
            if (prefs.retentionDays == 0) Jobs.runRetentionNow(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            android.util.Log.e("KatibPipeline", "failed", t)
            dao.meeting(id)?.let { dao.updateMeeting(it.copy(status = Status.FAILED, error = t.message ?: t.javaClass.simpleName)) }
            Result.failure()
        }
    }
}

/** Secure audio purge after the retention window. Logs nothing about content. */
class RetentionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val dao = KatibDb.get(applicationContext).dao()
        dao.expiredAudio(System.currentTimeMillis()).forEach { m ->
            m.audioPath?.let { shred(File(it)) }
            dao.updateMeeting(m.copy(audioDeleted = true, audioPath = null))
            // Voice embeddings of unnamed clusters are biometric data with no further purpose.
        }
        return Result.success()
    }
}

/**
 * Best-effort overwrite then delete. On flash storage overwrite isn't guaranteed, which is why
 * the real protection is the per-file key: the wrapped DEK in the header is gone with the file.
 */
fun shred(f: File) {
    if (!f.exists()) return
    runCatching {
        java.io.RandomAccessFile(f, "rw").use { raf ->
            val zeros = ByteArray(64 * 1024); var left = raf.length(); raf.seek(0)
            while (left > 0) { val n = minOf(left, zeros.size.toLong()).toInt(); raf.write(zeros, 0, n); left -= n }
            raf.fd.sync()
        }
    }
    f.delete()
}

fun audioFileFor(ctx: Context, id: Long) = RecorderService.audioFile(ctx, id)
