package app.katib.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Streams decrypted audio straight into AudioTrack; plaintext never touches disk. */
class Player(private val file: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _pos = MutableStateFlow(0f)
    val position: StateFlow<Float> = _pos
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    fun play(fromSec: Float = _pos.value) {
        stop()
        _playing.value = true
        job = scope.launch {
            val reader = EncryptedAudioReader(file)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(SAMPLE_RATE * 2)
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            try {
                track.play()
                val startChunk = fromSec.toInt().coerceIn(0, maxOf(0, reader.chunkCount - 1))
                val skipBytes = (((fromSec - startChunk) * SAMPLE_RATE).toInt() * 2).coerceAtLeast(0)
                for (i in startChunk until reader.chunkCount) {
                    if (!isActive) break
                    val b = reader.chunk(i)
                    val off = if (i == startChunk) minOf(skipBytes, b.size) else 0
                    _pos.value = i + off / (SAMPLE_RATE * 2f)
                    track.write(b, off, b.size - off)
                }
                if (isActive) _pos.value = 0f
            } finally {
                runCatching { track.stop() }; track.release(); reader.close()
                _playing.value = false
            }
        }
    }

    fun stop() { job?.cancel(); job = null; _playing.value = false }
    fun release() { stop(); scope.cancel() }
}
