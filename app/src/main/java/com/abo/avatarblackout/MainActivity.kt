package com.abo.avatarblackout

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val purple = Color.rgb(139, 92, 246)
    private val backgroundColor = Color.rgb(20, 17, 27)
    private val panel = Color.rgb(35, 29, 46)
    private val secondary = Color.rgb(177, 166, 194)
    private lateinit var content: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        showShell()
    }

    private fun showShell() {
        val pad = dp(20)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }
        val title = TextView(this).apply {
            text = "eyzy"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(pad, dp(18), pad, dp(4))
        }
        shell.addView(title)
        val subtitle = TextView(this).apply {
            text = "Privacy tools, kept simple"
            textSize = 14f
            setTextColor(secondary)
            setPadding(pad, 0, pad, dp(16))
        }
        shell.addView(subtitle)
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, 0, pad, dp(8))
        }
        val lowery = tabButton("Lowery")
        val sibha = tabButton("Sibha")
        tabs.addView(lowery, LinearLayout.LayoutParams(0, dp(48), 1f))
        tabs.addView(sibha, LinearLayout.LayoutParams(0, dp(48), 1f))
        shell.addView(tabs)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, pad)
        }
        scroll.addView(content)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(shell)
        lowery.setOnClickListener { selectTab(lowery, sibha); showLowery() }
        sibha.setOnClickListener { selectTab(sibha, lowery); showSibha() }
        selectTab(lowery, sibha)
        showLowery()
    }

    private fun showLowery() {
        content.removeAllViews()
        content.addView(card("Avatar blackout") {
            addText(it, "Hide avatar pictures with a narrow bar while keeping chats, buttons, and the keyboard usable.")
            val enabled = Switch(this).apply {
                text = "Blackout service enabled"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.isEnabled(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.set(this@MainActivity, checked)
                    AvatarService.instance?.refresh()
                }
            }
            it.addView(enabled)
            val access = actionButton("Open Accessibility settings")
            access.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            it.addView(access)
        })
        content.addView(card("Mask shape") {
            addText(it, "Lowery draws a narrow vertical black bar over each detected avatar. It does not cover message text, controls, or the keyboard.")
        })
        content.addView(card("Quick Settings") {
            addText(it, "Add the Hide avatars tile to toggle Lowery quickly from the notification shade.")
        })
    }

    private fun showSibha() {
        content.removeAllViews()
        val wood = Color.rgb(82, 52, 35)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(28), dp(20), dp(28))
            background = rounded(wood, 18)
        }
        val label = TextView(this).apply {
            text = "SIBHA"
            textSize = 14f
            letterSpacing = .18f
            setTextColor(Color.rgb(244, 211, 157))
        }
        val count = TextView(this).apply {
            textSize = 64f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
        }
        fun update() { count.text = Prefs.sibhaCount(this).toString() }
        update()
        val bead = Button(this).apply {
            text = "Count"
            textSize = 18f
            setTextColor(Color.WHITE)
            background = rounded(purple, 14)
            setOnClickListener {
                Prefs.setSibhaCount(this@MainActivity, Prefs.sibhaCount(this@MainActivity) + 1)
                update()
            }
        }
        val reset = actionButton("Reset")
        reset.setOnClickListener { Prefs.setSibhaCount(this, 0); update() }
        card.addView(label)
        card.addView(count)
        card.addView(bead, LinearLayout.LayoutParams(-1, dp(56)))
        card.addView(reset)
        content.addView(card)
    }

    private fun card(title: String, body: (LinearLayout) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(panel, 16)
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        body(box)
        val wrapper = LinearLayout(this).apply {
            setPadding(0, 0, 0, dp(12))
            addView(box, LinearLayout.LayoutParams(-1, -2))
        }
        return wrapper
    }

    private fun addText(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, dp(8), 0, dp(8))
        })
    }

    private fun actionButton(text: String) = Button(this).apply {
        this.text = text
        isAllCaps = false
        minHeight = dp(48)
        setTextColor(Color.WHITE)
        background = rounded(purple, 14)
    }

    private fun tabButton(text: String) = Button(this).apply {
        this.text = text
        textSize = 15f
        minHeight = dp(48)
        setTextColor(Color.WHITE)
        isAllCaps = false
        background = rounded(panel, 14)
    }

    private fun selectTab(selected: Button, other: Button) {
        selected.background = rounded(purple, 14)
        other.background = rounded(panel, 14)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
}
