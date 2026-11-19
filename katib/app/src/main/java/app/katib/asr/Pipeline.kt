package app.katib.asr

import android.content.Context
import app.katib.audio.SAMPLE_RATE
import app.katib.data.Prefs
import com.k2fsa.sherpa.onnx.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class AsrSegment(val start: Float, val end: Float, val text: String, var speaker: Int = -1)
data class Cluster(val idx: Int, val speechSec: Float, val embedding: FloatArray?)
data class PipelineResult(
    val segments: List<AsrSegment>, val clusters: List<Cluster>, val model: String, val rtf: Float,
)

/**
 * Post-meeting, fully on-device pipeline:
 *   decrypted PCM -> Silero VAD -> ASR per speech segment (Qwen3-ASR or Whisper)
 *                 -> pyannote segmentation + speaker embeddings + clustering (diarization)
 *                 -> per-cluster voice embedding for matching against remembered people.
 */
class Pipeline(private val ctx: Context) {
    private val prefs = Prefs(ctx)
    private val threads = max(2, min(6, Runtime.getRuntime().availableProcessors() - 2))

    private fun need(rel: String) = Models.path(ctx, rel) ?: error("Model missing: $rel. Install it in Settings → Models.")

    fun run(samples: FloatArray, progress: (stage: String, p: Float) -> Unit): PipelineResult {
        val t0 = System.currentTimeMillis()
        val audioSec = samples.size / SAMPLE_RATE.toFloat()

        // 1. VAD
        progress("vad", 0f)
        val speech = vad(samples)

        // 2. ASR
        val spec = Models.ASR.firstOrNull { it.id == prefs.asrModel && Models.installed(ctx, it) }
            ?: Models.ASR.firstOrNull { Models.installed(ctx, it) }
            ?: error("No transcription model installed. Open Settings → Models.")
        val rec = recognizer(spec)
        val out = ArrayList<AsrSegment>()
        try {
            speech.forEachIndexed { i, seg ->
                val stream = rec.createStream()
                stream.acceptWaveform(seg.samples, SAMPLE_RATE)
                if (spec.id == "qwen3") {
                    when (prefs.language) {
                        "ar" -> stream.setOption("language", "Arabic")
                        "en" -> stream.setOption("language", "English")
                    }
                }
                rec.decode(stream)
                val text = clean(rec.getResult(stream).text)
                stream.release()
                val start = seg.start / SAMPLE_RATE.toFloat()
                if (text.isNotBlank()) out += AsrSegment(start, start + seg.samples.size / SAMPLE_RATE.toFloat(), text)
                progress("asr", (i + 1f) / speech.size)
            }
        } finally { rec.release() }

        // 3. Diarization + 4. voice embeddings
        var clusters = emptyList<Cluster>()
        val canDiarize = prefs.diarize && Models.installed(ctx, Models.SEGMENTATION) && Models.installed(ctx, Models.EMBEDDING)
        if (canDiarize && out.isNotEmpty()) {
            progress("diarize", 0f)
            val diar = diarize(samples) { p -> progress("diarize", p) }
            assignSpeakers(out, diar)
            progress("voices", 0f)
            clusters = clusterEmbeddings(samples, diar)
        }

        val rtf = (System.currentTimeMillis() - t0) / 1000f / max(audioSec, 0.1f)
        return PipelineResult(out, clusters, spec.title, rtf)
    }

    private fun clean(s: String): String = s
        .replace(Regex("^language\\s+\\w+\\s*"), "")
        .replace("<asr_text>", "")
        .trim()

