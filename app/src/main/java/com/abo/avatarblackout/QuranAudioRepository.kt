package com.abo.avatarblackout

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal data class QuranTimelineState(
    val id: String,
    val chapters: List<Int>,
    val currentChapterIndex: Int = 0,
    val positionMs: Long = 0L,
    val lastPlayedAt: Long = 0L
) {
    val currentSurah: Int?
        get() = chapters.getOrNull(currentChapterIndex)
}

internal data class QuranRecitation(
    val reciterId: String,
    val reciterName: String,
    val moshafId: String,
    val moshafName: String,
    val server: String,
    val surahs: List<Int>
) {
    fun audioUrl(surah: Int): String =
        "${server.trimEnd('/')}/${surah.toString().padStart(3, '0')}.mp3"

    fun supportsFullQuran(): Boolean = surahs == (1..114).toList()
}

/**
 * Keeps listening position per timeline. The main Quran and the side-surah queue
 * have separate rows and never overwrite one another.
 */
internal object QuranAudioRepository {
    const val MAIN_TIMELINE = "main"
    const val SIDE_TIMELINE = "side"
    private const val PREFS = "avb"
    private const val ACTIVE_KEY = "quran_active_timeline"
    private const val SIDE_SELECTION_KEY = "quran_side_selection"
    private const val RECITATION_KEY = "quran_recitation"
    private const val CATALOG_KEY = "quran_recitation_catalog"
    private const val CATALOG_TIME_KEY = "quran_recitation_catalog_time"
    private const val CATALOG_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadTimeline(context: Context, id: String): QuranTimelineState {
        val raw = prefs(context).getString("quran_timeline_$id", null)
        if (raw.isNullOrBlank()) {
            return if (id == MAIN_TIMELINE) {
                QuranTimelineState(id, (1..114).toList())
            } else {
                QuranTimelineState(id, emptyList())
            }
        }
        return runCatching {
            val json = JSONObject(raw)
            val chaptersJson = json.optJSONArray("chapters") ?: JSONArray()
            val storedChapters = (0 until chaptersJson.length()).mapNotNull { index ->
                chaptersJson.optInt(index).takeIf { it in 1..114 }
            }.distinct()
            val chapters = if (id == MAIN_TIMELINE && storedChapters.isEmpty()) {
                (1..114).toList()
            } else {
                storedChapters
            }
            val index = json.optInt("chapterIndex", 0).coerceIn(
                0, (chapters.size - 1).coerceAtLeast(0)
            )
            QuranTimelineState(
                id = id,
                chapters = chapters,
                currentChapterIndex = index,
                positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
                lastPlayedAt = json.optLong("lastPlayedAt", 0L).coerceAtLeast(0L)
            )
        }.getOrElse {
            if (id == MAIN_TIMELINE) QuranTimelineState(id, (1..114).toList())
            else QuranTimelineState(id, emptyList())
        }
    }

    fun saveTimeline(context: Context, state: QuranTimelineState) {
        val chapters = state.chapters.filter { it in 1..114 }.distinct()
        val index = state.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        val json = JSONObject().apply {
            put("chapters", JSONArray(chapters))
            put("chapterIndex", index)
            put("positionMs", state.positionMs.coerceAtLeast(0L))
            put("lastPlayedAt", state.lastPlayedAt.coerceAtLeast(0L))
        }
        prefs(context).edit().putString("quran_timeline_${state.id}", json.toString()).apply()
    }

