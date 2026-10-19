package app.sidea.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sidea.data.Record
import app.sidea.ui.theme.Hand
import app.sidea.ui.theme.U
import app.sidea.ui.theme.Wide
import kotlin.math.sqrt
import kotlin.random.Random

/** Cassette shells: smoked clear, black, cream, ice blue, and the one in red everyone had. */
object Shells {
    private val shells = listOf(
        Color(0xFF4E4C48), Color(0xFF1F1F21), Color(0xFFE6DFCF), Color(0xFF9DB9CC), Color(0xFFB8402F), Color(0xFF6C7F5E),
    )

    fun of(seed: Long): Color = shells[Math.floorMod(seed / 3, shells.size.toLong()).toInt()]
}

/** Width over height of a compact cassette, 100 × 63.8 mm. */
const val CASSETTE = 100f / 63.8f

/**
 * A compact cassette, face on, filling the draw area: the shell, a paper label with the title in
 * ballpoint and a big side letter, the window with both tape packs ([progress] of the side wound from
 * left to right) and the hubs turned by [turnL] and [turnR].
 */
fun DrawScope.cassette(record: Record, sideName: String, progress: Float, turnL: Float, turnR: Float, wear: Float, text: TextMeasurer, creases: Int = 0) {
    val w = size.width
    val h = size.height
    val shell = Shells.of(record.seed)
    val p = Sleeves.palette(record.seed)
    val rnd = Random(record.seed)
    val dark = shell.red + shell.green + shell.blue < 1.4f
    drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(w * 0.01f, h * 0.025f), Size(w, h), CornerRadius(w * 0.035f))
    drawRoundRect(Brush.verticalGradient(listOf(shell.copy(alpha = 1f).lighten(0.08f), shell)), size = Size(w * 0.99f, h * 0.975f), cornerRadius = CornerRadius(w * 0.035f))
    // Screws.
    listOf(Offset(0.045f, 0.07f), Offset(0.945f, 0.07f), Offset(0.045f, 0.9f), Offset(0.945f, 0.9f), Offset(0.495f, 0.9f)).forEach { o ->
        val s = Offset(o.x * w, o.y * h)
        drawCircle(Color(0xFF9A9894), w * 0.014f, s)
        drawLine(Color(0xFF55534F), s - Offset(w * 0.009f, 0f), s + Offset(w * 0.009f, 0f), strokeWidth = w * 0.003f)
    }
    // Label.
    val lx = w * 0.075f
    val ly = h * 0.07f
    val lw = w * 0.84f
    val lh = h * 0.6f
    drawRoundRect(p[0].lighten(0.25f), Offset(lx, ly), Size(lw, lh), CornerRadius(w * 0.012f))
    drawRect(p[1], Offset(lx, ly + lh * 0.7f), Size(lw, lh * 0.1f))
    drawRect(p[2], Offset(lx, ly + lh * 0.8f), Size(lw, lh * 0.06f))
    // Lines to write on.
    for (i in 0..1) drawLine(Color(0xFF9AA5B8), Offset(lx + lw * 0.12f, ly + lh * (0.2f + i * 0.13f)), Offset(lx + lw * 0.97f, ly + lh * (0.2f + i * 0.13f)), strokeWidth = w * 0.0025f, alpha = 0.7f)
    val px = w / density / fontScale
    val pen = Color(0xFF1F3F8F)
    val line = text.measure(
        "${record.artist} — ${record.title}".trim(' ', '—'),
        TextStyle(fontFamily = Hand, fontSize = TextUnit(px * 0.058f, TextUnitType.Sp), color = pen),
        overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = (lw * 0.84f).toInt().coerceAtLeast(1)),
    )
    drawText(line, topLeft = Offset(lx + lw * 0.13f, ly + lh * 0.2f - line.size.height * 0.85f))
    val big = text.measure(sideName, TextStyle(fontFamily = Wide, fontWeight = FontWeight.SemiBold, fontSize = TextUnit(px * 0.075f, TextUnitType.Sp), color = p[3].takeIf { it != p[0] } ?: Color.Black))
    drawText(big, topLeft = Offset(lx + lw * 0.025f, ly + lh * 0.04f))
    // Window, with the tape packs seen through it.
    val wx = w * 0.25f
    val wy = h * 0.3f
    val ww = w * 0.5f
    val wh = h * 0.27f
    val window = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(wx, wy, wx + ww, wy + wh, CornerRadius(w * 0.02f))) }
    val hubL = Offset(w * 0.335f, h * 0.435f)
    val hubR = Offset(w * 0.665f, h * 0.435f)
    val hub = w * 0.048f
    val full = w * 0.155f
    val f = progress.coerceIn(0f, 1f)
    val packL = sqrt(hub * hub + (full * full - hub * hub) * (1 - f))
    val packR = sqrt(hub * hub + (full * full - hub * hub) * f)
    drawRoundRect(Color(0xFF2A2724), Offset(wx - w * 0.012f, wy - w * 0.012f), Size(ww + w * 0.024f, wh + w * 0.024f), CornerRadius(w * 0.026f))
    clipPath(window) {
        drawRect(Color(0xFF14110F), Offset(wx, wy), Size(ww, wh))
        drawCircle(Color(0xFF4A3426), packL, hubL)
        drawCircle(Color(0xFF4A3426), packR, hubR)
        drawCircle(Color(0xFF6B4D39), packL, hubL, style = Stroke(w * 0.003f))
        drawCircle(Color(0xFF6B4D39), packR, hubR, style = Stroke(w * 0.003f))
        // The tape running across between the packs.
        drawLine(Color(0xFF3A281D), Offset(hubL.x, hubL.y + packL), Offset(hubR.x, hubR.y + packR), strokeWidth = w * 0.004f)
        drawRect(Color.White, Offset(wx, wy), Size(ww, wh * 0.35f), alpha = if (dark) 0.06f else 0.1f)
    }
    // Hubs: white rings with six teeth, turning.
    listOf(hubL to turnL, hubR to turnR).forEach { (c, t) ->
        drawCircle(Color(0xFFF1EEE6), hub, c)
        rotate(t, c) {
            for (i in 0 until 6) {
                val a = polar(c, hub * 0.55f, i * 60f)
                val b = polar(c, hub * 0.85f, i * 60f)
                drawLine(Color(0xFF8E8A82), a, b, strokeWidth = w * 0.009f, cap = StrokeCap.Round)
            }
        }
        drawCircle(Color(0xFF1B1917), hub * 0.5f, c)
    }
    // The lower edge, where the heads go in.
    val trap = Path().apply {
        moveTo(w * 0.19f, h * 0.73f)
        lineTo(w * 0.8f, h * 0.73f)
        lineTo(w * 0.85f, h * 0.975f)
        lineTo(w * 0.14f, h * 0.975f)
        close()
    }
    drawPath(trap, shell.darken(0.12f))
    drawPath(trap, Color.Black, alpha = 0.25f, style = Stroke(w * 0.003f))
    listOf(0.29f, 0.7f).forEach { x -> drawCircle(Color(0xFF14110F), w * 0.018f, Offset(w * x, h * 0.86f)) }
    listOf(0.4f, 0.585f).forEach { x -> drawRect(Color(0xFF14110F), Offset(w * x, h * 0.82f), Size(w * 0.025f, h * 0.07f)) }
    drawRect(Color(0xFF14110F), Offset(w * 0.475f, h * 0.93f), Size(w * 0.05f, h * 0.045f))
    // A creased tape shows on its brown edge.
    if (creases > 0) {
        clipPath(window) {
            repeat(creases.coerceAtMost(6)) {
                val a = rnd.nextFloat() * 360f
                drawArc(Color(0xFFB08A63), a, 10f, false, Offset(hubR.x - packR * 0.9f, hubR.y - packR * 0.9f), Size(packR * 1.8f, packR * 1.8f), style = Stroke(w * 0.004f))
            }
        }
    }
    // Wear: a yellowed label, scuffed plastic, a peeling corner.
    if (wear > 0.02f) {
        drawRect(Color(0xFFD9B57A), Offset(lx, ly), Size(lw, lh), alpha = 0.3f * wear, blendMode = BlendMode.Multiply)
        repeat((wear * 24).toInt()) {
            val x = w * rnd.nextFloat()
            val y = h * rnd.nextFloat()
            drawLine(Color.White, Offset(x, y), Offset(x + w * 0.04f * rnd.nextFloat(), y + h * 0.02f * (rnd.nextFloat() - 0.5f)), strokeWidth = w * 0.002f, alpha = 0.25f * wear)
        }
        if (wear > 0.4f) {
            val peel = Path().apply {
                moveTo(lx + lw, ly)
                lineTo(lx + lw - w * 0.05f * wear, ly)
                lineTo(lx + lw, ly + w * 0.05f * wear)
                close()
            }
            drawPath(peel, Color(0xFFFFFBF0))
            drawPath(peel, Color.Black, alpha = 0.15f, style = Stroke(w * 0.002f))
        }
    }
}

