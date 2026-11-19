package app.katib.audio

import app.katib.security.Crypto
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted PCM16 mono file, chunked AES-256-GCM so long recordings are crash-safe and seekable.
 *
 * Layout: "ECA1" | sampleRate:i32 | wrappedKeyLen:i32 | wrappedKey | records...
 * Record i: iv(12) | AES-GCM(plain chunk i, aad = i as i64), each plain chunk = 1 s of audio
 * (except the last). A crash loses at most the in-flight second.
 */
const val SAMPLE_RATE = 16000
private const val CHUNK_PLAIN = SAMPLE_RATE * 2 // 1 second of PCM16
private const val RECORD = 12 + CHUNK_PLAIN + 16
private val MAGIC = "ECA1".toByteArray()

private fun aad(i: Long) = ByteBuffer.allocate(8).putLong(i).array()

class EncryptedAudioWriter(file: File) : Closeable {
    private val out = FileOutputStream(file)
    private val key = SecretKeySpec(Crypto.randomBytes(32), "AES")
    private val buf = ByteArray(CHUNK_PLAIN)
    private var fill = 0
    private var index = 0L
    var samplesWritten = 0L; private set
    private val md = java.security.MessageDigest.getInstance("SHA-256")
    /** SHA-256 of all plaintext PCM written; valid after close(). */
    var sha256Hex: String? = null; private set

    init {
        val wrapped = Crypto.wrap(key.encoded)
        val hdr = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC).putInt(SAMPLE_RATE).putInt(wrapped.size).array()
        out.write(hdr); out.write(wrapped); out.flush()
    }

    fun write(pcm: ShortArray, n: Int) {
        for (i in 0 until n) {
            val s = pcm[i].toInt()
            buf[fill++] = (s and 0xff).toByte()
            buf[fill++] = (s shr 8 and 0xff).toByte()
            if (fill == CHUNK_PLAIN) flushChunk()
        }
        samplesWritten += n
    }

    private fun flushChunk() {
        if (fill == 0) return
        val iv = Crypto.randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        c.updateAAD(aad(index++))
        md.update(buf, 0, fill)
        out.write(iv); out.write(c.doFinal(buf, 0, fill)); out.flush()
        out.fd.sync()
        fill = 0
    }

    override fun close() {
        if (sha256Hex != null) return
        flushChunk(); out.close(); key.encoded.fill(0)
        sha256Hex = md.digest().joinToString("") { "%02x".format(it) }
    }
}

class EncryptedAudioReader(file: File) : Closeable {
    private val raf = RandomAccessFile(file, "r")
    private val key: SecretKeySpec
    private val headerLen: Long
    val chunkCount: Int

    init {
        val din = DataInputStream(file.inputStream())
        val magic = ByteArray(4).also { din.readFully(it) }
        require(magic.contentEquals(MAGIC)) { "Not an Katib audio file" }
        din.readInt() // sample rate
        val wl = din.readInt()
        val wrapped = ByteArray(wl).also { din.readFully(it) }
        din.close()
        key = SecretKeySpec(Crypto.unwrap(wrapped), "AES")
        headerLen = 12L + wl
        val body = raf.length() - headerLen
        chunkCount = ((body + RECORD - 1) / RECORD).toInt()
    }

    val durationSec: Float
        get() {
            if (chunkCount == 0) return 0f
            val lastLen = (raf.length() - headerLen - (chunkCount - 1L) * RECORD) - 28
            return (chunkCount - 1) + lastLen / CHUNK_PLAIN.toFloat()
        }

    /** Returns PCM16 little-endian bytes for chunk i (1 second each). */
    fun chunk(i: Int): ByteArray {
        val off = headerLen + i.toLong() * RECORD
        val len = minOf(RECORD.toLong(), raf.length() - off).toInt()
        val rec = ByteArray(len)
        raf.seek(off); raf.readFully(rec)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, rec, 0, 12))
        c.updateAAD(aad(i.toLong()))
        return c.doFinal(rec, 12, len - 12)
    }

    /** Whole file as normalized floats, as expected by sherpa-onnx. */
    fun readAllFloats(): FloatArray {
        val total = (durationSec * SAMPLE_RATE).toInt() + SAMPLE_RATE
        val outArr = FloatArray(total)
        var n = 0
        for (i in 0 until chunkCount) {
            val b = chunk(i)
            var j = 0
            while (j + 1 < b.size) {
                val s = (b[j].toInt() and 0xff) or (b[j + 1].toInt() shl 8)
                outArr[n++] = s.toShort() / 32768f
                j += 2
            }
        }
        return outArr.copyOf(n)
    }

    override fun close() { raf.close(); key.encoded.fill(0) }
}

fun shortsToFloats(pcm: ShortArray, n: Int) = FloatArray(n) { pcm[it] / 32768f }
