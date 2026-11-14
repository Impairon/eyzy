package com.abo.avatarblackout

import android.app.Activity
import android.Manifest
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
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
    private val secondary = Color.rgb(177, 166, 194)
    private lateinit var content: LinearLayout
    private lateinit var menu: LinearLayout
    private lateinit var pageTitle: TextView
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
            text = "suizy"
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
            background = rounded(panel, 14)
            setOnClickListener { menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(54), dp(48)))
        pageTitle = TextView(this).apply {
            text = "Lowery"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(14), 0, 0, 0)
        }
        header.addView(pageTitle, LinearLayout.LayoutParams(0, dp(48), 1f))
        shell.addView(header)
        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, dp(8))
            background = rounded(panel, 16)
            visibility = View.GONE
        }
        shell.addView(menu)
        addMenuItem("Lowery") { showLowery(); menu.visibility = View.GONE }
        addMenuItem("Sibha") { showSibha(); menu.visibility = View.GONE }
        addMenuItem("Prayers") { showPrayers(); menu.visibility = View.GONE }
        addMenuItem("Settings") { showSettings(); menu.visibility = View.GONE }
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
        showLowery()
    }

    private fun showLowery() {
        pageTitle.text = "Lowery"
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
            addText(it, "Simple line is recommended: a thick, straight bar clipped to the avatar area. Dynamic follows changing avatar bounds when an app layout needs it.")
            val mode = Spinner(this)
            mode.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Simple line", "Dynamic")
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
        })
        content.addView(card("Quick Settings") {
            addText(it, "Add the Hide avatars tile to toggle Lowery quickly from the notification shade.")
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
        content.addView(card("Settings") {
            addText(it, "Control shared behavior for Lowery, Sibha, and Prayers.")
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
    }

    private fun peepIfNeeded(count: Int) {
        val every = Prefs.sibhaPeepEvery(this)
        if (every <= 0 || count % every != 0) return
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 150)
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(45)
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
        val url = URL("https://api.aladhan.com/v1/timings/$date?latitude=${encode(latitude)}&longitude=${encode(longitude)}&method=3")
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

    private fun addMenuItem(text: String, action: () -> Unit) {
        menu.addView(Button(this).apply {
            this.text = text
            textSize = 16f
            minHeight = dp(48)
            setTextColor(Color.WHITE)
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = rounded(panel, 12)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    companion object {
        private const val LOCATION_REQUEST = 1001
    }
}
