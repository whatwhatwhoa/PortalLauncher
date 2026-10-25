package io.github.whatwhatwhoa.portallauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** The spacing scale. Every gap and margin in the app is one of these; nothing else invents one. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** Sizes on the Portal's 1067×600 dp canvas; the layout picture is in architecture.md *Look*. */
object Dimens {
    // The design canvas. Another screen scales it to fit (MainActivity), the Portal at exactly 1;
    // the width the layout needs is the All apps grid (8 × 96 + 7 × 24 + 2 × 48).
    val canvasHeight = 600.dp
    val canvasMinWidth = 1032.dp

    val edge = Space.xxxl
    val top = Space.xxl

    // The status sits tighter into the corners than the content margins (owner's ask), then
    // 3 dp (about 5 px) tighter still (their second ask), then halved (their third).
    val statusTop = (Space.xl - 3.dp) / 2
    val statusEdge = (Space.xxl - 3.dp) / 2
    val statusGap = Space.m

    // Unfocused row tiles: 160 made a bit smaller (owner's ask), so an adaptive icon's 432 px
    // xxxhdpi layer is drawn slightly down, which stays crisp.
    val tile = 144.dp
    val tileGap = Space.l
    val tileRadius = Space.l
    // The focused tile grows by layout, so its neighbors move aside: 192 dp is 346 px, under a
    // 512 px cover.
    val tileFocused = 192.dp
    // The carousel's center line as a share of the screen's height: a hair below center, over
    // the hero's lower edge (owner's pick after trying 0.65 and 0.5).
    const val CAROUSEL_CENTER = 0.52f

    // The hero band from the top of the screen to its hairline: 490 of a 1920×620 hero's rows
    // at full width, the rest cropped evenly.
    val band = 272.dp

    val dockButton = 56.dp
    val dockGap = Space.xl
    val dockLabelGap = Space.m
    val bottom = Space.xl

    val hintBar = 56.dp
    // The hint's button disc, shrunk with the hint type.
    val glyph = 20.dp

    val drawerTile = 96.dp
    val drawerRadius = Space.m
    val drawerGap = Space.xl
    const val DRAWER_COLUMNS = 8

    val ring = 3.dp
    val ringGap = 3.dp
    const val FOCUS_SCALE = 1.08f
    const val LIFT_SCALE = 1.14f

    // Home behind the categories layer: its art and row this faint, drawn a little smaller, as if
    // zoomed out; the name, the dock and the hints go.
    const val GHOST_ALPHA = 0.18f
    const val GHOST_SCALE = 0.94f

    val sheet = 360.dp
    // Clears the status corner, which is drawn over the sheet.
    val sheetTop = Space.xxxl + Space.xxl
    val sheetIcon = 64.dp
    val sheetRow = 48.dp
    val artPreview = 96.dp

    // The effects (each can be switched off in Settings). The focus glow: a blur this wide outside
    // the ring, at this share of the accent, breathing with it.
    val glow = Space.l
    const val GLOW_ALPHA = 0.45f
    // Depth: in the dark a highlight along the top edge, in the light a soft shadow below.
    val rim = 1.dp
    const val RIM_ALPHA = 0.22f
    val shadowBlur = Space.s
    val shadowDrop = Space.xs
    const val SHADOW_ALPHA = 0.22f
    // The background gradient's bright spot: where the scenes are brightest, right of center.
    const val LIFT_X = 0.62f
    const val LIFT_Y = 0.45f
    const val LIFT_RADIUS = 0.75f
    // The settings row's drawn chevrons, either side of the focused value.
    val chevron = Space.s
    val chevronStroke = 1.5.dp
}

