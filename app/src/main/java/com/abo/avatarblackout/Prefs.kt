package com.abo.avatarblackout

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val FILE = "avb"
    private fun store(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private const val KEY = "enabled"
    private const val KEY_SIBHA_COUNT = "sibha_count"
    private const val KEY_SIBHA_PEEP_EVERY = "sibha_peep_every"
    private const val KEY_MASK_MODE = "mask_mode"
    private const val KEY_LINE_THICKNESS = "line_thickness_dp"
    private const val KEY_CIRCLE_SIZE = "circle_size_dp"
    private const val KEY_24_HOUR = "prayer_24_hour"

    enum class MaskMode { SIMPLE, DYNAMIC, NONE }
    private const val KEY_USE_GPS = "prayer_use_gps"
    private const val KEY_LATITUDE = "prayer_latitude"
    private const val KEY_LONGITUDE = "prayer_longitude"
    private const val KEY_PRAYER_DATE = "prayer_date"
    private const val KEY_PRAYER_TIMES = "prayer_times"
    private const val KEY_PRAYER_METHOD = "prayer_method"
    private const val KEY_MANUAL_RECT = "manual_hide_rect"
    private const val KEY_MANUAL_VISIBLE = "manual_hide_visible"
    private const val KEY_TILE_ACTION = "tile_action"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_PRIVACY_INDICATOR = "privacy_indicator"
    private const val KEY_PERFORMANCE_MODE = "performance_mode"
    private const val KEY_BOX_OPACITY = "box_opacity"
    private const val KEY_CONTROL_OPACITY = "control_opacity"
    private const val KEY_BOX_COLOR = "box_color"
    private const val KEY_HEADSET_TIME = "headset_time"
    private const val KEY_APP_LOCK = "app_lock_enabled"
    private const val KEY_APP_LOCK_BACKGROUND_AT = "app_lock_backgrounded_at"

    enum class TileAction { START_APP, START_APP_AND_PAW }
    enum class BoxColor { BLACK, PURPLE, WHITE }
    data class Profile(val id: String, val name: String, val packageName: String)

    fun isEnabled(c: Context) =
        store(c).getBoolean(KEY, false)

    fun set(c: Context, on: Boolean) =
        store(c).edit().putBoolean(KEY, on).apply()

    fun tileAction(c: Context): TileAction =
        runCatching {
            TileAction.valueOf(
                store(c)
                    .getString(KEY_TILE_ACTION, TileAction.START_APP.name)
                    ?: TileAction.START_APP.name
            )
        }.getOrDefault(TileAction.START_APP)

    fun setTileAction(c: Context, action: TileAction) =
        store(c)
            .edit().putString(KEY_TILE_ACTION, action.name).apply()

    fun privacyIndicator(c: Context) =
        store(c)
            .getBoolean(KEY_PRIVACY_INDICATOR, false)

    fun setPrivacyIndicator(c: Context, enabled: Boolean) =
        store(c)
            .edit().putBoolean(KEY_PRIVACY_INDICATOR, enabled).apply()

    fun performanceMode(c: Context) =
        store(c)
            .getBoolean(KEY_PERFORMANCE_MODE, false)

    fun setPerformanceMode(c: Context, enabled: Boolean) =
        store(c)
            .edit().putBoolean(KEY_PERFORMANCE_MODE, enabled).apply()

    fun appLockEnabled(c: Context) = store(c).getBoolean(KEY_APP_LOCK, false)
    fun setAppLockEnabled(c: Context, enabled: Boolean) = store(c).edit().putBoolean(KEY_APP_LOCK, enabled).apply()
    fun appLockBackgroundedAt(c: Context) = store(c).getLong(KEY_APP_LOCK_BACKGROUND_AT, 0L)
    fun setAppLockBackgroundedAt(c: Context, timestamp: Long) = store(c).edit().putLong(KEY_APP_LOCK_BACKGROUND_AT, timestamp).apply()

    fun headsetTime(c: Context) =
        store(c)
            .getBoolean(KEY_HEADSET_TIME, false)

    fun setHeadsetTime(c: Context, enabled: Boolean) =
        store(c)
            .edit().putBoolean(KEY_HEADSET_TIME, enabled).apply()

    fun boxOpacity(c: Context) =
        store(c).getInt(KEY_BOX_OPACITY, 100)

    fun setBoxOpacity(c: Context, value: Int) =
        store(c)
            .edit().putInt(KEY_BOX_OPACITY, value.coerceIn(25, 100)).apply()

    fun controlOpacity(c: Context) =
        store(c)
            .getInt(KEY_CONTROL_OPACITY, 65)

    fun setControlOpacity(c: Context, value: Int) =
        store(c)
            .edit().putInt(KEY_CONTROL_OPACITY, value.coerceIn(35, 100)).apply()

    fun boxColor(c: Context): BoxColor =
        runCatching {
            BoxColor.valueOf(
                store(c)
                    .getString(KEY_BOX_COLOR, BoxColor.BLACK.name) ?: BoxColor.BLACK.name
            )
        }.getOrDefault(BoxColor.BLACK)

    fun setBoxColor(c: Context, color: BoxColor) =
        store(c)
            .edit().putString(KEY_BOX_COLOR, color.name).apply()

    fun profiles(c: Context): List<Profile> {
        val raw = store(c)
            .getString(KEY_PROFILES, null)
        val parsed = runCatching {
            JSONArray(raw ?: "[]").let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let {
                        Profile(
                            it.optString("id"),
                            it.optString("name"),
                            it.optString("packageName")
                        ).takeIf { profile ->
                            profile.id.isNotBlank() && profile.name.isNotBlank()
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
        if (raw != null) return parsed
        val defaults = listOf(
            Profile("whatsapp", "WhatsApp", "com.whatsapp"),
            Profile("telegram", "Telegram", "org.telegram.messenger"),
            Profile("web", "Web apps", "com.linkedin.android")
        )
        saveProfiles(c, defaults)
        return defaults
    }

    fun addProfile(c: Context, name: String, packageName: String): Profile {
        val cleanName = name.trim().ifBlank { "New profile" }
        val existing = profiles(c).filter { profile ->
            profile.packageName == packageName.trim() ||
                (profile.id == "whatsapp" && packageName.startsWith("com.whatsapp")) ||
                (profile.id == "telegram" && packageName.startsWith("org.telegram"))
        }
        if (existing.isNotEmpty()) {
            val selected = existing.last()
            val duplicateIds = existing.map { it.id }.toSet()
            saveProfiles(c, profiles(c).filterNot { it.id in duplicateIds } + selected)
            return selected
        }
        val id = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .ifBlank { "profile" } + "-" + System.currentTimeMillis()
        val profile = Profile(id, cleanName, packageName.trim())
        saveProfiles(c, profiles(c) + profile)
        return profile
    }

    fun renameProfile(c: Context, id: String, name: String) {
        val cleanName = name.trim().ifBlank { "Profile" }
        saveProfiles(c, profiles(c).map {
            if (it.id == id) it.copy(name = cleanName) else it
        })
    }

    fun deleteProfile(c: Context, id: String) {
        val prefs = store(c)
        saveProfiles(c, profiles(c).filterNot { it.id == id })
        prefs.edit().apply {
            prefs.all.keys
                .filter {
                    it.startsWith("manual_hide_rect_${id}_") ||
                        it.startsWith("profile_hide_rect_${id}_")
                }
                .forEach { remove(it) }
        }.apply()
    }

    fun hasProfileForPackage(c: Context, packageName: String): Boolean =
        profiles(c).any { profile ->
            profile.packageName == packageName ||
                (profile.id == "whatsapp" && packageName.startsWith("com.whatsapp")) ||
                (profile.id == "telegram" && packageName.startsWith("org.telegram"))
        }

    fun profileForPackage(c: Context, packageName: String): Profile {
        return profiles(c).lastOrNull { profile ->
            profile.packageName == packageName ||
                (profile.id == "whatsapp" && packageName.startsWith("com.whatsapp")) ||
                (profile.id == "telegram" && packageName.startsWith("org.telegram"))
        }
            ?: Profile("package-${packageName.replace('.', '-')}", packageName, packageName)
    }

    private fun saveProfiles(c: Context, profiles: List<Profile>) {
        val array = JSONArray()
        profiles.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("packageName", it.packageName)
            })
        }
        store(c).edit()
            .putString(KEY_PROFILES, array.toString()).apply()
    }

    fun maskMode(c: Context): MaskMode =
        runCatching {
            MaskMode.valueOf(
                store(c)
                    .getString(KEY_MASK_MODE, MaskMode.SIMPLE.name) ?: MaskMode.SIMPLE.name
            )
        }.getOrDefault(MaskMode.SIMPLE)

    fun setMaskMode(c: Context, mode: MaskMode) =
        store(c)
            .edit().putString(KEY_MASK_MODE, mode.name).apply()

    fun lineThicknessDp(c: Context) =
        store(c).getInt(KEY_LINE_THICKNESS, 10)

    fun setLineThicknessDp(c: Context, value: Int) =
        store(c).edit()
            .putInt(KEY_LINE_THICKNESS, value.coerceIn(4, 64)).apply()

    fun circleSizeDp(c: Context) =
        store(c).getInt(KEY_CIRCLE_SIZE, 42)

    fun setCircleSizeDp(c: Context, value: Int) =
        store(c).edit()
            .putInt(KEY_CIRCLE_SIZE, value.coerceIn(24, 96)).apply()

    fun sibhaCount(c: Context) =
        store(c).getInt(KEY_SIBHA_COUNT, 0)

    fun setSibhaCount(c: Context, count: Int) =
        store(c).edit()
            .putInt(KEY_SIBHA_COUNT, count.coerceAtLeast(0)).apply()

    fun sibhaPeepEvery(c: Context) =
        store(c).getInt(KEY_SIBHA_PEEP_EVERY, 0)

    fun setSibhaPeepEvery(c: Context, count: Int) =
        store(c).edit()
            .putInt(KEY_SIBHA_PEEP_EVERY, count.coerceAtLeast(0)).apply()

    fun prayer24Hour(c: Context) =
        store(c).getBoolean(KEY_24_HOUR, false)

    fun setPrayer24Hour(c: Context, enabled: Boolean) =
        store(c).edit()
            .putBoolean(KEY_24_HOUR, enabled).apply()

    fun prayerMethod(c: Context) =
        store(c).getInt(KEY_PRAYER_METHOD, 5)

    fun setPrayerMethod(c: Context, method: Int) =
        store(c).edit()
            .putInt(KEY_PRAYER_METHOD, method).remove(KEY_PRAYER_DATE).apply()

    fun useGps(c: Context) =
        store(c).getBoolean(KEY_USE_GPS, true)

    fun setUseGps(c: Context, enabled: Boolean) =
        store(c).edit().putBoolean(KEY_USE_GPS, enabled).apply()

    fun latitude(c: Context) =
        store(c).getString(KEY_LATITUDE, "") ?: ""

    fun longitude(c: Context) =
        store(c).getString(KEY_LONGITUDE, "") ?: ""

    fun setCoordinates(c: Context, latitude: String, longitude: String) =
        store(c).edit()
            .putString(KEY_LATITUDE, latitude).putString(KEY_LONGITUDE, longitude).apply()

    fun prayerDate(c: Context) =
        store(c).getString(KEY_PRAYER_DATE, "") ?: ""

    fun prayerTimes(c: Context) =
        store(c).getString(KEY_PRAYER_TIMES, "") ?: ""

    fun setPrayerCache(c: Context, date: String, timesJson: String) =
        store(c).edit()
            .putString(KEY_PRAYER_DATE, date).putString(KEY_PRAYER_TIMES, timesJson).apply()

    fun manualHideRects(c: Context): List<Rect> =
        decodeRects(store(c).getString(KEY_MANUAL_RECT, null))

    fun manualHideVisible(c: Context) =
        store(c).getBoolean(KEY_MANUAL_VISIBLE, true)

    fun setManualHideVisible(c: Context, visible: Boolean) =
        store(c).edit()
            .putBoolean(KEY_MANUAL_VISIBLE, visible).apply()

    fun clearManualHide(c: Context) =
        store(c).edit()
            .remove(KEY_MANUAL_RECT).remove(KEY_MANUAL_VISIBLE).apply()

    fun clearLegacyManualHideRects(c: Context) =
        store(c).edit().remove(KEY_MANUAL_RECT).apply()

    fun manualHideRects(c: Context, profileId: String, orientation: String): List<Rect> =
        decodeRects(store(c).getString("manual_hide_rect_${profileId}_${orientation}", null))

    fun profileHideRects(c: Context, profileId: String, orientation: String): List<Rect> =
        decodeRects(store(c).getString("profile_hide_rect_${profileId}_${orientation}", null))

    fun saveProfileHideRects(
        c: Context,
        profileId: String,
        orientation: String,
        rects: List<Rect>
    ) {
        val key = "profile_hide_rect_${profileId}_${orientation}"
        store(c).edit().putString(key, encodeRects(rects)).apply()
    }

    fun saveManualHideRects(
        c: Context,
        profileId: String,
        orientation: String,
        rects: List<Rect>
    ) {
        val key = "manual_hide_rect_${profileId}_${orientation}"
        store(c).edit().putString(key, encodeRects(rects)).apply()
    }

    fun clearManualHideRects(c: Context, profileId: String, orientation: String) {
        store(c).edit()
                .remove("manual_hide_rect_${profileId}_${orientation}").apply()
    }

    private fun decodeRects(raw: String?): List<Rect> =
        raw?.split(';')?.mapNotNull { encoded ->
            val parts = encoded.split(',')
            if (parts.size != 4) return@mapNotNull null
            val left = parts[0].toIntOrNull() ?: return@mapNotNull null
            val top = parts[1].toIntOrNull() ?: return@mapNotNull null
            val right = parts[2].toIntOrNull() ?: return@mapNotNull null
            val bottom = parts[3].toIntOrNull() ?: return@mapNotNull null
            if (right <= left || bottom <= top) null else Rect(left, top, right, bottom)
        } ?: emptyList()

    private fun encodeRects(rects: List<Rect>): String =
        rects.asSequence()
            .filter { it.width() > 0 && it.height() > 0 }
            .joinToString(";") { "${it.left},${it.top},${it.right},${it.bottom}" }

    fun exportData(c: Context): String {
        val prefs = store(c)
        val root = JSONObject()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is String, is Boolean, is Int, is Long, is Float ->
                    root.put(key, value)
            }
        }
        return root.toString(2)
    }

    fun importData(c: Context, json: String) {
        val root = JSONObject(json)
        val edit = store(c).edit()
        root.keys().forEach { key ->
            when (val value = root.get(key)) {
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Double -> edit.putFloat(key, value.toFloat())
                is String -> edit.putString(key, value)
            }
        }
        edit.apply()
    }
}
