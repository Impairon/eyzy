package com.abo.avatarblackout

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        col.addView(TextView(this).apply {
            textSize = 16f
            text = "1. Enable \"Avatar Blackout\" in Accessibility.\n" +
                "2. Add the \"Hide avatars\" tile to your pull-down menu (edit tiles).\n" +
                "3. Tap the tile to turn black circles on or off."
        })
        col.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        setContentView(col)
    }
}
