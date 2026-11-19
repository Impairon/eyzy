package app.katib.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.katib.R

val Plex = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)
val PlexMono = FontFamily(Font(R.font.plex_mono_regular))
/** Display faces: a Latin editorial serif, and Amiri (classical Naskh, the scribe's hand) for Arabic. */
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
val Amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal), Font(R.font.amiri_bold, FontWeight.Bold))

/** Semantic colour tokens (design/tokens.json → semantic.color). */
@Immutable
data class KatibColors(
    val paper: Color,     // bg.canvas
    val surface: Color,   // bg.raised
    val ink: Color,       // text.primary
    val muted: Color,     // text.muted
    val line: Color,      // border.hairline
    val primary: Color,   // action.primary (solid ink, no brand hue)
    val onPrimary: Color,
    val signal: Color,    // signal.live: recording, destructive, warnings. Never decorative.
    val onSignal: Color,
    val verified: Color,  // status.verified
    val speakers: List<Color>,
    val dark: Boolean,
)

private val Light = with(Primitive) {
    KatibColors(
        paper = paper50, surface = paper100, ink = ink900, muted = ink600, line = paper200,
        primary = ink900, onPrimary = paper50, signal = vermilion600, onSignal = Color.White,
        verified = moss700, speakers = speakersLight, dark = false,
    )
}
private val Dark = with(Primitive) {
    KatibColors(
        paper = night950, surface = night900, ink = chalk100, muted = chalk400, line = night700,
        primary = chalk100, onPrimary = night950, signal = vermilion400, onSignal = night950,
        verified = moss300, speakers = speakersDark, dark = true,
    )
}

val LocalKatib = staticCompositionLocalOf { Light }
val LocalDisplayFont = staticCompositionLocalOf { InstrumentSerif }

object E {
    val c: KatibColors @Composable get() = LocalKatib.current
    /** Display family for the current UI language. */
    val display: FontFamily @Composable get() = LocalDisplayFont.current
}

/** semantic.type.label: mono, uppercase, tracked. Used for section labels, tags and metadata. */
val labelStyle @Composable get() = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, letterSpacing = 1.2.sp, color = E.c.muted)

@Composable
fun KatibTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val display = if (arabic) Amiri else InstrumentSerif
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.primary, onPrimary = c.onPrimary, background = c.paper, onBackground = c.ink,
        surface = c.paper, onSurface = c.ink, surfaceVariant = c.surface, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.paper, surfaceContainerLow = c.paper, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface, surfaceTint = Color.Transparent,
        outline = c.line, outlineVariant = c.line,
        secondary = c.ink, onSecondary = c.paper, secondaryContainer = c.ink, onSecondaryContainer = c.paper,
        primaryContainer = c.surface, onPrimaryContainer = c.ink,
        tertiary = c.signal, onTertiary = c.onSignal,
        error = c.signal, onError = c.onSignal,
    )
    val base = Typography()
    fun TextStyle.p() = copy(fontFamily = Plex)
    // Arabic Naskh needs more line height and a slightly smaller size than the Latin serif for equal visual weight.
    fun TextStyle.d(size: Int, lh: Int) = copy(fontFamily = display, fontWeight = FontWeight.Normal,
        fontSize = (if (arabic) size - 4 else size).sp, lineHeight = (if (arabic) lh + 10 else lh).sp, letterSpacing = 0.sp)
    val type = Typography(
        displayLarge = base.displayLarge.d(56, 58),
        displayMedium = base.displayMedium.d(44, 48),
        displaySmall = base.displaySmall.d(40, 44),
        headlineLarge = base.headlineLarge.d(34, 38),
        headlineMedium = base.headlineMedium.d(30, 34),
        headlineSmall = base.headlineSmall.d(28, 32),
        titleLarge = base.titleLarge.d(24, 28),
        titleMedium = base.titleMedium.p().copy(fontWeight = FontWeight.Medium, fontSize = 17.sp),
        titleSmall = base.titleSmall.p().copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyLarge = base.bodyLarge.p().copy(fontSize = 17.sp, lineHeight = 27.sp),
        bodyMedium = base.bodyMedium.p().copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.p().copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.p().copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
        labelMedium = base.labelMedium.p().copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.p(),
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(Radius.tag), small = RoundedCornerShape(Radius.card),
        medium = RoundedCornerShape(Radius.card), large = RoundedCornerShape(Radius.sheet),
        extraLarge = RoundedCornerShape(Radius.sheet),
    )
    CompositionLocalProvider(LocalKatib provides c, LocalDisplayFont provides display) {
        MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
    }
}
