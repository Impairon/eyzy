package com.abo.avatarblackout

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.max

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
    private class Item(val rect: Rect, val clip: Rect? = null)

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
    private val handler = Handler(Looper.getMainLooper())
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
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (Prefs.maskMode(this) == Prefs.MaskMode.SIMPLE &&
            e?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) return
        if (!Prefs.isEnabled(this) || pending) return
        pending = true
        handler.post(scan)
    }

    fun refresh() {
        handler.removeCallbacks(scan)
        handler.removeCallbacks(tick)
        pending = false
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
                if (packageName in TARGETS) {
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
        val clusters = sc.avatarRects.groupBy { it.centerX() / (12 * dp).toInt() }
        val column = clusters.values.maxByOrNull { it.size } ?: return null
        val center = column.map { it.centerX() }.average().toInt()
        val scroll = sc.scrollRect
        val top = max(
            (93 * dp).toInt(),
            column.minOf { it.top }
        )
        val bottom = (scroll?.bottom ?: column.maxOf { it.bottom })
            .coerceAtMost(column.maxOf { it.bottom })
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
                val left = (r.left - dx).toFloat()
                val top = (r.top - dy).toFloat()
                val right = (r.right - dx).toFloat()
                val bottom = (r.bottom - dy).toFloat()
                if (Prefs.maskMode(context) == Prefs.MaskMode.DYNAMIC) {
                    canvas.drawOval(RectF(left, top, right, bottom), paint)
                } else {
                    canvas.drawRect(left, top, right, bottom, paint)
                }
                canvas.restore()
            }
        }
    }
}
