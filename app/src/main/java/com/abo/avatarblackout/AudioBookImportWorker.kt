package com.abo.avatarblackout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.math.min

class AudioBookImportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private data class PendingTrack(
        val file: File,
        val sourceName: String,
        val extension: String
    )

    private data class TrackInfo(
        val pending: PendingTrack,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val trackNumber: Int,
        val discNumber: Int
    )

    private class ExtractionBudget(val maxBytes: Long) {
        var bytes = 0L
        fun add(count: Int) {
            bytes += count
            if (bytes > maxBytes) throw IllegalArgumentException(
                "The extracted audio is larger than the available storage limit."
            )
        }
    }

    private var lastProgressAt = 0L

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val urlText = inputData.getString(KEY_URL)?.trim().orEmpty()
        val bookName = inputData.getString(KEY_BOOK_NAME)?.trim().orEmpty()
        val bookId = inputData.getString(KEY_BOOK_ID)?.trim().orEmpty()
        if (urlText.isBlank() || bookName.isBlank() ||
            !bookId.matches(Regex("[A-Za-z0-9-]{1,80}"))
        ) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "The import details are incomplete."))
        }

        val root = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: applicationContext.filesDir,
            "audio_books"
        )
        val staging = File(root, ".$bookId.staging")
        val finalDirectory = File(root, bookId)
        var completed = false

        try {
            val uri = Uri.parse(urlText)
            if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                throw IllegalArgumentException("Enter a direct HTTP or HTTPS download link.")
            }
            root.mkdirs()
            if (!root.isDirectory) throw IllegalStateException("Could not create the audio library folder.")
            val available = StatFs(root.absolutePath).availableBytes
            if (available < MIN_FREE_SPACE) {
                throw IllegalStateException("There is not enough free storage for an audio book.")
            }
            staging.deleteRecursively()
            if (finalDirectory.exists()) throw IllegalStateException("This book already exists.")
            staging.mkdirs()

            setForeground(createForegroundInfo(0))
            val downloaded = File(staging, "download.part")
            val suggestedName = download(urlText, downloaded)
            ensureActive()
            setProgress(workDataOf(KEY_PROGRESS to 48, KEY_STAGE to "extract"))
            val tracksDirectory = File(staging, "tracks").apply { mkdirs() }
            val pending = extractAudio(downloaded, suggestedName, tracksDirectory, available)
            downloaded.delete()
            if (pending.isEmpty()) {
                throw IllegalArgumentException(
                    "No supported audio tracks were found. Use a direct audio file or ZIP/TAR/GZIP archive."
                )
            }

            setProgress(workDataOf(KEY_PROGRESS to 88, KEY_STAGE to "metadata"))
            val infos = pending.mapIndexed { index, track ->
                ensureActive()
                readMetadata(track, index)
            }.sortedWith(trackComparator())

            val usedNames = mutableSetOf<String>()
            val savedTracks = infos.mapIndexed { index, info ->
                ensureActive()
                val preferredTitle = info.title.takeIf { it.isNotBlank() }
                    ?: fallbackTitle(info.pending.sourceName, index)
                val stem = uniqueFileStem(preferredTitle, info.pending.extension, usedNames)
                val fileName = "$stem.${info.pending.extension}"
                val destination = File(staging, fileName)
                if (!info.pending.file.renameTo(destination)) {
                    info.pending.file.copyTo(destination, overwrite = false)
                    info.pending.file.delete()
                }
                AudioBookTrack(
                    path = File(finalDirectory, fileName).absolutePath,
                    title = preferredTitle,
                    artist = info.artist,
                    album = info.album,
                    durationMs = info.durationMs,
                    trackNumber = info.trackNumber,
                    discNumber = info.discNumber,
                    mimeType = mimeType(info.pending.extension),
                    sourceName = info.pending.sourceName
                )
            }

            val oldTracks = tracksDirectory
            oldTracks.deleteRecursively()
            if (!staging.renameTo(finalDirectory)) {
                throw IllegalStateException("Could not move the completed book into the library.")
            }
            val book = AudioBook(
                id = bookId,
                name = bookName,
                tracks = savedTracks
            )
            AudioBookRepository.save(applicationContext, book)
            completed = true
            setProgress(workDataOf(KEY_PROGRESS to 100, KEY_STAGE to "complete"))
            Result.success(workDataOf(KEY_BOOK_ID to bookId, KEY_BOOK_NAME to bookName))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(workDataOf(
                KEY_ERROR to (error.message ?: "The audio book could not be imported.")
            ))
        } finally {
            if (!completed) staging.deleteRecursively()
        }
    }

    private fun ensureActive() {
        if (isStopped) throw CancellationException("Audio book import was cancelled.")
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Audio book imports", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(applicationContext, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }.setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(inputData.getString(KEY_BOOK_NAME) ?: "Audio book")
            .setContentText("Downloading and organizing audio · $progress%")
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private suspend fun download(urlText: String, destination: File): String {
        val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 35_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Swizy-Audio-Library/1.0")
            setRequestProperty("Accept", "audio/*, application/zip, application/gzip, application/octet-stream, */*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("The server returned HTTP $code.")
            }
            val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.US)
            if (contentType == "text/html" || contentType == "application/xhtml+xml" ||
                contentType == "application/json"
            ) {
                throw IllegalArgumentException(
                    "That link opened a web page, not a direct audio or archive download."
                )
            }
            val length = connection.contentLengthLong
            if (length > MAX_DOWNLOAD_BYTES) {
                throw IllegalArgumentException("The download is larger than the supported 16 GB limit.")
            }
            if (length > 0L && length > StatFs(destination.parentFile!!.absolutePath).availableBytes - MIN_FREE_SPACE) {
                throw IllegalStateException("There is not enough free storage for this download.")
            }
            val dispositionName = fileNameFromDisposition(connection.getHeaderField("Content-Disposition"))
            val urlName = Uri.parse(connection.url.toString()).lastPathSegment.orEmpty()
            val suggestedName = dispositionName.ifBlank { urlName.ifBlank { "download" } }
            var copied = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_DOWNLOAD_BYTES) {
                            throw IllegalArgumentException("The download is larger than the supported 16 GB limit.")
                        }
                        output.write(buffer, 0, read)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastProgressAt > 450L) {
                            val percent = if (length > 0L) {
                                (copied * 45L / length).toInt().coerceIn(0, 45)
                            } else 8
                            setProgress(workDataOf(KEY_PROGRESS to percent, KEY_STAGE to "download"))
                            lastProgressAt = now
                        }
                    }
                }
            }
            if (destination.length() == 0L) throw IllegalArgumentException("The download was empty.")
            return suggestedName
        } finally {
            connection.disconnect()
        }
    }

    private fun extractAudio(
        source: File,
        suggestedName: String,
        outputDirectory: File,
        availableBytes: Long
    ): List<PendingTrack> {
        val budget = ExtractionBudget(
            min(MAX_EXTRACTED_BYTES, (availableBytes - MIN_FREE_SPACE).coerceAtLeast(0L))
        )
        if (budget.maxBytes <= 0L) throw IllegalStateException("There is not enough free storage to extract the archive.")
        val signature = ByteArray(512)
        val read = FileInputStream(source).use { input -> readSome(input, signature) }
        val isZip = read >= 4 && signature[0] == 'P'.code.toByte() &&
            signature[1] == 'K'.code.toByte()
        val isGzip = read >= 2 && signature[0] == 0x1f.toByte() && signature[1] == 0x8b.toByte()
        val isTar = read >= 262 && String(signature, 257, 5, Charsets.US_ASCII) == "ustar"

        return when {
            isZip -> extractZip(source, outputDirectory, budget)
            isTar -> FileInputStream(source).buffered().use { extractTar(it, outputDirectory, budget) }
            isGzip -> extractGzip(source, suggestedName, outputDirectory, budget)
            else -> {
                val extension = detectAudioExtension(source, suggestedName)
                    ?: throw IllegalArgumentException("The link is not a supported audio file or archive.")
                val copy = File(outputDirectory, "source_00001.$extension")
                source.copyTo(copy, overwrite = false)
                budget.add(copy.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                listOf(PendingTrack(copy, suggestedName, extension))
            }
        }
    }

    private fun extractZip(
        source: File,
        outputDirectory: File,
        budget: ExtractionBudget
    ): List<PendingTrack> {
        val result = mutableListOf<PendingTrack>()
        ZipInputStream(BufferedInputStream(FileInputStream(source))).use { input ->
            var entryCount = 0
            while (true) {
                ensureActive()
                val entry = input.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw IllegalArgumentException("The archive contains too many files.")
                }
                if (!entry.isDirectory) {
                    val extension = extensionOf(entry.name)
                    if (extension in SUPPORTED_AUDIO_EXTENSIONS) {
                        val target = File(outputDirectory, "source_${result.size + 1}.$extension")
                        copyEntry(input, target, budget)
                        if (target.length() > 0L) {
                            result.add(PendingTrack(target, entry.name, extension))
                        } else target.delete()
                    }
                }
                input.closeEntry()
                setProgress(workDataOf(
                    KEY_PROGRESS to (50 + (result.size % 35)),
                    KEY_STAGE to "extract"
                ))
            }
        }
        return result
    }

    private fun extractGzip(
        source: File,
        suggestedName: String,
        outputDirectory: File,
        budget: ExtractionBudget
    ): List<PendingTrack> {
        val buffered = BufferedInputStream(GZIPInputStream(FileInputStream(source)))
        buffered.use { input ->
            input.mark(512)
            val header = ByteArray(512)
            val count = readSome(input, header)
            input.reset()
            val isTar = count >= 262 && String(header, 257, 5, Charsets.US_ASCII) == "ustar"
            if (isTar) return extractTar(input, outputDirectory, budget)

            val strippedName = suggestedName.removeSuffix(".gz").removeSuffix(".GZ")
            val extension = detectAudioExtensionFromName(strippedName)
                ?: detectAudioExtensionFromStream(header, count)
                ?: throw IllegalArgumentException("The GZIP file does not contain a recognized audio format.")
            val target = File(outputDirectory, "source_00001.$extension")
            FileOutputStream(target).buffered().use { output ->
                copyBounded(input, output, budget)
            }
            return listOf(PendingTrack(target, strippedName, extension))
        }
    }

    private fun extractTar(
        input: InputStream,
        outputDirectory: File,
        budget: ExtractionBudget
    ): List<PendingTrack> {
        val result = mutableListOf<PendingTrack>()
        val header = ByteArray(512)
        var entries = 0
        while (true) {
            ensureActive()
            val count = readFullyOrEnd(input, header)
            if (count == 0) break
            if (count != 512) throw IllegalArgumentException("The TAR archive is damaged.")
            if (header.all { it == 0.toByte() }) break
            entries++
            if (entries > MAX_ARCHIVE_ENTRIES) throw IllegalArgumentException("The archive contains too many files.")

            val baseName = tarString(header, 0, 100)
            val prefix = tarString(header, 345, 155)
            val name = if (prefix.isBlank()) baseName else "$prefix/$baseName"
            val size = parseTarOctal(header, 124, 12)
            val type = header[156].toInt().toChar()
            if (size < 0L || size > budget.maxBytes) throw IllegalArgumentException("The TAR entry has an invalid size.")
            val extension = extensionOf(name)
            if (type == '0' || type == '\u0000' || type == '7') {
                if (extension in SUPPORTED_AUDIO_EXTENSIONS) {
                    val target = File(outputDirectory, "source_${result.size + 1}.$extension")
                    FileOutputStream(target).buffered().use { output ->
                        copyExactly(input, output, size, budget)
                    }
                    if (target.length() > 0L) result.add(PendingTrack(target, name, extension))
                    else target.delete()
                } else {
                    skipExactly(input, size)
                }
            } else {
                skipExactly(input, size)
            }
            val padding = (512L - (size % 512L)) % 512L
            skipExactly(input, padding)
            setProgress(workDataOf(KEY_PROGRESS to (50 + (entries % 35)), KEY_STAGE to "extract"))
        }
        return result
    }

    private fun readMetadata(track: PendingTrack, index: Int): TrackInfo {
        val retriever = MediaMetadataRetriever()
        var title = ""
        var artist = ""
        var album = ""
        var duration = 0L
        var trackNo = 0
        var discNo = 0
        try {
            retriever.setDataSource(track.file.absolutePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            trackNo = parseNumber(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
            discNo = parseNumber(
                runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                }.getOrNull()
            )
        } catch (_: Exception) {
            // Tags aren't mandatory; the original archive filename remains the fallback.
        } finally {
            runCatching { retriever.release() }
        }
        return TrackInfo(track, title, artist, album, duration, trackNo, discNo)
    }

    private fun trackComparator(): Comparator<TrackInfo> = Comparator { a, b ->
        val bothNumbered = a.trackNumber > 0 && b.trackNumber > 0
        if (bothNumbered) {
            val disc = a.discNumber.compareTo(b.discNumber)
            if (disc != 0) disc else a.trackNumber.compareTo(b.trackNumber)
        } else {
            naturalCompare(a.pending.sourceName, b.pending.sourceName)
        }
    }

    private fun naturalCompare(first: String, second: String): Int {
        val left = Regex("\\d+|\\D+").findAll(first.lowercase(Locale.ROOT)).map { it.value }.toList()
        val right = Regex("\\d+|\\D+").findAll(second.lowercase(Locale.ROOT)).map { it.value }.toList()
        for (i in 0 until min(left.size, right.size)) {
            val a = left[i]
            val b = right[i]
            val result = if (a.all(Char::isDigit) && b.all(Char::isDigit)) {
                a.trimStart('0').length.compareTo(b.trimStart('0').length).takeIf { it != 0 }
                    ?: a.trimStart('0').compareTo(b.trimStart('0'))
            } else a.compareTo(b)
            if (result != 0) return result
        }
        return left.size.compareTo(right.size)
    }

    private fun fallbackTitle(sourceName: String, index: Int): String {
        val base = sourceName.substringAfterLast('/').substringAfterLast('\\')
            .substringBeforeLast('.', sourceName).trim()
        if (base.isBlank()) return "Track ${index + 1}"
        return if (base.all { it.isDigit() || it == '-' || it == '_' || it == ' ' }) {
            "Track ${base.filter(Char::isDigit).ifBlank { (index + 1).toString() }}"
        } else base
    }

    private fun uniqueFileStem(title: String, extension: String, used: MutableSet<String>): String {
        val cleaned = title
            .replace(Regex("[\\u0000-\\u001f<>:\"/\\\\|?*]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.')
            .take(90)
            .ifBlank { "Track" }
        var candidate = cleaned
        var suffix = 2
        while (!used.add(candidate.lowercase(Locale.ROOT) + ".$extension")) {
            candidate = "$cleaned ($suffix)"
            suffix++
        }
        return candidate
    }

    private fun parseNumber(value: String?): Int =
        value?.substringBefore('/')?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.US)

    private fun detectAudioExtension(file: File, suggestedName: String): String? {
        detectAudioExtensionFromName(suggestedName)?.let { return it }
        val header = ByteArray(32)
        val count = FileInputStream(file).use { readSome(it, header) }
        return detectAudioExtensionFromStream(header, count)
    }

    private fun detectAudioExtensionFromName(name: String): String? =
        extensionOf(name).takeIf { it in SUPPORTED_AUDIO_EXTENSIONS }

    private fun detectAudioExtensionFromStream(bytes: ByteArray, count: Int): String? {
        if (count >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "fLaC") return "flac"
        if (count >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "OggS") return "ogg"
        if (count >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
        ) return "wav"
        if (count >= 3 && String(bytes, 0, 3, Charsets.US_ASCII) == "ID3") return "mp3"
        if (count >= 2 && bytes[0] == 0xff.toByte() &&
            (bytes[1].toInt() and 0xe0) == 0xe0
        ) return "mp3"
        if (count >= 8 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp") return "m4a"
        return null
    }

    private fun mimeType(extension: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "m4a", "m4b" -> "audio/mp4"
                "aac" -> "audio/aac"
                "flac" -> "audio/flac"
                "ogg", "opus" -> "audio/ogg"
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                else -> "application/octet-stream"
            }

    private fun fileNameFromDisposition(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val encoded = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(value)?.groupValues?.getOrNull(1)
        if (!encoded.isNullOrBlank()) return Uri.decode(encoded).substringAfterLast('/').substringAfterLast('\\')
        return Regex("filename=\"?([^\";]+)", RegexOption.IGNORE_CASE)
            .find(value)?.groupValues?.getOrNull(1)?.trim()
            ?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty()
    }

    private fun copyEntry(input: InputStream, target: File, budget: ExtractionBudget) {
        FileOutputStream(target).buffered().use { output -> copyBounded(input, output, budget) }
    }

    private fun copyBounded(input: InputStream, output: java.io.OutputStream, budget: ExtractionBudget) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            budget.add(read)
            output.write(buffer, 0, read)
        }
    }

    private fun copyExactly(
        input: InputStream,
        output: java.io.OutputStream,
        expected: Long,
        budget: ExtractionBudget
    ) {
        val buffer = ByteArray(64 * 1024)
        var remaining = expected
        while (remaining > 0L) {
            ensureActive()
            val read = input.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IllegalArgumentException("The TAR archive ended unexpectedly.")
            budget.add(read)
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipExactly(input: InputStream, amount: Long) {
        var remaining = amount
        val buffer = ByteArray(16 * 1024)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = input.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw IllegalArgumentException("The archive ended unexpectedly.")
                remaining -= read
            }
        }
    }

    private fun readSome(input: InputStream, destination: ByteArray): Int {
        var total = 0
        while (total < destination.size) {
            val count = input.read(destination, total, destination.size - total)
            if (count < 0) break
            total += count
        }
        return total
    }

    private fun readFullyOrEnd(input: InputStream, destination: ByteArray): Int {
        var total = 0
        while (total < destination.size) {
            val count = input.read(destination, total, destination.size - total)
            if (count < 0) break
            total += count
        }
        return total
    }

    private fun tarString(header: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { header[it] == 0.toByte() }
            ?: (offset + length)
        return String(header, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun parseTarOctal(header: ByteArray, offset: Int, length: Int): Long {
        val text = String(header, offset, length, Charsets.US_ASCII)
            .trim('\u0000', ' ')
        if (text.isBlank()) return 0L
        return text.toLongOrNull(8) ?: -1L
    }

    companion object {
        const val KEY_URL = "audio_url"
        const val KEY_BOOK_NAME = "audio_book_name"
        const val KEY_BOOK_ID = "audio_book_id"
        const val KEY_PROGRESS = "audio_import_progress"
        const val KEY_STAGE = "audio_import_stage"
        const val KEY_ERROR = "audio_import_error"
        const val KEY_RESULT_BOOK_ID = "audio_import_result_id"

        private const val CHANNEL_ID = "audio_book_imports"
        private const val NOTIFICATION_ID = 7421
        private const val MAX_DOWNLOAD_BYTES = 16L * 1024L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 32L * 1024L * 1024L * 1024L
        private const val MIN_FREE_SPACE = 64L * 1024L * 1024L
        private const val MAX_ARCHIVE_ENTRIES = 20_000

        private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "mp4", "aac", "adts", "wav", "wave",
            "flac", "ogg", "opus", "amr", "3gp", "mka", "mkv", "webm",
            "ts", "mp2", "aiff", "aif", "ac3", "ec3", "eac3", "m2ts",
            "mpg", "mpeg"
        )
    }
}
