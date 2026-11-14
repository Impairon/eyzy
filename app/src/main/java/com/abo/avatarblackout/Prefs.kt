package com.abo.avatarblackout

import android.content.Context

object Prefs {
    private const val FILE = "avb"
    private const val KEY = "enabled"
    private const val KEY_TELEGRAM = "telegram_mode"
    private const val KEY_WHATSAPP = "whatsapp_mode"
    private const val KEY_LINKEDIN = "linkedin_mode"
    private const val KEY_SIBHA_COUNT = "sibha_count"

    enum class MaskMode { EDGE, DYNAMIC, OFF }

    fun isEnabled(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(c: Context, on: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    fun mode(c: Context, packageName: String): MaskMode {
        val key = when {
            packageName.startsWith("org.telegram") -> KEY_TELEGRAM
            packageName.startsWith("com.whatsapp") -> KEY_WHATSAPP
            packageName == "com.linkedin.android" -> KEY_LINKEDIN
            else -> return MaskMode.DYNAMIC
        }
        val default = if (key == KEY_LINKEDIN) MaskMode.DYNAMIC else MaskMode.EDGE
        return runCatching {
            MaskMode.valueOf(c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(key, default.name) ?: default.name)
        }.getOrDefault(default)
    }

    fun setMode(c: Context, packageName: String, mode: MaskMode) {
        val key = when {
            packageName.startsWith("org.telegram") -> KEY_TELEGRAM
            packageName.startsWith("com.whatsapp") -> KEY_WHATSAPP
            packageName == "com.linkedin.android" -> KEY_LINKEDIN
            else -> return
        }
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(key, mode.name).apply()
    }

    fun sibhaCount(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_SIBHA_COUNT, 0)

    fun setSibhaCount(c: Context, count: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SIBHA_COUNT, count.coerceAtLeast(0)).apply()
}
