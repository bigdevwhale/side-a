package app.sidea.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import app.sidea.data.Covers
import app.sidea.data.Record
import app.sidea.ui.theme.Wide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sleeves for records without a picture, drawn from the record's seed in the manner of sixties and
 * seventies jacket design: Bauhaus circles, rainbow stripes, a setting sun, op-art squares, a dot
 * grid, one big letter. The same seed always gives the same sleeve, and its colours give the spine.
 */
object Sleeves {
    private fun c(v: Long) = Color(v)

    val palettes = listOf(
        listOf(c(0xFFE8D5B5), c(0xFFD9622B), c(0xFF8C3B1F), c(0xFF2E4A3F)),
        listOf(c(0xFFF2E3C6), c(0xFFE5A823), c(0xFFC2462E), c(0xFF3A2C4A)),
        listOf(c(0xFF1E2A38), c(0xFFE86A33), c(0xFFF4D35E), c(0xFFEDE6D6)),
        listOf(c(0xFFEDE3D1), c(0xFF2D6A6A), c(0xFFE2B13C), c(0xFFB9472B)),
        listOf(c(0xFF24323F), c(0xFF7FB3A6), c(0xFFF2C57C), c(0xFFDE6B48)),
        listOf(c(0xFFF0E6D2), c(0xFF3B5BA9), c(0xFFE94F37), c(0xFF1C1C1C)),
        listOf(c(0xFF5B2333), c(0xFFF7B267), c(0xFFF4845F), c(0xFFF7F4E9)),
        listOf(c(0xFFE3D7C1), c(0xFF6B8F71), c(0xFF2F3E46), c(0xFFC97B4A)),
        listOf(c(0xFF0F3B3A), c(0xFFF2A541), c(0xFFE8E1CF), c(0xFFC8553D)),
        listOf(c(0xFFD8CFE8), c(0xFF5B3E8C), c(0xFFF28F6B), c(0xFF22223B)),
    )

    const val STYLES = 6

    fun palette(seed: Long): List<Color> = palettes[Math.floorMod(seed, palettes.size.toLong()).toInt()]
    fun style(seed: Long): Int = Math.floorMod(seed / 11, STYLES.toLong()).toInt()

    /** The spine's colour: the sleeve's strongest. */
    fun spine(seed: Long): Color = palette(seed)[1]

    /** Ink that reads on [bg]. */
    fun inkOn(bg: Color): Color = if (bg.luminance() > 0.45f) Color(0xFF1B1916) else Color(0xFFF6F0E3)
}

