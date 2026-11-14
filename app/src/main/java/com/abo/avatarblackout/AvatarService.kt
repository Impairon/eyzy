package com.abo.avatarblackout

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

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

        // --- Telegram in-chat pixel check (tune these if it misses or over-blacks) ---
        private const val EDGE_DIST = 45        // colour jump (sum of R,G,B) across the circle's edge that counts as "an edge"
        private const val EDGE_SHARE = 0.55f    // share of the 24 edge samples that must show that jump
        private const val RECHECK_MS = 1500L    // look again at least this often (photos can load late)
        private const val SHOT_GAP_MS = 450L    // Android refuses screenshots closer together than this
    }

    /** One black circle: where to draw it, and an optional area it must not leave (for half-visible rows). */
    private class Item(val rect: Rect, val clip: Rect? = null)

    /** Everything found in one app window. Telegram guesses are kept apart so they can be dropped or replaced. */
    private class Scan {
        val items = ArrayList<Item>()
        val tgRows = ArrayList<Item>()   // chat-list avatar guesses
        val msgRows = ArrayList<Rect>()  // message rows inside a chat
        var listRect: Rect? = null
        var hasInput = false
    }

    private class Decision(val top: Int, val h: Int, val avatar: Boolean)

    private lateinit var wm: WindowManager
    private var overlay: OverlayView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var pending = false
    private val scan = Runnable { pending = false; doScan() }
    // safety net: rescan every second so a missed event or a dropped overlay recovers by itself
    private val tick = object : Runnable {
        override fun run() {
            if (Prefs.isEnabled(this@AvatarService)) {
                doScan()
                handler.postDelayed(this, 1000)
            }
        }
    }

    @Volatile private var tgDecisions: List<Decision> = emptyList()
    @Volatile private var tgAnalyzedAt = 0L
    @Volatile private var shotBusy = false
    private var lastShot = 0L

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addOverlay()
        refresh()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacks(tick)
        worker.shutdownNow()
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) tgDecisions = emptyList()
        if (!Prefs.isEnabled(this) || pending) return
        pending = true
        handler.post(scan)
    }

    fun refresh() {
        handler.removeCallbacks(scan)
        handler.removeCallbacks(tick)
        pending = false
        tgDecisions = emptyList()
        if (Prefs.isEnabled(this)) {
            doScan()
            handler.postDelayed(tick, 1000)
        } else {
            overlay?.setItems(emptyList())
        }
    }

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

    private fun doScan() {
        try {
            ensureOverlay()
            val out = ArrayList<Item>()
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = (if (Build.VERSION.SDK_INT >= 33)
                    w.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID)
                else w.root) ?: continue
                val packageName = root.packageName?.toString() ?: continue
                if (packageName in TARGETS && Prefs.mode(this, packageName) != Prefs.MaskMode.OFF) {
                    val sc = Scan()
                    walk(root, sc, 0)
                    out.addAll(sc.items)
                    // Telegram: no message box on screen = chat list (position guess);
                    // message box on screen = inside a chat (pixel check)
                    if (!sc.hasInput) out.addAll(sc.tgRows) else tgChat(sc, out, packageName)
                }
            }
            // if a big container and a bigger-than-half child both matched, keep the child
            val finalItems = out.filter { a ->
                out.none { b ->
                    b !== a && a.rect != b.rect && a.rect.contains(b.rect) &&
                        b.rect.area() * 2 >= a.rect.area()
                }
            }
            overlay?.setItems(finalItems)
        } catch (e: Exception) {
            Log.w(TAG, "scan failed", e)
        }
    }

    private fun walk(n: AccessibilityNodeInfo, sc: Scan, depth: Int) {
        if (depth > 45) return
        if (n.className?.contains("EditText") == true) sc.hasInput = true
        if (n.isVisibleToUser) avatarItem(n)?.let { sc.items.add(it) }
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
        if (ratio >= 0.8f && ratio <= 1.25f) return Item(r)
        // Half scrolled off the top or bottom edge: rebuild the whole circle and clip it to what is visible
        val textOnly = cls.contains("TextView") && !id.contains("initials")
        if (byId && !textOnly && h >= 0.25f * w && h < 0.8f * w) {
            val atBottom = (r.top + r.bottom) / 2 > resources.displayMetrics.heightPixels / 2
            val full = if (atBottom) Rect(r.left, r.top, r.right, r.top + w)
                       else Rect(r.left, r.bottom - w, r.right, r.bottom)
            return Item(full, r)
        }
        return null
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
    // Telegram inside a chat: avatars are not in the accessibility tree, so we look at the pixels.
    // ---------------------------------------------------------------------------------------

    /** Where Telegram draws the sender's avatar: bottom-left of the message row, 42dp wide. */
    private fun slot(r: Rect, dp: Float): Rect {
        val size = (42 * dp).toInt()
        val left = (6 * dp).toInt()
        val bottom = r.bottom - (2.3f * dp).toInt()
        return Rect(left, bottom - size, left + size, bottom)
    }

    private fun tgChat(sc: Scan, out: MutableList<Item>, packageName: String) {
        val list = sc.listRect ?: return
        val dp = resources.displayMetrics.density
        // skip date pills / service lines (too short for an avatar) and rows cut off at the bottom
        val rows = sc.msgRows.filter { it.height() >= 44 * dp && it.bottom < list.bottom - 2 }
        if (rows.isEmpty()) return
        val dec = tgDecisions

        // all rows move together when scrolling: find the most common vertical shift since the last look
        val votes = HashMap<Int, Int>()
        for (r in rows) for (d in dec) if (d.h == r.height()) {
            val s = r.top - d.top
            votes[s] = (votes[s] ?: 0) + 1
        }
        val shift = votes.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { abs(it.key) })
            .firstOrNull()?.key ?: 0

        var needLook = SystemClock.uptimeMillis() - tgAnalyzedAt > RECHECK_MS
        val toolbarBottom = (93 * dp).toInt()
        for (r in rows) {
            val m = dec.firstOrNull { it.h == r.height() && it.top + shift == r.top }
            if (m == null) { needLook = true; continue }
            if (m.avatar) {
                val avatar = slot(r, dp)
                val clip = if (Prefs.mode(this, packageName) == Prefs.MaskMode.EDGE) {
                    // Keep the side mask inside the avatar column; never paint over the message bubble.
                    Rect(r.left, max(r.top, toolbarBottom), r.left + (54 * dp).toInt(), r.bottom)
                } else {
                    Rect(r.left, max(r.top, toolbarBottom), r.right, r.bottom)
                }
                out.add(Item(avatar, clip))
            }
        }
        if (needLook) requestShot(rows)
    }

    private fun requestShot(rows: List<Rect>) {
        if (Build.VERSION.SDK_INT < 30 || shotBusy) return
        val now = SystemClock.uptimeMillis()
        if (now - lastShot < SHOT_GAP_MS) return
        shotBusy = true
        lastShot = now
        val snap = rows.map { Rect(it) }
        val dp = resources.displayMetrics.density
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    var decisions: List<Decision>? = null
                    try {
                        val hb = result.hardwareBuffer
                        val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hb.close()
                        hw?.recycle()
                        if (bmp != null) {
                            decisions = analyze(bmp, snap, dp)
                            bmp.recycle()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "analysis failed", e)
                    }
                    val d = decisions
                    handler.post {
                        shotBusy = false
                        if (d != null) {
                            tgDecisions = d
                            tgAnalyzedAt = SystemClock.uptimeMillis()
                        }
                        doScan()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "screenshot failed: $errorCode")
                    handler.post { shotBusy = false }
                }
            })
        } catch (e: Exception) {
            shotBusy = false
            Log.w(TAG, "takeScreenshot not available", e)
        }
    }

    private fun analyze(bmp: Bitmap, rows: List<Rect>, dp: Float): List<Decision>? {
        val sx = bmp.width.toFloat() / resources.displayMetrics.widthPixels
        // protected chats come back completely black: don't trust those
        var dark = 0; var total = 0
        for (i in 1..4) for (j in 1..4) {
            val x = bmp.width * i / 5
            val y = (bmp.height * (0.15f + 0.15f * j)).toInt()
            total++
            if ((bmp.getPixel(x, y) and 0xFFFFFF) == 0) dark++
        }
        if (dark >= total - 1) return null

        val result = ArrayList<Decision>()
        for (r in rows) {
            result.add(Decision(r.top, r.height(), looksLikeAvatar(bmp, slot(r, dp), sx)))
        }
        return result
    }

    private fun colorDist(a: Int, b: Int) =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))

    private fun looksLikeAvatar(b: Bitmap, s: Rect, sx: Float): Boolean {
        val cx = (s.left + s.right) / 2f * sx
        val cy = (s.top + s.bottom) / 2f * sx
        val rad = s.width() / 2f * sx
        fun inside(x: Int, y: Int) = x in 0 until b.width && y in 0 until b.height

        // 1) solid black inside the circle = our own overlay is already covering an avatar here
        var black = 0; var n = 0
        for (k in 0 until 12) {
            val a = k * 2 * Math.PI / 12
            val rr = if (k % 2 == 0) rad * 0.45f else rad * 0.75f
            val x = (cx + rr * cos(a)).toInt(); val y = (cy + rr * sin(a)).toInt()
            if (!inside(x, y)) continue
            n++
            if ((b.getPixel(x, y) and 0xFFFFFF) == 0) black++
        }
        if (n >= 8 && black >= n - 1) return true

        // 2) a hard circular edge: the colour just inside the rim differs from just outside it
        var valid = 0; var edges = 0
        val gap = 4f * sx
        for (k in 0 until 24) {
            val a = k * 2 * Math.PI / 24
            val xi = (cx + (rad - gap) * cos(a)).toInt(); val yi = (cy + (rad - gap) * sin(a)).toInt()
            val xo = (cx + (rad + gap) * cos(a)).toInt(); val yo = (cy + (rad + gap) * sin(a)).toInt()
            if (!inside(xi, yi) || !inside(xo, yo)) continue
            valid++
            if (colorDist(b.getPixel(xi, yi), b.getPixel(xo, yo)) > EDGE_DIST) edges++
        }
        val avatar = valid >= 12 && edges >= valid * EDGE_SHARE
        Log.d(TAG, "slot y=${s.top} edges=$edges/$valid black=$black/$n -> avatar=$avatar")
        return avatar
    }

    private class OverlayView(c: Context) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private var items: List<Item> = emptyList()
        private val loc = IntArray(2)

        fun setItems(i: List<Item>) { items = i; invalidate() }

        override fun onDraw(canvas: Canvas) {
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
                canvas.drawOval(
                    RectF(
                        (r.left - dx).toFloat(), (r.top - dy).toFloat(),
                        (r.right - dx).toFloat(), (r.bottom - dy).toFloat()
                    ), paint
                )
                canvas.restore()
            }
        }
    }
}