    private fun vad(samples: FloatArray): List<SpeechSegment> {
        val cfg = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = need(Models.VAD.required[0]),
                threshold = 0.5f, minSilenceDuration = 0.5f, minSpeechDuration = 0.25f,
                windowSize = 512, maxSpeechDuration = 20f,
            ),
            sampleRate = SAMPLE_RATE, numThreads = 1,
        )
        val vad = Vad(null, cfg)
        val segs = ArrayList<SpeechSegment>()
        try {
            var i = 0
            while (i + 512 <= samples.size) {
                vad.acceptWaveform(samples.copyOfRange(i, i + 512)); i += 512
                while (!vad.empty()) { segs += vad.front(); vad.pop() }
            }
            vad.flush()
            while (!vad.empty()) { segs += vad.front(); vad.pop() }
        } finally { vad.release() }
        return segs
    }

    private fun recognizer(spec: ModelSpec): OfflineRecognizer {
        val model = when (spec.id) {
            "qwen3" -> OfflineModelConfig(
                qwen3Asr = OfflineQwen3AsrModelConfig(
                    convFrontend = need(spec.required[0]), encoder = need(spec.required[1]),
                    decoder = need(spec.required[2]), tokenizer = need(spec.required[3]),
                    maxNewTokens = 256,
                ),
                numThreads = threads,
            )
            else -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = need(spec.required[0]), decoder = need(spec.required[1]),
                    language = when (prefs.language) { "ar" -> "ar"; "en" -> "en"; else -> "" },
                    task = "transcribe",
                ),
                tokens = need(spec.required[2]),
                numThreads = threads,
            )
        }
        return OfflineRecognizer(null, OfflineRecognizerConfig(modelConfig = model))
    }

    private fun embeddingConfig() = SpeakerEmbeddingExtractorConfig(
        model = need(Models.EMBEDDING.required[0]), numThreads = threads,
    )

    private fun diarize(samples: FloatArray, progress: (Float) -> Unit): Array<OfflineSpeakerDiarizationSegment> {
        val cfg = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(need(Models.SEGMENTATION.required[0])),
                numThreads = threads,
            ),
            embedding = embeddingConfig(),
            clustering = FastClusteringConfig(numClusters = -1, threshold = 0.6f),
            minDurationOn = 0.3f, minDurationOff = 0.5f,
        )
        val sd = OfflineSpeakerDiarization(null, cfg)
        try {
            // NB: processWithCallback's JNI side looks up a specialized invoke(IIJ) that Kotlin lambdas
            // don't emit (SIGABRT on device), so we use the plain call.
            progress(0.1f)
            return sd.process(samples).also { progress(1f) }
        } finally { sd.release() }
    }

    private fun assignSpeakers(asr: List<AsrSegment>, diar: Array<OfflineSpeakerDiarizationSegment>) {
        for (a in asr) {
            val overlap = HashMap<Int, Float>()
            for (d in diar) {
                val o = min(a.end, d.end) - max(a.start, d.start)
                if (o > 0) overlap[d.speaker] = (overlap[d.speaker] ?: 0f) + o
            }
            a.speaker = overlap.maxByOrNull { it.value }?.key
                ?: diar.minByOrNull { kotlin.math.abs((it.start + it.end) / 2 - (a.start + a.end) / 2) }?.speaker ?: -1
        }
    }

    /** One embedding per diarized speaker, from up to 30 s of their longest turns. */
    private fun clusterEmbeddings(samples: FloatArray, diar: Array<OfflineSpeakerDiarizationSegment>): List<Cluster> {
        val ex = SpeakerEmbeddingExtractor(null, embeddingConfig())
        try {
            return diar.groupBy { it.speaker }.map { (spk, segs) ->
                val total = segs.sumOf { (it.end - it.start).toDouble() }.toFloat()
                val chosen = segs.sortedByDescending { it.end - it.start }
                val buf = ArrayList<FloatArray>(); var acc = 0f
                for (s in chosen) {
                    if (acc >= 30f) break
                    val a = (s.start * SAMPLE_RATE).toInt().coerceIn(0, samples.size)
                    val b = (s.end * SAMPLE_RATE).toInt().coerceIn(a, samples.size)
                    buf += samples.copyOfRange(a, b); acc += s.end - s.start
                }
                Cluster(spk, total, if (acc >= 1.5f) embed(ex, buf) else null)
            }.sortedBy { it.idx }
        } finally { ex.release() }
    }

    private fun embed(ex: SpeakerEmbeddingExtractor, parts: List<FloatArray>): FloatArray? {
        val st = ex.createStream()
        parts.forEach { st.acceptWaveform(it, SAMPLE_RATE) }
        st.inputFinished()
        val e = if (ex.isReady(st)) ex.compute(st) else null
        st.release()
        return e?.let(::normalize)
    }

    data class Enrollment(val embedding: FloatArray?, val speechSec: Float, val consistency: Float?)

    /**
     * Voice-profile enrollment: 0 = find speech, 1 = build profile, 2 = self-check.
     * Self-check embeds the first and second half of the speech separately; a consistent voice
     * in a quiet room scores high, noise or a second voice scores low.
     */
    fun enroll(samples: FloatArray, step: (Int) -> Unit): Enrollment {
        step(0)
        val speech = if (Models.installed(ctx, Models.VAD)) vad(samples).map { it.samples } else listOf(samples)
        val speechSec = speech.sumOf { it.size } / SAMPLE_RATE.toFloat()
        if (speechSec < 6f) return Enrollment(null, speechSec, null)
        val ex = SpeakerEmbeddingExtractor(null, embeddingConfig())
        try {
            step(1)
            val full = embed(ex, speech) ?: return Enrollment(null, speechSec, null)
            step(2)
            val half = speech.sumOf { it.size } / 2
            val a = ArrayList<FloatArray>(); val b = ArrayList<FloatArray>(); var acc = 0
            for (s in speech) { if (acc < half) a += s else b += s; acc += s.size }
            val ea = embed(ex, a); val eb = embed(ex, b)
            val consistency = if (ea != null && eb != null) cosine(ea, eb) else null
            return Enrollment(full, speechSec, consistency)
        } finally { ex.release() }
    }

    companion object {
        fun normalize(v: FloatArray): FloatArray {
            val n = sqrt(v.fold(0f) { a, x -> a + x * x }).coerceAtLeast(1e-9f)
            return FloatArray(v.size) { v[it] / n }
        }
        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var d = 0f; var na = 0f; var nb = 0f
            for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            return d / (sqrt(na) * sqrt(nb)).coerceAtLeast(1e-9f)
        }
        /** Running mean of normalized embeddings: corrections refine the profile. */
        fun merge(old: FloatArray, n: Int, new: FloatArray): FloatArray =
            normalize(FloatArray(old.size) { (old[it] * n + new[it]) / (n + 1) })
    }
}
