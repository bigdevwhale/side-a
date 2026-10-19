package app.sidea.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import app.sidea.data.Record
import app.sidea.ui.theme.Mono
import app.sidea.ui.theme.Palette
import app.sidea.ui.theme.U
import app.sidea.ui.theme.Wide
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

/** Shared switches for the drawings. */
object Art {
    /** Screenshots: nothing turns, nothing sways. */
    var still = false
}

/** Where the grooves run on a disc of radius 1: from the edge in to the run-out. */
private const val OUTER = 0.95f
private const val INNER = 0.37f

/** A spot on the disc: [at] of the way through the side (0 at the edge), [angle] round it. */
data class Mark(val at: Float, val angle: Float)

/**
 * A vinyl record seen from above, turned by [turn] degrees: grooves, the brighter bands between
 * songs at [gaps], scratches, a dulling with [wear], and the label with the side on it. The light's
 * sheen stays put while the record turns under it.
 */
fun DrawScope.disc(c: Offset, r: Float, turn: Float, record: Record, side: Int, gaps: List<Float>, marks: List<Mark>, wear: Float, text: TextMeasurer, sideName: String) {
    drawCircle(Color.Black.copy(alpha = 0.45f), r * 1.015f, c + Offset(r * 0.02f, r * 0.03f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1C1B1A), Color(0xFF0D0D0D)), c, r), r, c)
    // Grooves.
    val rings = 46
    for (i in 0..rings) {
        val rr = r * (INNER + (OUTER - INNER) * i / rings)
        drawCircle(Color.White, rr, c, alpha = if (i % 3 == 0) 0.05f else 0.025f, style = Stroke(r * 0.004f))
    }
    // Between songs the groove runs smooth and catches the light.
    gaps.forEach { g ->
        val rr = r * (OUTER - (OUTER - INNER) * g)
        drawCircle(Color(0xFF050505), rr, c, style = Stroke(r * 0.018f))
        drawCircle(Color.White, rr, c, alpha = 0.09f, style = Stroke(r * 0.006f))
    }
    drawCircle(Color.White, r * OUTER, c, alpha = 0.06f, style = Stroke(r * 0.01f))
    // Years of dust and hairlines.
    if (wear > 0.02f) {
        val rnd = Random(record.seed)
        drawCircle(Color(0xFF8A8580), r * 0.98f, c, alpha = 0.08f * wear)
        repeat((wear * 40).toInt()) {
            val rr = r * (INNER + (OUTER - INNER) * rnd.nextFloat())
            val a0 = rnd.nextFloat() * 360f
            rotate(turn, c) {
                drawArc(Color.White, a0, 8f + rnd.nextFloat() * 30f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), alpha = 0.06f + 0.06f * wear, style = Stroke(r * 0.003f))
            }
        }
    }
    // Scratches: short bright cuts that turn with the record.
    rotate(turn, c) {
        marks.forEach { m ->
            val rr = r * (OUTER - (OUTER - INNER) * m.at)
            val p = polar(c, rr, m.angle)
            val dir = m.angle + 90f + (m.angle * 7f) % 50f - 25f
            val half = r * 0.07f
            val a = polar(p, half, dir)
            val b = polar(p, half, dir + 180f)
            drawLine(Color.White, a, b, strokeWidth = r * 0.012f, alpha = 0.12f, cap = StrokeCap.Round)
            drawLine(Color.White, a, b, strokeWidth = r * 0.004f, alpha = 0.7f, cap = StrokeCap.Round)
        }
    }
    // The light from the window, fixed.
    drawCircle(
        Brush.sweepGradient(
            0f to Color.Transparent, 0.08f to Color.White.copy(alpha = 0.10f), 0.16f to Color.Transparent,
            0.5f to Color.Transparent, 0.58f to Color.White.copy(alpha = 0.07f), 0.66f to Color.Transparent, 1f to Color.Transparent,
            center = c,
        ),
        r * OUTER, c,
    )
    // Label.
    val p = Sleeves.palette(record.seed)
    val lr = r * 0.33f
    rotate(turn, c) {
        drawCircle(p[1], lr, c)
        drawCircle(p[0], lr * 0.93f, c, style = Stroke(lr * 0.02f))
        val ink = Sleeves.inkOn(p[1])
        val px = lr / density / fontScale
        val title = text.measure(
            record.title,
            TextStyle(fontFamily = Wide, fontWeight = FontWeight.Medium, fontSize = TextUnit(px * 0.15f, TextUnitType.Sp), color = ink, textAlign = TextAlign.Center),
            overflow = TextOverflow.Ellipsis, maxLines = 2,
            constraints = Constraints.fixedWidth((lr * 1.3f).toInt().coerceAtLeast(1)),
        )
        drawText(title, topLeft = Offset(c.x - lr * 0.65f, c.y - lr * 0.62f))
        val artist = text.measure(
            record.artist.uppercase(),
            TextStyle(fontFamily = Mono, fontSize = TextUnit(px * 0.1f, TextUnitType.Sp), letterSpacing = 0.1.em, color = ink, textAlign = TextAlign.Center),
            overflow = TextOverflow.Ellipsis, maxLines = 1,
            constraints = Constraints.fixedWidth((lr * 1.4f).toInt().coerceAtLeast(1)),
        )
        drawText(artist, topLeft = Offset(c.x - lr * 0.7f, c.y + lr * 0.16f))
        val bottom = text.measure(
            "$sideName  ·  33⅓",
            TextStyle(fontFamily = Mono, fontSize = TextUnit(px * 0.1f, TextUnitType.Sp), letterSpacing = 0.1.em, color = ink.copy(alpha = 0.8f), textAlign = TextAlign.Center),
            maxLines = 1,
            constraints = Constraints.fixedWidth((lr * 1.4f).toInt().coerceAtLeast(1)),
        )
        drawText(bottom, topLeft = Offset(c.x - lr * 0.7f, c.y + lr * 0.45f))
        if (side == 1) drawCircle(ink, lr * 0.05f, c + Offset(lr * 0.6f, 0f), alpha = 0.5f)
    }
    drawCircle(Color(0xFFBDB8AE), r * 0.022f, c)
    drawCircle(Color(0xFF2A2826), r * 0.012f, c)
}

