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
    private var closeTarget: CloseTargetView? = null
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
    private var lastEventPackage = ""
    private var cachedProfileRects: List<Rect>? = null
    private var cachedProfilePackage = ""
    private var cachedManualRects: List<Rect>? = null
    private var cachedHasProfile = false
    private var cachedHasProfilePackage = ""
    private var timeSpeaker: TextToSpeech? = null
    private var timeSpeakerReady = false
    private var configuredTtsLocale: Locale? = null
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
    private var lastRenderKey: String? = null
    private val controlIcon by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.swizy_logo)?.let { source ->
            val scaled = android.graphics.Bitmap.createScaledBitmap(source, dp(128), dp(128), true)
            if (scaled !== source) source.recycle()
            scaled
        }
    }
    private val scan = Runnable { pending = false; doScan() }
    // safety net: rescan every second so a missed event or a dropped overlay recovers by itself
    private val tick = object : Runnable {
        override fun run() {
            if (Prefs.isEnabled(this@AvatarService)) {
                doScan()
                handler.postDelayed(this, if (Prefs.performanceMode(this@AvatarService)) 1500 else 600)
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
        hideCloseTarget()
        handler.removeCallbacks(headsetLongPress)
        handler.removeCallbacks(speechTimeout)
        timeSpeaker?.stop()
        timeSpeaker?.shutdown()
        timeSpeaker = null
        timeSpeakerReady = false
        configuredTtsLocale = null
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
        control?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        control = null
        overlay?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        overlay = null
        lastRenderKey = null
        lastEventPackage = ""
        lastKeyboardTop = 0
        cachedKeyboardTop = null
        invalidateProfileCache()
        ensureOverlay()
        if (manualSessionActive) {
            addControl()
        }
        doScan()
    }

    private var lastKeyboardTop = 0

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (!Prefs.isEnabled(this)) return
        val eventPkg = e?.packageName?.toString() ?: ""
        val kbTop = keyboardTop() ?: 0
        if (kbTop != lastKeyboardTop) {
            lastKeyboardTop = kbTop
            scheduleScan(0L)
            return
        }
        // Event package names can arrive late during rapid app switching. Keep them as hints;
        // the focused application window is resolved again in doScan().
        if (eventPkg.isNotBlank() &&
            eventPkg != applicationContext.packageName &&
            eventPkg != "com.android.systemui") {
            lastEventPackage = eventPkg
        }
        scheduleScan(55L)
    }

    private fun scheduleScan(delay: Long) {
        pending = true
        handler.removeCallbacks(scan)
        if (delay <= 0L) handler.post(scan) else handler.postDelayed(scan, delay)
    }

    fun refresh() {
        applyHeadsetFilter()
        handler.removeCallbacks(scan)
        handler.removeCallbacks(tick)
        pending = false
        lastRenderKey = null
        invalidateProfileCache()
        control?.refreshOpacity()
        if (Prefs.isEnabled(this)) {
            doScan()
            handler.postDelayed(
                tick,
                if (Prefs.performanceMode(this)) 1500 else 600
            )
        } else {
            overlay?.setItems(emptyList())
            removeControl()
        }
    }

    /** Says the time out loud. Also used by the "Say the time now" button in Settings. */
    fun announceCurrentTime() {
        if (speakingTime) return
        val locale = configuredTtsLocale ?: Locale.US
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
            configureSystemVoice(tts)
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

    private fun configureSystemVoice(tts: TextToSpeech) {
        val selected = tts.voice ?: tts.defaultVoice
        if (selected == null) {
            Log.w(TAG, "Android has no configured TTS voice")
            return
        }
        configuredTtsLocale = selected.locale
        val result = tts.setLanguage(selected.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.w(TAG, "configured TTS language is unavailable: ${selected.locale}")
        }
        tts.voice = selected
        Log.d(TAG, "using Android configured TTS voice ${selected.name} (${selected.locale})")
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
            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            val oriKey = orientationKey()
            x = prefs.getInt("control_x_$oriKey", prefs.getInt("control_x", screenW - dp(70)))
                .coerceIn(0, (screenW - dp(58)).coerceAtLeast(0))
            y = prefs.getInt("control_y_$oriKey", prefs.getInt("control_y", screenH / 2))
                .coerceIn(0, (screenH - dp(58)).coerceAtLeast(0))
        }

        try {
            wm.addView(v, lp)
            control = v
        } catch (ex: Exception) {
            Log.w(TAG, "addControl failed", ex)
        }
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

    private fun saveManualRects(rects: List<Rect>) {
        Prefs.saveManualHideRects(this, manualLayoutId(), orientationKey(), rects)
        cachedManualRects = null
    }

    private fun saveProfileRects(rects: List<Rect>) {
        Prefs.saveProfileHideRects(this, profileId(), orientationKey(), rects)
        cachedProfileRects = null
        cachedProfilePackage = ""
    }

    private var cachedKeyboardTop: Int? = null
    private var cachedKeyboardTopTime = 0L

    private fun keyboardTop(): Int? {
        // Cache for 100ms so multiple manualDisplayRect calls in one scan don't re-query windows
        val now = System.currentTimeMillis()
        if (cachedKeyboardTop != null && now - cachedKeyboardTopTime < 100) return cachedKeyboardTop
        cachedKeyboardTopTime = now
        cachedKeyboardTop = windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { window ->
                Rect().also { bounds ->
                    runCatching { window.getBoundsInScreen(bounds) }.getOrNull()
                }
            }
            .map { it.top }
            .filter { it > 0 }
            .minOrNull()
        return cachedKeyboardTop
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
        val existing = Prefs.profileForPackage(this, packageName)
        val profile = if (hadProfile) existing else Prefs.addProfile(
            this,
            "Profile ${packageName.substringAfterLast('.')}",
            packageName
        )
        profileCreationId = profile.id
        profileCreationNew = !hadProfile
        manualSessionProfileId = profile.id
        profileCaptureRects = if (hadProfile) {
            Prefs.profileHideRects(this, profile.id, orientationKey())
                .map { Rect(it) }.toMutableList()
        } else mutableListOf()
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
        hideCloseTarget()
    }

    private fun showCloseTarget() {
        if (closeTarget != null) return
        val target = CloseTargetView(this)
        val lp = WindowManager.LayoutParams(
            dp(76), dp(76), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(24) }
        runCatching { wm.addView(target, lp); closeTarget = target }
    }

    private fun hideCloseTarget() {
        closeTarget?.let { runCatching { wm.removeView(it) } }
        closeTarget = null
    }

    private fun isOverCloseTarget(rawY: Float): Boolean =
        rawY >= resources.displayMetrics.heightPixels - dp(112)

    private fun closeManualSession() {
        cancelProfileCreation()
        manualSessionActive = false
        manualSessionProfileId = null
        removeControl()
        doScan()
    }

    private inner class CloseTargetView(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            p.color = Color.argb(225, 29, 23, 43)
            canvas.drawCircle(cx, cy, dp(30).toFloat(), p)
            p.color = Color.rgb(139, 92, 246)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(2).toFloat()
            canvas.drawCircle(cx, cy, dp(30).toFloat(), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(3).toFloat()
            p.strokeCap = Paint.Cap.ROUND
            val d = dp(9).toFloat()
            canvas.drawLine(cx-d, cy-d, cx+d, cy+d, p)
            canvas.drawLine(cx+d, cy-d, cx-d, cy+d, p)
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.textSize = dp(10).toFloat()
            p.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            canvas.drawText(menuLabel("Close"), cx, height - dp(3).toFloat(), p)
        }
    }

    private fun menuLabel(value: String): String {
        if (getSharedPreferences("avb", Context.MODE_PRIVATE).getString("app_language", "ar") != "ar") return value
        return when (value) {
            "Add hidden area" -> "إضافة منطقة إخفاء"
            "Add profile" -> "إضافة ملف"
            "Edit profile" -> "تعديل الملف"
            "Save profile" -> "حفظ الملف"
            "Cancel profile" -> "إلغاء التعديل"
            "Remove last added area" -> "إزالة آخر منطقة"
            "Remove all black boxes" -> "إزالة كل مناطق الإخفاء"
            "Hide all black boxes" -> "إخفاء كل المناطق"
            "Show all black boxes" -> "إظهار كل المناطق"
            "Move black boxes" -> "تحريك مناطق الإخفاء"
            "Resize black boxes" -> "تغيير حجم مناطق الإخفاء"
            "Close" -> "إغلاق"
            else -> value
        }
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

    private fun invalidateProfileCache() {
        cachedProfileRects = null
        cachedProfilePackage = ""
        cachedManualRects = null
        cachedHasProfile = false
        cachedHasProfilePackage = ""
    }

    private fun hasProfileCached(): Boolean {
        val pkg = activePackage
        if (cachedHasProfilePackage == pkg) return cachedHasProfile
        cachedHasProfilePackage = pkg
        cachedHasProfile = Prefs.hasProfileForPackage(this, pkg)
        return cachedHasProfile
    }

    private fun profileRectsCached(): List<Rect> {
        val pkg = activePackage
        if (cachedProfilePackage == pkg && cachedProfileRects != null) return cachedProfileRects!!
        cachedProfilePackage = pkg
        cachedProfileRects = if (profileCreationId != null) profileCaptureRects
            else Prefs.profileHideRects(this, profileId(), orientationKey())
        return cachedProfileRects!!
    }

    private fun manualRectsCached(): List<Rect> {
        if (cachedManualRects != null) return cachedManualRects!!
        cachedManualRects = Prefs.manualHideRects(this, manualLayoutId(), orientationKey())
        return cachedManualRects!!
    }

    private fun doScan() {
        try {
            ensureOverlay()
            // Resolve the focused app every scan. Accessibility events are only hints and can
            // be stale when the user leaves and re-enters an app quickly.
            val detectedPackage = foregroundPackage()
                ?: lastEventPackage.takeIf {
                    it.isNotBlank() &&
                    it != applicationContext.packageName &&
                    it != "com.android.systemui"
                }
                ?: ""
            if (detectedPackage != activePackage) {
                activePackage = detectedPackage
                invalidateProfileCache()
                if (hasProfileCached()) Prefs.setManualHideVisible(this, true)
                Log.d(TAG, "foreground package changed to $activePackage")
            }
            val out = ArrayList<Item>()
            val hasActiveProfile = hasProfileCached()
            val currentProfileRects = profileRectsCached()
            val currentManualRects = manualRectsCached()
            val legacyProfileRects = if (hasActiveProfile) {
                Prefs.manualHideRects(this, profileId(), orientationKey())
            } else {
                emptyList()
            }
            if (hasActiveProfile && currentProfileRects.isEmpty() && legacyProfileRects.isNotEmpty()) {
                saveProfileRects(legacyProfileRects)
                Prefs.clearManualHideRects(this, profileId(), orientationKey())
                cachedProfileRects = null
                cachedProfilePackage = ""
            }
            val manualVisible = Prefs.manualHideVisible(this)
            val maskMode = Prefs.maskMode(this)
            val isTarget = activePackage in TARGETS
            // Short-circuit: no profile, no manual boxes, not a target app → nothing to draw
            if (!hasActiveProfile &&
                (currentManualRects.isEmpty() || !manualVisible) &&
                !isTarget &&
                profileCreationId == null
            ) {
                if (lastRenderKey != "empty") {
                    lastRenderKey = "empty"
                    overlay?.setItems(emptyList())
                }
                return
            }
            val manualItems = if (manualVisible && currentManualRects.isNotEmpty()) {
                currentManualRects.map { Item(manualDisplayRect(it), forceRect = true) }
            } else emptyList()
            val profileItems = if (manualVisible &&
                (hasActiveProfile || profileCreationId != null) &&
                currentProfileRects.isNotEmpty()
            ) {
                currentProfileRects.map { Item(manualDisplayRect(it), forceRect = true) }
            } else emptyList()
            out.addAll(manualItems)
            out.addAll(profileItems)
            if (maskMode != Prefs.MaskMode.NONE) {
              for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = (if (Build.VERSION.SDK_INT >= 33)
                    w.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID)
                else w.root) ?: continue
                val packageName = root.packageName?.toString() ?: continue
                if (packageName in TARGETS) {
                    val sc = Scan()
                    walk(root, sc, 0)
                    if (maskMode == Prefs.MaskMode.SIMPLE) {
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
            val renderKey = uniqueItems.joinToString("|") {
                "${it.rect.left},${it.rect.top},${it.rect.right},${it.rect.bottom}," +
                "${it.clip?.left},${it.clip?.top},${it.clip?.right},${it.clip?.bottom},${it.forceRect}"
            }
            if (renderKey != lastRenderKey) {
                lastRenderKey = renderKey
                overlay?.setItems(uniqueItems)
            }
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
        private var pressedMenuIndex = -1
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
            translationX = 0f
            translationY = 0f
            animate().alpha(1f).setDuration(120).start()
            postDelayed(fade, 2200)
        }

        private fun dock() {
            if (menu || dragging || !isAttachedToWindow) return
            val lp = layoutParams as? WindowManager.LayoutParams ?: return
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            val left = lp.x + width / 2 < screenWidth / 2
            val top = lp.y + height / 2 < screenHeight / 2
            val targetX = if (left) 0 else (screenWidth - width).coerceAtLeast(0)
            val targetY = if (top) 0 else (screenHeight - height).coerceAtLeast(0)
            animate().cancel()
            animate()
                .translationX((targetX - lp.x).toFloat())
                .translationY((targetY - lp.y).toFloat())
                .alpha(idleAlpha())
                .setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    if (!isAttachedToWindow || menu || dragging) return@withEndAction
                    lp.x = targetX
                    lp.y = targetY
                    translationX = 0f
                    translationY = 0f
                    runCatching { wm.updateViewLayout(this, lp) }
                    getSharedPreferences("avb", Context.MODE_PRIVATE).edit()
                        .putInt("control_x_${this@AvatarService.orientationKey()}", lp.x)
                        .putInt("control_y_${this@AvatarService.orientationKey()}", lp.y)
                        .apply()
                    animate().translationX(if (left) -dp(20).toFloat() else dp(20).toFloat())
                        .setDuration(140).start()
                }
                .start()
        }

        override fun onDraw(canvas: Canvas) {
            val ballX = if (menu) (width - dp(29)).toFloat() else width / 2f
            val ballY = height / 2f
            if (menu) {
                val panelRight = (width - dp(58)).toFloat()
                val panelRect = RectF(0f, 0f, panelRight, height.toFloat())
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(29, 23, 43)
                canvas.drawRoundRect(panelRect, dp(16).toFloat(), dp(16).toFloat(), paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1).toFloat()
                paint.color = Color.rgb(105, 79, 151)
                canvas.drawRoundRect(
                    RectF(dp(1).toFloat(), dp(1).toFloat(), panelRight - dp(1), height - dp(1).toFloat()),
                    dp(16).toFloat(), dp(16).toFloat(), paint
                )
                paint.style = Paint.Style.FILL

                // A branded first row: the same transparent mascot sits on a plum surface.
                paint.color = if (pressedMenuIndex == 0) Color.rgb(82, 58, 124)
                    else Color.rgb(62, 43, 94)
                canvas.drawRoundRect(
                    RectF(dp(7).toFloat(), dp(6).toFloat(), panelRight - dp(7), dp(52).toFloat()),
                    dp(12).toFloat(), dp(12).toFloat(), paint
                )
                val menuIcon = icon
                if (menuIcon != null) {
                    canvas.drawBitmap(
                        menuIcon, null,
                        RectF(dp(13).toFloat(), dp(12).toFloat(), dp(43).toFloat(), dp(42).toFloat()),
                        paint
                    )
                }
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.LEFT
                paint.typeface = android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD
                )
                paint.textSize = dp(13).toFloat()
                canvas.drawText(menuLabel("Add hidden area"), dp(51).toFloat(), dp(33).toFloat(), paint)

                val hasProfile = Prefs.hasProfileForPackage(this@AvatarService, activePackage)
                val rows = buildList {
                    if (profileCreationId != null) {
                        add("Save profile")
                        add("Cancel profile")
                    } else {
                        add(if (hasProfile) "Edit profile" else "Add profile")
                    }
                    add("Remove last added area")
                    add("Remove all black boxes")
                    add(if (Prefs.manualHideVisible(this@AvatarService)) "Hide all black boxes" else "Show all black boxes")
                    add("Move black boxes")
                    add("Resize black boxes")
                    add("Close")
                }
                val rowTop = dp(58)
                val rowHeight = dp(44)
                rows.forEachIndexed { index, label ->
                    val top = rowTop + index * rowHeight
                    val bottom = top + rowHeight - dp(2)
                    if (pressedMenuIndex == index + 1) {
                        paint.color = Color.rgb(68, 49, 101)
                        canvas.drawRoundRect(
                            RectF(dp(7).toFloat(), top.toFloat(), panelRight - dp(7), bottom.toFloat()),
                            dp(9).toFloat(), dp(9).toFloat(), paint
                        )
                    }
                    paint.color = if (index == rows.lastIndex) Color.rgb(213, 190, 255)
                        else Color.rgb(238, 233, 247)
                    paint.textSize = dp(12).toFloat()
                    paint.typeface = android.graphics.Typeface.create(
                        "sans-serif", android.graphics.Typeface.NORMAL
                    )
                    canvas.drawText(menuLabel(label), dp(16).toFloat(), (top + dp(27)).toFloat(), paint)
                    if (index < rows.lastIndex) {
                        paint.color = Color.argb(55, 190, 170, 225)
                        canvas.drawLine(
                            dp(14).toFloat(), bottom.toFloat(),
                            panelRight - dp(14), bottom.toFloat(), paint
                        )
                    }
                }
            }

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(139, 92, 246)
            canvas.drawCircle(ballX, ballY, dp(27).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.rgb(205, 183, 255)
            canvas.drawCircle(ballX, ballY, dp(27).toFloat(), paint)
            paint.style = Paint.Style.FILL
            val ballIcon = icon
            if (ballIcon != null) {
                canvas.drawBitmap(
                    ballIcon, null,
                    RectF(ballX - dp(16), ballY - dp(16), ballX + dp(16), ballY + dp(16)),
                    paint
                )
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && menu) {
                pressedMenuIndex = -1
                collapseMenu()
                wake()
                return true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (menu && event.x < width - dp(58)) {
                        pressedMenuIndex = ((event.y - dp(58)) / dp(44)).toInt() + 1
                        if (event.y < dp(58)) pressedMenuIndex = 0
                        invalidate()
                    }
                    wake()
                    animate().cancel()
                    translationX = 0f
                    translationY = 0f
                    alpha = 1f
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
                            if (!dragging) showCloseTarget()
                            dragging = true
                        }
                        lp.x = (controlStartX + totalDx).toInt().coerceIn(
                            0, (resources.displayMetrics.widthPixels - width).coerceAtLeast(0)
                        )
                        lp.y = (controlStartY + totalDy).toInt().coerceIn(
                            0, (resources.displayMetrics.heightPixels - height).coerceAtLeast(0)
                        )
                        // Keep the drag path lightweight: persist only once on release.
                        wm.updateViewLayout(this, lp)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging && !menu) {
                        val lp = layoutParams as WindowManager.LayoutParams
                        dragging = false
                        hideCloseTarget()
                        if (isOverCloseTarget(event.rawY)) {
                            closeManualSession()
                            return true
                        }
                        getSharedPreferences("avb", Context.MODE_PRIVATE).edit()
                            .putInt("control_x_${this@AvatarService.orientationKey()}", lp.x)
                            .putInt("control_y_${this@AvatarService.orientationKey()}", lp.y)
                            .apply()
                        // A manual release is a true stop: keep the exact dropped position.
                        // The delayed idle action may dock the ball later when it needs to hide.
                        animate().cancel()
                        translationX = 0f
                        translationY = 0f
                        postDelayed(fade, 2200)
                    } else if (!menu) {
                        menu = true
                        val lp = layoutParams as WindowManager.LayoutParams
                        menuOriginX = lp.x
                        menuOriginY = lp.y
                        lp.width = dp(250)
                        lp.height = dp(58 + (if (profileCreationId != null) 8 else 7) * 44)
                        lp.x = (lp.x - dp(192)).coerceAtLeast(0)
                        lp.y = (menuOriginY - (lp.height - dp(58)) / 2).coerceIn(
                            0, (resources.displayMetrics.heightPixels - lp.height).coerceAtLeast(0)
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
                        pressedMenuIndex = -1
                        when {
                            y < dp(58) -> beginManualHideSelection()
                            y < dp(102) -> {
                                if (profileCreationId != null) saveProfileCreation()
                                else beginProfileCreation()
                            }
                            profileCreationId != null && y < dp(146) -> cancelProfileCreation().also { doScan() }
                            y < dp(if (profileCreationId != null) 190 else 146) -> removeLastManualArea()
                            y < dp(if (profileCreationId != null) 234 else 190) -> removeAllManualAreas()
                            y < dp(if (profileCreationId != null) 278 else 234) -> toggleManualAreas()
                            y < dp(if (profileCreationId != null) 322 else 278) -> beginManualAreaMove()
                            y < dp(if (profileCreationId != null) 366 else 322) -> beginManualAreaResize()
                            else -> closeManualSession()
                        }
                        if (control === this && manualSessionActive) {
                            collapseMenu()
                        }
                        if (control === this) wake()
                    }

                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressedMenuIndex = -1
                    if (dragging) hideCloseTarget()
                    dragging = false
                    invalidate()
                    if (control === this) postDelayed(fade, 900)
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
