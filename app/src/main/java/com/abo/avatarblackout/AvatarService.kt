package com.abo.avatarblackout

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.format.DateFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.content.res.Configuration
import kotlin.math.max
import java.util.ArrayDeque
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AvatarService : AccessibilityService() {

    companion object {
        private const val TAG = "AvatarBlackout"
        @Volatile var instance: AvatarService? = null
        private val TELEGRAM = setOf(
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus"
        )
        private val TARGETS = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.linkedin.android", "org.thunderdog.challegram"
        ) + TELEGRAM
        private const val LINKEDIN = "com.linkedin.android"
        // view-id fragments that mark a profile picture
        private val AVATAR_ID = Regex("photo|avatar|picture|profile|portrait|headshot|actor_image|initials|group_image")

        private fun isMaleVoice(voice: Voice): Boolean {
            val name = voice.name.lowercase(Locale.ROOT)
            return !name.contains("female") &&
                (name.contains("#male") ||
                    name.contains("_male") ||
                    name.contains("-male") ||
                    name.contains("en-us-x-iol") ||
                    name.contains("en-us-x-iob") ||
                    listOf("david", "daniel", "george", "james", "john", "mark", "michael", "tom")
                        .any(name::contains))
        }

        fun hasMaleVoice(context: Context, result: (Boolean) -> Unit) {
            lateinit var probe: TextToSpeech
            probe = TextToSpeech(context.applicationContext) { status ->
                val available = status == TextToSpeech.SUCCESS &&
                    probe.voices.orEmpty().any(::isMaleVoice)
                probe.shutdown()
                result(available)
            }
        }
    }

    /** One black circle: where to draw it, and an optional area it must not leave (for half-visible rows). */
    private class Item(
        val rect: Rect,
        val clip: Rect? = null,
        val forceRect: Boolean = false
    )

    /** Everything found in one app window. Telegram guesses are kept apart so they can be dropped or replaced. */
    private class Scan {
        val items = ArrayList<Item>()
        val avatarRects = ArrayList<Rect>()
        val tgRows = ArrayList<Item>()   // chat-list avatar guesses
        val msgRows = ArrayList<Rect>()  // message rows inside a chat
        var listRect: Rect? = null
        var scrollRect: Rect? = null
        var hasInput = false
    }

    private lateinit var wm: WindowManager
    private var overlay: OverlayView? = null
    private var control: ControlView? = null
    private var manualSessionActive = false
    private var selectionStart: Pair<Float, Float>? = null
    private var movingAreaIndex = -1
    private var movingAreaStart: Pair<Float, Float>? = null
    private var movingTouchOffset: Pair<Float, Float>? = null
    private var movingPreview: Rect? = null
    private var resizingAreaIndex = -1
    private var resizingTouchOffset: Pair<Float, Float>? = null
    private var resizingPreview: Rect? = null
    private var profileCreationId: String? = null
    private var profileCaptureRects = mutableListOf<Rect>()
    private var profileCreationNew = false
    private var manualSessionProfileId: String? = null
    private var activePackage = ""
    private var timeSpeaker: TextToSpeech? = null
    private var timeSpeakerReady = false
    private var pendingTimeText: String? = null
    private val speechTimeout = Runnable { finishTimeAnnouncement() }
    private var headsetKeyDown = false
    private var headsetLongPressTriggered = false
    private var replayingMediaKey = false
    private val headsetLongPress = Runnable {
        if (headsetKeyDown && Prefs.headsetTime(this)) {
            headsetLongPressTriggered = true
            announceCurrentTime()
        }
    }
    private var resumeAfterTime = false
    private var speakingTime = false
    private val undoStack = ArrayDeque<List<Rect>>()
    private val redoStack = ArrayDeque<List<Rect>>()
    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private val controlIcon by lazy { BitmapFactory.decodeResource(resources, R.drawable.eyes) }
    private val scan = Runnable { pending = false; doScan() }
    // safety net: rescan every second so a missed event or a dropped overlay recovers by itself
    private val tick = object : Runnable {
        override fun run() {
            if (Prefs.isEnabled(this@AvatarService)) {
                doScan()
                handler.postDelayed(this, if (Prefs.performanceMode(this@AvatarService)) 3000 else 1000)
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addOverlay()
        refresh()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacks(tick)
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
        control?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        control = null
        handler.removeCallbacks(headsetLongPress)
        handler.removeCallbacks(speechTimeout)
        timeSpeaker?.stop()
        timeSpeaker?.shutdown()
        timeSpeaker = null
        timeSpeakerReady = false
        pendingTimeText = null
        speakingTime = false
        manualSessionActive = false
        profileCreationId = null
        profileCaptureRects = mutableListOf()
        profileCreationNew = false
        manualSessionProfileId = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    /**
     * Key filtering is requested only while the headset feature is on, so with it off the service
     * never sees (or delays) a single key press.
     */
    private fun applyHeadsetFilter() {
        val info = serviceInfo ?: return
        val flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        val want = Prefs.headsetTime(this)
        val has = info.flags and flag != 0
        if (want == has) return
        info.flags = if (want) info.flags or flag else info.flags and flag.inv()
        serviceInfo = info
    }

    private fun isHeadsetKey(code: Int) =
        // HEADSETHOOK is the middle button of most wired headsets, and the key behind the
        // "long press starts the voice assistant" behaviour
        code == KeyEvent.KEYCODE_HEADSETHOOK ||
            code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            code == KeyEvent.KEYCODE_MEDIA_STOP

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (replayingMediaKey || !Prefs.headsetTime(this)) return false
        if (!isHeadsetKey(event.keyCode)) return false
        Log.d(TAG, "headset key ${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action} " +
            "repeat=${event.repeatCount} device=${event.deviceId}")
        // while a call is ringing or running the button must keep answering / ending it
        val audio = getSystemService(AudioManager::class.java)
        if (audio != null && audio.mode != AudioManager.MODE_NORMAL) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // repeatCount 0 = a fresh press (this also recovers if an earlier release was lost)
                if (event.repeatCount == 0) {
                    headsetKeyDown = true
                    headsetLongPressTriggered = false
                    handler.removeCallbacks(headsetLongPress)
                    handler.postDelayed(headsetLongPress, 600L)
                }
                // swallowing the press is what stops the system launching the assistant
                return true
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(headsetLongPress)
                val wasLongPress = headsetLongPressTriggered
                headsetKeyDown = false
                headsetLongPressTriggered = false
                // short press: give it back to the media system so play/pause/next/previous still work
                if (!wasLongPress) dispatchMediaKey(event.keyCode)
                return true
            }
            else -> return true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (manualSessionActive) {
            removeControl()
            addControl()
        }
        doScan()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (!Prefs.isEnabled(this) || pending) return
        pending = true
        handler.postDelayed(scan, 80L)
    }

    fun refresh() {
        applyHeadsetFilter()
        handler.removeCallbacks(scan)
        handler.removeCallbacks(tick)
        pending = false
        control?.refreshOpacity()
        if (Prefs.isEnabled(this)) {
            doScan()
            handler.postDelayed(
                tick,
                if (Prefs.performanceMode(this)) 3000 else 1000
            )
        } else {
            overlay?.setItems(emptyList())
            removeControl()
        }
    }

    /** Says the time out loud. Also used by the "Say the time now" button in Settings. */
    fun announceCurrentTime() {
        if (speakingTime) return
        val locale = Locale.getDefault()
        val pattern = if (DateFormat.is24HourFormat(this)) "H:mm" else "h:mm a"
        val time = SimpleDateFormat(pattern, locale).format(Date())
        val text = if (locale.language == "en") "The time is $time" else time
        val audio = getSystemService(AudioManager::class.java)
        resumeAfterTime = audio?.isMusicActive == true
        if (resumeAfterTime) dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        speakingTime = true
        handler.removeCallbacks(speechTimeout)
        handler.postDelayed(speechTimeout, 12_000L) // never stay "speaking" (and never leave the music paused) forever
        speakTimeText(text)
    }

    /** The speech engine is started on first use and only once it reports ready is anything spoken. */
    private fun speakTimeText(text: String) {
        val engine = timeSpeaker
        if (engine != null && timeSpeakerReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "swizy-time")
            return
        }
        pendingTimeText = text
        if (engine != null) return // still starting; it speaks as soon as it is ready
        timeSpeaker = TextToSpeech(applicationContext) { status ->
            val tts = timeSpeaker
            if (status != TextToSpeech.SUCCESS || tts == null) {
                Log.w(TAG, "text-to-speech failed to start: $status")
                timeSpeaker = null
                pendingTimeText = null
                finishTimeAnnouncement()
                return@TextToSpeech
            }
            val result = tts.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.ENGLISH)
            }
            selectConfiguredVoice(tts)
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { handler.post { finishTimeAnnouncement() } }
                override fun onDone(utteranceId: String?) { handler.post { finishTimeAnnouncement() } }
            })
            timeSpeakerReady = true
            pendingTimeText?.let {
                pendingTimeText = null
                tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, "swizy-time")
            }
        }
    }

    private fun selectConfiguredVoice(tts: TextToSpeech) {
        val language = Locale.getDefault().language
        val voices = tts.voices ?: return
        val languageVoices = voices
            .filter { it.locale.language == language || it.locale.language == Locale.ENGLISH.language }
        val selected = when (Prefs.headsetVoiceGender(this)) {
            Prefs.VoiceGender.MALE -> languageVoices.firstOrNull(::isMaleVoice)
            Prefs.VoiceGender.FEMALE -> languageVoices.firstOrNull { voice ->
                val name = voice.name.lowercase(Locale.ROOT)
                name.contains("female") || name.contains("woman")
            }
        }
        if (selected != null) {
            tts.voice = selected
            Log.d(TAG, "selected ${Prefs.headsetVoiceGender(this).name.lowercase()} TTS voice ${selected.name}")
        } else {
            Log.w(TAG, "requested ${Prefs.headsetVoiceGender(this)} TTS voice is not installed; available=${languageVoices.map { it.name }}")
        }
    }

    private fun finishTimeAnnouncement() {
        handler.removeCallbacks(speechTimeout)
        if (!speakingTime) return
        speakingTime = false
        if (resumeAfterTime) {
            resumeAfterTime = false
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audio = getSystemService(AudioManager::class.java) ?: return
        replayingMediaKey = true
        try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } finally {
            replayingMediaKey = false
        }
    }

    fun summonManualHideControl() {
        if (!Prefs.isEnabled(this)) return
        doScan()
        manualSessionActive = true
        if (manualSessionProfileId == null) {
            manualSessionProfileId = Prefs.profileForPackage(this, activePackage).id
        }
        addControl()
        doScan()
    }

    private fun beginManualHideSelection() {
        if (!Prefs.isEnabled(this) || !manualSessionActive) return
        selectionStart = null
        removeControl()
        overlay?.setSelectionMode(true)
        setOverlayTouchable(true)
    }

    private fun finishManualSelection(rect: Rect) {
        if (rect.width() < dp(12) || rect.height() < dp(12)) {
            overlay?.setSelectionMode(false)
            setOverlayTouchable(false)
            addControl()
            return
        }
        rememberManualChange()
        if (profileCreationId != null) {
            profileCaptureRects.add(Rect(rect))
        } else {
            saveManualRects(manualRects() + rect)
        }
        Prefs.setManualHideVisible(this, true)
        overlay?.setSelectionMode(false)
        setOverlayTouchable(false)
        addControl()
        doScan()
    }

    private fun removeLastManualArea() {
        val areas = editableRects()
        if (areas.isEmpty()) return
        rememberEditableChange(areas)
        saveEditableRects(areas.dropLast(1))
        doScan()
    }

    private fun removeAllManualAreas() {
        val areas = editableRects()
        if (areas.isEmpty()) return
        rememberEditableChange(areas)
        if (profileCreationId != null ||
            (profileRects().isNotEmpty() && manualRects().isEmpty())
        ) {
            if (profileCreationId != null) profileCaptureRects.clear()
            else saveProfileRects(emptyList())
            doScan()
            return
        }
        Prefs.clearManualHide(this)
        saveManualRects(emptyList())
        Prefs.setManualHideVisible(this, true)
        doScan()
    }

    private fun rememberManualChange() {
        rememberEditableChange(editableRects())
    }

    private fun rememberEditableChange(areas: List<Rect>) {
        undoStack.addLast(areas.map { Rect(it) })
        while (undoStack.size > 20) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun editableRects(): List<Rect> {
        if (profileCreationId != null) return profileCaptureRects
        val profile = profileRects()
        return if (profile.isNotEmpty() && manualRects().isEmpty()) profile else manualRects()
    }

    private fun saveEditableRects(rects: List<Rect>) {
        if (profileCreationId != null) profileCaptureRects = rects.map { Rect(it) }.toMutableList()
        else if (profileRects().isNotEmpty() && manualRects().isEmpty()) saveProfileRects(rects)
        else saveManualRects(rects)
    }

    private fun undoManualChange() {
        if (undoStack.isEmpty()) return
        val current = editableRects()
        redoStack.addLast(current.map { Rect(it) })
        saveEditableRects(undoStack.removeLast())
        doScan()
    }

    private fun redoManualChange() {
        if (redoStack.isEmpty()) return
        val current = editableRects()
        undoStack.addLast(current.map { Rect(it) })
        saveEditableRects(redoStack.removeLast())
        doScan()
    }

    private fun toggleManualAreas() {
        Prefs.setManualHideVisible(this, !Prefs.manualHideVisible(this))
        doScan()
    }

    private fun beginManualAreaMove() {
        if (!Prefs.isEnabled(this) || !manualSessionActive) return
        movingAreaIndex = -1
        movingAreaStart = null
        movingTouchOffset = null
        movingPreview = null
        rememberManualChange()
        removeControl()
        overlay?.setMoveMode(true)
        setOverlayTouchable(true)
    }

    private fun beginManualAreaResize() {
        if (!Prefs.isEnabled(this) || !manualSessionActive) return
        resizingAreaIndex = -1
        resizingTouchOffset = null
        resizingPreview = null
        rememberManualChange()
        removeControl()
        overlay?.setResizeMode(true)
        setOverlayTouchable(true)
    }

    private fun finishManualAreaMove(index: Int, start: Pair<Float, Float>, end: Pair<Float, Float>) {
        val areas = editableRects()
        if (index in areas.indices) {
            val updated = areas.toMutableList()
            updated[index] = movingPreview ?: movedArea(areas[index], start, end)
            saveEditableRects(updated)
        }
        movingAreaIndex = -1
        movingAreaStart = null
        movingTouchOffset = null
        movingPreview = null
        overlay?.setMoveMode(false)
        setOverlayTouchable(false)
        addControl()
        doScan()
    }

    private fun movedArea(old: Rect, start: Pair<Float, Float>, end: Pair<Float, Float>): Rect {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val left = (old.left + end.first - start.first).toInt()
            .coerceIn(0, (screenW - old.width()).coerceAtLeast(0))
        val top = (old.top + end.second - start.second).toInt()
            .coerceIn(0, (screenH - old.height()).coerceAtLeast(0))
        return Rect(left, top, left + old.width(), top + old.height())
    }

    private fun areaAtTouch(old: Rect, x: Float, y: Float, offset: Pair<Float, Float>): Rect {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val left = (x - offset.first).toInt()
            .coerceIn(0, (screenW - old.width()).coerceAtLeast(0))
        val top = (y - offset.second).toInt()
            .coerceIn(0, (screenH - old.height()).coerceAtLeast(0))
        return Rect(left, top, left + old.width(), top + old.height())
    }

    private fun resizedArea(old: Rect, x: Float, y: Float, offset: Pair<Float, Float>): Rect {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val right = (x - offset.first).toInt().coerceIn(old.left + dp(12), screenW)
        val bottom = (y - offset.second).toInt().coerceIn(old.top + dp(12), screenH)
        return Rect(old.left, old.top, right, bottom)
    }

    private fun finishManualAreaResize() {
        val areas = editableRects()
        if (resizingAreaIndex in areas.indices && resizingPreview != null) {
            val updated = areas.toMutableList()
            updated[resizingAreaIndex] = resizingPreview!!
            saveEditableRects(updated)
        }
        resizingAreaIndex = -1
        resizingTouchOffset = null
        resizingPreview = null
        overlay?.setResizeMode(false)
        setOverlayTouchable(false)
        addControl()
        doScan()
    }

    private fun closeManualSession() {
        cancelProfileCreation()
        manualSessionActive = false
        manualSessionProfileId = null
        removeControl()
        doScan()
    }

    private fun setOverlayTouchable(touchable: Boolean) {
        overlay?.let {
            val lp = it.layoutParams as WindowManager.LayoutParams
            val interactionFlags = if (touchable) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or interactionFlags
            wm.updateViewLayout(it, lp)
        }
    }

    private fun addControl() {
        if (control != null || !manualSessionActive) return
        val v = ControlView(this)
        val lp = WindowManager.LayoutParams(
            dp(58), dp(58), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val prefs = getSharedPreferences("avb", Context.MODE_PRIVATE)
            x = prefs.getInt("control_x", resources.displayMetrics.widthPixels - dp(70))
                .coerceIn(0, resources.displayMetrics.widthPixels - dp(58))
            y = prefs.getInt("control_y", resources.displayMetrics.heightPixels / 2)
                .coerceIn(0, resources.displayMetrics.heightPixels - dp(58))
        }

        wm.addView(v, lp)
        control = v
    }

    private fun orientationKey() =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }

    private fun profileId() =
        profileCreationId ?: Prefs.profileForPackage(this, activePackage).id

    private fun manualLayoutId() = "manual"

    private fun manualRects(): List<Rect> {
        return Prefs.manualHideRects(this, manualLayoutId(), orientationKey())
    }

    private fun profileRects(): List<Rect> =
        if (profileCreationId != null) profileCaptureRects
        else Prefs.profileHideRects(this, profileId(), orientationKey())

    private fun saveManualRects(rects: List<Rect>) =
        Prefs.saveManualHideRects(this, manualLayoutId(), orientationKey(), rects)

    private fun saveProfileRects(rects: List<Rect>) =
        Prefs.saveProfileHideRects(this, profileId(), orientationKey(), rects)

    private fun keyboardTop(): Int? {
        return windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { window ->
                Rect().also { bounds ->
                    runCatching { window.getBoundsInScreen(bounds) }.getOrNull()
                }
            }
            .map { it.top }
            .filter { it > 0 }
            .minOrNull()
    }

    private fun manualDisplayRect(rect: Rect): Rect {
        val top = keyboardTop() ?: return Rect(rect)
        val screenHeight = resources.displayMetrics.heightPixels
        val keyboardHeight = (screenHeight - top).coerceAtLeast(0)
        if (keyboardHeight == 0) return Rect(rect)
        val safeBottom = (top - dp(56)).coerceAtLeast(0)
        if (rect.bottom <= safeBottom) return Rect(rect)

        val shift = (rect.bottom - safeBottom).coerceAtLeast(0)
        val shiftedTop = rect.top - shift
        val shiftedBottom = rect.bottom - shift
        val visibleTop = shiftedTop.coerceAtLeast(0)
        val visibleBottom = shiftedBottom.coerceAtMost(safeBottom)
        if (visibleBottom <= visibleTop) return Rect(visibleTop, visibleTop, visibleTop, visibleTop)
        return Rect(rect.left, visibleTop, rect.right, visibleBottom)
    }

    private fun manualStoredRect(displayed: Rect, index: Int): Rect {
        val top = keyboardTop() ?: return Rect(displayed)
        val safeBottom = (top - dp(56)).coerceAtLeast(0)
        val original = editableRects().getOrNull(index) ?: return Rect(displayed)
        val shift = (original.bottom - safeBottom).coerceAtLeast(0)
        return Rect(
            displayed.left,
            displayed.top + shift,
            displayed.right,
            displayed.bottom + shift
        )
    }

    private fun editableDisplayRects() = editableRects().map { manualDisplayRect(it) }

    private fun beginProfileCreation() {
        if (!Prefs.isEnabled(this) || !manualSessionActive) return
        val packageName = activePackage
        val hadProfile = Prefs.hasProfileForPackage(this, packageName)
        val profile = Prefs.addProfile(
            this,
            "Profile ${packageName.substringAfterLast('.')}",
            packageName
        )
        profileCreationId = profile.id
        profileCreationNew = !hadProfile
        manualSessionProfileId = profile.id
        profileCaptureRects = mutableListOf()
        removeControl()
        addControl()
        control?.invalidate()
        doScan()
    }

    private fun saveProfileCreation() {
        val id = profileCreationId ?: return
        val packageName = activePackage
        saveProfileRects(profileCaptureRects)
        Prefs.renameProfile(this, id, packageName.substringAfterLast('.').ifBlank { "Profile" })
        profileCreationId = null
        profileCaptureRects = mutableListOf()
        profileCreationNew = false
        control?.invalidate()
        doScan()
    }

    private fun cancelProfileCreation() {
        if (profileCreationNew) {
            profileCreationId?.let { Prefs.deleteProfile(this, it) }
        }
        profileCreationId = null
        profileCaptureRects = mutableListOf()
        profileCreationNew = false
    }

    private fun removeControl() {
        control?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        control = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun ensureOverlay() {
        val v = overlay
        if (v == null || !v.isAttachedToWindow) {
            v?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
            overlay = null
            addOverlay()
        }
    }

    private fun addOverlay() {
        val v = OverlayView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(v, lp)
        overlay = v
    }

    private fun Rect.area() = width() * height()

    private fun foregroundPackage(): String? {
        return windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedWith(
                compareByDescending<AccessibilityWindowInfo> { it.isFocused }
                    .thenByDescending { it.isActive }
                    .thenByDescending { it.layer }
            )
            .mapNotNull { window ->
                runCatching {
                    val root = if (Build.VERSION.SDK_INT >= 33) {
                        window.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID)
                    } else {
                        window.root
                    }
                    root?.packageName?.toString()
                }.getOrNull()
            }
            .firstOrNull()
            ?.takeUnless { it == applicationContext.packageName || it == "com.android.systemui" }
    }

    private fun doScan() {
        try {
            ensureOverlay()
            val detectedPackage = foregroundPackage() ?: ""
            if (detectedPackage != activePackage) {
                activePackage = detectedPackage
                if (Prefs.hasProfileForPackage(this, activePackage)) {
                    Prefs.setManualHideVisible(this, true)
                }
                Log.d(TAG, "foreground package changed to $activePackage")
            }
            val out = ArrayList<Item>()
            val hasActiveProfile = Prefs.hasProfileForPackage(this, activePackage)
            val legacyProfileRects = if (hasActiveProfile) {
                Prefs.manualHideRects(this, profileId(), orientationKey())
            } else {
                emptyList()
            }
            if (hasActiveProfile && profileRects().isEmpty() && legacyProfileRects.isNotEmpty()) {
                saveProfileRects(legacyProfileRects)
                Prefs.clearManualHideRects(this, profileId(), orientationKey())
            }
            val manualItems = if (
                Prefs.manualHideVisible(this) && manualRects().isNotEmpty()
            ) {
                manualRects().map { Item(manualDisplayRect(it), forceRect = true) }
            } else emptyList()
            val profileItems = if (
                Prefs.manualHideVisible(this) &&
                (hasActiveProfile || profileCreationId != null) &&
                profileRects().isNotEmpty()
            ) {
                profileRects().map { Item(manualDisplayRect(it), forceRect = true) }
            } else emptyList()
            out.addAll(manualItems)
            out.addAll(profileItems)
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = (if (Build.VERSION.SDK_INT >= 33)
                    w.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID)
                else w.root) ?: continue
                val packageName = root.packageName?.toString() ?: continue
                if (packageName in TARGETS && Prefs.maskMode(this) != Prefs.MaskMode.NONE) {
                    val sc = Scan()
                    walk(root, sc, 0)
                    if (Prefs.maskMode(this) == Prefs.MaskMode.SIMPLE) {
                        if (packageName in TELEGRAM) {
                            if (!sc.hasInput) tgListLine(sc, out) else tgChat(sc, out)
                        } else {
                            lineFor(sc)?.let { out.add(it) }
                        }
                    } else {
                        out.addAll(sc.items)
                        if (packageName in TELEGRAM) {
                        if (!sc.hasInput) out.addAll(sc.tgRows) else tgChat(sc, out)
                        }
                    }
                }
            }
            // if a big container and a bigger-than-half child both matched, keep the child
            val finalItems = out.filter { a ->
                out.none { b ->
                    b !== a && a.rect != b.rect && a.rect.contains(b.rect) &&
                        b.rect.area() * 2 >= a.rect.area()
                }
            }
            val uniqueItems = finalItems.distinctBy {
                "${it.rect.left},${it.rect.top},${it.rect.right},${it.rect.bottom}"
            }.toMutableList()
            manualItems.asReversed().forEach { manual ->
                if (uniqueItems.none { it.rect == manual.rect }) uniqueItems.add(0, manual)
            }
            Log.d(TAG, "rendering ${uniqueItems.size} avatar bars")
            overlay?.setItems(uniqueItems)
        } catch (e: Exception) {
            Log.w(TAG, "scan failed", e)
        }
    }

    private fun walk(n: AccessibilityNodeInfo, sc: Scan, depth: Int) {
        if (depth > 45) return
        if (n.className?.contains("EditText") == true) sc.hasInput = true
        val bounds = Rect().also { n.getBoundsInScreen(it) }
        val cls = n.className?.toString() ?: ""
        if (n.isVisibleToUser) {
            avatarItem(n)?.let {
                sc.items.add(it)
                sc.avatarRects.add(Rect(bounds))
            }
        }
        if (n.isVisibleToUser &&
            (n.isScrollable || cls.contains("RecyclerView") ||
                cls.contains("ListView") || cls.contains("ScrollView"))
        ) {
            if (sc.scrollRect == null || bounds.area() > sc.scrollRect!!.area()) {
                sc.scrollRect = bounds
            }
        }
        // Telegram draws avatars itself and exposes no avatar nodes, so we work from its list rows
        val telegramList = n.packageName?.toString() in TELEGRAM &&
            n.className?.contains("RecyclerView") == true
        val listBounds = if (telegramList) Rect().also { n.getBoundsInScreen(it) } else null
        if (listBounds != null) {
            val cur = sc.listRect
            if (cur == null || listBounds.area() > cur.area()) sc.listRect = listBounds
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            if (listBounds != null) {
                telegramRow(c, listBounds, sc.tgRows)
                telegramMsgRow(c, sc.msgRows)
            }
            walk(c, sc, depth + 1)
        }
    }

    private fun avatarItem(n: AccessibilityNodeInfo): Item? {
        val r = Rect(); n.getBoundsInScreen(r)
        val w = r.width(); val h = r.height()
        if (w <= 0 || h <= 0) return null
        val dp = resources.displayMetrics.density
        val id = n.viewIdResourceName?.lowercase() ?: ""
        val cls = n.className?.toString() ?: ""
        // WhatsApp exposes some avatars as Button / FrameLayout / TextView, so any view type counts here
        val byId = AVATAR_ID.containsMatchIn(id) && w >= 16 * dp && w <= 160 * dp
        // LinkedIn ids are less predictable: fall back to "small square image"
        val isImage = cls.contains("ImageView")
        val bySize = isImage && n.packageName?.toString() == LINKEDIN && w >= 28 * dp && w <= 110 * dp
        if (!byId && !bySize) return null
        val ratio = w.toFloat() / h
        if (ratio >= 0.8f && ratio <= 1.25f) return Item(maskFor(r))
        // Half scrolled off the top or bottom edge: rebuild the whole circle and clip it to what is visible
        val textOnly = cls.contains("TextView") && !id.contains("initials")
        if (byId && !textOnly && h >= 0.25f * w && h < 0.8f * w) {
            val atBottom = (r.top + r.bottom) / 2 > resources.displayMetrics.heightPixels / 2
            val full = if (atBottom) Rect(r.left, r.top, r.right, r.top + w)
                       else Rect(r.left, r.bottom - w, r.right, r.bottom)
            return Item(maskFor(full), r)
        }
        return null
    }

    private fun lineFor(sc: Scan): Item? {
        if (sc.avatarRects.isEmpty()) return null
        val dp = resources.displayMetrics.density
        val centers = sc.avatarRects.map { it.centerX() }.sorted()
        val center = centers[centers.size / 2]
        val scroll = sc.scrollRect
        val top = max(
            (93 * dp).toInt(),
            scroll?.top ?: sc.avatarRects.minOf { it.top }
        )
        val bottom = (scroll?.bottom ?: sc.avatarRects.maxOf { it.bottom })
            .coerceAtMost(resources.displayMetrics.heightPixels)
        if (bottom <= top) return null
        val width = (Prefs.lineThicknessDp(this) * dp).toInt()
        return Item(
            Rect(center - width / 2, top, center + width / 2, bottom),
            Rect(0, top, resources.displayMetrics.widthPixels, bottom)
        )
    }

    private fun tgListLine(sc: Scan, out: MutableList<Item>) {
        val row = sc.tgRows.firstOrNull() ?: return
        val list = sc.listRect ?: row.clip ?: return
        val dp = resources.displayMetrics.density
        val width = (Prefs.lineThicknessDp(this) * dp).toInt()
        val center = row.rect.centerX()
        val top = max((93 * dp).toInt(), list.top)
        val bottom = list.bottom.coerceAtMost(resources.displayMetrics.heightPixels)
        if (bottom > top) {
            out.add(Item(
                Rect(center - width / 2, top, center + width / 2, bottom),
                Rect(list.left, top, list.right, bottom)
            ))
        }
    }

    /** Telegram chat-list row (full width, ~72dp tall): the avatar is a 54dp circle at the left. */
    private fun telegramRow(c: AccessibilityNodeInfo, list: Rect, out: MutableList<Item>) {
        if (!c.isVisibleToUser || c.className?.contains("ViewGroup") != true) return
        val r = Rect(); c.getBoundsInScreen(r)
        val dp = resources.displayMetrics.density
        if (r.width() < resources.displayMetrics.widthPixels * 0.9f) return
        val full = 72 * dp
        val h = r.height()
        val whole = h >= (66 * dp).toInt() && h <= (80 * dp).toInt()
        val partial = h in 1 until (66 * dp).toInt()
        val rowH: Float
        val rowTop: Float
        when {
            whole -> { rowH = h.toFloat(); rowTop = r.top.toFloat() }
            partial && r.top <= list.top + 2 -> { rowH = full; rowTop = r.bottom - full }
            partial && r.bottom >= list.bottom - 2 -> { rowH = full; rowTop = r.top.toFloat() }
            else -> return
        }
        val size = rowH * 0.75f
        val left = r.left + rowH * 0.125f
        val top = rowTop + (rowH - size) / 2f
        out.add(Item(Rect(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt()), r))
    }

    /** A full-width Telegram row; inside a chat these are the messages. */
    private fun telegramMsgRow(c: AccessibilityNodeInfo, out: MutableList<Rect>) {
        if (!c.isVisibleToUser || c.className?.contains("ViewGroup") != true) return
        val r = Rect(); c.getBoundsInScreen(r)
        if (r.width() < resources.displayMetrics.widthPixels * 0.9f) return
        out.add(r)
    }

    // ---------------------------------------------------------------------------------------
    // Telegram inside a chat: avatars are not in the accessibility tree, so derive their
    // fixed left-side position from the visible message rows.
    // ---------------------------------------------------------------------------------------

    private fun tgChat(sc: Scan, out: MutableList<Item>) {
        val list = sc.listRect ?: return
        val dp = resources.displayMetrics.density
        // skip date pills / service lines (too short for an avatar) and rows cut off at the bottom
        val rows = sc.msgRows.filter { it.height() >= 44 * dp && it.bottom < list.bottom - 2 }
        if (rows.isEmpty()) return
        val toolbarBottom = (93 * dp).toInt()
        if (Prefs.maskMode(this) == Prefs.MaskMode.SIMPLE) {
            val top = max(toolbarBottom, rows.minOf { it.top })
            val bottom = rows.maxOf { it.bottom }.coerceAtMost(list.bottom)
            val column = Rect(
                (6 * dp).toInt(),
                top,
                (48 * dp).toInt(),
                bottom
            )
            out.add(Item(barFor(column), Rect(list.left, top, list.right, bottom)))
            return
        }
        for (r in rows) {
            val avatar = dynamicSlot(r, dp)
            val clip = Rect(r.left, max(r.top, toolbarBottom), r.left + (54 * dp).toInt(), r.bottom)
            out.add(Item(maskFor(avatar), clip))
        }
    }

    private fun dynamicSlot(r: Rect, dp: Float): Rect {
        val size = (42 * dp).toInt()
        val left = (r.left + 6 * dp).toInt()
        val bottom = r.bottom - (2.3f * dp).toInt()
        return Rect(left, bottom - size, left + size, bottom)
    }

    private fun maskFor(r: Rect): Rect {
        return if (Prefs.maskMode(this) == Prefs.MaskMode.DYNAMIC) circleFor(r) else barFor(r)
    }

    private fun barFor(r: Rect): Rect {
        val width = (Prefs.lineThicknessDp(this) * resources.displayMetrics.density).toInt()
        val center = (r.left + r.right) / 2
        return Rect(center - width / 2, r.top, center + width / 2, r.bottom)
    }

    private fun circleFor(r: Rect): Rect {
        val size = (Prefs.circleSizeDp(this) * resources.displayMetrics.density).toInt()
        val actual = minOf(size, r.width(), r.height())
        val cx = (r.left + r.right) / 2
        val cy = (r.top + r.bottom) / 2
        return Rect(cx - actual / 2, cy - actual / 2, cx + actual / 2, cy + actual / 2)
    }

    private inner class OverlayView(c: Context) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val resizeHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(139, 92, 246)
        }
        private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(16).toFloat()
        }
        private var items: List<Item> = emptyList()
        private var selection = false
        private var moveMode = false
        private var resizeMode = false
        private var selectionEnd: Pair<Float, Float>? = null
        private val loc = IntArray(2)

        fun setItems(i: List<Item>) { items = i; invalidate() }
        fun setSelectionMode(enabled: Boolean) {
            selection = enabled
            selectionEnd = null
            invalidate()
        }
        fun setMoveMode(enabled: Boolean) {
            moveMode = enabled
            movingAreaIndex = -1
            movingAreaStart = null
            movingTouchOffset = null
            movingPreview = null
            invalidate()
        }
        fun setResizeMode(enabled: Boolean) {
            resizeMode = enabled
            resizingAreaIndex = -1
            resizingTouchOffset = null
            resizingPreview = null
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!selection && !moveMode && !resizeMode) return false
            getLocationOnScreen(loc)
            val screenX = event.x + loc[0]
            val screenY = event.y + loc[1]
            if (moveMode) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val areas = editableDisplayRects()
                        movingAreaIndex = areas.indexOfLast { it.contains(screenX.toInt(), screenY.toInt()) }
                        if (movingAreaIndex >= 0) {
                            movingAreaStart = screenX to screenY
                            val area = areas[movingAreaIndex]
                            movingTouchOffset = (
                                screenX - area.left.toFloat()
                            ) to (
                                screenY - area.top.toFloat()
                            )
                            movingPreview = Rect(areas[movingAreaIndex])
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val index = movingAreaIndex
                        val offset = movingTouchOffset
                        if (index >= 0 && offset != null) {
                            val areas = editableDisplayRects()
                            val area = areas.getOrNull(index) ?: return true
                            movingPreview = manualStoredRect(areaAtTouch(
                                area,
                                screenX, screenY, offset
                            ), index)
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val start = movingAreaStart
                        if (movingAreaIndex >= 0 && start != null) {
                            finishManualAreaMove(
                                movingAreaIndex, start, screenX to screenY
                            )
                        } else {
                            setMoveMode(false)
                            setOverlayTouchable(false)
                            addControl()
                        }
                    }
                }
                return true
            }
            if (resizeMode) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val areas = editableDisplayRects()
                        val handle = dp(48)
                        resizingAreaIndex = areas.indexOfLast {
                            screenX >= it.right - handle && screenX <= it.right &&
                                screenY >= it.bottom - handle && screenY <= it.bottom
                        }
                        if (resizingAreaIndex >= 0) {
                            val area = areas[resizingAreaIndex]
                            resizingTouchOffset = (
                                screenX - area.right.toFloat()
                            ) to (
                                screenY - area.bottom.toFloat()
                            )
                            resizingPreview = Rect(area)
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val index = resizingAreaIndex
                        val offset = resizingTouchOffset
                        if (index >= 0 && offset != null) {
                            val areas = editableDisplayRects()
                            val area = areas.getOrNull(index) ?: return true
                            resizingPreview = manualStoredRect(resizedArea(
                                area,
                                screenX, screenY, offset
                            ), index)
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (resizingAreaIndex >= 0) {
                            finishManualAreaResize()
                        } else {
                            setResizeMode(false)
                            setOverlayTouchable(false)
                            addControl()
                        }
                    }
                }
                return true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    selectionStart = screenX to screenY
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    selectionEnd = screenX to screenY
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val start = selectionStart
                    if (start != null) {
                        selectionEnd = screenX to screenY
                        finishManualSelection(Rect(
                            minOf(start.first, screenX).toInt(),
                            minOf(start.second, screenY).toInt(),
                            maxOf(start.first, screenX).toInt(),
                            maxOf(start.second, screenY).toInt()
                        ))
                    }
                    selectionStart = null
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            paint.color = when (Prefs.boxColor(context)) {
                Prefs.BoxColor.BLACK -> Color.BLACK
                Prefs.BoxColor.PURPLE -> Color.rgb(139, 92, 246)
                Prefs.BoxColor.WHITE -> Color.WHITE
            }
            paint.alpha = (Prefs.boxOpacity(context) * 255 / 100).coerceIn(0, 255)
            getLocationOnScreen(loc)
            val dx = loc[0]; val dy = loc[1]
            for (it in items) {
                canvas.save()
                it.clip?.let { k ->
                    canvas.clipRect(
                        (k.left - dx).toFloat(), (k.top - dy).toFloat(),
                        (k.right - dx).toFloat(), (k.bottom - dy).toFloat()
                    )
                }
                val r = it.rect
                if (it.forceRect && movingAreaIndex >= 0 && movingPreview != null) {
                    val manualIndex = editableRects().indexOfFirst { area -> manualDisplayRect(area) == r }
                    if (manualIndex == movingAreaIndex) continue
                }
                if (it.forceRect && resizingAreaIndex >= 0 && resizingPreview != null) {
                    val manualIndex = editableRects().indexOfFirst { area -> manualDisplayRect(area) == r }
                    if (manualIndex == resizingAreaIndex) continue
                }
                val left = (r.left - dx).toFloat()
                val top = (r.top - dy).toFloat()
                val right = (r.right - dx).toFloat()
                val bottom = (r.bottom - dy).toFloat()
                if (!it.forceRect && Prefs.maskMode(context) == Prefs.MaskMode.DYNAMIC) {
                    canvas.drawOval(RectF(left, top, right, bottom), paint)
                } else {
                    canvas.drawRect(left, top, right, bottom, paint)
                }
                canvas.restore()
            }
            movingPreview?.let { preview ->
                if (movingAreaIndex >= 0) {
                    canvas.drawRect(
                        preview.left - dx.toFloat(), preview.top - dy.toFloat(),
                        preview.right - dx.toFloat(), preview.bottom - dy.toFloat(), paint
                    )
                }
            }
            resizingPreview?.let { preview ->
                if (resizingAreaIndex >= 0) {
                    canvas.drawRect(
                        preview.left - dx.toFloat(), preview.top - dy.toFloat(),
                        preview.right - dx.toFloat(), preview.bottom - dy.toFloat(), paint
                    )
                }
            }
            if (Prefs.isEnabled(context) && Prefs.privacyIndicator(context)) {
                val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(220, 35, 29, 46)
                }
                canvas.drawRoundRect(
                    dp(12).toFloat(), dp(12).toFloat(),
                    dp(106).toFloat(), dp(42).toFloat(),
                    dp(15).toFloat(), dp(15).toFloat(), indicatorPaint
                )
                indicatorPaint.color = Color.rgb(139, 92, 246)
                canvas.drawCircle(dp(27).toFloat(), dp(27).toFloat(), dp(5).toFloat(), indicatorPaint)
                indicatorPaint.color = Color.WHITE
                indicatorPaint.textSize = dp(12).toFloat()
                canvas.drawText("swizy", dp(39).toFloat(), dp(31).toFloat(), indicatorPaint)
            }
            if (resizeMode) {
                val activeIndex = resizingAreaIndex
                val activePreview = resizingPreview
                editableRects().forEachIndexed { index, area ->
                    val displayedArea = manualDisplayRect(area)
                    val handleArea = if (index == activeIndex && activePreview != null) {
                        activePreview
                    } else {
                        displayedArea
                    }
                    val handle = dp(18).toFloat()
                    canvas.drawRoundRect(
                        handleArea.right - handle - dx,
                        handleArea.bottom - handle - dy,
                        handleArea.right.toFloat() - dx,
                        handleArea.bottom.toFloat() - dy,
                        dp(4).toFloat(),
                        dp(4).toFloat(),
                        resizeHandlePaint
                    )
                }
            }
            if (selection) {
                val start = selectionStart
                val end = selectionEnd
                if (start != null && end != null) {
                    val localDx = dx.toFloat()
                    val localDy = dy.toFloat()
                    val r = RectF(
                        minOf(start.first, end.first) - localDx,
                        minOf(start.second, end.second) - localDy,
                        maxOf(start.first, end.first) - localDx,
                        maxOf(start.second, end.second) - localDy
                    )
                    canvas.drawRect(r, guidePaint)
                }
                canvas.drawText("Drag over the area to hide", dp(16).toFloat(), dp(48).toFloat(), textPaint)
            }
            if (moveMode) {
                canvas.drawText("Drag a black box to move it", dp(16).toFloat(), dp(48).toFloat(), textPaint)
            }
            if (resizeMode) {
                canvas.drawText(
                    "Drag the lower-right corner to resize",
                    dp(16).toFloat(), dp(48).toFloat(), textPaint
                )
            }
        }
    }

    private inner class ControlView(c: Context) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val icon get() = controlIcon
        private var menu = false
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var menuOriginX = 0
        private var menuOriginY = 0
        private var touchStartX = 0f
        private var touchStartY = 0f
        private var controlStartX = 0
        private var controlStartY = 0
        private val fade = Runnable { dock() }

        init {
            alpha = idleAlpha()
            postDelayed(fade, 900)
        }

        fun refreshOpacity() {
            if (!menu) alpha = idleAlpha()
            invalidate()
        }

        private fun idleAlpha() = Prefs.controlOpacity(this@AvatarService) / 100f

        private fun wake() {
            removeCallbacks(fade)
            animate().cancel()
            animate().translationX(0f).alpha(1f).setDuration(120).start()
            postDelayed(fade, 2200)
        }

        private fun dock() {
            if (menu) return
            val lp = layoutParams as? WindowManager.LayoutParams
            val onLeft = (lp?.x ?: 0) < resources.displayMetrics.widthPixels / 2
            animate().translationX(if (onLeft) -dp(20).toFloat() else dp(20).toFloat())
                .alpha(idleAlpha()).setDuration(300).start()
        }

        override fun onDraw(canvas: Canvas) {
            val ballX: Float = if (menu) (width - dp(29)).toFloat() else width / 2f
            val ballY: Float = height / 2f
            if (menu) {
                paint.color = Color.argb(235, 35, 29, 46)
                canvas.drawRoundRect(
                    0f, 0f, (width - dp(58)).toFloat(), height.toFloat(),
                    dp(14).toFloat(), dp(14).toFloat(), paint
                )
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.LEFT
                paint.textSize = dp(14).toFloat()
                canvas.drawText("Add", dp(16).toFloat(), dp(42).toFloat(), paint)
                canvas.drawText(
                    if (profileCreationId != null) "Save profile" else "Add profile",
                    dp(16).toFloat(), dp(84).toFloat(), paint
                )
                canvas.drawText("Remove last added area", dp(16).toFloat(), dp(126).toFloat(), paint)
                canvas.drawText("Remove all black boxes", dp(16).toFloat(), dp(168).toFloat(), paint)
                canvas.drawText("Undo", dp(16).toFloat(), dp(210).toFloat(), paint)
                canvas.drawText("Redo", dp(16).toFloat(), dp(252).toFloat(), paint)
                canvas.drawText(
                    if (Prefs.manualHideVisible(this@AvatarService)) "Hide all black boxes"
                    else "Show all black boxes",
                    dp(16).toFloat(), dp(294).toFloat(), paint
                )
                canvas.drawText("Move black boxes", dp(16).toFloat(), dp(336).toFloat(), paint)
                canvas.drawText("Resize black boxes", dp(16).toFloat(), dp(378).toFloat(), paint)
                canvas.drawText("Close", dp(16).toFloat(), dp(420).toFloat(), paint)
            }
            paint.color = purple()
            canvas.drawCircle(ballX, ballY, dp(26).toFloat(), paint)
            if (icon != null) {
                canvas.drawBitmap(
                    icon, null,
                    RectF(ballX - dp(15), ballY - dp(15), ballX + dp(15), ballY + dp(15)),
                    paint
                )
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && menu) {
                collapseMenu()
                wake()
                return true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wake()
                    removeCallbacks(fade)
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    downX = event.rawX
                    downY = event.rawY
                    val start = layoutParams as WindowManager.LayoutParams
                    controlStartX = start.x
                    controlStartY = start.y
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!menu) {
                        val lp = layoutParams as WindowManager.LayoutParams
                        val totalDx = event.rawX - touchStartX
                        val totalDy = event.rawY - touchStartY
                        if (kotlin.math.hypot(totalDx.toDouble(), totalDy.toDouble()) > dp(4)) {
                            dragging = true
                        }
                        lp.x = (controlStartX + totalDx).toInt().coerceIn(
                            0, resources.displayMetrics.widthPixels - width
                        )
                        lp.y = (controlStartY + totalDy).toInt().coerceIn(
                            0, resources.displayMetrics.heightPixels - height
                        )
                        wm.updateViewLayout(this, lp)
                        getSharedPreferences("avb", Context.MODE_PRIVATE).edit()
                            .putInt("control_x", lp.x).putInt("control_y", lp.y).apply()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging && !menu) {
                        dragging = false
                        postDelayed(fade, 2200)
                    } else if (!menu) {
                        menu = true
                        val lp = layoutParams as WindowManager.LayoutParams
                        menuOriginX = lp.x
                        menuOriginY = lp.y
                        lp.width = dp(250)
                        lp.height = dp(457)
                        lp.x = (lp.x - dp(192)).coerceAtLeast(0)
                        lp.y = (menuOriginY - (lp.height - dp(58)) / 2).coerceIn(
                            0, resources.displayMetrics.heightPixels - lp.height
                        )
                        translationX = 0f
                        lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        wm.updateViewLayout(this, lp)
                        isFocusableInTouchMode = true
                        requestFocus()
                        invalidate()
                    } else {
                        if (event.x >= width - dp(58)) {
                            collapseMenu()
                            wake()
                            return true
                        }
                        val y = event.y
                        when {
                            y < dp(58) -> beginManualHideSelection()
                            y < dp(100) -> {
                                if (profileCreationId != null) saveProfileCreation()
                                else beginProfileCreation()
                            }
                            y < dp(142) -> removeLastManualArea()
                            y < dp(184) -> removeAllManualAreas()
                            y < dp(226) -> undoManualChange()
                            y < dp(268) -> redoManualChange()
                            y < dp(310) -> toggleManualAreas()
                            y < dp(352) -> beginManualAreaMove()
                            y < dp(394) -> beginManualAreaResize()
                            else -> closeManualSession()
                        }
                        if (control != null && manualSessionActive) {
                            collapseMenu()
                        }
                        wake()
                    }

                    return true
                }
            }
            return true
        }

        private fun collapseMenu() {
            menu = false
            val lp = layoutParams as WindowManager.LayoutParams
            lp.width = dp(58)
            lp.height = dp(58)
            lp.x = menuOriginX.coerceIn(0, resources.displayMetrics.widthPixels - dp(58))
            lp.y = menuOriginY.coerceIn(0, resources.displayMetrics.heightPixels - dp(58))
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            translationX = 0f
            wm.updateViewLayout(this, lp)
            clearFocus()
            invalidate()
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (menu && event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    collapseMenu()
                    wake()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        private fun purple() = Color.rgb(139, 92, 246)
    }
}
