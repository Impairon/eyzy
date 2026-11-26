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
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AudioBookImportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getString(KEY_BOOK_ID).orEmpty()
        val bookName = inputData.getString(KEY_BOOK_NAME).orEmpty().ifBlank { "My books" }
        val source = inputData.getString(KEY_SOURCE_URI).orEmpty()
        if (bookId.isBlank() || source.isBlank()) return@withContext failure("The selected folder is no longer available.")

        val folder = DocumentFile.fromTreeUri(applicationContext, Uri.parse(source))
            ?: return@withContext failure("The selected folder is no longer available.")
        if (!folder.isDirectory) return@withContext failure("Choose a folder containing audio files.")

        val musicRoot = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: applicationContext.filesDir,
            "audio_books"
        )
        val target = File(musicRoot, bookId)
        runCatching {
            target.deleteRecursively()
            if (!target.mkdirs() && !target.isDirectory) error("Could not create the audio library folder.")
        }.getOrElse { return@withContext failure(it.message ?: "Could not create the audio library folder.") }

        try {
            setForeground(foregroundInfo(0))
            val files = mutableListOf<DocumentFile>()
            collectAudioFiles(folder, files)
            if (files.isEmpty()) {
                target.deleteRecursively()
                return@withContext failure("No supported audio tracks were found.")
            }

            val tracks = mutableListOf<AudioBookTrack>()
            files.forEachIndexed { index, document ->
                if (isStopped) throw kotlinx.coroutines.CancellationException()
                val sourceName = document.name.orEmpty().ifBlank { "Track ${index + 1}" }
                val metadata = readMetadata(document.uri)
                val title = metadata.title.ifBlank { sourceName.substringBeforeLast('.', sourceName) }
                val outputName = safeFileName(title, index + 1) + "." +
                    sourceName.substringAfterLast('.', "audio").lowercase(Locale.ROOT)
                val output = uniqueFile(target, outputName)
                applicationContext.contentResolver.openInputStream(document.uri)?.use { input ->
                    FileOutputStream(output).use { out -> input.copyTo(out) }
                } ?: error("Could not read an audio file.")
                tracks += AudioBookTrack(
                    path = output.absolutePath,
                    title = title,
                    artist = metadata.artist,
                    album = metadata.album,
                    durationMs = metadata.durationMs,
                    trackNumber = metadata.trackNumber,
                    discNumber = metadata.discNumber,
                    mimeType = document.type.orEmpty(),
                    sourceName = sourceName
                )
                val percent = ((index + 1) * 100 / files.size).coerceIn(0, 100)
                setProgress(workDataOf(KEY_PROGRESS to percent, KEY_STAGE to "copy"))
                setForeground(foregroundInfo(percent))
            }

            val sorted = tracks.sortedWith(
                compareBy<AudioBookTrack> { it.discNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
                    .thenBy { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
                    .thenBy { naturalKey(it.sourceName) }
            )
            AudioBookRepository.save(applicationContext, AudioBook(
                id = bookId,
                name = bookName,
                tracks = sorted
            ))
            setProgress(workDataOf(KEY_PROGRESS to 100, KEY_STAGE to "done"))
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            target.deleteRecursively()
            throw cancelled
        } catch (error: Exception) {
            target.deleteRecursively()
            failure(error.message ?: "The audio book could not be imported.")
        }
    }

    private fun collectAudioFiles(folder: DocumentFile, output: MutableList<DocumentFile>) {
        folder.listFiles().forEach { item ->
            when {
                item.isDirectory -> collectAudioFiles(item, output)
                item.isFile && isAudioFile(item) -> output += item
            }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val mime = file.type.orEmpty()
        val extension = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        return mime.startsWith("audio/") || extension in AUDIO_EXTENSIONS
    }

    private data class TrackMetadata(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = 0L,
        val trackNumber: Int = 0,
        val discNumber: Int = 0
    )

    private fun readMetadata(uri: Uri): TrackMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(applicationContext, uri)
            fun value(key: Int) = retriever.extractMetadata(key).orEmpty()
            val track = value(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val numbers = Regex("(\\d+)").findAll(track).mapNotNull { it.value.toIntOrNull() }.toList()
            TrackMetadata(
                title = value(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = value(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = value(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = value(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L,
                trackNumber = numbers.firstOrNull() ?: 0,
                discNumber = value(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER).toIntOrNull() ?: 0
            )
        } catch (_: Exception) {
            TrackMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun safeFileName(value: String, fallback: Int): String =
        value.replace(Regex("[\\\\/:*?\\"<>|]"), "_").trim().take(80).ifBlank { "Track $fallback" }

    private fun uniqueFile(directory: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "audio")
        var candidate = File(directory, name)
        var suffix = 2
        while (candidate.exists()) candidate = File(directory, "$base ($suffix).$extension").also { suffix++ }
        return candidate
    }

    private fun naturalKey(value: String): String =
        Regex("(\\d+)|(\\D+)").findAll(value.lowercase(Locale.ROOT)).joinToString("") {
            it.value.toLongOrNull()?.let { number -> number.toString().padStart(20, '0') } ?: it.value
        }

    private suspend fun failure(message: String): Result {
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    private fun foregroundInfo(progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Book imports", NotificationManager.IMPORTANCE_LOW
            ))
        }
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(UiText.translate("Importing audio book", applicationContext.getSharedPreferences("avb", Context.MODE_PRIVATE).getString("app_language", "ar") ?: "ar"))
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_BOOK_NAME = "book_name"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_STAGE = "stage"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "swizy_book_import"
        private const val NOTIFICATION_ID = 4172
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "mp4", "aac", "wav", "flac", "ogg", "oga",
            "opus", "amr", "3gp", "mka", "webm", "aiff", "aif"
        )
    }
}