// The owner wanted Segoe UI, which can't ship; Selawik is Microsoft's open look-alike, picked over
// Inter, Nunito Sans and Manrope (2026-10-25). Its files are static and there is no Medium, so
// Medium uses Regular, as Segoe UI itself does.
val selawik = FontFamily(
    Font(R.font.selawik_regular, FontWeight.Normal),
    Font(R.font.selawik_regular, FontWeight.Medium),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
    Font(R.font.selawik_bold, FontWeight.Bold),
)

// Selawik reads small beside other fonts at the same size; the owner settled on 1.15 after
// trying 1.3. Every size and line height is multiplied by it.
const val FONT_SCALE = 1.15f

class Type(private val family: FontFamily, private val scale: Float = 1f) {
    private fun style(weight: FontWeight, size: Int, line: Int) =
        TextStyle(fontFamily = family, fontWeight = weight, fontSize = (size * scale).sp, lineHeight = (line * scale).sp)

    // For the focused app's name: the first try's 40/48 ×3, then ×1.75, and bold (owner's asks).
    val display = style(FontWeight.Bold, 210, 252)
    // The categories layer's names: five stack in the canvas with room to spare.
    val heading = style(FontWeight.Bold, 48, 56)
    val title = style(FontWeight.SemiBold, 24, 32)
    val body = style(FontWeight.Medium, 18, 24)
    // Button hints: caption-sized (owner's ask, from 16/24), so the centered dock clears them.
    val hint = style(FontWeight.Medium, 14, 20)
    val caption = style(FontWeight.Medium, 14, 20)
}

/** A text size or line height as layout space, so it follows the font scale like the text does. */
@Composable
fun TextUnit.asDp(): Dp = with(LocalDensity.current) { this@asDp.toDp() }

/**
 * White is the default (owner's pick): the text color, so near-black in the light theme. The
 * others are a hue and a chroma, made into OKLCH at a lightness fixed per theme, so each reads
 * with the same weight on the light and the dark background: about 11:1 on Black at 0.80, and
 * 3.8–4.4:1 on the light background at 0.55 (0.60 left Mint at 3.1). The owner keeps their
 * favorites from this first set (`decisions/M6-settings.md`).
 */
enum class Accent(val label: String, val hue: Float?, val chroma: Float = 0f) {
    White("White", null),
    IceBlue("Ice blue", 235f, 0.08f),
    Mint("Mint", 165f, 0.11f),
    Amber("Amber", 75f, 0.14f),
    Coral("Coral", 28f, 0.15f),
    Lavender("Lavender", 295f, 0.12f);

    fun on(dark: Boolean) = hue?.let { oklch(if (dark) 0.80f else 0.55f, chroma, it) } ?: text(dark)
}

/** The theme follows Android's dark setting unless the owner fixes it. */
enum class Theme(val label: String) { System("System"), Light("Light"), Dark("Dark") }

/** The focus ring's breath, each way; Slow is the default since the owner halved it. */
enum class Pulse(val label: String, val millis: Int?) { Off("Off", null), Slow("Slow", 1800), Normal("Normal", 900) }

/** The overlay effects, each switchable so the owner can judge it against its absence. */
data class Effects(val gradient: Boolean = true, val glow: Boolean = true, val depth: Boolean = true)

/** Settings → Look. The defaults are what Home looked like before there were settings, plus the effects. */
data class LookSettings(
    val theme: Theme = Theme.System,
    val accent: Accent = Accent.White,
    /** A [Backdrop.id]; a wallpaper whose file has gone falls back to Black. */
    val backdrop: String = Backdrop.Black.id,
    val scenes: Boolean = true,
    val covers: Boolean = true,
    val effects: Effects = Effects(),
    val pulse: Pulse = Pulse.Slow,
)

/** The status clock: Android's own 12/24-hour setting unless the owner fixes it. */
enum class ClockFormat(val label: String, val pattern: String?) {
    System("System", null),
    H12("12-hour", "h:mm a"),
    H24("24-hour", "HH:mm"),
}

/** Where the Home key takes Home when pressed while already there. */
enum class StartOn(val label: String) { Favorites("Favorites"), Left("Where you left") }

