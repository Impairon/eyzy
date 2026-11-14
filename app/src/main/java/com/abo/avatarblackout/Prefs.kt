package com.abo.avatarblackout

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val FILE = "avb"
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
    private const val KEY_BOX_COLOR = "box_color"

    enum class TileAction { START_APP, START_APP_AND_PAW }
    enum class BoxColor { BLACK, PURPLE, WHITE }
    data class Profile(val id: String, val name: String, val packageName: String)

    fun isEnabled(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(c: Context, on: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    fun tileAction(c: Context): TileAction =
        runCatching {
            TileAction.valueOf(
                c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .getString(KEY_TILE_ACTION, TileAction.START_APP.name)
                    ?: TileAction.START_APP.name
            )
        }.getOrDefault(TileAction.START_APP)

    fun setTileAction(c: Context, action: TileAction) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_TILE_ACTION, action.name).apply()

    fun privacyIndicator(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRIVACY_INDICATOR, false)

    fun setPrivacyIndicator(c: Context, enabled: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PRIVACY_INDICATOR, enabled).apply()

    fun performanceMode(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERFORMANCE_MODE, false)

    fun setPerformanceMode(c: Context, enabled: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PERFORMANCE_MODE, enabled).apply()

    fun boxOpacity(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_BOX_OPACITY, 100)

    fun setBoxOpacity(c: Context, value: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_BOX_OPACITY, value.coerceIn(25, 100)).apply()

    fun boxColor(c: Context): BoxColor =
        runCatching {
            BoxColor.valueOf(
                c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .getString(KEY_BOX_COLOR, BoxColor.BLACK.name) ?: BoxColor.BLACK.name
            )
        }.getOrDefault(BoxColor.BLACK)

    fun setBoxColor(c: Context, color: BoxColor) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_BOX_COLOR, color.name).apply()

    fun profiles(c: Context): List<Profile> {
        val raw = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
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
        if (parsed.isNotEmpty()) return parsed
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
        val id = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .ifBlank { "profile" } + "-" + System.currentTimeMillis()
        val profile = Profile(id, cleanName, packageName.trim())
        saveProfiles(c, profiles(c) + profile)
        return profile
    }

    fun profileForPackage(c: Context, packageName: String): Profile {
        return profiles(c).firstOrNull { profile ->
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
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROFILES, array.toString()).apply()
    }

    fun maskMode(c: Context): MaskMode =
        runCatching {
            MaskMode.valueOf(
                c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .getString(KEY_MASK_MODE, MaskMode.SIMPLE.name) ?: MaskMode.SIMPLE.name
            )
        }.getOrDefault(MaskMode.SIMPLE)

    fun setMaskMode(c: Context, mode: MaskMode) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_MASK_MODE, mode.name).apply()

    fun lineThicknessDp(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_LINE_THICKNESS, 10)

    fun setLineThicknessDp(c: Context, value: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LINE_THICKNESS, value.coerceIn(4, 64)).apply()

    fun circleSizeDp(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_CIRCLE_SIZE, 42)

    fun setCircleSizeDp(c: Context, value: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_CIRCLE_SIZE, value.coerceIn(24, 96)).apply()

    fun sibhaCount(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_SIBHA_COUNT, 0)

    fun setSibhaCount(c: Context, count: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SIBHA_COUNT, count.coerceAtLeast(0)).apply()

    fun sibhaPeepEvery(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_SIBHA_PEEP_EVERY, 0)

    fun setSibhaPeepEvery(c: Context, count: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SIBHA_PEEP_EVERY, count.coerceAtLeast(0)).apply()

    fun prayer24Hour(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_24_HOUR, false)

    fun setPrayer24Hour(c: Context, enabled: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_24_HOUR, enabled).apply()

    fun prayerMethod(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_PRAYER_METHOD, 5)

    fun setPrayerMethod(c: Context, method: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_PRAYER_METHOD, method).remove(KEY_PRAYER_DATE).apply()

    fun useGps(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_USE_GPS, true)

    fun setUseGps(c: Context, enabled: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_USE_GPS, enabled).apply()

    fun latitude(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LATITUDE, "") ?: ""

    fun longitude(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LONGITUDE, "") ?: ""

    fun setCoordinates(c: Context, latitude: String, longitude: String) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_LATITUDE, latitude).putString(KEY_LONGITUDE, longitude).apply()

    fun prayerDate(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PRAYER_DATE, "") ?: ""

    fun prayerTimes(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PRAYER_TIMES, "") ?: ""

    fun setPrayerCache(c: Context, date: String, timesJson: String) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_PRAYER_DATE, date).putString(KEY_PRAYER_TIMES, timesJson).apply()

    fun manualHideRect(c: Context): android.graphics.Rect? {
        return manualHideRects(c).lastOrNull()
    }

    fun setManualHideRect(c: Context, rect: android.graphics.Rect) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_MANUAL_RECT, "${rect.left},${rect.top},${rect.right},${rect.bottom}")
            .putBoolean(KEY_MANUAL_VISIBLE, true).apply()

    fun manualHideRects(c: Context): List<android.graphics.Rect> {
        val raw = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_MANUAL_RECT, null) ?: return emptyList()
        return raw.split(';').mapNotNull { encoded ->
            val values = encoded.split(',').mapNotNull { it.toIntOrNull() }
            if (values.size == 4 && values[2] > values[0] && values[3] > values[1]) {
                android.graphics.Rect(values[0], values[1], values[2], values[3])
            } else null
        }
    }

    fun addManualHideRect(c: Context, rect: android.graphics.Rect) {
        val all = manualHideRects(c) + rect
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_MANUAL_RECT, all.joinToString(";") {
                "${it.left},${it.top},${it.right},${it.bottom}"
            })
            .putBoolean(KEY_MANUAL_VISIBLE, true).apply()
    }

    fun removeLastManualHideRect(c: Context) {
        val all = manualHideRects(c).dropLast(1)
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            if (all.isEmpty()) remove(KEY_MANUAL_RECT)
            else putString(KEY_MANUAL_RECT, all.joinToString(";") {
                "${it.left},${it.top},${it.right},${it.bottom}"
            })
        }.apply()
    }

    fun replaceManualHideRect(c: Context, index: Int, rect: android.graphics.Rect) {
        val all = manualHideRects(c).toMutableList()
        if (index !in all.indices) return
        all[index] = rect
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_MANUAL_RECT, all.joinToString(";") {
                "${it.left},${it.top},${it.right},${it.bottom}"
            }).apply()
    }

    fun manualHideVisible(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_MANUAL_VISIBLE, true)

    fun setManualHideVisible(c: Context, visible: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MANUAL_VISIBLE, visible).apply()

    fun clearManualHide(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove(KEY_MANUAL_RECT).remove(KEY_MANUAL_VISIBLE).apply()

    fun manualHideRects(c: Context, profileId: String, orientation: String): List<Rect> {
        val key = "manual_hide_rect_${profileId}_${orientation}"
        val raw = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(key, null)
            ?: return emptyList()
        return raw.split(';').mapNotNull { encoded ->
            val values = encoded.split(',').mapNotNull { it.toIntOrNull() }
            if (values.size == 4 && values[2] > values[0] && values[3] > values[1]) {
                Rect(values[0], values[1], values[2], values[3])
            } else null
        }
    }

    fun saveManualHideRects(
        c: Context,
        profileId: String,
        orientation: String,
        rects: List<Rect>
    ) {
        val key = "manual_hide_rect_${profileId}_${orientation}"
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(key, rects.joinToString(";") {
                "${it.left},${it.top},${it.right},${it.bottom}"
            }).apply()
    }

    fun exportData(c: Context): String {
        val prefs = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
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
        val edit = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
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
