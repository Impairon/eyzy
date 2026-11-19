package app.katib.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Katib design tokens, mirrored from design/tokens.json (source of truth).
 * Tiers: Primitive → semantic (KatibColors, Space, Radius, Stroke, Motion) → component usage in screens.
 * Screens must use semantic tokens (E.c.*, Space.*, Radius.*) and never a raw Color(0x…) or primitive.
 */
internal object Primitive {
    val paper50 = Color(0xFFF4F1EA); val paper100 = Color(0xFFECE7DC); val paper200 = Color(0xFFDCD5C6)
    val ink900 = Color(0xFF161614); val ink600 = Color(0xFF5E5A52)
    val night950 = Color(0xFF121211); val night900 = Color(0xFF1C1B19); val night700 = Color(0xFF302E2A)
    val chalk100 = Color(0xFFECE8DF); val chalk400 = Color(0xFFA39E93)
    val vermilion600 = Color(0xFFC93A1E); val vermilion400 = Color(0xFFFF6A4D)
    val moss700 = Color(0xFF3D6A35); val moss300 = Color(0xFF8DBF7E)

    // Speaker inks: earthy, distinct, all ≥ 4.5:1 on their canvas.
    val speakersLight = listOf(0xFF3B5BA9, 0xFF9A6200, 0xFF3F7A4A, 0xFF8E3B73, 0xFF4F6D7A, 0xFF6B6B1F, 0xFF5E4FA2, 0xFF7A5230).map(::Color)
    val speakersDark = listOf(0xFF8EA8F0, 0xFFE2A64A, 0xFF86C48F, 0xFFE08CC2, 0xFF97B7C4, 0xFFC9C86A, 0xFFB3A6F0, 0xFFD3A47E).map(::Color)
}

object Space {
    val xs = 4.dp; val s = 8.dp; val m = 16.dp; val l = 24.dp; val xl = 32.dp
    val gutter = 20.dp
}

object Radius { val tag = 2.dp; val card = 4.dp; val sheet = 8.dp }

object Stroke { val hairline = 1.dp; val rule = 1.5.dp }

object Motion {
    const val fast = 120; const val base = 220; const val slow = 420
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
