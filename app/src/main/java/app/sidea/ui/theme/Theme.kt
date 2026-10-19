package app.sidea.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.sidea.R
import app.sidea.data.Format

/**
 * Two rooms, picked at the start. **Walnut**: a late-seventies hi-fi in a dark room, walnut veneer,
 * brushed aluminium, one orange "stereo" lamp. **Silver deck**: an eighties tape deck in daylight,
 * grey plastic, a red record lamp, cassettes in a black rack. Each also comes the other way round,
 * [WalnutDay] and [SilverNight], for a light or dark theme.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    /** The lamp: for the one thing to do. */
    val lamp: Color,
    val lampSoft: Color,
    val onLamp: Color,
    /** The shelf (walnut planks, or the black cassette rack). */
    val shelf: Color,
    val shelfDark: Color,
    val shelfLight: Color,
    /** Knobs, the platter rim, the deck's face. */
    val metal: Color,
    val metalDark: Color,
    /** Inner sleeves and J-cards: paper, with ballpoint on it. */
    val paper: Color,
    val pen: Color,
)

val Walnut = Palette(
    dark = true,
    bg = Color(0xFF17120E),
    surface = Color(0xFF221A14),
    surface2 = Color(0xFF2D231B),
    line = Color(0xFF433529),
    ink = Color(0xFFF2E7D5),
    muted = Color(0xFFBCA98F),
    faint = Color(0xFF85735F),
    lamp = Color(0xFFFF9A3C),
    lampSoft = Color(0xFF3F2815),
    onLamp = Color(0xFF1A120B),
    shelf = Color(0xFF6B4429),
    shelfDark = Color(0xFF3B2414),
    shelfLight = Color(0xFF93613B),
    metal = Color(0xFFC9C5BC),
    metalDark = Color(0xFF6E6A63),
    paper = Color(0xFFEFE4CE),
    pen = Color(0xFF2B3B6B),
)

val SilverDeck = Palette(
    dark = false,
    bg = Color(0xFFE3E0D9),
    surface = Color(0xFFF3F1EC),
    surface2 = Color(0xFFD5D1C8),
    line = Color(0xFFC0BBB0),
    ink = Color(0xFF212328),
    muted = Color(0xFF575A61),
    faint = Color(0xFF8C8E94),
    lamp = Color(0xFFD63E2E),
    lampSoft = Color(0xFFF1D2CB),
    onLamp = Color(0xFFFFF8F4),
    shelf = Color(0xFF2C2E33),
    shelfDark = Color(0xFF17181B),
    shelfLight = Color(0xFF474A51),
    metal = Color(0xFFBDBFC3),
    metalDark = Color(0xFF7D8086),
    paper = Color(0xFFF7F3E8),
    pen = Color(0xFF1F3F8F),
)

/** Walnut by day: the same hi-fi in a sunlit room, cream walls, a deeper orange lamp. */
val WalnutDay = Walnut.copy(
    dark = false,
    bg = Color(0xFFEFE6D8),
    surface = Color(0xFFF8F2E8),
    surface2 = Color(0xFFE5D9C7),
    line = Color(0xFFD3C4AE),
    ink = Color(0xFF2A1E15),
    muted = Color(0xFF6B5A48),
    faint = Color(0xFF9A8874),
    lamp = Color(0xFFCF6A17),
    lampSoft = Color(0xFFF4DEC4),
    onLamp = Color(0xFFFFF8F0),
    paper = Color(0xFFFBF6EC),
)

/** The silver deck at night: smoked black plastic, a dimmed face, the red lamp brighter. */
val SilverNight = SilverDeck.copy(
    dark = true,
    bg = Color(0xFF14161A),
    surface = Color(0xFF1D2025),
    surface2 = Color(0xFF272B31),
    line = Color(0xFF363B43),
    ink = Color(0xFFE8E9EC),
    muted = Color(0xFFA3A7AF),
    faint = Color(0xFF6F747D),
    lamp = Color(0xFFFF5B47),
    lampSoft = Color(0xFF3B1F1B),
    onLamp = Color(0xFF1A0F0D),
    shelf = Color(0xFF101114),
    shelfDark = Color(0xFF07080A),
    shelfLight = Color(0xFF2C2F36),
    metal = Color(0xFF8F9299),
    metalDark = Color(0xFF4A4D54),
    paper = Color(0xFFEDE8DC),
)

