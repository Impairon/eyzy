package com.abo.avatarblackout

import android.app.Activity
import android.app.KeyguardManager
import android.app.LocaleManager
import android.app.AlertDialog
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.content.ComponentName
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.LocaleList
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Surface
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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : FragmentActivity() {
    // Swizy's violet identity: deep plum surfaces keep the accent vivid without glare.
    private val purple = Color.rgb(139, 92, 246) // #8B5CF6
    private val backgroundColor = Color.rgb(17, 14, 27) // #110E1B
    private val panel = Color.rgb(27, 23, 42) // #1B172A
    private val panelRaised = Color.rgb(39, 33, 58) // #27213A
    private val border = Color.rgb(65, 55, 91) // #41375B
    private val secondary = Color.rgb(190, 181, 211) // soft lavender
    private lateinit var content: LinearLayout
    private lateinit var menu: LinearLayout
    private lateinit var pageTitle: TextView
    private lateinit var root: FrameLayout
    private lateinit var mainScrollView: ScrollView
    private var savedScrollY = 0
    private lateinit var navigationBar: LinearLayout
    private val navigationItems = mutableListOf<Pair<Int, TextView>>()
    private var currentPage = 0
    private var quranController: MediaController? = null
    private var quranControllerFuture: ListenableFuture<MediaController>? = null
    private var quranRecitations: List<QuranRecitation> = emptyList()
    private var selectedQuranRecitation: QuranRecitation? = null
    private var loadingQuranRecitations = false
    private var quranReciterSpinner: Spinner? = null
    private var quranSurahSpinner: Spinner? = null
    private var quranStatusText: TextView? = null
    private var quranNowPlayingText: TextView? = null
    private var quranPlayPauseButton: Button? = null
    private var quranSeekBar: SeekBar? = null
    private var quranPositionText: TextView? = null
    private var quranDurationText: TextView? = null
    private var mainTimelineSummary: TextView? = null
    private var sideTimelineSummary: TextView? = null
    private var quranSeeking = false
    private var audioBookListContainer: LinearLayout? = null
    private var audioImportStatusText: TextView? = null
    private var audioImportObservationAttached = false
    private val quranProgressHandler = Handler(Looper.getMainLooper())
    private val quranProgressTicker = object : Runnable {
        override fun run() {
            updateQuranPlaybackUi()
            if (currentPage == 4) quranProgressHandler.postDelayed(this, 1_000L)
        }
    }
    private val quranPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateQuranPlaybackUi()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateQuranPlaybackUi()
        override fun onPlaybackStateChanged(playbackState: Int) = updateQuranPlaybackUi()
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) = updateQuranPlaybackUi()
    }
    private var prayerRequestInFlight = false
    private var prayerRequestId = 0L
    private var activeLocationListener: LocationListener? = null
    private var unlockedThisSession = false
    private var lockPromptActive = false
    private var deviceCredentialLauncherActive = false
    private var lockOverlay: LinearLayout? = null
    private var lockMessage: TextView? = null
    private val appLockDelayMs = 60_000L
    private val lockRequestCode = 4401
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var compassAzimuth = 0f
    private var compassTilt = 0f
    private var compassInitialized = false
    private var lastHeadingTimestampNanos = 0L
    private var compassAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var measuredMagneticFieldUt: Float? = null
    private var expectedMagneticFieldUt: Float? = null
    private var magneticDisturbanceSamples = 0
    private var magneticClearSamples = 0
    private var magneticFieldDisturbed = false
    private var compassView: CompassView? = null
    private var compassRefreshNote: TextView? = null
    private var displayRotation = Surface.ROTATION_0
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private val prayerLabels = linkedMapOf(
        "Fajr" to "Fajr",
        "Sunrise" to "Sunrise",
        "Dhuhr" to "Dhuhr",
        "Asr" to "Asr",
        "Maghrib" to "Maghrib",
        "Isha" to "Isha"
    )

    override fun attachBaseContext(newBase: Context) {
        val savedLanguage = newBase.getSharedPreferences("avb", Context.MODE_PRIVATE)
            .getString("app_language", "ar") ?: "ar"
        val platformLanguage = if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val locales = newBase.getSystemService(LocaleManager::class.java).applicationLocales
                if (!locales.isEmpty) locales[0].language else null
            }.getOrNull()
        } else null
        val language = platformLanguage?.takeIf { it.isNotBlank() } ?: savedLanguage
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    private fun currentAppLanguage(): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) return locales[0].language
        }
        return getSharedPreferences("avb", MODE_PRIVATE).getString("app_language", "ar") ?: "ar"
    }

    private fun changeAppLanguage(language: String) {
        if (language == currentAppLanguage()) return
        getSharedPreferences("avb", MODE_PRIVATE).edit()
            .putString("app_language", language).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language)
        } else {
            recreate()
        }
    }

    private fun translate(value: String): String =
        UiText.translate(value, currentAppLanguage())

    private fun localizeTree(view: View, language: String = currentAppLanguage()) {
        if (view is TextView && view.tag != USER_CONTENT_TAG) {
            view.text = UiText.translate(view.text.toString(), language)
            if (view is EditText) {
                view.hint = UiText.translate(view.hint?.toString().orEmpty(), language)
            }
            if (language == "ar" && Build.VERSION.SDK_INT >= 26) {
                val bold = view.typeface?.isBold == true
                // Android's system sans family covers Arabic without external font assets.
                view.typeface = Typeface.create(
                    "sans-serif",
                    if (bold) Typeface.BOLD else Typeface.NORMAL
                )
            }
        }
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                localizeTree(view.getChildAt(index), language)
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        instance = this
        installCrashLogger()
        currentPage = b?.getInt(STATE_PAGE, 0)?.coerceIn(0, 5) ?: 0
        savedScrollY = b?.getInt(STATE_SCROLL_Y, 0)?.coerceAtLeast(0) ?: 0
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        showShell()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        savedScrollY = if (::mainScrollView.isInitialized) mainScrollView.scrollY else savedScrollY
        outState.putInt(STATE_PAGE, currentPage)
        outState.putInt(STATE_SCROLL_Y, savedScrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::content.isInitialized || !::mainScrollView.isInitialized) return
        val scrollY = mainScrollView.scrollY
        root.layoutDirection = if (currentAppLanguage() == "ar") {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
        openPage(currentPage, preserveScroll = true, restoreScrollY = scrollY)
    }

    override fun onDestroy() {
        prayerRequestId++
        prayerRequestInFlight = false
        activeLocationListener?.let { listener ->
            runCatching {
                (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(listener)
            }
        }
        activeLocationListener = null
        quranProgressHandler.removeCallbacks(quranProgressTicker)
        quranController?.removeListener(quranPlayerListener)
        quranControllerFuture?.let { MediaController.releaseFuture(it) }
        quranControllerFuture = null
        quranController = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) {
            openPage(currentPage, preserveScroll = true, restoreScrollY = savedScrollY)
        } else {
            registerSensors()
        }
        enforceAppLock()
    }

    override fun onStop() {
        if (Prefs.appLockEnabled(this)) Prefs.setAppLockBackgroundedAt(this, System.currentTimeMillis())
        super.onStop()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == lockRequestCode) {
            deviceCredentialLauncherActive = false
            if (resultCode == RESULT_OK) unlockApp()
            else showLockMessage(translate("Authentication required to open Swizy."))
            return
        }

        if (requestCode == AUDIO_BOOK_FILE_REQUEST ||
            requestCode == AUDIO_BOOK_FOLDER_REQUEST
        ) {
            if (resultCode != RESULT_OK) return
            val uri = data?.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val isFolder = requestCode == AUDIO_BOOK_FOLDER_REQUEST
            val sourceType = if (isFolder) {
                AudioBookImportWorker.TYPE_FOLDER
            } else {
                AudioBookImportWorker.TYPE_FILE
            }
            val displayName = if (isFolder) "" else displayNameForUri(uri)
                .substringBeforeLast('.', displayNameForUri(uri))
            askAudioBookName(sourceType, uri.toString(), displayName)
            return
        }

        if (requestCode != EXPORT_REQUEST && requestCode != IMPORT_REQUEST) return
        if (resultCode != RESULT_OK || data?.data == null) return

        runCatching {
            if (requestCode == EXPORT_REQUEST) {
                contentResolver.openOutputStream(data.data!!)?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(Prefs.exportData(this)) }
                }
                Toast.makeText(this, translate("Layouts exported"), Toast.LENGTH_SHORT).show()
            } else {
                val json = contentResolver.openInputStream(data.data!!)?.use {
                    InputStreamReader(it).use { reader -> reader.readText() }
                } ?: return
                Prefs.importData(this, json)
                AvatarService.instance?.refresh()
                showSettings()
                Toast.makeText(this, translate("Layouts imported"), Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        if (::mainScrollView.isInitialized) savedScrollY = mainScrollView.scrollY
        quranProgressHandler.removeCallbacks(quranProgressTicker)
        super.onPause()
        unregisterSensors()
    }

    private fun enforceAppLock() {
        if (!Prefs.appLockEnabled(this)) {
            unlockedThisSession = true
            Prefs.setAppLockBackgroundedAt(this, 0L)
            removeLockOverlay()
            return
        }
        if (deviceCredentialLauncherActive || lockPromptActive) return
        val backgroundedAt = Prefs.appLockBackgroundedAt(this)
        val expired = backgroundedAt > 0L && System.currentTimeMillis() - backgroundedAt >= appLockDelayMs
        if (!unlockedThisSession || expired) {
            unlockedThisSession = false
            showLockOverlay()
            authenticateForApp()
        } else Prefs.setAppLockBackgroundedAt(this, 0L)
    }

    private fun showLockOverlay() {
        if (lockOverlay != null || !::root.isInitialized) return
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(backgroundColor)
            isClickable = true
            isFocusable = true
        }
        val logo = ImageView(this).apply { setImageResource(R.drawable.swizy_logo); scaleType = ImageView.ScaleType.FIT_CENTER }
        overlay.addView(logo, LinearLayout.LayoutParams(dp(72), dp(72)).apply { gravity = Gravity.CENTER; bottomMargin = dp(20) })
        val title = TextView(this).apply { text = translate("Swizy is locked"); textSize = 23f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        overlay.addView(title, LinearLayout.LayoutParams(-1, -2))
        val message = TextView(this).apply { text = translate("Authenticate to continue."); textSize = 14f; gravity = Gravity.CENTER; setTextColor(secondary); setPadding(0, dp(10), 0, dp(22)) }
        lockMessage = message
        overlay.addView(message, LinearLayout.LayoutParams(-1, -2))
        val retry = actionButton(translate("Unlock with fingerprint")).apply { setOnClickListener { authenticateForApp() } }
        overlay.addView(retry, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) })
        val credential = actionButton(translate("Use device PIN or password")).apply { setOnClickListener { launchDeviceCredential() } }
        overlay.addView(credential, LinearLayout.LayoutParams(-1, dp(50)))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        lockOverlay = overlay
    }

    private fun showLockMessage(message: String) { if (lockOverlay == null) showLockOverlay(); lockMessage?.text = message }
    private fun removeLockOverlay() { lockOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }; lockOverlay = null; lockMessage = null }
    private fun unlockApp() {
        unlockedThisSession = true
        lockPromptActive = false
        deviceCredentialLauncherActive = false
        Prefs.setAppLockBackgroundedAt(this, 0L)
        removeLockOverlay()
    }

    private fun isDeviceSecure(): Boolean = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    @Suppress("DEPRECATION") // Required only for the Android 10-and-older credential fallback.
    private fun launchDeviceCredential() {
        if (!Prefs.appLockEnabled(this) || unlockedThisSession || deviceCredentialLauncherActive || lockPromptActive) return
        if (!isDeviceSecure()) { showLockMessage(translate("Set a PIN, pattern, or password in Android Settings first.")); return }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Modern Android presents the system credential screen through BiometricPrompt.
            lockPromptActive = true
            deviceCredentialLauncherActive = true
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlockApp()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        lockPromptActive = false
                        deviceCredentialLauncherActive = false
                        showLockMessage(translate("Authentication required to open Swizy."))
                    }
                })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(translate("Unlock Swizy"))
                .setSubtitle(translate("Confirm it is you to continue"))
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(info)
            return
        }

        // Android 10 and older have no DEVICE_CREDENTIAL-only BiometricPrompt flow.
        @Suppress("DEPRECATION")
        val intent = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager)
            .createConfirmDeviceCredentialIntent(
                translate("Unlock Swizy"),
                translate("Confirm it is you to continue")
            )
        if (intent == null) {
            showLockMessage(translate("Device screen-lock authentication is unavailable."))
            return
        }
        deviceCredentialLauncherActive = true
        startActivityForResult(intent, lockRequestCode)
    }

    private fun authenticateForApp() {
        if (!Prefs.appLockEnabled(this) || unlockedThisSession || lockPromptActive) return
        lockPromptActive = true
        val authenticators = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL else BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            lockPromptActive = false
            showLockMessage(translate("Fingerprint is unavailable. Use your device screen lock."))
            if (isDeviceSecure()) launchDeviceCredential()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlockApp() }
            override fun onAuthenticationFailed() { showLockMessage(translate("Fingerprint not recognized. Try again.")) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                lockPromptActive = false
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) launchDeviceCredential()
                else showLockMessage(translate("Authentication required to open Swizy."))
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(translate("Unlock Swizy")).setSubtitle(translate("Confirm it is you to continue"))
        if (Build.VERSION.SDK_INT >= 30) info.setAllowedAuthenticators(authenticators)
        else info.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText(translate("Use device PIN or password"))
        prompt.authenticate(info.build())
    }

    private fun enableAppLock(enabled: Boolean): Boolean {
        if (enabled) {
            val auth = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL else BiometricManager.Authenticators.BIOMETRIC_STRONG
            val biometricReady = BiometricManager.from(this).canAuthenticate(auth) == BiometricManager.BIOMETRIC_SUCCESS
            if (!biometricReady && !isDeviceSecure()) {
                AlertDialog.Builder(this).setTitle(translate("Set up device security first")).setMessage(translate("Add a fingerprint or a PIN, pattern, or password in Android Settings before enabling the app lock."))
                    .setPositiveButton(translate("Open Settings")) { _, _ -> startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                    .setNegativeButton(translate("Cancel"), null).show()
                return false
            }
        }
        Prefs.setAppLockEnabled(this, enabled)
        if (!enabled) { unlockedThisSession = true; Prefs.setAppLockBackgroundedAt(this, 0L); removeLockOverlay() }
        return true
    }
    private fun registerSensors() {
        if (sensorManager == null) {
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        }
        displayRotation = currentDisplayRotation()
        compassInitialized = false
        compassRefreshNote?.visibility = View.VISIBLE
        lastHeadingTimestampNanos = 0L
        compassAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        measuredMagneticFieldUt = null
        magneticDisturbanceSamples = 0
        magneticClearSamples = 0
        magneticFieldDisturbed = false

        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationSensor != null) {
            sensorManager?.registerListener(
                sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_GAME
            )
            // Monitor raw magnetic-field strength as an independent interference hint.
            // This is diagnostic only; the fused rotation-vector sensor still supplies heading.
            if (magSensor != null) {
                sensorManager?.registerListener(
                    magneticMonitorListener, magSensor, SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        } else if (accelSensor != null && magSensor != null) {
            sensorManager?.registerListener(
                accelMagListener, accelSensor, SensorManager.SENSOR_DELAY_GAME
            )
            sensorManager?.registerListener(
                accelMagListener, magSensor, SensorManager.SENSOR_DELAY_GAME
            )
        }
        compassView?.setSensorState(false, compassAccuracy, magneticFieldDisturbed)
    }

    private fun currentDisplayRotation(): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(sensorListener)
        sensorManager?.unregisterListener(accelMagListener)
        sensorManager?.unregisterListener(magneticMonitorListener)
        compassInitialized = false
        lastHeadingTimestampNanos = 0L
        compassView?.setSensorState(false, compassAccuracy, magneticFieldDisturbed)
    }

    private fun updateMagneticField(values: FloatArray) {
        if (values.size < 3) return
        val x = values[0]
        val y = values[1]
        val z = values[2]
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return

        val measured = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        if (!measured.isFinite() || measured <= 0f) return
        measuredMagneticFieldUt = measured

        val expected = expectedMagneticFieldUt
        if (expected != null) {
            // Require repeated out-of-range samples to avoid warnings flickering on noise.
            // The broad tolerance detects gross anomalies, not every directional distortion.
            val suspicious = kotlin.math.abs(measured - expected) > maxOf(12f, expected * 0.25f)
            if (suspicious) {
                magneticDisturbanceSamples++
                magneticClearSamples = 0
                if (magneticDisturbanceSamples >= 3) magneticFieldDisturbed = true
            } else {
                magneticClearSamples++
                magneticDisturbanceSamples = 0
                if (magneticClearSamples >= 5) magneticFieldDisturbed = false
            }
        }

        compassView?.setSensorState(
            compassInitialized, compassAccuracy, magneticFieldDisturbed
        )
    }

    private fun updateHeadingFromMatrix(R: FloatArray, timestampNanos: Long) {
        // Display remapping makes the heading refer to the displayed top edge.
        val rotation = currentDisplayRotation().also { displayRotation = it }
        val (xAxis, yAxis) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        val remapped = FloatArray(9)
        if (!SensorManager.remapCoordinateSystem(R, xAxis, yAxis, remapped)) return

        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)

        // Android's rotation matrix supplies tilt-compensated magnetic azimuth.
        // Keep this magnetic heading paired with the magnetic Qibla target.
        val rawMagneticAzimuth = QiblaMath.normalize360(
            Math.toDegrees(orientation[0].toDouble()).toFloat()
        )
        val pitchDegrees = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val now = if (timestampNanos > 0L) timestampNanos else SystemClock.elapsedRealtimeNanos()

        if (!compassInitialized) {
            compassAzimuth = rawMagneticAzimuth
            compassTilt = kotlin.math.abs(pitchDegrees)
            compassInitialized = true
        } else {
            val elapsedSeconds = ((now - lastHeadingTimestampNanos).coerceAtLeast(1L)) / 1_000_000_000f
            val alpha = (1f - Math.exp((-elapsedSeconds / 0.24f).toDouble()).toFloat())
                .coerceIn(0.04f, 0.42f)
            val delta = QiblaMath.normalizeSigned(rawMagneticAzimuth - compassAzimuth)
            val responsiveAlpha = if (kotlin.math.abs(delta) > 70f) maxOf(alpha, 0.34f) else alpha
            compassAzimuth = QiblaMath.smoothAngle(
                compassAzimuth, rawMagneticAzimuth, responsiveAlpha
            )
            compassTilt += (kotlin.math.abs(pitchDegrees) - compassTilt) * 0.18f
        }
        if (!compassTilt.isFinite()) compassTilt = kotlin.math.abs(pitchDegrees)
        lastHeadingTimestampNanos = now

        compassRefreshNote?.visibility = View.GONE
        compassView?.setHeading(
            compassAzimuth, compassTilt, compassAccuracy, magneticFieldDisturbed
        )
    }

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            compassAccuracy = accuracy
            compassView?.setSensorState(
                compassInitialized, compassAccuracy, magneticFieldDisturbed
            )
        }
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR ||
                event.sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
                compassAccuracy = event.accuracy
                val matrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                updateHeadingFromMatrix(matrix, event.timestamp)
            }
        }
    }

    private val magneticMonitorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Keep the rotation-vector accuracy as the main heading quality indicator.
        }
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                updateMagneticField(event.values)
            }
        }
    }

    private val accelMagListener = object : SensorEventListener {
        private val gravity = FloatArray(3)
        private val geomagnetic = FloatArray(3)
        private var hasGravity = false
        private var hasGeomagnetic = false
        private var gravityTimestamp = 0L
        private var magneticTimestamp = 0L

        fun resetSamples() {
            hasGravity = false
            hasGeomagnetic = false
            gravityTimestamp = 0L
            magneticTimestamp = 0L
            gravity.fill(0f)
            geomagnetic.fill(0f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                compassAccuracy = accuracy
                compassView?.setSensorState(
                    compassInitialized, compassAccuracy, magneticFieldDisturbed
                )
            }
        }
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    if (event.values.size < 3) return
                    val a = 0.82f
                    if (!hasGravity) {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                    } else {
                        for (i in 0..2) gravity[i] = a * gravity[i] + (1f - a) * event.values[i]
                    }
                    gravityTimestamp = event.timestamp
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    if (event.values.size < 3) return
                    compassAccuracy = event.accuracy
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    hasGeomagnetic = true
                    magneticTimestamp = event.timestamp
                    updateMagneticField(event.values)
                }
            }

            if (!hasGravity || !hasGeomagnetic) return
            val now = maxOf(gravityTimestamp, magneticTimestamp)
            if (now - minOf(gravityTimestamp, magneticTimestamp) > 1_000_000_000L) return

            val matrix = FloatArray(9)
            if (SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)) {
                updateHeadingFromMatrix(matrix, now)
            }
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
                // A physical left swipe advances; a right swipe goes back.
                onSwipe(if (event.x < downX) 1 else -1)
                tracking = false
            } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                tracking = false
            }
            return true
        }
    }

    private fun showShell() {
        // Keep the bottom navigation and horizontal page swipes working together.
        root = SwipeFrameLayout(this) { direction -> navigate(direction) }.apply {
            setBackgroundColor(backgroundColor)
            layoutDirection = if (currentAppLanguage() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(10))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.swizy_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Swizy"
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(40), dp(40)))
        pageTitle = TextView(this).apply {
            text = translate("Swizy")
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(8), 0)
        }
        header.addView(pageTitle, LinearLayout.LayoutParams(0, dp(48), 1f))
        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(panelRaised, 14)
            contentDescription = translate("Settings")
            setOnClickListener { openPage(5) }
        }
        header.addView(settings, LinearLayout.LayoutParams(dp(48), dp(48)))
        shell.addView(header)

        mainScrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(20))
        }
        mainScrollView.addView(content)
        shell.addView(mainScrollView, LinearLayout.LayoutParams(-1, 0, 1f))

        navigationBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(panel)
                setStroke(dp(1), border)
                cornerRadius = dp(18).toFloat()
            }
        }
        navigationItems.clear()
        addNavigationItem("Lowery", 0)
        addNavigationItem("Sibha", 1)
        addNavigationItem("Prayers", 2)
        addNavigationItem("Quran", 4)
        val navHolder = LinearLayout(this).apply {
            setPadding(dp(12), dp(4), dp(12), dp(10))
            addView(navigationBar, LinearLayout.LayoutParams(-1, dp(58)))
        }
        shell.addView(navHolder)

        menu = LinearLayout(this).apply { visibility = View.GONE }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        openPage(currentPage, preserveScroll = true, restoreScrollY = savedScrollY)
    }

    private fun addNavigationItem(label: String, page: Int) {
        val item = TextView(this).apply {
            text = translate(label)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(44)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { openPage(page) }
            contentDescription = translate(label)
        }
        navigationBar.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        navigationItems.add(page to item)
    }

    private fun refreshNavigation() {
        if (!::navigationBar.isInitialized) return
        navigationItems.forEach { (page, item) ->
            val selected = currentPage == page || (currentPage == 3 && page == 2)
            item.setTextColor(if (selected) Color.WHITE else secondary)
            item.background = if (selected) rounded(purple, 12) else rounded(Color.TRANSPARENT, 12)
        }
        if (::pageTitle.isInitialized) pageTitle.text = translate(pageTitle.text.toString())
    }

    private fun openPage(
        page: Int,
        preserveScroll: Boolean = false,
        restoreScrollY: Int? = null
    ) {
        val targetPage = page.mod(6)
        val targetScrollY = when {
            restoreScrollY != null -> restoreScrollY.coerceAtLeast(0)
            preserveScroll && ::mainScrollView.isInitialized -> mainScrollView.scrollY
            targetPage == currentPage && ::mainScrollView.isInitialized -> mainScrollView.scrollY
            else -> 0
        }
        if (currentPage == 3) compassView = null
        if (targetPage == 3) {
            compassInitialized = false
            displayRotation = if (Build.VERSION.SDK_INT >= 30) {
                display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        }
        currentPage = targetPage
        if (currentPage == 3) {
            // Re-register on every Qibla entry so Android restarts the sensor
            // stream and we wait for fresh readings instead of reusing stale state.
            unregisterSensors()
            accelMagListener.resetSamples()
            registerSensors()
        }
        menu.visibility = View.GONE
        when (currentPage) {
            0 -> showLowery()
            1 -> showSibha()
            2 -> showPrayers()
            3 -> showQibla()
            4 -> showQuranAudio()
            else -> showSettings()
        }
        localizeTree(content)
        refreshNavigation()
        savedScrollY = targetScrollY
        if (::mainScrollView.isInitialized) {
            mainScrollView.post { mainScrollView.scrollTo(0, targetScrollY) }
        }
    }

    private fun navigate(step: Int) {
        openPage((currentPage + step).mod(6))
    }

    private fun toggleMenu() {
        if (menu.visibility == View.VISIBLE) {
            val out = TranslateAnimation(0f, -menu.width.toFloat(), 0f, 0f).apply {
                duration = 220
                interpolator = android.view.animation.DecelerateInterpolator()
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
            menu.animate().translationX(0f).setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }

    private fun showLowery() {
        pageTitle.text = translate("Lowery")
        content.removeAllViews()

        // A compact mascot-led introduction keeps the page recognizable without crowding it.
        content.addView(card(translate("Lowery") + " ✨") { section ->
            val intro = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(10))
            }
            val mascot = ImageView(this).apply {
                setImageResource(R.drawable.swizy_logo)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Lowery mascot"
                background = rounded(panelRaised, 18)
                setPadding(dp(5), dp(5), dp(5), dp(5))
            }
            intro.addView(mascot, LinearLayout.LayoutParams(dp(68), dp(68)))
            val description = TextView(this).apply {
                text = if (currentAppLanguage() == "ar") {
                    "يحمي صور الحسابات مع إبقاء المحادثات والأزرار ولوحة المفاتيح قابلة للاستخدام."
                } else {
                    "Protect profile pictures while keeping chats, buttons, and the keyboard usable."
                }
                textSize = 14f
                setTextColor(secondary)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, 0, 0)
            }
            intro.addView(description, LinearLayout.LayoutParams(0, -2, 1f))
            section.addView(intro)

            val enabled = Switch(this).apply {
                text = if (currentAppLanguage() == "ar") "تفعيل لويري" else "Enable Lowery"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.isEnabled(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.set(this@MainActivity, checked)
                    AvatarService.instance?.refresh()
                }
            }
            section.addView(enabled)
            val access = actionButton("Open Accessibility settings")
            access.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            section.addView(access)
        })

        content.addView(card("Hide an area manually") {
            addText(it, "Choose any part of WhatsApp or another supported app and keep it completely black. The small side control can hide or restore it, choose a new area, or remove it.")
            val manual = actionButton("Summon the paw")
            manual.setOnClickListener {
                val service = AvatarService.instance
                if (service == null || !Prefs.isEnabled(this)) {
                    Toast.makeText(this, translate("Enable the swizy accessibility service first"), Toast.LENGTH_LONG).show()
                } else {
                    service.summonManualHideControl()
                    Toast.makeText(this, translate("Use the ball menu, then choose Add"), Toast.LENGTH_LONG).show()
                }
            }
            it.addView(manual)
        })
    }

    private fun showSibha() {
        pageTitle.text = "Sibha"
        content.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(28), dp(20), dp(28))
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(Color.rgb(61, 43, 103), Color.rgb(31, 24, 53))
                cornerRadius = dp(18).toFloat()
            }
        }
        val label = TextView(this).apply {
            text = "SIBHA"
            textSize = 14f
            letterSpacing = .18f
            setTextColor(Color.rgb(220, 204, 255))
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

    private fun showQuranAudio() {
        pageTitle.text = "Quran"
        content.removeAllViews()

        content.addView(card("Main Quran") { section ->
            addText(section, "The main Quran and side surah keep separate saved positions.")
            mainTimelineSummary = TextView(this).apply {
                textSize = 14f
                setTextColor(secondary)
                setPadding(0, dp(4), 0, dp(8))
            }
            section.addView(mainTimelineSummary)
            section.addView(actionButton("Continue main Quran").apply {
                setOnClickListener { playMainQuran() }
            })
        })

        content.addView(card("Side listening") { section ->
            addText(section, "Choose a surah for side listening.")
            val surahs = (1..114).map {
                QuranSurahs.name(it, currentAppLanguage())
            }.toTypedArray()
            val selector = styledSpinner().apply {
                adapter = optionAdapter(surahs)
                setSelection(QuranAudioRepository.selectedSideSurah(this@MainActivity) - 1)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(
                        parent: AdapterView<*>?, view: View?, position: Int, id: Long
                    ) {
                        QuranAudioRepository.setSelectedSideSurah(this@MainActivity, position + 1)
                    }
                }
            }
            quranSurahSpinner = selector
            section.addView(selector, LinearLayout.LayoutParams(-1, dp(52)))
            sideTimelineSummary = TextView(this).apply {
                textSize = 14f
                setTextColor(secondary)
                setPadding(0, dp(8), 0, dp(8))
            }
            section.addView(sideTimelineSummary)
            section.addView(actionButton("Play selected side surah").apply {
                setOnClickListener {
                    val surah = quranSurahSpinner?.selectedItemPosition?.plus(1)
                        ?: QuranAudioRepository.selectedSideSurah(this@MainActivity)
                    playSideSurah(surah)
                }
            })
            section.addView(actionButton("Random side surah").apply {
                setOnClickListener {
                    val randomSurah = kotlin.random.Random.nextInt(1, 115)
                    quranSurahSpinner?.setSelection(randomSurah - 1)
                    playSideSurah(randomSurah)
                }
            })
        })

        content.addView(card("Your books") { section ->
            addText(section, "Add a folder, audio file, archive, or direct download link.")
            section.addView(actionButton("Add new book").apply {
                setOnClickListener { showNewAudioBookSourceDialog() }
            })
            audioImportStatusText = TextView(this).apply {
                textSize = 13f
                setTextColor(secondary)
                setPadding(0, dp(6), 0, dp(6))
            }
            section.addView(audioImportStatusText)
            audioBookListContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            section.addView(audioBookListContainer)
        })

        content.addView(card("Player controls") { section ->
            addText(section, "Playback continues in the background.")
            addText(section, "Reciter")
            quranReciterSpinner = styledSpinner().apply {
                adapter = optionAdapter(arrayOf("Loading reciters…"))
            }
            section.addView(quranReciterSpinner, LinearLayout.LayoutParams(-1, dp(52)))

            quranNowPlayingText = TextView(this).apply {
                text = translate("No audio playing")
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(0, dp(12), 0, dp(4))
            }
            section.addView(quranNowPlayingText)

            quranStatusText = TextView(this).apply {
                text = translate("Loading reciters…")
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(secondary)
                setPadding(0, 0, 0, dp(8))
            }
            section.addView(quranStatusText)

            quranSeekBar = SeekBar(this).apply {
                max = 1000
                progress = 0
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        quranSeeking = true
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        val controller = quranController
                        val duration = controller?.duration ?: 0L
                        if (controller != null && duration > 0L) {
                            controller.seekTo(duration * (seekBar?.progress ?: 0) / 1000L)
                        }
                        quranSeeking = false
                        updateQuranPlaybackUi()
                    }

                    override fun onProgressChanged(
                        seekBar: SeekBar?, progress: Int, fromUser: Boolean
                    ) {
                        if (fromUser && quranSeeking) {
                            val duration = quranController?.duration ?: 0L
                            if (duration > 0L) {
                                quranPositionText?.text =
                                    formatQuranDuration(duration * progress / 1000L)
                            }
                        }
                    }
                })
            }
            section.addView(quranSeekBar)

            val timeRow = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            quranPositionText = TextView(this).apply {
                text = "00:00"
                textSize = 12f
                setTextColor(secondary)
            }
            quranDurationText = TextView(this).apply {
                text = "--:--"
                textSize = 12f
                setTextColor(secondary)
                gravity = Gravity.END
            }
            timeRow.addView(quranPositionText, LinearLayout.LayoutParams(0, -2, 1f))
            timeRow.addView(quranDurationText, LinearLayout.LayoutParams(0, -2, 1f))
            section.addView(timeRow)

            val controls = LinearLayout(this).apply {
                gravity = Gravity.CENTER
            }
            val previous = Button(this).apply {
                text = "◀|"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = rounded(panelRaised, 12)
                setOnClickListener { quranController?.seekToPreviousMediaItem() }
            }
            quranPlayPauseButton = Button(this).apply {
                text = translate("Play")
                isAllCaps = false
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(purple, 12)
                setOnClickListener { toggleQuranPlayback() }
            }
            val next = Button(this).apply {
                text = "|▶"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = rounded(panelRaised, 12)
                setOnClickListener { quranController?.seekToNextMediaItem() }
            }
            controls.addView(previous, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(6)
            })
            controls.addView(quranPlayPauseButton, LinearLayout.LayoutParams(0, dp(48), 1.4f).apply {
                marginEnd = dp(6)
            })
            controls.addView(next, LinearLayout.LayoutParams(0, dp(48), 1f))
            section.addView(controls)
        })

        connectQuranController()
        loadQuranRecitations()
        updateQuranTimelineSummaries()
        updateQuranPlaybackUi()
        quranProgressHandler.removeCallbacks(quranProgressTicker)
        quranProgressHandler.post(quranProgressTicker)
        renderAudioBookLibrary()
        observeAudioBookImports()
    }

    private fun connectQuranController() {
        if (quranController != null || quranControllerFuture != null) {
            updateQuranPlaybackUi()
            return
        }
        val token = SessionToken(this, ComponentName(this, QuranPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        quranControllerFuture = future
        future.addListener({
            if (isDestroyed) {
                MediaController.releaseFuture(future)
                return@addListener
            }
            runCatching { future.get() }.onSuccess { controller ->
                quranController = controller
                controller.addListener(quranPlayerListener)
                updateQuranPlaybackUi()
            }.onFailure {
                quranControllerFuture = null
                quranStatusText?.text = translate("Could not connect to the audio player.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadQuranRecitations() {
        if (quranRecitations.isNotEmpty()) {
            applyQuranRecitations(quranRecitations)
            return
        }
        val cached = QuranAudioRepository.cachedRecitations(this)
        if (cached.isNotEmpty()) applyQuranRecitations(cached)
        if (loadingQuranRecitations) return
        loadingQuranRecitations = true
        val language = if (currentAppLanguage() == "ar") "ar" else "eng"
        thread(name = "quran-reciter-catalog") {
            val fresh = runCatching {
                QuranAudioRepository.fetchRecitations(language)
            }.getOrDefault(emptyList())
            runOnUiThread {
                loadingQuranRecitations = false
                if (fresh.isNotEmpty()) {
                    QuranAudioRepository.cacheRecitations(this, fresh)
                    applyQuranRecitations(fresh)
                } else if (quranRecitations.isEmpty()) {
                    quranStatusText?.text = translate("Could not load reciters. Check your connection.")
                }
            }
        }
    }

    private fun applyQuranRecitations(recitations: List<QuranRecitation>) {
        if (recitations.isEmpty()) {
            if (quranRecitations.isEmpty()) {
                quranStatusText?.text = translate("No complete recitations were found.")
            }
            return
        }
        quranRecitations = recitations
        val saved = QuranAudioRepository.selectedRecitation(this)
        selectedQuranRecitation = recitations.firstOrNull {
            saved != null && it.moshafId == saved.moshafId
        } ?: recitations.first()
        selectedQuranRecitation?.let {
            QuranAudioRepository.saveSelectedRecitation(this, it)
        }
        quranReciterSpinner?.let { spinner ->
            spinner.adapter = optionAdapter(recitations.map {
                "${it.reciterName} · ${it.moshafName}"
            }.toTypedArray())
            val selectedIndex = recitations.indexOfFirst {
                it.moshafId == selectedQuranRecitation?.moshafId
            }.coerceAtLeast(0)
            spinner.setSelection(selectedIndex, false)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    recitations.getOrNull(position)?.let { recitation ->
                        selectedQuranRecitation = recitation
                        QuranAudioRepository.saveSelectedRecitation(this@MainActivity, recitation)
                        quranStatusText?.text =
                            translate("This reciter selection applies the next time you load a timeline.")
                    }
                }
            }
        }
        quranStatusText?.text = translate("Ready")
    }

    private fun playMainQuran() {
        saveCurrentQuranPosition()
        startQuranTimeline(QuranAudioRepository.loadTimeline(this, QuranAudioRepository.MAIN_TIMELINE))
    }

    private fun playSideSurah(surah: Int) {
        saveCurrentQuranPosition()
        val state = QuranAudioRepository.selectSideSurah(this, surah)
        startQuranTimeline(state)
    }

    private fun resumeSideSurah() {
        saveCurrentQuranPosition()
        val existing = QuranAudioRepository.loadTimeline(this, QuranAudioRepository.SIDE_TIMELINE)
        val state = if (existing.chapters.isEmpty()) {
            QuranAudioRepository.selectSideSurah(
                this, QuranAudioRepository.selectedSideSurah(this)
            )
        } else existing
        startQuranTimeline(state)
    }

    private fun startQuranTimeline(state: QuranTimelineState) {
        val controller = quranController
        if (controller == null) {
            connectQuranController()
            Toast.makeText(this, translate("The audio player is still starting. Try again."), Toast.LENGTH_SHORT).show()
            return
        }
        val recitation = selectedQuranRecitation
            ?: QuranAudioRepository.selectedRecitation(this)
        if (recitation == null || !recitation.supportsFullQuran()) {
            Toast.makeText(this, translate("Choose a reciter to begin streaming."), Toast.LENGTH_LONG).show()
            loadQuranRecitations()
            return
        }
        if (state.chapters.isEmpty()) return
        val timelineTitle = if (state.id == QuranAudioRepository.MAIN_TIMELINE) {
            translate("Main timeline")
        } else translate("Side timeline")
        val mediaItems = state.chapters.mapIndexed { index, surah ->
            MediaItem.Builder()
                .setMediaId("${state.id}|$index")
                .setUri(recitation.audioUrl(surah))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(QuranSurahs.name(surah, currentAppLanguage()))
                        .setArtist(recitation.reciterName)
                        .setAlbumTitle(timelineTitle)
                        .build()
                )
                .build()
        }
        QuranAudioRepository.setActiveTimeline(this, state.id)
        controller.setMediaItems(
            mediaItems,
            state.currentChapterIndex.coerceIn(0, mediaItems.lastIndex),
            state.positionMs.coerceAtLeast(0L)
        )
        controller.prepare()
        controller.play()
        updateQuranPlaybackUi()
    }

    private fun saveCurrentQuranPosition() {
        val controller = quranController ?: return
        val mediaId = controller.currentMediaItem?.mediaId ?: return
        val parts = mediaId.split('|')
        if (parts.size != 2) return
        val timelineId = parts[0]
        val index = parts[1].toIntOrNull() ?: return
        when {
            timelineId == QuranAudioRepository.MAIN_TIMELINE ||
                timelineId == QuranAudioRepository.SIDE_TIMELINE -> {
                QuranAudioRepository.saveProgress(this, timelineId, index, controller.currentPosition)
            }
            timelineId.startsWith("book:") -> {
                AudioBookRepository.saveProgress(
                    this, timelineId.removePrefix("book:"), index, controller.currentPosition
                )
            }
        }
    }

    private fun toggleQuranPlayback() {
        val controller = quranController
        if (controller == null) {
            connectQuranController()
            return
        }
        when {
            controller.isPlaying -> controller.pause()
            controller.mediaItemCount > 0 -> controller.play()
            QuranAudioRepository.activeTimeline(this) == QuranAudioRepository.SIDE_TIMELINE ->
                resumeSideSurah()
            QuranAudioRepository.activeTimeline(this).startsWith("book:") ->
                resumeAudioBook(QuranAudioRepository.activeTimeline(this).removePrefix("book:"))
            else -> playMainQuran()
        }
        updateQuranPlaybackUi()
    }

    private fun updateQuranTimelineSummaries() {
        val main = QuranAudioRepository.loadTimeline(this, QuranAudioRepository.MAIN_TIMELINE)
        val side = QuranAudioRepository.loadTimeline(this, QuranAudioRepository.SIDE_TIMELINE)
        mainTimelineSummary?.text = main.currentSurah?.let {
            "${QuranSurahs.name(it, currentAppLanguage())} · ${formatQuranDuration(main.positionMs)}"
        } ?: translate("No audio playing")
        sideTimelineSummary?.text = side.currentSurah?.let {
            "${QuranSurahs.name(it, currentAppLanguage())} · ${formatQuranDuration(side.positionMs)}"
        } ?: translate("No audio playing")
    }

    private fun updateQuranPlaybackUi() {
        val controller = quranController
        val item = controller?.currentMediaItem
        quranNowPlayingText?.text =
            item?.mediaMetadata?.title?.toString() ?: translate("No audio playing")
        quranPlayPauseButton?.text =
            translate(if (controller?.isPlaying == true) "Pause" else "Play")

        val duration = controller?.duration?.takeIf { it > 0L } ?: 0L
        val position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
        if (!quranSeeking) {
            quranSeekBar?.let { seek ->
                seek.progress = if (duration > 0L) {
                    (position * 1000L / duration).toInt().coerceIn(0, 1000)
                } else 0
                quranPositionText?.text = formatQuranDuration(position)
            }
        }
        quranDurationText?.text = if (duration > 0L) formatQuranDuration(duration) else "--:--"

        val timelineId = item?.mediaId?.substringBefore('|')
        val timelineLabel = when {
            timelineId == QuranAudioRepository.MAIN_TIMELINE -> translate("Main timeline")
            timelineId == QuranAudioRepository.SIDE_TIMELINE -> translate("Side timeline")
            timelineId?.startsWith("book:") == true -> translate("Audio book")
            else -> ""
        }
        quranNowPlayingText?.tag = if (item != null) USER_CONTENT_TAG else null
        val playbackState = when {
            controller == null -> translate("Loading reciters…")
            controller.isPlaying -> translate("Playing")
            item != null -> translate("Paused")
            else -> translate("Choose a reciter to begin streaming.")
        }
        quranStatusText?.text = listOf(timelineLabel, playbackState)
            .filter { it.isNotBlank() }.joinToString(" · ")
        updateQuranTimelineSummaries()
    }

    private fun formatQuranDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun showNewAudioBookSourceDialog() {
        val choices = arrayOf(
            translate("Choose a folder"),
            translate("Choose a file or archive"),
            translate("Online link")
        )
        AlertDialog.Builder(this)
            .setTitle(translate("Choose book source"))
            .setItems(choices) { _, index ->
                when (index) {
                    0 -> openAudioBookFolderPicker()
                    1 -> openAudioBookFilePicker()
                    2 -> showOnlineAudioLinkDialog()
                }
            }
            .setNegativeButton(translate("Cancel"), null)
            .show()
    }

    private fun openAudioBookFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, AUDIO_BOOK_FOLDER_REQUEST)
    }

    private fun openAudioBookFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "audio/*",
                    "application/zip",
                    "application/gzip",
                    "application/x-tar",
                    "application/octet-stream"
                )
            )
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, AUDIO_BOOK_FILE_REQUEST)
    }

    private fun showOnlineAudioLinkDialog() {
        AlertDialog.Builder(this)
            .setTitle(translate("Legal use reminder"))
            .setMessage(
                translate(
                    "Make sure downloading from this link is legal, that you have permission to download it, and that it does not violate copyright or the website's Terms of Service or other rules."
                )
            )
            .setPositiveButton(translate("I understand and continue")) { _, _ ->
                showOnlineAudioLinkInputDialog()
            }
            .setNegativeButton(translate("Cancel"), null)
            .show()
    }

    private fun showOnlineAudioLinkInputDialog() {
        val linkInput = EditText(this).apply {
            hint = translate("Paste a direct audio or archive link")
            setTextColor(Color.WHITE)
            setHintTextColor(secondary)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = outlined(panelRaised, border, 12)
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(linkInput, LinearLayout.LayoutParams(-1, dp(54)))
        }
        AlertDialog.Builder(this)
            .setTitle(translate("Online link"))
            .setMessage(translate("Use a direct HTTP or HTTPS link to an audio file or archive."))
            .setView(wrapper)
            .setPositiveButton(translate("Continue")) { _, _ ->
                val link = linkInput.text.toString().trim()
                if (link.isBlank()) {
                    Toast.makeText(this, translate("Enter a direct download link."), Toast.LENGTH_SHORT).show()
                } else {
                    askAudioBookName(AudioBookImportWorker.TYPE_URL, link, suggestedNameFromUrl(link))
                }
            }
            .setNegativeButton(translate("Cancel"), null)
            .show()
    }

    private fun suggestedNameFromUrl(value: String): String {
        val segment = Uri.parse(value).lastPathSegment.orEmpty()
        return segment.substringBeforeLast('.', segment)
            .replace(Regex("[-_]+"), " ")
            .trim()
    }

    private fun displayNameForUri(uri: Uri): String {
        contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (column >= 0) return cursor.getString(column).orEmpty()
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
    }

    private fun askAudioBookName(sourceType: String, sourceValue: String, suggestedName: String) {
        val nameInput = EditText(this).apply {
            hint = translate("Book name")
            setText(suggestedName)
            setTextColor(Color.WHITE)
            setHintTextColor(secondary)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = outlined(panelRaised, border, 12)
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(nameInput, LinearLayout.LayoutParams(-1, dp(54)))
        }
        AlertDialog.Builder(this)
            .setTitle(translate("Name this book"))
            .setView(wrapper)
            .setPositiveButton(translate("Continue")) { _, _ ->
                val bookName = nameInput.text.toString().trim()
                if (bookName.isBlank()) {
                    Toast.makeText(this, translate("Enter a book name."), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                enqueueAudioBookImport(sourceType, sourceValue, bookName)
            }
            .setNegativeButton(translate("Cancel"), null)
            .show()
    }

    private fun enqueueAudioBookImport(sourceType: String, sourceValue: String, bookName: String) {
        val bookId = AudioBookRepository.newId()
        val data = Data.Builder()
            .putString(AudioBookImportWorker.KEY_SOURCE_TYPE, sourceType)
            .putString(AudioBookImportWorker.KEY_BOOK_NAME, bookName)
            .putString(AudioBookImportWorker.KEY_BOOK_ID, bookId)
            .apply {
                if (sourceType == AudioBookImportWorker.TYPE_URL) {
                    putString(AudioBookImportWorker.KEY_URL, sourceValue)
                } else {
                    putString(AudioBookImportWorker.KEY_SOURCE_URI, sourceValue)
                }
            }
            .build()
        val request = OneTimeWorkRequestBuilder<AudioBookImportWorker>()
            .setInputData(data)
            .addTag(AUDIO_BOOK_IMPORT_TAG)
            .build()
        runCatching {
            WorkManager.getInstance(applicationContext).enqueue(request)
        }.onSuccess {
            audioImportStatusText?.text = translate("Import queued…")
            observeAudioBookImports()
        }.onFailure {
            audioImportStatusText?.text = translate("Could not start the import.")
        }
    }

    private fun observeAudioBookImports() {
        if (audioImportObservationAttached) return
        audioImportObservationAttached = true
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(AUDIO_BOOK_IMPORT_TAG)
            .observe(this) { infos ->
                val active = infos.firstOrNull { !it.state.isFinished }
                if (active != null) {
                    val percent = active.progress.getInt(AudioBookImportWorker.KEY_PROGRESS, 0)
                    val stage = when (active.progress.getString(AudioBookImportWorker.KEY_STAGE)) {
                        "download" -> translate("Downloading…")
                        "copy" -> translate("Copying files…")
                        "extract" -> translate("Extracting audio…")
                        "metadata" -> translate("Reading track information…")
                        else -> translate("Preparing book…")
                    }
                    audioImportStatusText?.text = "$stage $percent%"
                    return@observe
                }

                val finished = infos.firstOrNull {
                    it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
                } ?: return@observe
                if (finished.state == WorkInfo.State.SUCCEEDED) {
                    audioImportStatusText?.text = translate("Book imported successfully.")
                    renderAudioBookLibrary()
                } else {
                    val error = finished.outputData.getString(AudioBookImportWorker.KEY_ERROR).orEmpty()
                    audioImportStatusText?.text = translateImportError(error)
                }
            }
    }

    private fun translateImportError(error: String): String {
        if (error.startsWith("The server returned HTTP ")) {
            return translate("Download server error: ") + error.substringAfter("HTTP ")
        }
        return translate(error).ifBlank { translate("The audio book could not be imported.") }
    }

    private fun renderAudioBookLibrary() {
        val container = audioBookListContainer ?: return
        container.removeAllViews()
        val books = AudioBookRepository.all(this)
        if (books.isEmpty()) {
            val empty = TextView(this).apply {
                text = translate("No books yet. Add a folder, archive, audio file, or direct link.")
                textSize = 13f
                setTextColor(secondary)
                setPadding(0, dp(8), 0, dp(8))
            }
            container.addView(empty)
            return
        }
        books.forEach { book ->
            val bookCard = card("Audio book") { section ->
                val title = TextView(this).apply {
                    text = book.name
                    tag = USER_CONTENT_TAG
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(3), 0, dp(5))
                }
                section.addView(title)
                val currentTrack = book.currentTrack
                val details = TextView(this).apply {
                    text = book.tracks.size.toString() + " · " +
                        (currentTrack?.title.orEmpty()) + " · " +
                        formatQuranDuration(book.positionMs)
                    tag = USER_CONTENT_TAG
                    textSize = 13f
                    setTextColor(secondary)
                    setPadding(0, 0, 0, dp(6))
                }
                section.addView(details)
                val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val resume = actionButton("Resume book").apply {
                    setOnClickListener { resumeAudioBook(book.id) }
                    layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                        marginEnd = dp(6)
                    }
                }
                val options = actionButton("Manage").apply {
                    setOnClickListener { showAudioBookOptions(book.id) }
                    layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
                }
                controls.addView(resume)
                controls.addView(options)
                section.addView(controls)
            }
            container.addView(bookCard)
        }
    }

    private fun showAudioBookOptions(bookId: String) {
        val book = AudioBookRepository.load(this, bookId) ?: return
        val options = arrayOf(
            translate("Resume book"),
            translate("Rename book"),
            translate("Add bookmark"),
            translate("Bookmarks"),
            translate("Delete book")
        )
        AlertDialog.Builder(this)
            .setTitle(book.name)
            .setItems(options) { _, index ->
                when (index) {
                    0 -> resumeAudioBook(bookId)
                    1 -> renameAudioBook(bookId)
                    2 -> addAudioBookBookmark(bookId)
                    3 -> showAudioBookBookmarks(bookId)
                    4 -> confirmDeleteAudioBook(bookId)
                }
            }
            .setNegativeButton(translate("Cancel"), null)
            .show()
    }

    private fun renameAudioBook(bookId: String) {
        val book = AudioBookRepository.load(this, bookId) ?: return
        val input = EditText(this).apply {
            setText(book.name)
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = outlined(panelRaised, border, 12)
        }
        AlertDialog.Builder(this)
            .setTitle(translate("Rename book"))
            .setView(input)
            .setPositiveButton(translate("Save")) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    AudioBookRepository.rename(this, bookId, name)
                    renderAudioBookLibrary()
                }
            }
            .setNegativeButton(translate("Cancel"), null)
            .show()
    }

    private fun addAudioBookBookmark(bookId: String) {
        val book = AudioBookRepository.load(this, bookId) ?: return
        val controller = quranController
        val mediaId = controller?.currentMediaItem?.mediaId.orEmpty()
        val parts = mediaId.split('|')
        val isCurrentBook = parts.size == 2 && parts[0] == "book:" + bookId
        val trackIndex = if (isCurrentBook) parts[1].toIntOrNull() ?: book.currentTrackIndex
            else book.currentTrackIndex
        val position = if (isCurrentBook) controller?.currentPosition ?: book.positionMs
            else book.positionMs
        val defaultName = translate("Bookmark") + " " + (book.bookmarks.size + 1)
        val input = EditText(this).apply {
            setText(defaultName)
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = outlined(panelRaised, border, 12)
        }
        AlertDialog.Builder(this)
            .setTitle(translate("Bookmark name"))
            .setView(input)
            .setPositiveButton(translate("Save")) { _, _ ->
                AudioBookRepository.addBookmark(
                    this, bookId, input.text.toString().ifBlank { defaultName }, trackIndex, position
                )
                audioImportStatusText?.text = translate("Bookmark saved.")
                renderAudioBookLibrary()
            }
            .setNegativeButton(translate("Cancel"), null)
            .show()
    }

    private fun showAudioBookBookmarks(bookId: String) {
        val book = AudioBookRepository.load(this, bookId) ?: return
        if (book.bookmarks.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(translate("Bookmarks"))
                .setMessage(translate("No bookmarks yet."))
                .setPositiveButton(translate("Close"), null)
                .show()
            return
        }
        val labels = book.bookmarks.map { bookmark ->
            val track = book.tracks.getOrNull(bookmark.trackIndex)?.title.orEmpty()
            bookmark.name + " · " + track + " · " + formatQuranDuration(bookmark.positionMs)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(translate("Bookmarks"))
            .setItems(labels) { _, index ->
                book.bookmarks.getOrNull(index)?.let { showAudioBookBookmarkOptions(bookId, it) }
            }
            .setNegativeButton(translate("Close"), null)
            .show()
    }

    private fun showAudioBookBookmarkOptions(bookId: String, bookmark: AudioBookBookmark) {
        val choices = arrayOf(
            translate("Resume bookmark"),
            translate("Rename bookmark"),
            translate("Delete bookmark")
        )
        AlertDialog.Builder(this)
            .setTitle(bookmark.name)
            .setItems(choices) { _, index ->
                when (index) {
                    0 -> resumeAudioBook(bookId, bookmark.trackIndex, bookmark.positionMs)
                    1 -> {
                        val input = EditText(this).apply {
                            setText(bookmark.name)
                            setTextColor(Color.WHITE)
                        }
                        AlertDialog.Builder(this)
                            .setTitle(translate("Rename bookmark"))
                            .setView(input)
                            .setPositiveButton(translate("Save")) { _, _ ->
                                AudioBookRepository.renameBookmark(
                                    this, bookId, bookmark.id, input.text.toString()
                                )
                                renderAudioBookLibrary()
                            }
                            .setNegativeButton(translate("Cancel"), null)
                            .show()
                    }
                    2 -> {
                        AudioBookRepository.removeBookmark(this, bookId, bookmark.id)
                        renderAudioBookLibrary()
                    }
                }
            }
            .setNegativeButton(translate("Cancel"), null)
            .show()
    }

    private fun confirmDeleteAudioBook(bookId: String) {
        val book = AudioBookRepository.load(this, bookId) ?: return
        AlertDialog.Builder(this)
            .setTitle(translate("Delete book"))
            .setMessage(translate("Delete this book and its copied audio files?"))
            .setPositiveButton(translate("Delete")) { _, _ ->
                if (quranController?.currentMediaItem?.mediaId?.startsWith("book:" + bookId + "|") == true) {
                    quranController?.stop()
                    QuranAudioRepository.setActiveTimeline(this, QuranAudioRepository.MAIN_TIMELINE)
                }
                AudioBookRepository.delete(this, bookId)
                renderAudioBookLibrary()
            }
            .setNegativeButton(translate("Cancel"), null)
            .show()
    }

    private fun resumeAudioBook(
        bookId: String,
        targetTrackIndex: Int? = null,
        targetPositionMs: Long? = null
    ) {
        val book = AudioBookRepository.load(this, bookId)
        if (book == null) {
            Toast.makeText(this, translate("This book is no longer available."), Toast.LENGTH_SHORT).show()
            renderAudioBookLibrary()
            return
        }
        val controller = quranController
        if (controller == null) {
            connectQuranController()
            Toast.makeText(this, translate("The audio player is still starting. Try again."), Toast.LENGTH_SHORT).show()
            return
        }
        saveCurrentQuranPosition()
        val items = book.tracks.mapIndexed { index, track ->
            MediaItem.Builder()
                .setMediaId("book:" + bookId + "|" + index)
                .setUri(Uri.fromFile(File(track.path)))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist.ifBlank { book.name })
                        .setAlbumTitle(track.album.ifBlank { book.name })
                        .build()
                )
                .build()
        }
        val startIndex = (targetTrackIndex ?: book.currentTrackIndex).coerceIn(0, items.lastIndex)
        val startPosition = targetPositionMs ?: book.positionMs
        AudioBookRepository.setActive(this, bookId)
        QuranAudioRepository.setActiveTimeline(this, "book:" + bookId)
        controller.setMediaItems(items, startIndex, startPosition.coerceAtLeast(0L))
        controller.prepare()
        controller.play()
        updateQuranPlaybackUi()
        renderAudioBookLibrary()
    }

    private fun showQibla() {
        pageTitle.text = "Qibla"
        content.removeAllViews()
        if (sensorManager == null) {
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }
        val lat = Prefs.latitude(this).toDoubleOrNull()
        val lon = Prefs.longitude(this).toDoubleOrNull()
        content.addView(card("Qibla finder") {
            addText(it, "Point your phone to find the direction of the Kaaba in Mecca.")
            val refreshNote = TextView(this@MainActivity).apply {
                text = translate("Refreshing compass for a moment…")
                textSize = 13f
                setTextColor(purple)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(8))
            }
            compassRefreshNote = refreshNote
            refreshNote.visibility = if (compassInitialized) View.GONE else View.VISIBLE
            it.addView(refreshNote)
            refreshNote.postDelayed({
                if (compassRefreshNote === refreshNote) refreshNote.visibility = View.GONE
            }, 1800L)
            if (lat == null || lon == null) {
                addText(it, "Set your location in the Prayers page first, or enable GPS there, then come back.")
                val help = actionButton("Go to Prayers")
                help.setOnClickListener { openPage(2) }
                it.addView(help)
                return@card
            }
            if (!lat.isFinite() || lat !in -90.0..90.0 ||
                !lon.isFinite() || lon !in -180.0..180.0
            ) {
                addText(it, "The saved location is invalid. Update it from the Prayers page.")
                val help = actionButton("Go to Prayers")
                help.setOnClickListener { openPage(2) }
                it.addView(help)
                return@card
            }

            val geo = GeomagneticField(
                lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()
            )
            expectedMagneticFieldUt = geo.fieldStrength / 1000f
            measuredMagneticFieldUt = null
            magneticDisturbanceSamples = 0
            magneticClearSamples = 0
            magneticFieldDisturbed = false
            val trueBearing = QiblaMath.initialBearing(lat, lon)
            // Android's orientation azimuth is magnetic. Compare it with a
            // magnetic Qibla target, not a true-north target.
            val magneticBearing = QiblaMath.magneticBearingFromTrue(
                trueBearing, geo.declination.toDouble()
            ).toFloat()

            val compass = CompassView(this).apply {
                setQiblaBearing(magneticBearing)
                setHeading(
                    compassAzimuth, compassTilt, compassAccuracy, magneticFieldDisturbed,
                    compassInitialized
                )
            }
            compassView = compass
            val info = TextView(this).apply {
                text = "Qibla from true north: ${String.format(Locale.US, "%.1f", trueBearing)}°"
                textSize = 15f
                setTextColor(purple)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(4))
            }
            it.addView(info)
            val headingText = TextView(this).apply {
                text = "Finding compass direction…"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(secondary)
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), dp(10))
                minHeight = dp(40)
            }
            it.addView(headingText)
            it.addView(compass, LinearLayout.LayoutParams(-1, dp(300)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
            val diagnosticsText = TextView(this).apply {
                textSize = 11f
                setTextColor(secondary)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(2), dp(4), dp(6))
            }
            it.addView(diagnosticsText)
            fun updateCompassDiagnostics() {
                val heading = if (compassInitialized) {
                    String.format(Locale.US, "%.1f°", compassAzimuth)
                } else "--"
                val field = measuredMagneticFieldUt?.let {
                    String.format(Locale.US, "%.1f", it)
                } ?: "--"
                val quality = when (compassAccuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Good"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
                    else -> "Unreliable"
                }
                diagnosticsText.text = translate(
                    "Heading: $heading magnetic · Tilt: ${compassTilt.toInt()}° · Field: $field µT · $quality"
                )
            }
            updateCompassDiagnostics()
            val coords = TextView(this).apply {
                text = "Your location: ${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lon)}"
                textSize = 13f
                setTextColor(secondary)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(4))
            }
            it.addView(coords)
            addText(
                it,
                "Hold the phone at your comfortable upward angle (around 45° is fine). Keep the screen facing you and use the top edge as the forward direction. The compass compensates for tilt."
            )
            if (rotationSensor == null && (accelSensor == null || magSensor == null)) {
                refreshNote.visibility = View.GONE
                addText(it, "A live compass sensor is unavailable. The calculated Qibla bearing is shown, but the arrow cannot track your phone.")
            }
            compass.statusListener = { message, reliable ->
                headingText.text = translate(message)
                headingText.setTextColor(
                    if (reliable) Color.rgb(34, 197, 94)
                    else if (message.contains("interference", true) ||
                        message.contains("weak", true) || message.contains("unreliable", true)
                    ) Color.rgb(249, 115, 22) else secondary
                )
                updateCompassDiagnostics()
            }
            compass.setSensorAvailable(
                rotationSensor != null || (accelSensor != null && magSensor != null)
            )
            compass.refreshStatus()
        })
        content.addView(card("About Qibla") {
            addText(it, "The Qibla is the direction faced during prayer, toward the Kaaba in Mecca. The compass shows this direction relative to north as detected by your phone.")
        })
    }

    private inner class CompassView(c: android.content.Context) : View(c) {
        private var azimuth = 0f
        private var qiblaBearing = 0f
        private var tilt = 0f
        private var accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        private var hasLiveHeading = false
        private var sensorAvailable = true
        private var magneticFieldDisturbed = false
        private var alignedSince = 0L
        private var lastStatus = ""
        var statusListener: ((String, Boolean) -> Unit)? = null
        private val accent = purple
        private val secondaryColor = secondary
        private val borderColor = border
        private val panelColor = panel
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = panelRaised }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(15).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun setQiblaBearing(deg: Float) {
            qiblaBearing = QiblaMath.normalize360(deg)
            updateGuidance()
            invalidate()
        }

        fun setHeading(
            heading: Float,
            tiltDegrees: Float,
            sensorAccuracy: Int,
            fieldDisturbed: Boolean,
            live: Boolean = true
        ) {
            azimuth = QiblaMath.normalize360(heading)
            tilt = tiltDegrees
            accuracy = sensorAccuracy
            magneticFieldDisturbed = fieldDisturbed
            hasLiveHeading = live
            updateGuidance()
            invalidate()
        }

        fun setSensorAvailable(available: Boolean) {
            sensorAvailable = available
            updateGuidance()
            invalidate()
        }

        fun setSensorState(live: Boolean, sensorAccuracy: Int, fieldDisturbed: Boolean) {
            hasLiveHeading = live
            accuracy = sensorAccuracy
            magneticFieldDisturbed = fieldDisturbed
            if (!live || fieldDisturbed || sensorAccuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
                alignedSince = 0L
            }
            updateGuidance()
            invalidate()
        }

        private fun updateGuidance() {
            val now = SystemClock.elapsedRealtime()
            val difference = QiblaMath.relativeQiblaAngle(qiblaBearing, azimuth)
            val absoluteDifference = kotlin.math.abs(difference)
            val reliable = sensorAvailable && hasLiveHeading &&
                accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
                !magneticFieldDisturbed
            if (!reliable) alignedSince = 0L

            val message = when {
                !sensorAvailable ->
                    "Live compass unavailable — showing the calculated bearing only."
                !hasLiveHeading ->
                    "Finding compass direction…"
                magneticFieldDisturbed -> {
                    val measured = measuredMagneticFieldUt?.toInt()
                    val expected = expectedMagneticFieldUt?.toInt()
                    if (measured != null && expected != null) {
                        "Magnetic interference suspected ($measured µT; expected about $expected µT). Move away from your laptop or metal."
                    } else {
                        "Magnetic interference detected. Move away from your laptop, magnets or metal."
                    }
                }
                accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ->
                    "Compass is unreliable. Move away from laptops, magnets and metal, then hold still while it settles."
                accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ->
                    "Compass signal is weak. Move away from metal and electronics."
                absoluteDifference <= (if (alignedSince == 0L) 5f else 8f) -> {
                    if (alignedSince == 0L) alignedSince = now
                    if (now - alignedSince >= 900L) "Facing Qibla"
                    else "Hold steady — checking alignment…"
                }
                else -> {
                    alignedSince = 0L
                    when {
                        absoluteDifference >= 160f -> "Qibla is behind you — turn around."
                        difference > 0f -> "Turn right ${absoluteDifference.toInt()}°"
                        else -> "Turn left ${absoluteDifference.toInt()}°"
                    }
                }
            }

            lastStatus = message
            statusListener?.invoke(message, reliable && message == "Facing Qibla")
        }

        fun refreshStatus() {
            lastStatus = ""
            updateGuidance()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = dp(280)
            setMeasuredDimension(size, size + dp(50))
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = (minOf(width, height) / 2f) - dp(12)

            canvas.drawCircle(cx, cy, radius, bgPaint)
            ringPaint.color = borderColor
            canvas.drawCircle(cx, cy, radius, ringPaint)
            ringPaint.color = accent
            canvas.drawCircle(cx, cy, radius - dp(3), ringPaint)

            canvas.save()
            canvas.rotate(-azimuth, cx, cy)

            for (i in 0..359 step 6) {
                val angle = Math.toRadians(i.toDouble()).toFloat()
                val isMajor = i % 90 == 0
                val inner = if (isMajor) radius - dp(18) else radius - dp(8)
                tickPaint.color = if (isMajor) accent else secondaryColor
                tickPaint.strokeWidth = if (isMajor) dp(2).toFloat() else dp(1).toFloat()
                canvas.drawLine(
                    cx + (radius - dp(4)) * Math.sin(angle.toDouble()).toFloat(),
                    cy - (radius - dp(4)) * Math.cos(angle.toDouble()).toFloat(),
                    cx + inner * Math.sin(angle.toDouble()).toFloat(),
                    cy - inner * Math.cos(angle.toDouble()).toFloat(),
                    tickPaint
                )
            }

            val cardinals = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
            for ((label, deg) in cardinals) {
                val angle = Math.toRadians(deg.toDouble()).toFloat()
                val dist = radius - dp(28)
                labelPaint.color = if (label == "N") accent else Color.WHITE
                canvas.drawText(
                    label,
                    cx + dist * Math.sin(angle.toDouble()).toFloat(),
                    cy - dist * Math.cos(angle.toDouble()).toFloat() + dp(5),
                    labelPaint
                )
            }

            canvas.restore()

            val qiblaAngle = Math.toRadians(((qiblaBearing - azimuth + 360) % 360).toDouble()).toFloat()
            val needleLen = radius - dp(24)
            val tipX = cx + needleLen * Math.sin(qiblaAngle.toDouble()).toFloat()
            val tipY = cy - needleLen * Math.cos(qiblaAngle.toDouble()).toFloat()
            val baseAngle1 = qiblaAngle + Math.toRadians(150.0).toFloat()
            val baseAngle2 = qiblaAngle - Math.toRadians(150.0).toFloat()
            val baseR = dp(10).toFloat()
            val path = Path().apply {
                moveTo(tipX, tipY)
                lineTo(
                    cx + baseR * Math.sin(baseAngle1.toDouble()).toFloat(),
                    cy - baseR * Math.cos(baseAngle1.toDouble()).toFloat()
                )
                lineTo(
                    cx + baseR * Math.sin(baseAngle2.toDouble()).toFloat(),
                    cy - baseR * Math.cos(baseAngle2.toDouble()).toFloat()
                )
                close()
            }
            needlePaint.color = accent
            canvas.drawPath(path, needlePaint)
            dotPaint.color = Color.rgb(30, 144, 255)
            canvas.drawCircle(tipX, tipY, dp(7).toFloat(), dotPaint)

            needlePaint.color = panelColor
            canvas.drawCircle(cx, cy, dp(6).toFloat(), needlePaint)
            needlePaint.color = accent
            canvas.drawCircle(cx, cy, dp(4).toFloat(), needlePaint)

            // Accuracy dot
            val accColor = when {
                magneticFieldDisturbed -> Color.rgb(249, 115, 22)
                accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Color.rgb(34, 197, 94)
                accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Color.rgb(250, 204, 21)
                accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Color.rgb(249, 115, 22)
                else -> Color.rgb(239, 68, 68)
            }
            dotPaint.color = accColor
            canvas.drawCircle(cx, cy - radius + dp(14), dp(4).toFloat(), dotPaint)

            // Tilt indicator
            labelPaint.textSize = dp(12).toFloat()
            labelPaint.color = secondaryColor
            canvas.drawText(if (currentAppLanguage() == "ar") "الميل: ${tilt.toInt()}°" else "Tilt: ${tilt.toInt()}°", cx, cy + dp(44), labelPaint)

            // The alignment message is rendered in the live status line above the dial.
            // It is deliberately not inferred from one noisy frame here.
        }
    }

    private fun showSettings() {
        pageTitle.text = "Settings"
        content.removeAllViews()
        val languageCard = card("Language") {
            addText(it, "Choose the language used by Swizy.")
            val language = styledSpinner()
            language.adapter = optionAdapter(arrayOf("العربية", "English"))
            language.setSelection(if (currentAppLanguage() == "ar") 0 else 1)
            language.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selected = if (position == 0) "ar" else "en"
                    if (selected != currentAppLanguage()) changeAppLanguage(selected)
                }
            }
            it.addView(language, LinearLayout.LayoutParams(-1, dp(52)))
        }
        content.addView(languageCard)
        val appLockCard = card("App lock") { cardContent ->
            addText(cardContent, "Protect Swizy when you open it. It locks again after 60 seconds in the background.")
            val appLock = Switch(this).apply {
                text = translate("Lock app with fingerprint")
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.appLockEnabled(this@MainActivity)
                setOnCheckedChangeListener { button, checked ->
                    if (!enableAppLock(checked)) {
                        button.setOnCheckedChangeListener(null)
                        button.isChecked = false
                        button.setOnCheckedChangeListener { _, next -> enableAppLock(next) }
                    }
                }
            }
            cardContent.addView(appLock)
        }
        content.addView(appLockCard)
        // Keep the existing controls intact while presenting them in a calmer visual hierarchy.
        content.addView(collapsibleSection("swizy", defaultOpen = true) { section ->
            addText(section, "Control how swizy masks profile pictures.")
            addText(section, "swizy mask mode")
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
                    val selected = Prefs.MaskMode.entries[position]
                    if (selected != Prefs.maskMode(this@MainActivity)) {
                        Prefs.setMaskMode(this@MainActivity, selected)
                        AvatarService.instance?.refreshAppearance(rescan = true)
                    }
                }
            })
            section.addView(mode, LinearLayout.LayoutParams(-1, dp(52)))
            addText(section, "Mask color")
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
                    val selected = Prefs.BoxColor.entries[position]
                    if (selected != Prefs.boxColor(this@MainActivity)) {
                        Prefs.setBoxColor(this@MainActivity, selected)
                        AvatarService.instance?.refreshAppearance()
                    }
                }
            })
            section.addView(color, LinearLayout.LayoutParams(-1, dp(52)))
            addSlider(section, "Mask opacity (black boxes)", 25, 100, Prefs.boxOpacity(this)) {
                Prefs.setBoxOpacity(this, it)
                AvatarService.instance?.refreshAppearance()
            }
            addSlider(section, "Paw opacity when idle", 35, 100, Prefs.controlOpacity(this)) {
                Prefs.setControlOpacity(this, it)
                AvatarService.instance?.refreshAppearance()
            }
            addSlider(section, "Line thickness", 4, 64, Prefs.lineThicknessDp(this)) {
                Prefs.setLineThicknessDp(this, it)
                AvatarService.instance?.refreshAppearance(rescan = true)
            }
            addSlider(section, "Dynamic circle size", 24, 96, Prefs.circleSizeDp(this)) {
                Prefs.setCircleSizeDp(this, it)
                AvatarService.instance?.refreshAppearance(rescan = true)
            }
            val indicator = Switch(this).apply {
                text = "Show privacy indicator while masking"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.privacyIndicator(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setPrivacyIndicator(this@MainActivity, checked)
                    AvatarService.instance?.refreshAppearance()
                }
            }
            section.addView(indicator)
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
            section.addView(performance)
            addText(section, "Quick Settings action")
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
            section.addView(tileAction, LinearLayout.LayoutParams(-1, dp(52)))
        })
        content.addView(collapsibleSection("Sibha", defaultOpen = false) { section ->
            addText(section, "Settings for the prayer counter.")
            val peep = EditText(this).apply {
                hint = "Peep every N counts (0 disables)"
                setText(Prefs.sibhaPeepEvery(this@MainActivity).takeIf { it > 0 }?.toString() ?: "")
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setTextColor(Color.WHITE)
                setHintTextColor(secondary)
            }
            section.addView(peep)
            val save = actionButton("Save Sibha reminder")
            save.setOnClickListener {
                Prefs.setSibhaPeepEvery(this@MainActivity, peep.text.toString().toIntOrNull() ?: 0)
                Toast.makeText(this, translate("Sibha reminder saved"), Toast.LENGTH_SHORT).show()
            }
            section.addView(save)
        })
        content.addView(collapsibleSection("Prayers", defaultOpen = false) { section ->
            addText(section, "Prayer time calculation and announcement settings.")
            addText(section, "Prayer calculation method")
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
                        // A response calculated with the previous method must not overwrite this one.
                        prayerRequestId++
                        prayerRequestInFlight = false
                        Prefs.setPrayerMethod(this@MainActivity, methodValues[position])
                    }
                }
            })
            section.addView(method, LinearLayout.LayoutParams(-1, dp(52)))
            val format = Switch(this).apply {
                text = "Use 24-hour prayer times"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.prayer24Hour(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setPrayer24Hour(this@MainActivity, checked)
                }
            }
            section.addView(format)
            val headsetTime = Switch(this).apply {
                text = "Speak the time on long-press headset button"
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = Prefs.headsetTime(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setHeadsetTime(this@MainActivity, checked)
                    AvatarService.instance?.refresh()
                }
            }
            section.addView(headsetTime)
            val voiceInfo = actionButton("How to change voice")
            voiceInfo.setOnClickListener {
                showVoiceInstructions()
            }
            section.addView(voiceInfo)
            val testTime = actionButton("Say the time now")
            testTime.setOnClickListener {
                val service = AvatarService.instance
                if (service != null) service.announceCurrentTime()
                else Toast.makeText(this, translate("Turn on the swizy accessibility service first"), Toast.LENGTH_SHORT).show()
            }
            section.addView(testTime)
        })
        content.addView(collapsibleSection("Profiles & backup", defaultOpen = false) { section ->
            addText(section, "Manual boxes are saved separately for each app profile and screen orientation.")
            addText(
                section,
                "Create profiles from the paw menu: choose Add profile in the target app, draw your boxes, then choose Save profile. swizy will apply that profile automatically whenever the app is open."
            )
            val profiles = Prefs.profiles(this)
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
                section.addView(row)
            }
            val export = actionButton("Export layouts")
            export.setOnClickListener { exportBackup() }
            section.addView(export)
            val import = actionButton("Import layouts")
            import.setOnClickListener { importBackup() }
            section.addView(import)
        })
        localizeTree(content)
        refreshNavigation()
    }

    private fun collapsibleSection(
        title: String,
        defaultOpen: Boolean,
        body: (LinearLayout) -> Unit
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), border)
            }
            elevation = dp(1).toFloat()
        }
        val arrow = TextView(this).apply {
            text = if (defaultOpen) "▼" else "▶"
            textSize = 14f
            setTextColor(purple)
            setPadding(0, 0, dp(8), 0)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        header.addView(arrow)
        header.addView(TextView(this).apply {
            text = translate(title)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        box.addView(header)
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (defaultOpen) View.VISIBLE else View.GONE
        }
        body(section)
        box.addView(section)
        header.setOnClickListener {
            val isOpen = section.visibility == View.VISIBLE
            section.visibility = if (isOpen) View.GONE else View.VISIBLE
            arrow.text = if (isOpen) "▶" else "▼"
        }
        val wrapper = LinearLayout(this).apply {
            setPadding(0, 0, 0, dp(12))
            addView(box, LinearLayout.LayoutParams(-1, -2))
        }
        return wrapper
    }

    private fun showVoiceInstructions() {
        AlertDialog.Builder(this)
            .setTitle("Change the announcement voice")
            .setMessage(
                "1. Go to Settings and search for Text to speech\n" +
                "2. Choose a language you like\n" +
                "3. For voices, go to the favorite engine and click the settings or gear tab on the side and choose Install voice data\n" +
                "4. Choose the language and voice\n" +
                "5. Reboot the phone & enjoy"
            )
            .setPositiveButton("Open Android Settings") { _, _ ->
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
        val qiblaAction = actionButton("Open Qibla compass")
        qiblaAction.setOnClickListener { openPage(3) }
        content.addView(qiblaAction)
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
        localizeTree(content)
        refreshNavigation()
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun updatePrayerTimes(useGps: Boolean, latitudeText: String, longitudeText: String) {
        if (useGps && !hasLocationPermission()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_REQUEST
            )
            return
        }
        val coordinates = if (useGps) currentLocation() else {
            val lat = latitudeText.toDoubleOrNull()
            val lon = longitudeText.toDoubleOrNull()
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                Toast.makeText(this, translate("Enter valid latitude and longitude"), Toast.LENGTH_LONG).show()
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
        if (prayerRequestInFlight) return
        prayerRequestInFlight = true
        val requestId = ++prayerRequestId
        val method = Prefs.prayerMethod(this)
        Toast.makeText(this, translate("Updating prayer times…"), Toast.LENGTH_SHORT).show()
        thread(name = "prayer-update") {
            val result = fetchPrayerTimes(latitude, longitude, method)
            runOnUiThread {
                if (requestId != prayerRequestId) return@runOnUiThread
                prayerRequestInFlight = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result != null) {
                    Prefs.setPrayerCache(this, todayKey(), result.toString())
                    // A background response should never pull the user away from another page.
                    if (currentPage == 2) showPrayers()
                    Toast.makeText(this, translate("Prayer times updated"), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, translate("Could not update. Cached times remain available."), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestCurrentLocation() {
        if (activeLocationListener != null) return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            Toast.makeText(this, translate("Enable location services or enter coordinates."), Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, translate("Finding your location…"), Toast.LENGTH_SHORT).show()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (activeLocationListener !== this) return
                activeLocationListener = null
                runCatching { manager.removeUpdates(this) }
                fetchPrayerTimesFor(location.latitude, location.longitude)
            }
        }
        activeLocationListener = listener
        var requested = false
        providers.forEach { provider ->
            if (runCatching {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                true
            }.getOrDefault(false)) {
                requested = true
            }
        }
        if (!requested) {
            activeLocationListener = null
            Toast.makeText(this, translate("Location unavailable. Enter coordinates instead."), Toast.LENGTH_LONG).show()
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (activeLocationListener === listener) {
                activeLocationListener = null
                runCatching { manager.removeUpdates(listener) }
                Toast.makeText(this, translate("Location unavailable. Enter coordinates instead."), Toast.LENGTH_LONG).show()
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

    private fun fetchPrayerTimes(latitude: Double, longitude: Double, method: Int): JSONObject? {
        val date = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        val url = URL(
            "https://api.aladhan.com/v1/timings/$date?latitude=${encode(latitude)}" +
                "&longitude=${encode(longitude)}&method=$method"
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
        if (requestCode == LOCATION_REQUEST && hasLocationPermission()) showPrayers()
    }

    private fun card(title: String, body: (LinearLayout) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), border)
            }
            elevation = dp(1).toFloat()
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
            this.text = translate(text)
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
        fun renderValue(number: Int) {
            value.text = "${translate(label)}: ${number}${if (label.contains("opacity", true)) "%" else " dp"}"
        }
        val slider = SeekBar(this).apply {
            this.max = max - min
            progress = initial.coerceIn(min, max) - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val selected = min + progress
                    renderValue(selected)
                    if (fromUser) onChanged(selected)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        renderValue(initial.coerceIn(min, max))
        parent.addView(value)
        parent.addView(slider, LinearLayout.LayoutParams(-1, dp(42)))
    }

    private fun actionButton(text: String) = Button(this).apply {
        this.text = translate(text)
        isAllCaps = false
        minHeight = dp(48)
        setPadding(dp(14), 0, dp(14), 0)
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.rgb(183, 156, 255))
            colors = intArrayOf(purple, Color.rgb(105, 65, 205))
        }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(0, dp(5), 0, dp(5))
        }
    }

    private fun styledSpinner() = Spinner(this).apply {
        background = outlined(panelRaised, border, 12)
        setPopupBackgroundDrawable(outlined(panel, border, 12))
        setPadding(dp(12), 0, dp(12), 0)
    }

    private fun optionAdapter(values: Array<String>) =
        object : ArrayAdapter<String>(this, 0, values) {
            private fun row(position: Int) = TextView(this@MainActivity).apply {
                text = translate(values[position])
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
        private const val STATE_PAGE = "swizy_current_page"
        private const val STATE_SCROLL_Y = "swizy_scroll_y"
        private const val USER_CONTENT_TAG = "swizy_user_content"
        private const val AUDIO_BOOK_FILE_REQUEST = 1010
        private const val AUDIO_BOOK_FOLDER_REQUEST = 1011
        private const val AUDIO_BOOK_IMPORT_TAG = "swizy_audio_book_import"
    }
}