/**
 * The arm's geometry: its pivot sits up and to the right of the platter; the needle reaches any
 * radius by swinging about it.
 */
private class Arm(val c: Offset, val r: Float) {
    val pivot = c + Offset(r * 1.02f, -r * 0.94f)
    val length = r * 1.2f
    private val d = hypot(pivot.x - c.x, pivot.y - c.y)
    private val toCentre = Math.toDegrees(atan2((c.y - pivot.y).toDouble(), (c.x - pivot.x).toDouble())).toFloat()

    /** The arm's angle with the needle at [at] of the way through the side; null puts it on its rest. */
    fun angle(at: Float?): Float {
        if (at == null) return 74f
        val rr = r * (OUTER - (OUTER - INNER) * at.coerceIn(0f, 1f))
        val cos = ((length * length + d * d - rr * rr) / (2 * length * d)).coerceIn(-1f, 1f)
        return toCentre - Math.toDegrees(acos(cos.toDouble())).toFloat()
    }

    /** The cueing lever, by the pivot: tap it to raise or lower the needle. */
    val lever = pivot + Offset(-r * 0.06f, r * 0.32f)

    /** Where the headshell is with the arm at [angle]: tapping it works the lever too. */
    fun head(angle: Float): Offset = polar(polar(pivot, length * 0.82f, angle), length * 0.2f, angle + 22f)

    /** Is [p] on the arm (anywhere from the pivot to the headshell), give or take a fingertip? */
    fun touches(p: Offset, angle: Float): Boolean {
        val head = head(angle)
        val bend = polar(pivot, length * 0.82f, angle)
        return near(p, pivot, bend) || near(p, bend, head) || (p - head).getDistance() < r * 0.14f
    }

    private fun near(p: Offset, a: Offset, b: Offset): Boolean {
        val ab = b - a
        val t = (((p - a).x * ab.x + (p - a).y * ab.y) / (ab.x * ab.x + ab.y * ab.y)).coerceIn(0f, 1f)
        return (p - (a + ab * t)).getDistance() < r * 0.09f
    }

    /** How far through the side a finger at [p] would put the needle. */
    fun at(p: Offset): Float = ((OUTER - hypot(p.x - c.x, p.y - c.y) / r) / (OUTER - INNER)).coerceIn(0f, 1f)
}

/**
 * The tonearm: counterweight, pivot, the long arm, the headshell. Raised, it lifts towards you: its
 * shadow falls further and the headshell looks bigger.
 */