fun palette(format: Format, dark: Boolean = format == Format.VINYL) = when (format) {
    Format.VINYL -> if (dark) Walnut else WalnutDay
    Format.TAPE -> if (dark) SilverNight else SilverDeck
}

val LocalPalette = staticCompositionLocalOf { Walnut }
val LocalFormat = staticCompositionLocalOf { Format.VINYL }

/** Unbounded: wide and round, like the lettering on a seventies receiver. */
val Wide = FontFamily(
    Font(R.font.unbounded_medium, FontWeight.Medium),
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
)

/** Manrope: plain and clear, for everything else. */
val Sans = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/** Caveat: a ballpoint on an inner sleeve or a J-card. */
val Hand = FontFamily(Font(R.font.caveat, FontWeight.Normal))

/** PT Mono: the tape counter and the small print on the deck. */
val Mono = FontFamily(Font(R.font.pt_mono, FontWeight.Normal))

object U {
    val c: Palette @Composable get() = LocalPalette.current
    val tape: Boolean @Composable get() = LocalFormat.current == Format.TAPE

    val RXl = RoundedCornerShape(24.dp)
    val RLg = RoundedCornerShape(18.dp)
    val RMd = RoundedCornerShape(14.dp)
    val RSm = RoundedCornerShape(10.dp)
}

object UType {
    val Hero: TextStyle @Composable get() = TextStyle(fontFamily = Wide, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 38.sp, color = U.c.ink)
    val Display: TextStyle @Composable get() = TextStyle(fontFamily = Wide, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 31.sp, color = U.c.ink)
    val Title: TextStyle @Composable get() = TextStyle(fontFamily = Wide, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, color = U.c.ink)
    val Sub: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 23.sp, color = U.c.muted)
    val Eyebrow: TextStyle @Composable get() = TextStyle(fontFamily = Mono, fontSize = 12.sp, letterSpacing = 0.16.em, color = U.c.faint)
    val Strong: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = U.c.ink)
    val Body: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 23.sp, color = U.c.ink)
    val Muted: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, color = U.c.muted)
    val Small: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = U.c.faint)
    val Button: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val Note: TextStyle @Composable get() = TextStyle(fontFamily = Hand, fontSize = 25.sp, lineHeight = 28.sp, color = U.c.pen)
    val Counter: TextStyle @Composable get() = TextStyle(fontFamily = Mono, fontSize = 13.sp, letterSpacing = 0.08.em, color = U.c.muted)
}

@Composable
fun SideTheme(format: Format, dark: Boolean = format == Format.VINYL, content: @Composable () -> Unit) {
    val p = palette(format, dark)
    CompositionLocalProvider(LocalPalette provides p, LocalFormat provides format) {
        val base = if (p.dark) darkColorScheme() else lightColorScheme()
        MaterialTheme(
            colorScheme = base.copy(
                primary = p.lamp,
                onPrimary = p.onLamp,
                primaryContainer = p.lampSoft,
                onPrimaryContainer = p.ink,
                secondary = p.lamp,
                background = p.bg,
                surface = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surface2,
                surfaceContainerHighest = p.surface2,
                surfaceContainerLow = p.surface,
                onBackground = p.ink,
                onSurface = p.ink,
                onSurfaceVariant = p.muted,
                outline = p.line,
                outlineVariant = p.line,
            ),
            typography = MaterialTheme.typography.copy(
                bodyLarge = UType.Body,
                bodyMedium = UType.Body.copy(fontSize = 15.sp),
                labelLarge = UType.Button,
            ),
            content = content,
        )
    }
}