private fun Color.lighten(k: Float) = Color(red + (1 - red) * k, green + (1 - green) * k, blue + (1 - blue) * k, alpha)
private fun Color.darken(k: Float) = Color(red * (1 - k), green * (1 - k), blue * (1 - k), alpha)

/**
 * A cassette with turning hubs. [running]: 0 still, 1 playing, ±2 winding. The fuller pack turns
 * slower, as on a real tape.
 */
@Composable
fun Cassette(record: Record, sideName: String, progress: Float, running: Int, wear: Float, modifier: Modifier = Modifier, creases: Int = 0) {
    val text = rememberTextMeasurer()
    var turnL by remember { mutableFloatStateOf(10f) }
    var turnR by remember { mutableFloatStateOf(40f) }
    val run by rememberUpdatedState(if (Art.still) 0 else running)
    val at by rememberUpdatedState(progress)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { t ->
            val dt = if (last == 0L) 0f else (t - last) / 1e9f
            last = t
            if (run != 0) {
                // Tape moves at a fixed speed past the head, so a pack's turn rate goes as 1/radius.
                val speed = when (run) { 1 -> 90f; 2 -> 900f; -2 -> -900f; else -> 0f }
                val rl = sqrt(0.1f + 0.9f * (1 - at))
                val rr = sqrt(0.1f + 0.9f * at)
                turnL = (turnL - speed * dt / rl) % 360f
                turnR = (turnR - speed * dt / rr) % 360f
            }
        }
    }
    Canvas(modifier.aspectRatio(CASSETTE)) { cassette(record, sideName, progress, turnL, turnR, wear, text, creases) }
}

