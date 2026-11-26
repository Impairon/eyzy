package com.abo.avatarblackout

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class AudioBookTrack(
    val path: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val mimeType: String = "",
    val sourceName: String = ""
)

internal data class AudioBookBookmark(
    val id: String,
    val name: String,
    val trackIndex: Int,
    val positionMs: Long
)

internal data class AudioBook(
    val id: String,
    val name: String,
    val tracks: List<AudioBookTrack>,
    val currentTrackIndex: Int = 0,
    val positionMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val bookmarks: List<AudioBookBookmark> = emptyList()
) {
    val currentTrack: AudioBookTrack?
        get() = tracks.getOrNull(currentTrackIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)))
}

/** Small local catalogue: audio payloads stay as files; only track metadata is stored here. */
internal object AudioBookRepository {
    private const val PREFS = "avb"
    private const val BOOK_IDS = "audio_book_ids"
    private const val ACTIVE_BOOK = "audio_active_book"

    private fun progressIndexKey(id: String) = "audio_book_progress_index_$id"
    private fun progressPositionKey(id: String) = "audio_book_progress_position_$id"
    private fun progressTimeKey(id: String) = "audio_book_progress_time_$id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun newId(): String = UUID.randomUUID().toString()

    fun all(context: Context): List<AudioBook> {
        val ids = runCatching {
            val array = JSONArray(prefs(context).getString(BOOK_IDS, "[]"))
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        return ids.mapNotNull { load(context, it) }.sortedByDescending { it.lastPlayedAt }
    }

    fun load(context: Context, id: String): AudioBook? {
        val store = prefs(context)
        val raw = store.getString("audio_book_$id", null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val tracksJson = json.optJSONArray("tracks") ?: JSONArray()
            val tracks = (0 until tracksJson.length()).mapNotNull { index ->
                val item = tracksJson.optJSONObject(index) ?: return@mapNotNull null
                AudioBookTrack(
                    path = item.optString("path"),
                    title = item.optString("title"),
                    artist = item.optString("artist"),
                    album = item.optString("album"),
                    durationMs = item.optLong("durationMs").coerceAtLeast(0L),
                    trackNumber = item.optInt("trackNumber").coerceAtLeast(0),
                    discNumber = item.optInt("discNumber").coerceAtLeast(0),
                    mimeType = item.optString("mimeType"),
                    sourceName = item.optString("sourceName")
                ).takeIf { it.path.isNotBlank() && File(it.path).isFile }
            }
            if (tracks.isEmpty()) return@runCatching null
            val bookmarkJson = json.optJSONArray("bookmarks") ?: JSONArray()
            val bookmarks = (0 until bookmarkJson.length()).mapNotNull { index ->
                val item = bookmarkJson.optJSONObject(index) ?: return@mapNotNull null
                val trackIndex = item.optInt("trackIndex", -1)
                if (trackIndex !in tracks.indices) return@mapNotNull null
                AudioBookBookmark(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    trackIndex = trackIndex,
                    positionMs = item.optLong("positionMs").coerceAtLeast(0L)
                ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
            }
            AudioBook(
                id = id,
                name = json.optString("name", "Audio book").ifBlank { "Audio book" },
                tracks = tracks,
                currentTrackIndex = store.getInt(
                    progressIndexKey(id), json.optInt("currentTrackIndex", 0)
                ).coerceIn(0, tracks.lastIndex),
                positionMs = store.getLong(
                    progressPositionKey(id), json.optLong("positionMs")
                ).coerceAtLeast(0L),
                lastPlayedAt = store.getLong(
                    progressTimeKey(id), json.optLong("lastPlayedAt")
                ).coerceAtLeast(0L),
                bookmarks = bookmarks
            )
        }.getOrNull()
    }

    fun save(context: Context, book: AudioBook) {
        require(book.id.matches(Regex("[A-Za-z0-9-]{1,80}")))
        require(book.tracks.isNotEmpty())
        val tracks = JSONArray()
        book.tracks.forEach { track ->
            tracks.put(JSONObject().apply {
                put("path", track.path)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("durationMs", track.durationMs.coerceAtLeast(0L))
                put("trackNumber", track.trackNumber.coerceAtLeast(0))
                put("discNumber", track.discNumber.coerceAtLeast(0))
                put("mimeType", track.mimeType)
                put("sourceName", track.sourceName)
            })
        }
        val bookmarks = JSONArray()
        book.bookmarks.forEach { bookmark ->
            bookmarks.put(JSONObject().apply {
                put("id", bookmark.id)
                put("name", bookmark.name)
                put("trackIndex", bookmark.trackIndex)
                put("positionMs", bookmark.positionMs.coerceAtLeast(0L))
            })
        }
        val json = JSONObject().apply {
            put("name", book.name)
            put("tracks", tracks)
            put("currentTrackIndex", book.currentTrackIndex.coerceIn(0, book.tracks.lastIndex))
            put("positionMs", book.positionMs.coerceAtLeast(0L))
            put("lastPlayedAt", book.lastPlayedAt.coerceAtLeast(0L))
            put("bookmarks", bookmarks)
        }
        val store = prefs(context)
        val ids = runCatching {
            val array = JSONArray(store.getString(BOOK_IDS, "[]"))
            (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
        }.getOrDefault(emptyList())
        store.edit()
            .putString("audio_book_${book.id}", json.toString())
            .putInt(progressIndexKey(book.id), book.currentTrackIndex.coerceIn(0, book.tracks.lastIndex))
            .putLong(progressPositionKey(book.id), book.positionMs.coerceAtLeast(0L))
            .putLong(progressTimeKey(book.id), book.lastPlayedAt.coerceAtLeast(0L))
            .putString(BOOK_IDS, JSONArray((ids + book.id).distinct()).toString())
            .apply()
    }

    fun saveProgress(context: Context, bookId: String, trackIndex: Int, positionMs: Long) {
        if (trackIndex < 0 || !bookId.matches(Regex("[A-Za-z0-9-]{1,80}"))) return
        val now = System.currentTimeMillis()
        // Playback ticks update only three scalar values, not the full track catalogue.
        prefs(context).edit()
            .putInt(progressIndexKey(bookId), trackIndex)
            .putLong(progressPositionKey(bookId), positionMs.coerceAtLeast(0L))
            .putLong(progressTimeKey(bookId), now)
            .putString(ACTIVE_BOOK, bookId)
            .apply()
    }

    fun setActive(context: Context, bookId: String) {
        prefs(context).edit().putString(ACTIVE_BOOK, bookId).apply()
    }

    fun activeBookId(context: Context): String? =
        prefs(context).getString(ACTIVE_BOOK, null)

    fun rename(context: Context, bookId: String, name: String) {
        val book = load(context, bookId) ?: return
        save(context, book.copy(name = name.trim().ifBlank { book.name }))
    }

    fun addBookmark(
        context: Context,
        bookId: String,
        name: String,
        trackIndex: Int,
        positionMs: Long
    ) {
        val book = load(context, bookId) ?: return
        if (trackIndex !in book.tracks.indices) return
        val bookmark = AudioBookBookmark(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Bookmark ${book.bookmarks.size + 1}" },
            trackIndex = trackIndex,
            positionMs = positionMs.coerceAtLeast(0L)
        )
        save(context, book.copy(bookmarks = book.bookmarks + bookmark))
    }

    fun renameBookmark(context: Context, bookId: String, bookmarkId: String, name: String) {
        val book = load(context, bookId) ?: return
        val updated = book.bookmarks.map {
            if (it.id == bookmarkId) it.copy(name = name.trim().ifBlank { it.name }) else it
        }
        save(context, book.copy(bookmarks = updated))
    }

    fun removeBookmark(context: Context, bookId: String, bookmarkId: String) {
        val book = load(context, bookId) ?: return
        save(context, book.copy(bookmarks = book.bookmarks.filterNot { it.id == bookmarkId }))
    }

    fun delete(context: Context, bookId: String) {
        val book = load(context, bookId)
        val store = prefs(context)
        val ids = runCatching {
            val array = JSONArray(store.getString(BOOK_IDS, "[]"))
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() && it != bookId }
        }.getOrDefault(emptyList())
        store.edit()
            .remove("audio_book_$bookId")
            .remove(progressIndexKey(bookId))
            .remove(progressPositionKey(bookId))
            .remove(progressTimeKey(bookId))
            .putString(BOOK_IDS, JSONArray(ids).toString())
            .apply()
        val root = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                ?: context.filesDir,
            "audio_books"
        )
        File(root, bookId).deleteRecursively()
        if (prefs(context).getString(ACTIVE_BOOK, null) == bookId) {
            store.edit().remove(ACTIVE_BOOK).apply()
        }
    }
}
