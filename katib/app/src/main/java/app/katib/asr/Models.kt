package app.katib.asr

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val REL = "https://github.com/k2-fsa/sherpa-onnx/releases/download"

/**
 * A downloadable model. [dir] is the folder (or file) name it lives under in the models dir,
 * [required] are the paths (relative to the models dir) that must exist for it to count as installed.
 */
data class ModelSpec(
    val id: String,
    val title: String,
    val url: String,
    val sizeMb: Int,
    val required: List<String>,
    val sha256: String,
    val archive: Boolean = url.endsWith(".tar.bz2"),
)

object Models {
    val VAD = ModelSpec(
        "vad", "Silero VAD", "$REL/asr-models/silero_vad.onnx", 1, listOf("silero_vad.onnx"),
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    )
    val SEGMENTATION = ModelSpec(
        "seg", "Speaker segmentation (pyannote 3.0)",
        "$REL/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2", 7,
        listOf("sherpa-onnx-pyannote-segmentation-3-0/model.onnx"),
        "24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488",
    )
    val EMBEDDING = ModelSpec(
        "emb", "Speaker embedding (WeSpeaker ResNet34)",
        "$REL/speaker-recongition-models/wespeaker_en_voxceleb_resnet34_LM.onnx", 27,
        listOf("wespeaker_en_voxceleb_resnet34_LM.onnx"),
        "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012",
    )
    private const val QWEN = "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"
    val QWEN3 = ModelSpec(
        "qwen3", "Qwen3-ASR 0.6B · Arabic + English", "$REL/asr-models/$QWEN.tar.bz2", 879,
        listOf("$QWEN/conv_frontend.onnx", "$QWEN/encoder.int8.onnx", "$QWEN/decoder.int8.onnx", "$QWEN/tokenizer"),
        "393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96",
    )
    private const val WB = "sherpa-onnx-whisper-base"
    val WHISPER_BASE = ModelSpec(
        "whisper-base", "Whisper base · fast, multilingual", "$REL/asr-models/$WB.tar.bz2", 208,
        listOf("$WB/base-encoder.int8.onnx", "$WB/base-decoder.int8.onnx", "$WB/base-tokens.txt"),
        "911b2083efd7c0dca2ac3b358b75222660dc09fb716d64fbfc417ba6c99ff3de",
    )

    val ASR = listOf(QWEN3, WHISPER_BASE)
    val SUPPORT = listOf(VAD, SEGMENTATION, EMBEDDING)
    val ALL = ASR + SUPPORT

    /** Internal storage first; app-specific external dir is accepted for adb-pushed models. */
    fun roots(ctx: Context): List<File> =
        listOfNotNull(File(ctx.filesDir, "models"), ctx.getExternalFilesDir(null)?.let { File(it, "models") })

    fun path(ctx: Context, rel: String): String? =
        roots(ctx).map { File(it, rel) }.firstOrNull { it.exists() }?.absolutePath

    fun installed(ctx: Context, m: ModelSpec) = m.required.all { path(ctx, it) != null }

    fun download(ctx: Context, m: ModelSpec, onProgress: (Float) -> Unit) {
        val root = File(ctx.filesDir, "models").apply { mkdirs() }
        val tmp = File(root, "${m.id}.part")
        val conn = (URL(m.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 20000; readTimeout = 60000
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: (m.sizeMb * 1_000_000L)
        val md = MessageDigest.getInstance("SHA-256")
        conn.inputStream.use { inp ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 16); var done = 0L; var n: Int
                while (inp.read(buf).also { n = it } >= 0) {
                    out.write(buf, 0, n); md.update(buf, 0, n); done += n
                    onProgress((done.toFloat() / total).coerceAtMost(1f) * if (m.archive) 0.6f else 1f)
                }
            }
        }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (got != m.sha256) { tmp.delete(); error("Checksum mismatch for ${m.title}") }
        if (!m.archive) {
            tmp.renameTo(File(root, m.required.first()))
        } else {
            TarArchiveInputStream(BZip2CompressorInputStream(tmp.inputStream().buffered(1 shl 16))).use { tar ->
                var e = tar.nextEntry; var written = 0L
                while (e != null) {
                    val f = File(root, e.name).canonicalFile
                    require(f.path.startsWith(root.canonicalPath)) { "bad archive entry" }
                    if (e.isDirectory) f.mkdirs() else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { tar.copyTo(it) }
                        written += e.size
                        onProgress(0.6f + 0.4f * (written.toFloat() / (m.sizeMb * 1_100_000f)).coerceAtMost(1f))
                    }
                    e = tar.nextEntry
                }
            }
            tmp.delete()
        }
        onProgress(1f)
    }

    fun delete(ctx: Context, m: ModelSpec) {
        val root = File(ctx.filesDir, "models")
        m.required.map { it.substringBefore('/') }.distinct().forEach { File(root, it).deleteRecursively() }
    }
}
