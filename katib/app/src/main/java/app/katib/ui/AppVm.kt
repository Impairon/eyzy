package app.katib.ui

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.katib.asr.ModelSpec
import app.katib.asr.Models
import app.katib.asr.Pipeline
import app.katib.audio.RecorderService
import app.katib.audio.SAMPLE_RATE
import app.katib.data.*
import app.katib.security.Crypto
import app.katib.security.Provenance
import app.katib.work.Jobs
import app.katib.work.shred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

data class ConsentDraft(val participants: String, val method: String)

sealed interface EnrollState {
    data object Idle : EnrollState
    data class Recording(val seconds: Float, val level: Float) : EnrollState
    /** step: 0 finding speech, 1 building profile, 2 checking consistency */
    data class Processing(val step: Int) : EnrollState
    data class Done(val speechSec: Float, val consistency: Float?) : EnrollState
    data class Failed(val reason: String, val speechSec: Float = 0f) : EnrollState
}

class AppVm(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    val dao = KatibDb.get(app).dao()
    val prefs = Prefs(app)

    val meetings = dao.meetings().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val people = dao.people().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val rec = RecorderService.state

    // ---------- Recording ----------
    val consent = MutableStateFlow<ConsentDraft?>(null)
    val title = MutableStateFlow("")
    /** Personal note: owner only, no consent step. */
    val personal = MutableStateFlow(false)
    val withLocation = MutableStateFlow(prefs.locationDefault)

    fun startRecording(defaultTitle: String, locationGranted: Boolean) {
        val note = personal.value
        val c = if (note) ConsentDraft("Owner only", "self · personal note") else consent.value ?: return
        viewModelScope.launch {
            val t = title.value.ifBlank { defaultTitle }
            val id = dao.insertMeeting(Meeting(title = t, createdAt = System.currentTimeMillis(), audioPath = null,
                language = prefs.language, kind = if (note) "note" else "meeting", uuid = java.util.UUID.randomUUID().toString()))
            if (withLocation.value && locationGranted) viewModelScope.launch {
                app.katib.audio.Locator.current(ctx)?.let { l ->
                    dao.meeting(id)?.let { dao.updateMeeting(it.copy(lat = l.latitude, lon = l.longitude,
                        locAccuracyM = if (l.hasAccuracy()) l.accuracy else null, locAt = l.time)) }
                }
            }
            val path = RecorderService.audioFile(ctx, id).absolutePath
            dao.updateMeeting(dao.meeting(id)!!.copy(audioPath = path))
            dao.insertConsent(ConsentLog(meetingId = id, participants = c.participants, method = c.method,
                confirmedAt = System.currentTimeMillis()))
            RecorderService.start(ctx, id)
        }
    }

    fun stopRecording() {
        RecorderService.send(ctx, RecorderService.ACTION_STOP)
        consent.value = null; title.value = ""; withLocation.value = prefs.locationDefault
    }
    fun pause() = RecorderService.send(ctx, RecorderService.ACTION_PAUSE)
    fun resume() = RecorderService.send(ctx, RecorderService.ACTION_RESUME)

    // ---------- Meetings ----------
    fun segments(id: Long) = dao.segments(id)
    fun speakers(id: Long) = dao.speakers(id)
    fun meeting(id: Long) = dao.meetingFlow(id)
    suspend fun consentFor(id: Long) = dao.consent(id)

    fun rename(id: Long, t: String) = viewModelScope.launch { dao.rename(id, t) }
    fun editSegment(id: Long, text: String) = viewModelScope.launch {
        dao.editSegment(id, text)
        val mid = dao.segmentMeeting(id) ?: return@launch
        runCatching { Provenance.sealTranscript(ctx, mid, "user_edit") }
    }

    // ---------- Provenance ----------
    suspend fun isNote(id: Long) = dao.meeting(id)?.kind == "note"
    fun provenance(id: Long) = dao.provenanceFlow(id)

    /** Builds the capture manifest; returns bytes to sign, or null if not sealable. */
    suspend fun capturePayload(meetingId: Long): String? = withContext(Dispatchers.IO) {
        var m = dao.meeting(meetingId) ?: return@withContext null
        if (m.audioSha256 == null || dao.provenance(meetingId).any { it.kind == "capture" }) return@withContext null
        if (m.uuid == null) { m = m.copy(uuid = java.util.UUID.randomUUID().toString()); dao.updateMeeting(m) }
        Provenance.capturePayload(ctx, m, dao.consent(meetingId).firstOrNull(), dao.peopleNow().any { it.isOwner && it.embedding != null })
    }

    fun storeCaptureSeal(meetingId: Long, payload: String, sig: ByteArray) = viewModelScope.launch(Dispatchers.IO) {
        val bytes = payload.toByteArray()
        dao.insertProvenance(ProvenanceRecord(meetingId = meetingId, kind = "capture", payload = payload,
            payloadSha256 = Provenance.sha256(bytes), prevSha256 = null,
            signature = android.util.Base64.encodeToString(sig, android.util.Base64.NO_WRAP),
            keyAlias = Provenance.USER_KEY, certChain = Provenance.certChainPem(Provenance.USER_KEY),
            createdAt = System.currentTimeMillis()))
    }

    suspend fun evidenceFile(meetingId: Long): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val m = dao.meeting(meetingId)!!
        File(dir, "evidence-${m.uuid ?: meetingId}.json").apply { writeText(Provenance.evidenceBundle(ctx, meetingId)) }
    }
    fun retranscribe(id: Long) = viewModelScope.launch {
        dao.meeting(id)?.let { dao.updateMeeting(it.copy(status = Status.QUEUED, progress = 0f, error = null)) }
        Jobs.transcribe(ctx, id)
    }
    fun deleteAudioNow(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        val m = dao.meeting(id) ?: return@launch
        m.audioPath?.let { shred(File(it)) }
        dao.updateMeeting(m.copy(audioPath = null, audioDeleted = true))
    }
    fun deleteMeeting(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        WorkManager.getInstance(ctx).cancelUniqueWork("transcribe-$id")
        val m = dao.meeting(id) ?: return@launch
        m.audioPath?.let { shred(File(it)) }
        dao.deleteSegments(id); dao.deleteSpeakers(id); dao.deleteProvenance(id); dao.deleteMeeting(m)
    }

    /** Label a speaker cluster. Optionally remembers (or refines) the voice profile — explicit opt-in. */
    fun labelSpeaker(sp: MeetingSpeaker, existing: Person?, newName: String?, remember: Boolean) =
        viewModelScope.launch {
            val emb = sp.embedding?.toFloats()
            val pid = when {
                existing != null -> {
                    if (remember && emb != null) {
                        val upd = existing.embedding?.let { Pipeline.merge(it.toFloats(), existing.samples, emb) } ?: emb
                        dao.updatePerson(existing.copy(embedding = upd.toBytes(), samples = existing.samples + 1))
                    }
                    existing.id
                }
                !newName.isNullOrBlank() -> dao.insertPerson(Person(name = newName.trim(),
                    embedding = if (remember) emb?.toBytes() else null, consentAt = System.currentTimeMillis()))
                else -> null
            }
            dao.assignSpeaker(sp.id, pid)
        }

    // ---------- Search ----------
    suspend fun search(q: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val terms = q.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .map { it.replace("\"", "") }.filter { it.isNotBlank() }
        if (terms.isEmpty()) return@withContext emptyList()
        val fts = runCatching { dao.searchFts(terms.joinToString(" ") { "\"$it\"*" }) }.getOrDefault(emptyList())
        fts.ifEmpty { dao.searchLike(q.trim()) }
    }

    // ---------- People ----------
    fun renamePerson(p: Person, name: String) = viewModelScope.launch { dao.updatePerson(p.copy(name = name)) }
    fun deletePerson(p: Person) = viewModelScope.launch { dao.unlinkPerson(p.id); dao.deletePerson(p) }
    suspend fun meetingsWith(pid: Long) = dao.meetingsWith(pid)

    val enroll = MutableStateFlow<EnrollState>(EnrollState.Idle)
    private var enrollJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startEnroll() {
        if (!Models.installed(ctx, Models.EMBEDDING)) { enroll.value = EnrollState.Failed("models"); return }
        enrollJob = viewModelScope.launch(Dispatchers.Default) {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuf, SAMPLE_RATE))
            val all = FloatArray(SAMPLE_RATE * 40)
            var n = 0
            val buf = ShortArray(SAMPLE_RATE / 10)
            enroll.value = EnrollState.Recording(0f, 0f)
            r.startRecording()
            try {
                while (isActive && n < SAMPLE_RATE * 30 && enroll.value is EnrollState.Recording) {
                    val k = r.read(buf, 0, buf.size)
                    if (k <= 0) continue
                    var peak = 0
                    for (i in 0 until k) { if (n < all.size) all[n++] = buf[i] / 32768f; peak = max(peak, abs(buf[i].toInt())) }
                    enroll.value = EnrollState.Recording(n / SAMPLE_RATE.toFloat(), peak / 32768f)
                }
            } finally { r.stop(); r.release() }
            if (enroll.value !is EnrollState.Recording && enroll.value !is EnrollState.Processing) return@launch // cancelled
            enroll.value = EnrollState.Processing(0)
            val res = runCatching { Pipeline(ctx).enroll(all.copyOf(n)) { s -> enroll.value = EnrollState.Processing(s) } }
            all.fill(0f) // raw enrollment audio is never stored
            val er = res.getOrNull()
            if (er?.embedding == null) { enroll.value = EnrollState.Failed("speech", er?.speechSec ?: 0f); return@launch }
            if ((er.consistency ?: 1f) < 0.35f) { enroll.value = EnrollState.Failed("noisy", er.speechSec); return@launch }
            val emb = er.embedding
            val owner = dao.peopleNow().firstOrNull { it.isOwner }
            if (owner != null) dao.updatePerson(owner.copy(embedding = emb.toBytes(), samples = 1))
            else dao.insertPerson(Person(name = "Me", isOwner = true, embedding = emb.toBytes(), consentAt = System.currentTimeMillis()))
            enroll.value = EnrollState.Done(er.speechSec, er.consistency)
        }
    }
    fun finishEnroll() { if (enroll.value is EnrollState.Recording) enroll.value = EnrollState.Processing(0) }
    fun resetEnroll() { enrollJob?.cancel(); enroll.value = EnrollState.Idle }

    // ---------- Models ----------
    val downloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val modelTick = MutableStateFlow(0)
    val modelError = MutableStateFlow<String?>(null)

    fun installed(m: ModelSpec) = Models.installed(ctx, m)
    fun install(m: ModelSpec) {
        if (downloads.value.containsKey(m.id)) return
        downloads.update { it + (m.id to 0f) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Models.download(ctx, m) { p -> downloads.update { d -> d + (m.id to p) } }
            } catch (t: Throwable) {
                modelError.value = "${m.title}: ${t.message}"
            } finally {
                downloads.update { it - m.id }; modelTick.update { it + 1 }
            }
        }
    }
    fun installEssentials() = (Models.SUPPORT + Models.QWEN3).filterNot(::installed).forEach(::install)

    // ---------- Wipe ----------
    fun wipeEverything(onDone: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        RecorderService.send(ctx, RecorderService.ACTION_STOP)
        WorkManager.getInstance(ctx).cancelAllWork()
        File(ctx.noBackupFilesDir, "audio").listFiles()?.forEach { shred(it) }
        KatibDb.closeAndForget()
        ctx.deleteDatabase("katib.db")
        Crypto.destroyAll(ctx)
        prefs.clear()
        ctx.cacheDir.deleteRecursively()
        withContext(Dispatchers.Main) { onDone() }
    }
}
