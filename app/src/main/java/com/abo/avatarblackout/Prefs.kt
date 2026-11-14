package com.abo.avatarblackout

import android.content.Context

object Prefs {
    private const val FILE = "avb"
    private const val KEY = "enabled"

    fun isEnabled(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(c: Context, on: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
}
