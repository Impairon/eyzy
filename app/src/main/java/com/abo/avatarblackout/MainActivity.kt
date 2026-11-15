package com.abo.avatarblackout

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.View
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.TranslateAnimation
import android.widget.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val purple = Color.rgb(139, 92, 246)
    private val backgroundColor = Color.rgb(20, 17, 27)
    private val panel = Color.rgb(35, 29, 46)
    private val panelRaised = Color.rgb(45, 37, 59)
    private val border = Color.rgb(79, 64, 99)
    private val secondary = Color.rgb(177, 166, 194)
    private lateinit var content: LinearLayout
    private lateinit var menu: LinearLayout
    private lateinit var pageTitle: TextView
    private lateinit var root: FrameLayout
    private var currentPage = 0
    private val prayerLabels = linkedMapOf(
        "Fajr" to "Fajr",
        "Sunrise" to "Sunrise",
        "Dhuhr" to "Dhuhr",
        "Asr" to "Asr",
        "Maghrib" to "Maghrib",
        "Isha" to "Isha"
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        instance = this
        installCrashLogger()
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        showShell()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) {
            openPage(currentPage)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (menu.visibility == View.VISIBLE) {
            toggleMenu()
        } else {
            super.onBackPressed()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (menu.visibility == View.VISIBLE && event.actionMasked == MotionEvent.ACTION_DOWN) {
            val location = IntArray(2)
            menu.getLocationOnScreen(location)
            val inside = event.rawX >= location[0] &&
                event.rawX < location[0] + menu.width &&
                event.rawY >= location[1] &&
                event.rawY < location[1] + menu.height
            if (!inside) {
                toggleMenu()
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(filesDir, "swizy-crash.log").appendText(
                    "${Date()} ${thread.name}\n${error.stackTraceToString()}\n"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private class SwipeFrameLayout(
        context: android.content.Context,
        private val onSwipe: (Int) -> Unit
    ) : FrameLayout(context) {
        private var downX = 0f
        private var downY = 0f
        private var tracking = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    tracking = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (!tracking && kotlin.math.abs(dx) > 48f &&
                        kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f
                    ) {
                        tracking = true
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
            }
            return tracking
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP && tracking) {
                onSwipe(if (event.x < downX) -1 else 1)
                tracking = false
            } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                tracking = false
            }
            return true
        }
    }

    private fun showShell() {
        val pad = dp(20)
        root = SwipeFrameLayout(this) { direction ->
            navigate(if (direction < 0) 1 else -1)
        }.apply {
            setBackgroundColor(backgroundColor)
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }
        val title = TextView(this).apply {
            text = "swizy"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(pad, dp(18), pad, dp(4))
        }
        shell.addView(title)
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, 0, pad, dp(8))
        }
        val menuButton = Button(this).apply {
            text = "☰"
            textSize = 25f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = outlined(panelRaised, purple, 14)
            setOnClickListener { toggleMenu() }
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(54), dp(48)))
        pageTitle = TextView(this).apply {
            text = "swizy"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(14), 0, 0, 0)
        }
        header.addView(pageTitle, LinearLayout.LayoutParams(0, dp(48), 1f))
        shell.addView(header)
        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(18), pad, dp(14))
            background = outlined(Color.rgb(28, 23, 38), border, 20)
            visibility = View.GONE
        }
        val drawerParams = FrameLayout.LayoutParams(dp(292), -1).apply {
            gravity = Gravity.START
        }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        root.addView(menu, drawerParams)
        menu.addView(TextView(this).apply {
            text = "swizy menu"
            textSize = 13f
            letterSpacing = .12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(secondary)
            setPadding(dp(6), 0, dp(6), dp(12))
        }, LinearLayout.LayoutParams(-1, dp(34)))
        addMenuItem("swizy") { openPage(0) }
        addMenuItem("Sibha") { openPage(1) }
        addMenuItem("Prayers") { openPage(2) }
        addMenuItem("Settings") { openPage(3) }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, pad)
        }
        scroll.addView(content)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        menu.bringToFront()
        setContentView(root)
        openPage(0)
    }

    private fun openPage(page: Int) {
        currentPage = page.mod(4)
        menu.visibility = View.GONE
        when (currentPage) {
            0 -> showLowery()
            1 -> showSibha()
            2 -> showPrayers()
            else -> showSettings()
        }
    }

    private fun navigate(step: Int) {
        openPage((currentPage + step).mod(4))
    }

    private fun toggleMenu() {
        if (menu.visibility == View.VISIBLE) {
            val out = TranslateAnimation(0f, -menu.width.toFloat(), 0f, 0f).apply {
                duration = 180
                fillAfter = true
            }

            out.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) = Unit
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) = Unit
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    menu.visibility = View.GONE
                    menu.clearAnimation()
                }
            })
            menu.startAnimation(out)
        } else {
            menu.visibility = View.VISIBLE
            menu.translationX = -menu.width.toFloat()
            menu.animate().translationX(0f).setDuration(180).start()
        }
    }

    private fun showLowery() {
        pageTitle.text = "swizy"
        content.removeAllViews()
        content.addView(card("swizy magic ✨") {
            addText(it, "swizy protects profile pictures with a narrow bar while keeping chats, buttons, and the keyboard usable.")
            val enabled = Switch(this).apply {
                text = "swizy is watching"
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
        content.addView(card("Hide an area manually") {
            addText(it, "Choose any part of WhatsApp or another supported app and keep it completely black. The small side control can hide or restore it, choose a new area, or remove it.")
            val manual = actionButton("Summon the paw")
            manual.setOnClickListener {
                val service = AvatarService.instance
                if (service == null || !Prefs.isEnabled(this)) {
                    Toast.makeText(this, "Enable the swizy accessibility service first", Toast.LENGTH_LONG).show()
                } else {
                    service.summonManualHideControl()
                    Toast.makeText(this, "Use the ball menu, then choose Add", Toast.LENGTH_LONG).show()
                }
            }
            it.addView(manual)
        })
        content.addView(card("Mask shape") {
            addText(it, "Simple line is the default straight vertical bar. Dynamic circle mode and both size controls are available in Settings.")
        })
        content.addView(card("Quick Settings") {
            addText(it, "Add the swizy tile to start or close swizy from the notification shade.")
        })
    }

    private fun showSibha() {
        pageTitle.text = "Sibha"
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
            text = "Tap to count"
            textSize = 24f
            minHeight = dp(180)
            setTextColor(Color.WHITE)
            background = rounded(purple, 28)
            setOnClickListener {
                val next = Prefs.sibhaCount(this@MainActivity) + 1
                Prefs.setSibhaCount(this@MainActivity, next)
                peepIfNeeded(next)
                update()
            }
        }
        val reset = actionButton("Reset")
        reset.setOnClickListener { Prefs.setSibhaCount(this, 0); update() }
        card.addView(label)
        card.addView(count)
        card.addView(bead, LinearLayout.LayoutParams(-1, dp(180)))
        content.addView(card)
        content.addView(Space(this), LinearLayout.LayoutParams(1, dp(18)))
        content.addView(reset, LinearLayout.LayoutParams(-1, dp(52)))
    }

    private fun showSettings() {
        pageTitle.text = "Settings"
        content.removeAllViews()
        content.addView(card("Profiles & backup") {
            addText(it, "Manual boxes are saved separately for each app profile and screen orientation.")
            val profiles = Prefs.profiles(this)
            addText(
                it,
                "Create profiles from the paw menu: choose Add profile in the target app, draw your boxes, then choose Save profile. swizy will apply that profile automatically whenever the app is open."
            )
            profiles.forEach { profile ->
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(TextView(this@MainActivity).apply {
                    text = "${profile.name}\n${profile.packageName}"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                }, LinearLayout.LayoutParams(0, dp(58), 1f))
                val remove = actionButton("Remove")
                remove.setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Remove profile?")
                        .setMessage(profile.name)
                        .setPositiveButton("Remove") { _, _ ->
                            Prefs.deleteProfile(this@MainActivity, profile.id)
                            AvatarService.instance?.refresh()
                            showSettings()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                row.addView(remove, LinearLayout.LayoutParams(dp(110), dp(52)))
                it.addView(row)
            }
            val export = actionButton("Export layouts")
            export.setOnClickListener { exportBackup() }
            it.addView(export)
            val import = actionButton("Import layouts")
            import.setOnClickListener { importBackup() }
            it.addView(import)
        })
        content.addView(card("Settings") {
            addText(it, "Control shared behavior for swizy, Sibha, and Prayers.")
            addText(it, "Prayer calculation method")
            val method = styledSpinner()
            val methodNames = arrayOf(
                "Egyptian General Authority",
                "Muslim World League",
                "Umm Al-Qura",
                "Karachi",
                "ISNA"
            )
            val methodValues = intArrayOf(5, 3, 4, 1, 2)
            method.adapter = optionAdapter(methodNames)
            method.setSelection(methodValues.indexOf(Prefs.prayerMethod(this)).coerceAtLeast(0))
            method.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    if (Prefs.prayerMethod(this@MainActivity) != methodValues[position]) {
                        Prefs.setPrayerMethod(this@MainActivity, methodValues[position])
                    }
                }
            })
            it.addView(method, LinearLayout.LayoutParams(-1, dp(52)))
            addText(it, "swizy mask mode")
            val mode = styledSpinner()
            mode.adapter = optionAdapter(
                arrayOf("Simple line", "Dynamic circle", "Do nothing (manual only)")
            )
            mode.setSelection(Prefs.maskMode(this).ordinal)
            mode.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    Prefs.setMaskMode(this@MainActivity, Prefs.MaskMode.entries[position])
                    AvatarService.instance?.refresh()
                }
            })
            it.addView(mode, LinearLayout.LayoutParams(-1, dp(52)))
            addText(it, "Mask color")
            val color = styledSpinner()
            color.adapter = optionAdapter(
                arrayOf("Black (privacy safe)", "Purple", "White")
            )
            color.setSelection(Prefs.boxColor(this).ordinal)
            color.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    Prefs.setBoxColor(this@MainActivity, Prefs.BoxColor.entries[position])
                    AvatarService.instance?.refresh()
                }
            })
            it.addView(color, LinearLayout.LayoutParams(-1, dp(52)))
            addSlider(it, "Mask opacity (black boxes)", 25, 100, Prefs.boxOpacity(this)) {
                Prefs.setBoxOpacity(this, it)
                AvatarService.instance?.refresh()
            }
            addSlider(it, "Paw opacity when idle", 35, 100, Prefs.controlOpacity(this)) {
                Prefs.setControlOpacity(this, it)
                AvatarService.instance?.refresh()
            }
            addText(it, "Quick Settings action")
            val tileAction = styledSpinner()
            tileAction.adapter = optionAdapter(
                arrayOf("Start app", "Start app and summon the paw")
            )
            tileAction.setSelection(Prefs.tileAction(this).ordinal)
            tileAction.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    Prefs.setTileAction(this@MainActivity, Prefs.TileAction.entries[position])
                }
            })
            it.addView(tileAction, LinearLayout.LayoutParams(-1, dp(52)))
            val indicator = Switch(this).apply {
                text = "Show privacy indicator while masking"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.privacyIndicator(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setPrivacyIndicator(this@MainActivity, checked)
                    AvatarService.instance?.refresh()
                }
            }
            it.addView(indicator)
            val performance = Switch(this).apply {
                text = "Battery saver mode"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.performanceMode(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setPerformanceMode(this@MainActivity, checked)
                    AvatarService.instance?.refresh()
                }
            }
            it.addView(performance)
            var updatingHeadsetSwitch = false
            val headsetTime = Switch(this).apply {
                text = "Speak the time on long-press headset button"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.headsetTime(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    if (updatingHeadsetSwitch) return@setOnCheckedChangeListener
                    if (checked && Prefs.headsetTime(this@MainActivity)) return@setOnCheckedChangeListener
                    if (checked) {
                        updatingHeadsetSwitch = true
                        isChecked = false
                        updatingHeadsetSwitch = false
                        chooseHeadsetVoice(this)
                    } else {
                        Prefs.setHeadsetTime(this@MainActivity, false)
                        AvatarService.instance?.refresh()
                    }
                }
            }
            it.addView(headsetTime)
            val chooseVoice = actionButton(
                "Voice: ${Prefs.headsetVoiceGender(this).name.lowercase().replaceFirstChar { it.uppercase() }}"
            )
            chooseVoice.setOnClickListener { chooseHeadsetVoice(headsetTime) }
            it.addView(chooseVoice)
            val testTime = actionButton("Say the time now")
            testTime.setOnClickListener {
                val service = AvatarService.instance
                if (service != null) service.announceCurrentTime()
                else Toast.makeText(this, "Turn on the swizy accessibility service first", Toast.LENGTH_SHORT).show()
            }
            it.addView(testTime)
            addSlider(it, "Line thickness", 4, 64, Prefs.lineThicknessDp(this)) {
                Prefs.setLineThicknessDp(this, it)
                AvatarService.instance?.refresh()
            }
            addSlider(it, "Dynamic circle size", 24, 96, Prefs.circleSizeDp(this)) {
                Prefs.setCircleSizeDp(this, it)
                AvatarService.instance?.refresh()
            }
            val format = Switch(this).apply {
                text = "Use 24-hour prayer times"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.prayer24Hour(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setPrayer24Hour(this@MainActivity, checked)
                }
            }

            it.addView(format)
            val peep = EditText(this).apply {
                hint = "Peep every N counts (0 disables)"
                setText(Prefs.sibhaPeepEvery(this@MainActivity).takeIf { it > 0 }?.toString() ?: "")
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setTextColor(Color.WHITE)
                setHintTextColor(secondary)
            }
            it.addView(peep)
            val save = actionButton("Save Sibha reminder")
            save.setOnClickListener {
                Prefs.setSibhaPeepEvery(this@MainActivity, peep.text.toString().toIntOrNull() ?: 0)
                Toast.makeText(this, "Sibha reminder saved", Toast.LENGTH_SHORT).show()
            }
            it.addView(save)
        })
        content.addView(card("Release") {
            addText(it, "swizy 1.6.0")
            addText(it, "Changelog: manual profiles, orientation-aware layouts, undo/redo, backup tools, privacy indicator, and battery saver mode.")
            addText(it, "Crash logs are stored locally and are never uploaded automatically.")
        })
    }

    private fun chooseHeadsetVoice(headsetSwitch: Switch) {
        val options = arrayOf("Male", "Female")
        val selected = if (Prefs.headsetVoiceGender(this) == Prefs.VoiceGender.MALE) 0 else 1
        AlertDialog.Builder(this)
            .setTitle("Choose announcement voice")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                if (which == 0) {
                    AvatarService.hasMaleVoice(this) { available ->
                        runOnUiThread {
                            if (available) {
                                Prefs.setHeadsetVoiceGender(this, Prefs.VoiceGender.MALE)
                                Prefs.setHeadsetTime(this, true)
                                headsetSwitch.isChecked = true
                                AvatarService.instance?.refresh()
                                dialog.dismiss()
                            } else {
                                Prefs.setHeadsetTime(this, false)
                                headsetSwitch.isChecked = false
                                dialog.dismiss()
                                showMissingMaleVoiceInstructions()
                            }
                        }
                    }
                } else {
                    Prefs.setHeadsetVoiceGender(this, Prefs.VoiceGender.FEMALE)
                    Prefs.setHeadsetTime(this, true)
                    headsetSwitch.isChecked = true
                    AvatarService.instance?.refresh()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMissingMaleVoiceInstructions() {
        AlertDialog.Builder(this)
            .setTitle("Male voice not installed")
            .setMessage(
                "Android is not currently using a recognized male voice. In Text-to-speech " +
                    "settings, choose your TTS engine, set the language to English (United States), " +
                    "download a male voice under Voice data, select it as the active voice, then " +
                    "return to swizy and choose Male again."
            )
            .setPositiveButton("Open TTS settings") { _, _ ->
                openTextToSpeechSettings()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openTextToSpeechSettings() {
        try {
            startActivity(Intent("android.settings.TTS_SETTINGS"))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    "Open Android Settings, then search for Text-to-speech output.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun exportBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "swizy-layouts.json")
        }
        startActivityForResult(intent, EXPORT_REQUEST)
    }

    private fun importBackup() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/json"
                addCategory(Intent.CATEGORY_OPENABLE)
            },
            IMPORT_REQUEST
        )
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        runCatching {
            if (requestCode == EXPORT_REQUEST) {
                contentResolver.openOutputStream(data.data!!)?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(Prefs.exportData(this)) }
                }
                Toast.makeText(this, "Layouts exported", Toast.LENGTH_SHORT).show()
            } else if (requestCode == IMPORT_REQUEST) {
                val json = contentResolver.openInputStream(data.data!!)?.use {
                    InputStreamReader(it).use { reader -> reader.readText() }
                } ?: return
                Prefs.importData(this, json)
                AvatarService.instance?.refresh()
                showSettings()
                Toast.makeText(this, "Layouts imported", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun peepIfNeeded(count: Int) {
        val every = Prefs.sibhaPeepEvery(this)
        if (every <= 0 || count % every != 0) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { tone.release() } }, 300
            )
        }
        runCatching {
            val effect = VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= 26) {
                getSystemService(Vibrator::class.java).vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Vibrator::class.java)
                vibrator.vibrate(60)
            }
        }
    }

    private fun showPrayers() {
        pageTitle.text = "Prayers"
        content.removeAllViews()
        val date = todayKey()
        val times = parsePrayerTimes(Prefs.prayerTimes(this))
        val timesCard = card("Today's prayer times") {
            val dateText = TextView(this).apply {
                text = if (Prefs.prayerDate(this@MainActivity) == date) displayDate() else "No current schedule cached"
                textSize = 14f
                setTextColor(secondary)
                setPadding(0, dp(6), 0, dp(10))
            }
            it.addView(dateText)
            if (times.isEmpty()) {
                addText(it, "Set a location below and refresh. The last saved schedule remains available offline.")
            } else {
                times.forEach { (name, value) ->
                    val row = LinearLayout(this).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(4), 0, dp(4))
                    }
                    row.addView(TextView(this).apply {
                        text = name
                        textSize = 16f
                        setTextColor(Color.WHITE)
                    }, LinearLayout.LayoutParams(0, dp(42), 1f))
                    row.addView(TextView(this).apply {
                        text = value
                        textSize = 17f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(purple)
                    })
                    it.addView(row)
                }
            }
        }
        content.addView(timesCard)
        content.addView(card("Location and updates") {
            addText(it, "Prayer times are fetched once per day and cached for offline use.")
            val gps = Switch(this).apply {
                text = "Use device location"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.useGps(this@MainActivity)
            }
            it.addView(gps)
            val latitude = EditText(this).apply {
                hint = "Latitude, e.g. 30.0444"
                setText(Prefs.latitude(this@MainActivity))
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                setTextColor(Color.WHITE)
                setHintTextColor(secondary)
            }
            val longitude = EditText(this).apply {
                hint = "Longitude, e.g. 31.2357"
                setText(Prefs.longitude(this@MainActivity))
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                setTextColor(Color.WHITE)
                setHintTextColor(secondary)
            }
            it.addView(latitude)
            it.addView(longitude)
            fun updateLocationInputs() {
                val manual = !gps.isChecked
                latitude.visibility = if (manual) View.VISIBLE else View.GONE
                longitude.visibility = if (manual) View.VISIBLE else View.GONE
            }
            gps.setOnCheckedChangeListener { _, checked ->
                Prefs.setUseGps(this@MainActivity, checked)
                updateLocationInputs()
            }
            updateLocationInputs()
            val refresh = actionButton("Update prayer times")
            refresh.setOnClickListener {
                Prefs.setUseGps(this, gps.isChecked)
                Prefs.setCoordinates(this, latitude.text.toString().trim(), longitude.text.toString().trim())
                updatePrayerTimes(gps.isChecked, latitude.text.toString(), longitude.text.toString())
            }
            it.addView(refresh)
        })
        if (Prefs.prayerDate(this) != date) updatePrayerTimes(Prefs.useGps(this), Prefs.latitude(this), Prefs.longitude(this))
    }

    private fun updatePrayerTimes(useGps: Boolean, latitudeText: String, longitudeText: String) {
        if (useGps && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST)
            return
        }
        val coordinates = if (useGps) currentLocation() else {
            val lat = latitudeText.toDoubleOrNull()
            val lon = longitudeText.toDoubleOrNull()
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                Toast.makeText(this, "Enter valid latitude and longitude", Toast.LENGTH_LONG).show()
                return
            }
            lat to lon
        }
        if (coordinates == null) {
            requestCurrentLocation()
            return
        }
        fetchPrayerTimesFor(coordinates.first, coordinates.second)
    }

    private fun fetchPrayerTimesFor(latitude: Double, longitude: Double) {
        Toast.makeText(this, "Updating prayer times…", Toast.LENGTH_SHORT).show()
        thread(name = "prayer-update") {
            val result = fetchPrayerTimes(latitude, longitude)
            runOnUiThread {
                if (result != null) {
                    Prefs.setPrayerCache(this, todayKey(), result.toString())
                    showPrayers()
                    Toast.makeText(this, "Prayer times updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Could not update. Cached times remain available.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestCurrentLocation() {
                val manager = getSystemService(LOCATION_SERVICE) as LocationManager
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
                if (providers.isEmpty()) {
                    Toast.makeText(this, "Enable location services or enter coordinates.", Toast.LENGTH_LONG).show()
                    return
                }
                Toast.makeText(this, "Finding your location…", Toast.LENGTH_SHORT).show()
                var finished = false
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (finished) return
                        finished = true
                        runCatching { manager.removeUpdates(this) }
                        fetchPrayerTimesFor(location.latitude, location.longitude)
                    }
                }
                providers.forEach { provider ->
                    runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!finished) {
                        finished = true
                        runCatching { manager.removeUpdates(listener) }
                        Toast.makeText(this, "Location unavailable. Enter coordinates instead.", Toast.LENGTH_LONG).show()
                    }
                }, 15_000L)
    }

    private fun currentLocation(): Pair<Double, Double>? {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.asSequence().mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    }

    private fun fetchPrayerTimes(latitude: Double, longitude: Double): JSONObject? {
        val date = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        val url = URL(
            "https://api.aladhan.com/v1/timings/$date?latitude=${encode(latitude)}" +
                "&longitude=${encode(longitude)}&method=${Prefs.prayerMethod(this)}"
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) return null
            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            JSONObject(body).getJSONObject("data").getJSONObject("timings")
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePrayerTimes(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val objectJson = JSONObject(json)
            prayerLabels.mapNotNull { (key, label) ->
                objectJson.optString(key).takeIf { it.isNotBlank() }?.let {
                    label to formatPrayerTime(it.substringBefore(" "))
                }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun encode(value: Double) = URLEncoder.encode(value.toString(), "UTF-8")
    private fun todayKey() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun displayDate() = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())

    private fun formatPrayerTime(value: String): String {
        if (Prefs.prayer24Hour(this)) return value
        return runCatching {
            val input = SimpleDateFormat("HH:mm", Locale.US)
            val output = SimpleDateFormat("h:mm a", Locale.getDefault())
            output.format(input.parse(value)!!)
        }.getOrDefault(value)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == LOCATION_REQUEST && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) showPrayers()
    }

    private fun card(title: String, body: (LinearLayout) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = outlined(panel, border, 16)
            elevation = dp(2).toFloat()
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(4))
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
            setLineSpacing(dp(2).toFloat(), 1f)
        })
    }

    private fun addSlider(
        parent: LinearLayout,
        label: String,
        min: Int,
        max: Int,
        initial: Int,
        onChanged: (Int) -> Unit
    ) {
        val value = TextView(this).apply {
            textSize = 14f
            setTextColor(secondary)
        }
        fun update(number: Int) {
            value.text = "$label: ${number}${if (label.contains("opacity", true)) "%" else " dp"}"
            onChanged(number)
        }
        val slider = SeekBar(this).apply {
            this.max = max - min
            progress = initial.coerceIn(min, max) - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    update(min + progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        update(initial.coerceIn(min, max))
        parent.addView(value)
        parent.addView(slider, LinearLayout.LayoutParams(-1, dp(42)))
    }

    private fun actionButton(text: String) = Button(this).apply {
        this.text = text
        isAllCaps = false
        minHeight = dp(48)
        setPadding(dp(14), 0, dp(14), 0)
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        background = outlined(purple, Color.rgb(188, 162, 255), 14)
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(0, dp(5), 0, dp(5))
        }
    }

    private fun addMenuItem(text: String, action: () -> Unit) {
        menu.addView(Button(this).apply {
            this.text = text
            textSize = 16f
            minHeight = dp(48)
            setTextColor(Color.WHITE)
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(12), 0)
            background = outlined(panelRaised, border, 12)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply {
            setMargins(0, dp(4), 0, dp(4))
        })
    }

    private fun styledSpinner() = Spinner(this).apply {
        background = outlined(panelRaised, border, 12)
        setPopupBackgroundDrawable(outlined(Color.rgb(28, 23, 38), border, 12))
        setPadding(dp(12), 0, dp(12), 0)
    }

    private fun optionAdapter(values: Array<String>) =
        object : ArrayAdapter<String>(this, 0, values) {
            private fun row(position: Int) = TextView(this@MainActivity).apply {
                text = values[position]
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                minHeight = dp(48)
                background = rounded(panelRaised, 10)
            }

            override fun getView(
                position: Int,
                convertView: View?,
                parent: android.view.ViewGroup
            ): View = row(position)

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: android.view.ViewGroup
            ): View = row(position)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun outlined(color: Int, strokeColor: Int, radiusDp: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), strokeColor)
        }

    companion object {
        @Volatile var instance: MainActivity? = null
        private const val LOCATION_REQUEST = 1001
        private const val EXPORT_REQUEST = 1002
        private const val IMPORT_REQUEST = 1003
    }
}