    fun saveProgress(context: Context, timelineId: String, chapterIndex: Int, positionMs: Long) {
        val state = loadTimeline(context, timelineId)
        if (chapterIndex !in state.chapters.indices) return
        saveTimeline(
            context,
            state.copy(
                currentChapterIndex = chapterIndex,
                positionMs = positionMs.coerceAtLeast(0L),
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    fun activeTimeline(context: Context): String =
        prefs(context).getString(ACTIVE_KEY, MAIN_TIMELINE)
            ?.takeIf {
                it == MAIN_TIMELINE || it == SIDE_TIMELINE ||
                    (it.startsWith("book:") &&
                        it.removePrefix("book:").matches(Regex("[A-Za-z0-9-]{1,80}")))
            } ?: MAIN_TIMELINE

    fun setActiveTimeline(context: Context, id: String) {
        val validBook = id.startsWith("book:") &&
            id.removePrefix("book:").matches(Regex("[A-Za-z0-9-]{1,80}"))
        if (id == MAIN_TIMELINE || id == SIDE_TIMELINE || validBook) {
            prefs(context).edit().putString(ACTIVE_KEY, id).apply()
        }
    }

    fun selectedSideSurah(context: Context): Int =
        prefs(context).getInt(SIDE_SELECTION_KEY, 18).coerceIn(1, 114)

    fun setSelectedSideSurah(context: Context, surah: Int) {
        if (surah in 1..114) {
            prefs(context).edit().putInt(SIDE_SELECTION_KEY, surah).apply()
        }
    }

    fun selectSideSurah(context: Context, surah: Int): QuranTimelineState {
        require(surah in 1..114)
        setSelectedSideSurah(context, surah)
        val existing = loadTimeline(context, SIDE_TIMELINE)
        val selected = if (existing.chapters == listOf(surah)) {
            existing
        } else {
            QuranTimelineState(SIDE_TIMELINE, listOf(surah))
        }
        saveTimeline(context, selected)
        return selected
    }

    fun selectedRecitation(context: Context): QuranRecitation? {
        val raw = prefs(context).getString(RECITATION_KEY, null) ?: return null
        return decodeRecitation(runCatching { JSONObject(raw) }.getOrNull())
    }

    fun saveSelectedRecitation(context: Context, recitation: QuranRecitation) {
        prefs(context).edit().putString(
            RECITATION_KEY, encodeRecitation(recitation).toString()
        ).apply()
    }

    fun cachedRecitations(context: Context): List<QuranRecitation> {
        val store = prefs(context)
        if (System.currentTimeMillis() - store.getLong(CATALOG_TIME_KEY, 0L) > CATALOG_TTL_MS) {
            return emptyList()
        }
        val raw = store.getString(CATALOG_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { decodeRecitation(array.optJSONObject(it)) }
                .filter { it.supportsFullQuran() }
        }.getOrDefault(emptyList())
    }

    fun cacheRecitations(context: Context, recitations: List<QuranRecitation>) {
        val array = JSONArray()
        recitations.forEach { array.put(encodeRecitation(it)) }
        prefs(context).edit()
            .putString(CATALOG_KEY, array.toString())
            .putLong(CATALOG_TIME_KEY, System.currentTimeMillis())
            .apply()
    }

    fun fetchRecitations(language: String): List<QuranRecitation> {
        val url = URL("https://www.mp3quran.net/api/v3/reciters?language=$language")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val reciters = JSONObject(body).optJSONArray("reciters") ?: return emptyList()
            buildList {
                for (reciterIndex in 0 until reciters.length()) {
                    val reciter = reciters.optJSONObject(reciterIndex) ?: continue
                    val moshafs = reciter.optJSONArray("moshaf") ?: continue
                    for (moshafIndex in 0 until moshafs.length()) {
                        val moshaf = moshafs.optJSONObject(moshafIndex) ?: continue
                        val server = moshaf.optString("server").trim()
                        if (!server.startsWith("https://")) continue
                        val surahs = moshaf.optString("surah_list")
                            .split(',')
                            .mapNotNull { it.trim().toIntOrNull() }
                            .filter { it in 1..114 }
                            .distinct()
                            .sorted()
                        val recitation = QuranRecitation(
                            reciterId = reciter.optString("id"),
                            reciterName = reciter.optString("name"),
                            moshafId = moshaf.optString("id"),
                            moshafName = moshaf.optString("name"),
                            server = server,
                            surahs = surahs
                        )
                        if (recitation.supportsFullQuran()) add(recitation)
                    }
                }
            }.sortedBy { it.reciterName.lowercase() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeRecitation(value: QuranRecitation) = JSONObject().apply {
        put("reciterId", value.reciterId)
        put("reciterName", value.reciterName)
        put("moshafId", value.moshafId)
        put("moshafName", value.moshafName)
        put("server", value.server)
        put("surahs", JSONArray(value.surahs))
    }

    private fun decodeRecitation(json: JSONObject?): QuranRecitation? {
        json ?: return null
        return runCatching {
            val surahsJson = json.optJSONArray("surahs") ?: JSONArray()
            val surahs = (0 until surahsJson.length())
                .mapNotNull { surahsJson.optInt(it).takeIf { number -> number in 1..114 } }
                .distinct().sorted()
            QuranRecitation(
                reciterId = json.optString("reciterId"),
                reciterName = json.optString("reciterName"),
                moshafId = json.optString("moshafId"),
                moshafName = json.optString("moshafName"),
                server = json.optString("server"),
                surahs = surahs
            ).takeIf {
                it.reciterName.isNotBlank() && it.moshafId.isNotBlank() &&
                    it.server.startsWith("https://") && it.supportsFullQuran()
            }
        }.getOrNull()
    }
}
