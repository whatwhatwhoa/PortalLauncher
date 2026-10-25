package io.github.whatwhatwhoa.portallauncher

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val edge = Space.xxxl
    val top = Space.xxl

    // 160 dp at density 288 is 288 px, so an adaptive icon's 108 dp layer lands on 432 px:
    // exactly the xxxhdpi bitmap apps ship. Row tiles draw app icons 1:1.
    val tile = 160.dp
    val tileGap = Space.l
    val tileRadius = Space.l
    val titleGap = Space.xl

    val dockButton = 56.dp
    val dockGap = Space.xl
    val dockLabelGap = Space.m
    val dockAboveHints = Space.xxl

    val hintBar = 56.dp
    val glyph = Space.xl

    val drawerTile = 96.dp
    val drawerRadius = Space.m
    val drawerGap = Space.xl
    const val DRAWER_COLUMNS = 8

    val ring = 3.dp
    val ringGap = 3.dp
    const val FOCUS_SCALE = 1.08f
}

/** The fonts on trial (Q-font); the owner flips through them with X until one is picked. */
enum class FontChoice(val label: String, private val res: Int) {
    Inter("Inter", R.font.inter),
    NunitoSans("Nunito Sans", R.font.nunito_sans),
    Manrope("Manrope", R.font.manrope);

    // Each weight is an instance of the same variable file, so all four come from one resource.
    fun family() = FontFamily(listOf(400, 500, 600, 700).map { Font(res, FontWeight(it)) })
}

class Type(family: FontFamily) {
    val title = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp)
    val body = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp)
    val hint = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp)
    val caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
}

/**
 * Accents are a hue only. The colour comes from OKLCH at a lightness fixed per theme, so every
 * accent reads with the same weight on the light and the dark background.
 */
enum class Accent(val label: String, val hue: Float) {
    Cyan("Cyan", 215f),
    Coral("Coral", 28f),
    Lime("Lime", 135f),
    Violet("Violet", 300f);

    fun on(dark: Boolean) = oklch(if (dark) 0.80f else 0.60f, 0.15f, hue)
}

data class Palette(
    val background: Color,
    val text: Color,
    val surface: Color,
    val muted: Color,
    val rule: Color,
    val accent: Color,
)

fun palette(dark: Boolean, accent: Accent): Palette {
    val background = if (dark) Color(0xFF2D2D2D) else Color(0xFFEBEBEB)
    val text = if (dark) Color.White else Color(0xFF2D2D2D)
    return Palette(
        background = background,
        text = text,
        surface = lerp(background, text, 0.08f),
        muted = text.copy(alpha = 0.6f),
        rule = text.copy(alpha = 0.16f),
        accent = accent.on(dark),
    )
}

val LocalPalette = staticCompositionLocalOf { palette(true, Accent.Cyan) }
val LocalType = staticCompositionLocalOf { Type(FontFamily.Default) }

/** OKLCH to sRGB; chroma is lowered until the colour fits the sRGB gamut, so the hue holds. */
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
