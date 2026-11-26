/* Adapted from the Voice audiobook scanner (GPL-3.0). See LICENSE-VOICE.md. */
package com.abo.avatarblackout

import android.content.Context
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

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
                books.forEach { AudioBookRepository.save(context.applicationContext, it) }
                books
            }
            withContext(Dispatchers.Main) { callback(result) }
        }
    }

    private suspend fun scanBook(context: Context, candidate: AudioBookCandidate): AudioBook? {
        val files = candidate.file.audioFilesInOrder()
        if (files.isEmpty()) return null
        val metadataLimit = Semaphore(5)
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
        val book = AudioBook(
            id = AudioBookRepository.idForUri(candidate.file.uri.toString()),
            name = candidate.name,
            tracks = sorted
        )
        return book
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
            children.sortedWith(compareBy<CachedAudioDocument> { it.isFile }.thenBy { it.name.orEmpty().lowercase(Locale.ROOT) })
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
