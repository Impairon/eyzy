package com.abo.avatarblackout

import android.content.Context

object Prefs {
    private const val FILE = "avb"
    private const val KEY = "enabled"
    private const val KEY_SIBHA_COUNT = "sibha_count"
    private const val KEY_SIBHA_PEEP_EVERY = "sibha_peep_every"
    private const val KEY_MASK_MODE = "mask_mode"
    private const val KEY_LINE_THICKNESS = "line_thickness_dp"
    private const val KEY_CIRCLE_SIZE = "circle_size_dp"
    private const val KEY_24_HOUR = "prayer_24_hour"

    enum class MaskMode { SIMPLE, DYNAMIC }
    private const val KEY_USE_GPS = "prayer_use_gps"
    private const val KEY_LATITUDE = "prayer_latitude"
    private const val KEY_LONGITUDE = "prayer_longitude"
    private const val KEY_PRAYER_DATE = "prayer_date"
    private const val KEY_PRAYER_TIMES = "prayer_times"
    private const val KEY_PRAYER_METHOD = "prayer_method"

    fun isEnabled(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(c: Context, on: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

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
            .putInt(KEY_LINE_THICKNESS, value.coerceIn(4, 32)).apply()

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
}
