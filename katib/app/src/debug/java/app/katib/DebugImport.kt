package app.katib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.katib.audio.EncryptedAudioWriter
import app.katib.audio.RecorderService
import app.katib.audio.SAMPLE_RATE
import app.katib.data.*
import app.katib.work.Jobs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Debug-only: import a 16 kHz mono PCM16 WAV as a meeting and transcribe it (Phase 0 benchmarking). */
class DebugImport : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val exportId = intent.getLongExtra("export", -1)
        if (exportId > 0) {
            val pr = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val out = File(ctx.filesDir, "test/evidence-$exportId.json")
                    out.writeText(app.katib.security.Provenance.evidenceBundle(ctx, exportId))
                    Log.i("KatibDebug", "exported ${out.path}")
                } catch (t: Throwable) { Log.e("KatibDebug", "export failed", t) } finally { pr.finish() }
            }
            return
        }
        val path = intent.getStringExtra("path") ?: return
        val title = intent.getStringExtra("title") ?: File(path).nameWithoutExtension
        intent.getStringExtra("lang")?.let { Prefs(ctx).language = it }
        intent.getStringExtra("model")?.let { Prefs(ctx).asrModel = it }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = File(path).readBytes()
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                var off = 12; var dataOff = -1; var dataLen = 0
                while (off + 8 <= bytes.size) {
                    val id = String(bytes, off, 4); val len = bb.getInt(off + 4)
                    if (id == "fmt ") require(bb.getShort(off + 10).toInt() == 1 && bb.getInt(off + 12) == SAMPLE_RATE) { "need 16k mono" }
                    if (id == "data") { dataOff = off + 8; dataLen = minOf(len, bytes.size - dataOff); break }
                    off += 8 + len
                }
                require(dataOff > 0)
                val pcm = ShortArray(dataLen / 2) { bb.getShort(dataOff + it * 2) }
                val dao = KatibDb.get(ctx).dao()
                val id = dao.insertMeeting(Meeting(title = title, createdAt = System.currentTimeMillis(), audioPath = null, status = Status.QUEUED))
                val f = RecorderService.audioFile(ctx, id)
                val w = EncryptedAudioWriter(f); w.write(pcm, pcm.size); w.close()
                dao.updateMeeting(dao.meeting(id)!!.copy(audioPath = f.absolutePath, durationSec = pcm.size / SAMPLE_RATE.toFloat(),
                    uuid = java.util.UUID.randomUUID().toString(), audioSha256 = w.sha256Hex))
                dao.insertConsent(ConsentLog(meetingId = id, participants = "debug import", method = "test", confirmedAt = System.currentTimeMillis()))
                Jobs.transcribe(ctx, id)
                Log.i("KatibDebug", "imported meeting $id")
            } catch (t: Throwable) { Log.e("KatibDebug", "import failed", t) }
            finally { pending.finish() }
        }
    }
}
