package app.sidea.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import app.sidea.ui.theme.Mono
import app.sidea.ui.theme.U
import kotlin.math.log10
import kotlin.math.sin

/** A peak (0 to 1) as a meter position (0 to 1): −20 VU at the left stop, +3 at the right. */
fun meter(peak: Float): Float {
    val db = 20 * log10(peak.coerceAtLeast(0.0001f)) + 6f
    return ((db + 20f) / 23f).coerceIn(0f, 1f)
}

/**
 * The level meters. On vinyl, two lit VU dials with needles; on tape, two rows of LEDs. [levels] gives
 * the live peaks, or null when the sound plays elsewhere and can't be read: then the needles just
 * breathe while [playing].
 */
@Composable
fun Meters(levels: (() -> Pair<Float, Float>)?, playing: Boolean, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val pal = U.c
    val tape = U.tape
    var l by remember { mutableFloatStateOf(0f) }
    var r by remember { mutableFloatStateOf(0f) }
    val read by rememberUpdatedState(levels)
    val on by rememberUpdatedState(playing && !Art.still)
    LaunchedEffect(Unit) {
        var last = 0L
        var t = 0f
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 0f else (now - last) / 1e9f
            last = now
            t += dt
            val (tl, tr) = when {
                !on -> 0f to 0f
                read != null -> read!!().let { meter(it.first) to meter(it.second) }
                else -> (0.55f + 0.12f * sin(t * 2.1f) + 0.08f * sin(t * 5.3f)) to (0.53f + 0.12f * sin(t * 1.7f + 1f) + 0.08f * sin(t * 4.1f))
            }
            // A VU needle takes about 300 ms to get there and as long to fall back.
            val k = (dt * if (tape) 14f else 7f).coerceAtMost(1f)
            l += (tl - l) * k
            r += (tr - r) * k
        }
    }
    // Screenshots show the needles mid-swing.
    val sl = if (Art.still && playing) 0.62f else l
    val sr = if (Art.still && playing) 0.57f else r
    Canvas(modifier) {
        if (tape) leds(sl, sr, text) else {
            val gap = size.width * 0.04f
            val w = (size.width - gap) / 2
            dial(Offset.Zero, Size(w, size.height), sl, "L", text, pal.lamp, playing)
            dial(Offset(w + gap, 0f), Size(w, size.height), sr, "R", text, pal.lamp, playing)
        }
    }
}

private fun DrawScope.dial(o: Offset, s: Size, at: Float, name: String, text: TextMeasurer, lamp: Color, lit: Boolean) {
    val corner = CornerRadius(s.width * 0.05f)
    drawRoundRect(Color(0xFF2A2018), o, s, corner)
    val face = Size(s.width * 0.92f, s.height * 0.86f)
    val fo = o + Offset(s.width * 0.04f, s.height * 0.07f)
    val glow = if (lit) Color(0xFFF7D9A2) else Color(0xFFD9C8A8)
    drawRoundRect(Brush.radialGradient(listOf(glow, glow.copy(red = glow.red * 0.85f, green = glow.green * 0.8f, blue = glow.blue * 0.7f)), fo + Offset(face.width / 2, face.height), face.width), fo, face, CornerRadius(s.width * 0.03f))
    val pivot = fo + Offset(face.width / 2, face.height * 1.12f)
    val rad = face.height * 0.95f
    // Scale: black up to 0 VU, red above.
    val from = -130f
    val to = -50f
    val zero = from + (to - from) * (20f / 23f)
    drawArc(Color(0xFF2B2622), from, zero - from, false, pivot - Offset(rad, rad), Size(rad * 2, rad * 2), style = Stroke(s.width * 0.012f))
    drawArc(Color(0xFFC0392B), zero, to - zero, false, pivot - Offset(rad, rad), Size(rad * 2, rad * 2), style = Stroke(s.width * 0.03f))
    for (i in 0..10) {
        val a = from + (to - from) * i / 10f
        drawLine(if (a > zero) Color(0xFFC0392B) else Color(0xFF2B2622), polar(pivot, rad, a), polar(pivot, rad * 1.07f, a), strokeWidth = s.width * 0.008f)
    }
    val px = s.width / density / fontScale
    val vu = text.measure("VU", TextStyle(fontFamily = Mono, fontSize = TextUnit(px * 0.09f, TextUnitType.Sp), color = Color(0xFF2B2622)))
    drawText(vu, topLeft = fo + Offset(face.width / 2 - vu.size.width / 2f, face.height * 0.52f))
    val n = text.measure(name, TextStyle(fontFamily = Mono, fontSize = TextUnit(px * 0.07f, TextUnitType.Sp), color = Color(0xFF6B5D4E)))
    drawText(n, topLeft = fo + Offset(face.width * 0.08f, face.height * 0.72f))
    // Needle, seen only through the window.
    val a = from + (to - from) * at
    clipRect(fo.x, fo.y, fo.x + face.width, fo.y + face.height * 0.88f) {
        drawLine(Color(0xFF1B1714), pivot, polar(pivot, rad * 1.04f, a), strokeWidth = s.width * 0.01f, cap = StrokeCap.Round)
    }
    // The lamp behind the glass.
    if (lit) drawRoundRect(lamp.copy(alpha = 0.08f), fo, face, CornerRadius(s.width * 0.03f))
    drawRect(Color(0xFF2A2018), fo + Offset(0f, face.height * 0.88f), Size(face.width, face.height * 0.12f))
}

private fun DrawScope.leds(l: Float, r: Float, text: TextMeasurer) {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFF15171A), size = size, cornerRadius = CornerRadius(w * 0.02f))
    val n = 20
    val left = w * 0.08f
    val right = w * 0.03f
    val cell = (w - left - right) / n
    val px = w / density / fontScale
    listOf(l to "L", r to "R").forEachIndexed { row, (v, name) ->
        val y = h * (0.22f + row * 0.36f)
        val label = text.measure(name, TextStyle(fontFamily = Mono, fontSize = TextUnit(px * 0.03f, TextUnitType.Sp), color = Color(0xFF8C9096)))
        drawText(label, topLeft = Offset(w * 0.03f, y + h * 0.1f - label.size.height / 2f))
        for (i in 0 until n) {
            val on = v * n > i
            val c = when {
                i >= n - 3 -> Color(0xFFE5452F)
                i >= n - 7 -> Color(0xFFF2B33D)
                else -> Color(0xFF5FD38B)
            }
            drawRoundRect(if (on) c else c.copy(alpha = 0.12f), Offset(left + i * cell, y), Size(cell * 0.72f, h * 0.2f), CornerRadius(cell * 0.15f))
        }
    }
    val scale = listOf("-20", "-10", "-5", "0", "+3")
    scale.forEachIndexed { i, s ->
        val t = text.measure(s, TextStyle(fontFamily = Mono, fontSize = TextUnit(px * 0.024f, TextUnitType.Sp), color = Color(0xFF6E7278)))
        val x = left + (w - left - right) * listOf(0f, 0.43f, 0.65f, 0.87f, 1f)[i] - t.size.width / 2f
        drawText(t, topLeft = Offset(x.coerceIn(0f, w - t.size.width), h * 0.86f - t.size.height / 2f))
    }
}