/** Settings → Home. The defaults are how Home behaved before there were settings. */
data class HomeSettings(
    val clock: ClockFormat = ClockFormat.System,
    val batteryPercent: Boolean = true,
    val hints: Boolean = true,
    val wraps: Boolean = false,
    val start: StartOn = StartOn.Favorites,
)

private fun text(dark: Boolean) = if (dark) Color.White else Color(0xFF2D2D2D)

/**
 * What Home sits on. Black is the default (the owner's pick from the trial). A wallpaper is a
 * file in the app's `files/backdrops` on the Portal, so third-party images stay out of the APK
 * and the repo.
 */
sealed class Backdrop(val id: String, val label: String) {
    data object Dark : Backdrop("dark", "Dark")
    data object Black : Backdrop("black", "Black")
    data object Glow : Backdrop("glow", "Hero glow")
    class Wallpaper(val file: File) : Backdrop("file:${file.name}", file.nameWithoutExtension)
}

data class Palette(
    val dark: Boolean,
    val background: Color,
    val text: Color,
    val surface: Color,
    val muted: Color,
    val rule: Color,
    val accent: Color,
    /** The background gradient's bright spot, fading to [background]. */
    val lift: Color,
)

fun palette(dark: Boolean, accent: Accent, black: Boolean = false): Palette {
    // Dark is much darker than the Switch's #2D2D2D (owner's ask); Black is the OLED's off.
    val background = when {
        !dark -> Color(0xFFEBEBEB)
        black -> Color.Black
        else -> Color(0xFF141414)
    }
    val text = text(dark)
    val accentColor = accent.on(dark)
    // In the dark the spot is the accent, faint, so a palette tints it; in the light it is near
    // white, tinted only by a hued accent (White's is near-black there and would gray it).
    val lift = if (dark) {
        lerp(background, accentColor, 0.12f)
    } else {
        lerp(lerp(background, Color.White, 0.8f), accentColor, if (accent.hue != null) 0.06f else 0f)
    }
    return Palette(
        dark = dark,
        background = background,
        text = text,
        surface = lerp(background, text, 0.08f),
        muted = text.copy(alpha = 0.6f),
        rule = text.copy(alpha = 0.16f),
        accent = accentColor,
        lift = lift,
    )
}

val LocalPalette = staticCompositionLocalOf { palette(true, Accent.White) }
val LocalType = staticCompositionLocalOf { Type(selawik, FONT_SCALE) }
val LocalEffects = staticCompositionLocalOf { Effects() }
val LocalPulse = staticCompositionLocalOf { Pulse.Slow }
val LocalHome = staticCompositionLocalOf { HomeSettings() }

/** False while the system's animations are off (animator duration scale 0): everything snaps. */
val LocalMotion = staticCompositionLocalOf { true }

/** OKLCH to sRGB; chroma is lowered until the color fits the sRGB gamut, so the hue holds. */
fun oklch(lightness: Float, chroma: Float, hueDegrees: Float): Color {
    val h = Math.toRadians(hueDegrees.toDouble())
    var c = chroma.toDouble()
    while (true) {
        val a = c * cos(h)
        val b = c * sin(h)
        val l = cube(lightness + 0.3963377774 * a + 0.2158037573 * b)
        val m = cube(lightness - 0.1055613458 * a - 0.0638541728 * b)
        val s = cube(lightness - 0.0894841775 * a - 1.2914855480 * b)
        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        if ((r in 0.0..1.0 && g in 0.0..1.0 && bl in 0.0..1.0) || c <= 0.0) {
            return Color(encode(r), encode(g), encode(bl))
        }
        c -= 0.005
    }
}

private fun cube(x: Double) = x * x * x

private fun encode(linear: Double): Float {
    val x = linear.coerceIn(0.0, 1.0)
    val v = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
    return v.toFloat()
}