private fun DrawScope.tonearm(arm: Arm, angle: Float, lifted: Boolean, pal: Palette) {
    val r = arm.r
    val needle = polar(arm.pivot, arm.length, angle)
    val back = polar(arm.pivot, r * 0.22f, angle + 180f)
    val shadow = Offset(r * (if (lifted) 0.11f else 0.025f), r * (if (lifted) 0.14f else 0.03f))
    translate(shadow.x, shadow.y) {
        drawLine(Color.Black.copy(alpha = if (lifted) 0.28f else 0.35f), back, needle, strokeWidth = r * 0.04f, cap = StrokeCap.Round)
    }
    // Base and pivot.
    drawCircle(pal.metalDark, r * 0.13f, arm.pivot)
    drawCircle(Brush.radialGradient(listOf(pal.metal, pal.metalDark), arm.pivot - Offset(r * 0.03f, r * 0.03f), r * 0.12f), r * 0.1f, arm.pivot)
    // Counterweight.
    drawLine(pal.metalDark, arm.pivot, back, strokeWidth = r * 0.05f, cap = StrokeCap.Round)
    drawCircle(Brush.radialGradient(listOf(pal.metal, Color(0xFF3A3836)), back, r * 0.08f), r * 0.07f, back)
    // The arm, with a slight bend before the headshell.
    val bend = polar(arm.pivot, arm.length * 0.82f, angle)
    val thick = if (lifted) 1.25f else 1f
    drawLine(pal.metal, arm.pivot, bend, strokeWidth = r * 0.028f * thick, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.5f), arm.pivot, bend, strokeWidth = r * 0.008f * thick, cap = StrokeCap.Round)
    val head = arm.head(angle)
    drawLine(pal.metal, bend, head, strokeWidth = r * 0.028f * thick, cap = StrokeCap.Round)
    rotate(angle + 22f, head) {
        val k = if (lifted) 1.2f else 1f
        drawRect(Color(0xFF26231F), head - Offset(r * 0.04f * k, r * 0.045f * k), Size(r * 0.12f * k, r * 0.09f * k))
        drawRect(pal.lamp.copy(alpha = 0.85f), head + Offset(r * 0.045f * k, -r * 0.045f * k), Size(r * 0.018f * k, r * 0.09f * k))
    }
    // The cueing lever: a slot with its knob at the top (raised) or the bottom (lowered), and arrows.
    val lever = arm.lever
    drawRoundRect(Color(0xFF14110E), lever - Offset(r * 0.045f, r * 0.1f), Size(r * 0.09f, r * 0.2f), CornerRadius(r * 0.04f))
    drawRoundRect(pal.metalDark, lever - Offset(r * 0.012f, r * 0.075f), Size(r * 0.024f, r * 0.15f), CornerRadius(r * 0.012f))
    val knob = lever + Offset(0f, if (lifted) -r * 0.06f else r * 0.06f)
    drawCircle(Color.Black.copy(alpha = 0.4f), r * 0.042f, knob + Offset(r * 0.008f, r * 0.01f))
    drawCircle(Brush.radialGradient(listOf(if (lifted) pal.lamp else pal.metal, pal.metalDark), knob - Offset(r * 0.01f, r * 0.01f), r * 0.05f), r * 0.04f, knob)
    val tip = r * 0.022f
    val up = lever + Offset(r * 0.085f, -r * 0.06f)
    val down = lever + Offset(r * 0.085f, r * 0.06f)
    drawPath(Path().apply { moveTo(up.x, up.y - tip); lineTo(up.x + tip, up.y + tip * 0.6f); lineTo(up.x - tip, up.y + tip * 0.6f); close() }, if (lifted) pal.lamp else pal.metalDark)
    drawPath(Path().apply { moveTo(down.x, down.y + tip); lineTo(down.x + tip, down.y - tip * 0.6f); lineTo(down.x - tip, down.y - tip * 0.6f); close() }, if (lifted) pal.metalDark else pal.lamp)
}