/**
 * The front of a cassette's J-card, seen through the case: the sleeve art above, and the title in
 * ballpoint on the paper below.
 */
@Composable
fun JCard(record: Record, wear: Float, modifier: Modifier = Modifier, lettering: Boolean = true, picture: ImageBitmap? = null) {
    val pal = U.c
    Box(
        modifier
            .aspectRatio(0.64f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(pal.paper)
            .border(1.dp, Color.Black.copy(alpha = 0.25f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Sleeve(record, 0f, Modifier.fillMaxWidth().aspectRatio(1f), lettering = false, picture = picture)
            if (lettering) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(record.title, fontFamily = Hand, fontSize = 22.sp, lineHeight = 22.sp, color = pal.pen, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(record.artist, fontFamily = Hand, fontSize = 17.sp, lineHeight = 18.sp, color = pal.pen.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // The case over it: a sheen and the wear of the paper underneath.
        Canvas(Modifier.fillMaxSize()) {
            wornSleeve(wear * 0.6f, record.seed)
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.08f)), Offset.Zero, Offset(size.width, size.height)))
            drawRect(Color.White.copy(alpha = 0.3f), Offset(size.width * 0.04f, 0f), Size(size.width * 0.012f, size.height))
        }
    }
}

/** A record's front in the app's look: a sleeve for vinyl, a J-card in its case for tape. */
@Composable
fun Cover(record: Record, wear: Float, modifier: Modifier = Modifier, lettering: Boolean = true, picture: ImageBitmap? = null) {
    if (U.tape) JCard(record, wear, modifier, lettering, picture)
    else Sleeve(record, wear, modifier.aspectRatio(1f), lettering, picture)
}