/** Draws the generated front of a sleeve over the whole draw area. */
fun DrawScope.sleeve(seed: Long, title: String, artist: String, text: TextMeasurer?) {
    val p = Sleeves.palette(seed)
    val rnd = Random(seed)
    val w = size.width
    val h = size.height
    drawRect(p[0])
    clipRect {
        when (Sleeves.style(seed)) {
            0 -> { // Bauhaus: a big circle, a half circle, a small one.
                drawCircle(p[1], w * 0.34f, Offset(w * (0.34f + rnd.nextFloat() * 0.1f), h * 0.6f))
                drawArc(p[2], 180f, 180f, true, Offset(w * 0.42f, h * 0.36f), Size(w * 0.56f, w * 0.56f))
                drawCircle(p[3], w * 0.09f, Offset(w * 0.78f, h * 0.8f))
            }
            1 -> { // Rainbow stripes bending out of a corner.
                val corner = Offset(-w * 0.1f, h * 1.1f)
                val bands = 5
                for (i in bands downTo 1) {
                    drawCircle(p[1 + (i % 3)], w * (0.35f + i * 0.17f), corner)
                }
                drawCircle(p[0], w * 0.35f, corner)
            }
            2 -> { // A sun going down behind slats.
                drawRect(Brush.verticalGradient(listOf(p[0], p[3].copy(alpha = 0.55f).compositeOver(p[0]))))
                val sun = Offset(w * 0.5f, h * 0.58f)
                drawCircle(Brush.verticalGradient(listOf(p[2], p[1]), startY = sun.y - w * 0.3f, endY = sun.y + w * 0.3f), w * 0.3f, sun)
                var y = sun.y
                var gap = w * 0.012f
                while (y < h) {
                    drawRect(p[0], Offset(0f, y), Size(w, gap))
                    y += gap * 3.2f
                    gap *= 1.35f
                }
                drawRect(p[3], Offset(0f, h * 0.86f), Size(w, h * 0.14f))
            }
            3 -> { // Op-art: squares inside squares, turned a little each time.
                val n = 9
                for (i in 0 until n) {
                    val s = w * (1.25f - i * 0.13f)
                    rotate(i * 4f + rnd.nextFloat() * 2f, Offset(w / 2, h / 2)) {
                        drawRect(if (i % 2 == 0) p[1] else p[0], Offset((w - s) / 2, (h - s) / 2), Size(s, s))
                    }
                }
                drawCircle(p[2], w * 0.08f, Offset(w / 2, h / 2))
            }
            4 -> { // Dots that swell in a wave.
                val n = 8
                val cell = w / n
                val phase = rnd.nextFloat() * 6f
                for (x in 0 until n) for (y in 0 until n) {
                    val k = (sin(x * 0.8f + y * 0.55f + phase) + 1f) / 2f
                    drawCircle(if ((x + y) % 5 == 0) p[2] else p[1], cell * (0.08f + 0.38f * k), Offset(cell * (x + 0.5f), cell * (y + 0.5f)))
                }
            }
            else -> { // One big letter, cropped.
                drawRect(p[3], Offset(0f, h * 0.72f), Size(w, h * 0.28f))
                if (text != null) {
                    val letter = title.trim().firstOrNull()?.uppercase() ?: "A"
                    val layout = text.measure(letter, TextStyle(fontFamily = Wide, fontWeight = FontWeight.SemiBold, fontSize = TextUnit(w * 0.9f / density / fontScale, androidx.compose.ui.unit.TextUnitType.Sp), color = p[1]))
                    drawText(layout, topLeft = Offset(w * 0.52f - layout.size.width / 2f, h * 0.5f - layout.size.height / 2f))
                }
                drawCircle(p[2], w * 0.07f, Offset(w * 0.82f, h * 0.2f))
            }
        }
    }
    if (text != null) {
        val ink = Sleeves.inkOn(p[0])
        val m = w * 0.07f
        val px = w / density / fontScale
        val a = text.measure(
            artist.uppercase(),
            TextStyle(fontFamily = Wide, fontWeight = FontWeight.Medium, fontSize = TextUnit(px * 0.045f, androidx.compose.ui.unit.TextUnitType.Sp), letterSpacing = 0.12.em, color = ink),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = Constraints(maxWidth = (w - 2 * m).toInt().coerceAtLeast(1)),
        )
        val t = text.measure(
            title,
            TextStyle(fontFamily = Wide, fontWeight = FontWeight.SemiBold, fontSize = TextUnit(px * 0.075f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = TextUnit(px * 0.085f, androidx.compose.ui.unit.TextUnitType.Sp), color = ink),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            constraints = Constraints(maxWidth = (w - 2 * m).toInt().coerceAtLeast(1)),
        )
        drawText(a, topLeft = Offset(m, m))
        drawText(t, topLeft = Offset(m, m + a.size.height + w * 0.012f))
    }
}

private fun Color.compositeOver(bg: Color): Color = Color(
    red = red * alpha + bg.red * (1 - alpha),
    green = green * alpha + bg.green * (1 - alpha),
    blue = blue * alpha + bg.blue * (1 - alpha),
)

/**
 * What years of listening do to a sleeve: the ring the record wears through the card, soft white
 * corners, frayed edges, a yellowing, and in the end a few creases.
 */
fun DrawScope.wornSleeve(wear: Float, seed: Long) {
    if (wear <= 0.01f) return
    val w = size.width
    val h = size.height
    val rnd = Random(seed xor 0x5EED)
    val white = Color(0xFFF8F2E6)
    clipRect {
        drawRect(Color(0xFFD9B57A), alpha = 0.2f * wear, blendMode = BlendMode.Multiply)
        // Ring wear: the disc inside, pressed through the card, rubbed more in some places than others.
        val c = Offset(w / 2, h / 2)
        val r = w * 0.44f
        val segs = 180
        val p1 = rnd.nextFloat() * 6f
        val p2 = rnd.nextFloat() * 6f
        for (i in 0 until segs) {
            val t = i * 2 * PI.toFloat() / segs
            val k = ((sin(t * 2 + p1) + sin(t * 5 + p2) * 0.5f) / 1.5f + 1f) / 2f
            drawArc(white, i * 360f / segs, 360f / segs + 0.6f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), alpha = wear * (0.06f + 0.28f * k), style = Stroke(w * (0.01f + 0.022f * k * wear)))
        }
        // Corners and edges.
        val cr = w * 0.1f * (0.6f + wear)
        listOf(Offset(0f, 0f), Offset(w, 0f), Offset(0f, h), Offset(w, h)).forEach { o ->
            drawCircle(Brush.radialGradient(listOf(white.copy(alpha = 0.5f * wear), Color.Transparent), o, cr), cr, o)
        }
        drawRect(white, style = Stroke(w * 0.012f), alpha = 0.35f * wear)
        // Creases on a sleeve that has been everywhere.
        if (wear > 0.35f) {
            repeat(((wear - 0.35f) * 8).toInt() + 1) {
                val y0 = h * rnd.nextFloat()
                val len = w * (0.06f + 0.14f * rnd.nextFloat())
                val fromLeft = rnd.nextBoolean()
                val x0 = if (fromLeft) 0f else w
                val x1 = if (fromLeft) len else w - len
                drawLine(white, Offset(x0, y0), Offset(x1, y0 + (rnd.nextFloat() - 0.5f) * len * 0.3f), strokeWidth = w * 0.004f, alpha = 0.45f * wear, cap = StrokeCap.Round)
            }
        }
    }
}

/** A record's sleeve: its picture or the generated design, worn by [wear]. */
@Composable
fun Sleeve(record: Record, wear: Float, modifier: Modifier = Modifier, lettering: Boolean = true, picture: ImageBitmap? = null) {
    val saved = rememberCover(record.cover)
    val image = picture ?: saved
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        val img = image
        if (img != null) drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()), dstOffset = IntOffset.Zero)
        else sleeve(record.seed, record.title, record.artist, if (lettering) text else null)
        wornSleeve(wear, record.seed)
    }
}

/** The saved cover picture [name], loaded off the main thread. */
@Composable
fun rememberCover(name: String?): ImageBitmap? {
    val c = LocalContext.current
    val image by produceState<ImageBitmap?>(null, name) {
        value = if (name == null) null else withContext(Dispatchers.IO) { Covers.load(c, name)?.asImageBitmap() }
    }
    return image
}

/** A rounded rectangle path, for shapes the drawing APIs don't round on their own. */
fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) = Path().apply {
    addRoundRect(androidx.compose.ui.geometry.RoundRect(x, y, x + w, y + h, CornerRadius(r, r)))
}

/** A point on a circle, degrees clockwise from three o'clock. */
fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = deg * PI.toFloat() / 180f
    return Offset(c.x + r * cos(a), c.y + r * sin(a))
}