/** The walnut plinth with the platter's rim, and a speed lamp. */
private fun DrawScope.plinth(c: Offset, r: Float, pal: Palette, on: Boolean) {
    val w = size.width
    val h = size.height
    val rnd = Random(7)
    drawRoundRect(Brush.verticalGradient(listOf(pal.shelfLight, pal.shelf, pal.shelfDark)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
    // Grain.
    repeat(26) {
        val y = h * rnd.nextFloat()
        drawLine(pal.shelfDark, Offset(0f, y), Offset(w, y + (rnd.nextFloat() - 0.5f) * h * 0.08f), strokeWidth = w * (0.002f + 0.004f * rnd.nextFloat()), alpha = 0.35f)
    }
    drawRoundRect(Color(0xFF1B1611), Offset(w * 0.025f, h * 0.03f), Size(w * 0.95f, h * 0.94f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f), alpha = 0.92f)
    // Platter rim with its strobe dots.
    drawCircle(Brush.radialGradient(listOf(pal.metal, pal.metalDark), c - Offset(r * 0.3f, r * 0.3f), r * 1.4f), r * 1.06f, c)
    for (i in 0 until 90) {
        drawCircle(Color(0xFF2A2826), r * 0.008f, polar(c, r * 1.035f, i * 4f), alpha = 0.6f)
    }
    // Speed buttons and the lamp.
    val base = Offset(w * 0.08f, h * 0.9f)
    drawCircle(pal.metal, w * 0.022f, base)
    drawCircle(pal.metalDark, w * 0.022f, base + Offset(w * 0.07f, 0f))
    drawCircle(if (on) pal.lamp else Color(0xFF3B2A1D), w * 0.01f, base + Offset(w * 0.14f, 0f))
    if (on) drawCircle(pal.lamp.copy(alpha = 0.25f), w * 0.03f, base + Offset(w * 0.14f, 0f))
}

/**
 * The turntable in the listening room. The record turns while [spinning]; the needle sits at [needle]
 * of the way through the side (null: the arm is on its rest). Dragging the arm moves the needle, and
 * [onMove] gets where it was let go. Tapping the cueing lever or the headshell calls [onCue].
 */
@Composable
fun Turntable(
    record: Record,
    side: Int,
    sideName: String,
    gaps: List<Float>,
    marks: List<Mark>,
    wear: Float,
    spinning: Boolean,
    needle: Float?,
    lifted: Boolean,
    onMove: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
    onCue: (() -> Unit)? = null,
) {
    val pal = U.c
    val text = rememberTextMeasurer()
    var turn by remember { mutableFloatStateOf(37f) }
    val spin by rememberUpdatedState(spinning && !Art.still)
    LaunchedEffect(Unit) {
        var last = 0L
        var speed = 0f
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else (t - last) / 1e9f
                last = t
                // 33⅓ rpm is 200°/s; the platter takes a moment to come up to speed and to slow down.
                val target = if (spin) 200f else 0f
                speed += (target - speed) * (dt * 2.5f).coerceAtMost(1f)
                turn = (turn + speed * dt) % 360f
            }
        }
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val move by rememberUpdatedState(onMove)
    val cue by rememberUpdatedState(onCue)
    val at by rememberUpdatedState(needle)
    Canvas(
        modifier.pointerInput(Unit) {
            val c = Offset(size.width * 0.43f, size.height * 0.5f)
            val r = size.width * 0.37f
            val arm = Arm(c, r)
            detectTapGestures { p ->
                val near = (p - arm.lever).getDistance() < r * 0.16f || arm.touches(p, arm.angle(at))
                if (near) cue?.invoke()
            }
        }.pointerInput(Unit) {
            val c = Offset(size.width * 0.43f, size.height * 0.5f)
            val r = size.width * 0.37f
            val arm = Arm(c, r)
            detectDragGestures(
                onDragStart = { p -> if (move != null) dragging = arm.at(p) },
                onDrag = { ch, _ -> if (dragging != null) dragging = arm.at(ch.position) },
                onDragEnd = { dragging?.let { move?.invoke(it) }; dragging = null },
                onDragCancel = { dragging = null },
            )
        },
    ) {
        val c = Offset(size.width * 0.43f, size.height * 0.5f)
        val r = size.width * 0.37f
        plinth(c, r, pal, spinning)
        disc(c, r, turn, record, side, gaps, marks, wear, text, sideName)
        val arm = Arm(c, r)
        tonearm(arm, arm.angle(dragging ?: needle), lifted || dragging != null, pal)
    }
}

/** The disc on its own, for small places (the "now playing" bar). */
@Composable
fun SmallDisc(record: Record, spinning: Boolean, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    var turn by remember { mutableFloatStateOf(0f) }
    val spin by rememberUpdatedState(spinning && !Art.still)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L && spin) turn = (turn + 200f * (t - last) / 1e9f) % 360f
            last = t
        }
    }
    Canvas(modifier) {
        val r = size.minDimension / 2
        disc(center, r, turn, record, 0, emptyList(), emptyList(), 0f, text, "")
    }
}
