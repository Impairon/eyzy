/* Adapted from the Voice audiobook scanner (GPL-3.0). See LICENSE-VOICE.md. */
package com.abo.avatarblackout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.File

internal enum class AudioBookFolderMode {
    SingleBook,
    Audiobooks,
    Authors
}

internal data class AudioBookFolderPreview(
    val folderName: String,
    val suggestedMode: AudioBookFolderMode,
    val candidates: Map<AudioBookFolderMode, List<AudioBookCandidate>>
)

internal data class AudioBookCandidate(
    val file: AudioBookScanner.CachedAudioDocument,
    val name: String,
    val author: String? = null
)

internal object AudioBookScanner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val metadataLimit = Semaphore(5)
    private val scanMutex = Mutex()

    fun preview(context: Context, uri: Uri, callback: (Result<AudioBookFolderPreview>) -> Unit) {
        scope.launch {
            val result = runCatching {
                val root = CachedAudioDocument(context.applicationContext, uri)
                check(root.isDirectory) { "Choose a folder containing audiobooks." }
                val modes = AudioBookFolderMode.entries.associateWith { root.books(it) }
                AudioBookFolderPreview(
                    folderName = root.nameWithoutExtension(),
                    suggestedMode = root.guessFolderMode(),
                    candidates = modes
                )
            }
            withContext(Dispatchers.Main) { callback(result) }
        }
    }

    fun addBooks(
        context: Context,
        preview: AudioBookFolderPreview,
        mode: AudioBookFolderMode,
        callback: (Result<List<AudioBook>>) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                scanMutex.withLock {
                val candidates = preview.candidates[mode].orEmpty()
                val bookLimit = Semaphore(5)
                val books = coroutineScope {
                    candidates.map { candidate ->
                        async {
                            bookLimit.withPermit {
                                scanBook(context.applicationContext, candidate)
                            }
                        }
                    }.mapNotNull { it.await() }
                }
                books.forEach { book ->
                    val previous = AudioBookRepository.load(context.applicationContext, book.id)
                    val previousTrackPath = previous?.currentTrack?.path
                    val matchingIndex = book.tracks.indexOfFirst { it.path == previousTrackPath }
                    val restored = book.copy(
                        currentTrackIndex = if (matchingIndex >= 0) matchingIndex else 0,
                        positionMs = if (matchingIndex >= 0) previous?.positionMs ?: 0L else 0L,
                        lastPlayedAt = previous?.lastPlayedAt ?: 0L,
                        bookmarks = previous?.bookmarks.orEmpty().filter { it.trackIndex in book.tracks.indices },
                        coverPath = book.coverPath ?: previous?.coverPath
                    )
                    AudioBookRepository.save(context.applicationContext, restored)
                }
                books
                }
            }
            withContext(Dispatchers.Main) { callback(result) }
        }
    }

    private suspend fun scanBook(context: Context, candidate: AudioBookCandidate): AudioBook? {
        val files = candidate.file.audioFilesInOrder()
        if (files.isEmpty()) return null
        val tracks = coroutineScope {
            files.map { file ->
                async {
                    metadataLimit.withPermit {
                        readTrack(context, file)
                    }
                }
            }.mapNotNull { it.await() }
        }
        if (tracks.isEmpty()) return null
        val sorted = tracks.map { track ->
            if (track.artist.isBlank() && !candidate.author.isNullOrBlank()) track.copy(artist = candidate.author)
            else track
        }.sortedWith(compareBy<AudioBookTrack> { it.discNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
            .thenBy { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
            .thenBy { naturalKey(it.sourceName) })
        val bookId = AudioBookRepository.idForUri(candidate.file.uri.toString())
        val coverPath = extractCover(context, candidate.file, files, bookId)
        val book = AudioBook(
            id = bookId,
            name = candidate.name,
            tracks = sorted,
            coverPath = coverPath
        )
        return book
    }

    // Mirrors Voice 8.2.4-2: keep an existing cover, prefer an image
    // stored beside the book, then inspect the first five audio tracks.
    private fun extractCover(
        context: Context,
        bookFolder: CachedAudioDocument,
        files: List<CachedAudioDocument>,
        bookId: String
    ): String? {
        val directory = File(context.filesDir, "audio_book_covers")
        val destination = File(directory, "$bookId.jpg")
        if (destination.isFile && isUsableCover(destination)) return destination.absolutePath
        destination.delete()
        directory.mkdirs()

        if (bookFolder.isDirectory) {
            for (localCover in bookFolder.imageFilesInOrder()) {
                val saved = runCatching {
                    context.contentResolver.openInputStream(localCover.uri)?.use { it.readBytes() }
                        ?.let { storeCover(context, bookId, it) }
                }.getOrNull()
                if (saved != null) return saved
            }
        }

        // Artwork is not guaranteed to be embedded in the first chapter. Keep
        // checking tracks until we find a usable image instead of giving up
        // after an arbitrary first-five limit.
        for (file in files) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, file.uri)
                val picture = retriever.embeddedPicture ?: continue
                if (picture.isNotEmpty()) {
                    storeCover(context, bookId, picture)?.let { return it }
                }
            } catch (_: Exception) {
                // Some audio containers do not expose embedded artwork to Android.
            } finally {
                runCatching { retriever.release() }
            }
        }
        destination.delete()
        return null
    }

    internal fun storeCover(context: Context, bookId: String, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        return try {
            val directory = File(context.filesDir, "audio_book_covers")
            if (!directory.exists() && !directory.mkdirs()) return null
            val destination = File(directory, "$bookId.jpg")
            val written = destination.outputStream().buffered().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            if (written && destination.isFile && destination.length() > 0L) destination.absolutePath
            else {
                destination.delete()
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun isUsableCover(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private fun readTrack(context: Context, file: CachedAudioDocument): AudioBookTrack? {
        val sourceName = file.name.orEmpty().ifBlank { "Audio" }
        AudioBookRepository.cachedTrack(context, file.uri.toString(), file.lastModified, file.length)?.let {
            return it.copy(sourceName = sourceName, mimeType = file.mimeType.orEmpty())
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, file.uri)
            fun value(key: Int) = retriever.extractMetadata(key).orEmpty()
            val track = value(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val numbers = Regex("(\\d+)").findAll(track).mapNotNull { it.value.toIntOrNull() }.toList()
            AudioBookTrack(
                path = file.uri.toString(),
                title = value(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    .ifBlank { sourceName.substringBeforeLast('.', sourceName) },
                artist = value(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = value(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = value(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L,
                trackNumber = numbers.firstOrNull() ?: 0,
                discNumber = value(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER).toIntOrNull() ?: 0,
                mimeType = file.mimeType.orEmpty(),
                sourceName = sourceName,
                lastModified = file.lastModified,
                fileSize = file.length
            ).also { AudioBookRepository.cacheTrack(context, file.uri.toString(), it) }
        } catch (_: Exception) {
            AudioBookTrack(
                path = file.uri.toString(),
                title = sourceName.substringBeforeLast('.', sourceName),
                mimeType = file.mimeType.orEmpty(),
                sourceName = sourceName,
                lastModified = file.lastModified,
                fileSize = file.length
            ).also { AudioBookRepository.cacheTrack(context, file.uri.toString(), it) }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun naturalKey(value: String): String =
        Regex("(\\d+)|(\\D+)").findAll(value.lowercase(Locale.ROOT)).joinToString("") {
            it.value.toLongOrNull()?.let { number -> number.toString().padStart(20, '0') } ?: it.value
        }

    private fun CachedAudioDocument.guessFolderMode(): AudioBookFolderMode {
        val folders = audioFolders()
        if (folders.isNotEmpty() && folders.all { it.isPartFolder() }) return AudioBookFolderMode.SingleBook
        val (wholeBooks, looseChapters) = children.filter { it.isAudioFile() }.partition { it.looksLikeWholeBook() }
        if (looseChapters.size > folders.size + wholeBooks.size) return AudioBookFolderMode.SingleBook
        val authorFolders = folders.count { it.looksLikeAuthor() }
        return if (authorFolders * 2 > folders.size) AudioBookFolderMode.Authors else AudioBookFolderMode.Audiobooks
    }

    private fun CachedAudioDocument.books(mode: AudioBookFolderMode): List<AudioBookCandidate> {
        val candidates: List<Pair<CachedAudioDocument, String?>> = when (mode) {
            AudioBookFolderMode.SingleBook -> listOf(this to null)
            AudioBookFolderMode.Audiobooks -> children.map { it to null }
            AudioBookFolderMode.Authors -> children.flatMap { author ->
                if (author.isFile) listOf(author to null)
                else author.children.map { it to author.nameWithoutExtension() }
            }
        }
        return candidates.filter { it.first.audioFileCount() > 0 }.map { (file, author) ->
            AudioBookCandidate(file, file.nameWithoutExtension(), author)
        }
    }

    private fun CachedAudioDocument.imageFilesInOrder(): List<CachedAudioDocument> =
        if (!isDirectory) {
            if (isImageFile()) listOf(this) else emptyList()
        } else {
            children.flatMap { it.imageFilesInOrder() }
                .sortedWith(compareBy<CachedAudioDocument> { it.coverRank() }
                    .thenBy { naturalKey(it.name.orEmpty()) })
        }

    // Match Voice's cover preference: an explicitly named cover beats incidental
    // images (scans, page photos, etc.) in the same audiobook folder.
    private fun CachedAudioDocument.coverRank(): Int =
        when (name.orEmpty().substringBeforeLast('.', name.orEmpty()).lowercase(Locale.ROOT)) {
            "cover" -> 0
            "front" -> 1
            "folder" -> 2
            else -> 3
        }

    private fun CachedAudioDocument.isImageFile(): Boolean {
        if (!isFile) return false
        val ext = name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        return mimeType.orEmpty().startsWith("image/") ||
            ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }

    private fun CachedAudioDocument.audioFolders(): List<CachedAudioDocument> =
        children.filter { it.isDirectory && it.audioFileCount() > 0 }

    private fun CachedAudioDocument.looksLikeAuthor(): Boolean =
        children.none { it.isAudioFile() } && audioFolders().any { !it.isPartFolder() }

    private fun CachedAudioDocument.looksLikeWholeBook(): Boolean =
        name.orEmpty().endsWith(".m4b", true) || length > 200_000_000L

    private fun CachedAudioDocument.isPartFolder(): Boolean = PART_FOLDER.matches(name.orEmpty().trim())

    private fun CachedAudioDocument.audioFileCount(): Int =
        if (isDirectory) children.sumOf { it.audioFileCount() } else if (isAudioFile()) 1 else 0

    private fun CachedAudioDocument.isAudioFile(): Boolean {
        if (!isFile) return false
        val ext = name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        return mimeType.orEmpty().startsWith("audio/") || ext in AUDIO_EXTENSIONS
    }

    private fun CachedAudioDocument.audioFilesInOrder(): List<CachedAudioDocument> =
        if (isDirectory) {
            children.sortedWith(compareBy<CachedAudioDocument> { it.isFile }.thenBy { naturalKey(it.name.orEmpty()) })
                .flatMap { it.audioFilesInOrder() }
        } else if (isAudioFile()) listOf(this) else emptyList()

    private fun CachedAudioDocument.nameWithoutExtension(): String {
        val value = name ?: uri.lastPathSegment.orEmpty()
        return if (isFile) value.substringBeforeLast('.', value).ifBlank { value } else value
    }

    internal class CachedAudioDocument(
        private val context: Context,
        val uri: Uri,
        private val prefetched: DocumentContents? = null
    ) {
        val children: List<CachedAudioDocument> by lazy {
            if (isDirectory) AudioBookScanner.queryChildren(context, uri) else emptyList()
        }
        private val contents: DocumentContents? by lazy { prefetched ?: AudioBookScanner.queryDocument(context, uri) }
        val name: String? by lazy { contents?.name }
        val isFile: Boolean by lazy { contents?.isFile ?: false }
        val isDirectory: Boolean by lazy { contents?.isDirectory ?: false }
        val length: Long by lazy { contents?.length ?: 0L }
        val lastModified: Long by lazy { contents?.lastModified ?: 0L }
        val mimeType: String? by lazy { contents?.mimeType }
    }

    internal data class DocumentContents(
        val name: String?,
        val mimeType: String?,
        val isFile: Boolean,
        val isDirectory: Boolean,
        val length: Long,
        val lastModified: Long
    )

    private fun queryDocument(context: Context, uri: Uri): DocumentContents? =
        runCatching {
            context.contentResolver.query(uri, COLUMNS, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.readContents() else null
            }
        }.getOrNull()

    private fun queryChildren(context: Context, uri: Uri): List<CachedAudioDocument> =
        runCatching {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri, DocumentsContract.getDocumentId(uri)
            )
            context.contentResolver.query(childrenUri, COLUMNS, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        if (idIndex < 0) continue
                        val id = cursor.getString(idIndex) ?: continue
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                        val contents = cursor.readContents()
                        if (contents.name?.startsWith(".") == true) continue
                        add(CachedAudioDocument(context, childUri, contents))
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())

    private fun Cursor.readContents(): DocumentContents {
        fun string(column: String): String? {
            val index = getColumnIndex(column)
            return if (index < 0 || isNull(index)) null else getString(index)
        }
        fun long(column: String): Long {
            val index = getColumnIndex(column)
            return if (index < 0 || isNull(index)) 0L else getLong(index)
        }
        val mime = string(DocumentsContract.Document.COLUMN_MIME_TYPE)
        return DocumentContents(
            name = string(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            mimeType = mime,
            isFile = mime != null && mime != DocumentsContract.Document.MIME_TYPE_DIR,
            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
            length = long(DocumentsContract.Document.COLUMN_SIZE),
            lastModified = long(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        )
    }

    private val COLUMNS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    private val PART_FOLDER = Regex("(?i)^((.*[\\s._-])?(cd|disc|disk|tape|side)|part|teil|vol\\.?|volume)?[\\s._-]*\\d{1,3}$")
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "mp4", "aac", "wav", "flac", "ogg", "oga",
        "opus", "amr", "3gp", "mka", "webm", "aiff", "aif"
    )
}
