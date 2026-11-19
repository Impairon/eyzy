package app.katib.data

import android.content.Context

/** Non-sensitive settings only. Content lives in the encrypted DB. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("katib_prefs", Context.MODE_PRIVATE)

    var asrModel: String
        get() = p.getString("asr", "qwen3")!!
        set(v) = p.edit().putString("asr", v).apply()

    /** "auto", "ar", "en" */
    var language: String
        get() = p.getString("lang", "auto")!!
        set(v) = p.edit().putString("lang", v).apply()

    var retentionDays: Int
        get() = p.getInt("retention", 5)
        set(v) = p.edit().putInt("retention", v.coerceIn(0, 30)).apply()

    var diarize: Boolean
        get() = p.getBoolean("diarize", true)
        set(v) = p.edit().putBoolean("diarize", v).apply()

    /** Cosine threshold for matching a speaker cluster to a known voice. */
    var matchThreshold: Float
        get() = p.getFloat("match", 0.55f)
        set(v) = p.edit().putFloat("match", v).apply()

    var lockEnabled: Boolean
        get() = p.getBoolean("lock", true)
        set(v) = p.edit().putBoolean("lock", v).apply()

    /** Attach phone location to new recordings by default (can be changed per recording). */
    var locationDefault: Boolean
        get() = p.getBoolean("loc", false)
        set(v) = p.edit().putBoolean("loc", v).apply()

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    fun clear() = p.edit().clear().apply()
}
