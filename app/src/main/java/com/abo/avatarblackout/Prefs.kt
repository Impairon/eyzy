package com.abo.avatarblackout

import android.content.Context

object Prefs {
    private const val FILE = "avb"
    private const val KEY = "enabled"
    private const val KEY_MASK_MODE = "mask_mode"
    private const val KEY_SIBHA_COUNT = "sibha_count"

    enum class MaskMode { AUTO, EDGE, DYNAMIC, OFF }

    fun isEnabled(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(c: Context, on: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    fun mode(c: Context): MaskMode {
        return runCatching {
            MaskMode.valueOf(c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_MASK_MODE, MaskMode.AUTO.name) ?: MaskMode.AUTO.name)
        }.getOrDefault(MaskMode.AUTO)
    }

    fun setMode(c: Context, mode: MaskMode) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_MASK_MODE, mode.name).apply()
    }

    fun sibhaCount(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_SIBHA_COUNT, 0)

    fun setSibhaCount(c: Context, count: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SIBHA_COUNT, count.coerceAtLeast(0)).apply()
}
