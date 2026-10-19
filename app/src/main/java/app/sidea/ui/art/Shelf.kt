package app.sidea.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import app.sidea.data.Record
import app.sidea.ui.theme.Hand
import app.sidea.ui.theme.Palette
import app.sidea.ui.theme.U
import app.sidea.ui.theme.Wide
import kotlin.random.Random

/** One thing on the shelf: the record, how worn, and whether it's new this week (a price sticker still on). */
data class Spine(val record: Record, val wear: Float, val fresh: Boolean)

/** Vinyl: three walnut planks of eight. Tape: a black rack, two columns of twelve. */
private const val VINYL_ROWS = 3
private const val VINYL_COLS = 8
private const val TAPE_ROWS = 12
private const val TAPE_COLS = 2

/** Width over height of the whole shelf. */
fun shelfAspect(tape: Boolean) = if (tape) 1.18f else 0.98f

/**
 * Everything on the shelf, 24 places with the empty ones showing. Records stand with their
 * spines out and crack along the spine with wear; cassettes lie in a rack, spines out, titles in
 * ballpoint. A tap opens one.
 */
@Composable
fun Shelf(spines: List<Spine>, onOpen: (Record) -> Unit, modifier: Modifier = Modifier) {
    val pal = U.c
    val tape = U.tape
    val text = rememberTextMeasurer()
    val open by rememberUpdatedState(onOpen)
    val items by rememberUpdatedState(spines)
    Canvas(
        modifier
            .aspectRatio(shelfAspect(tape))
            .pointerInput(tape) {
                detectTapGestures { p ->
                    val i = if (tape) tapeSlot(p, size.width.toFloat(), size.height.toFloat()) else vinylSlot(p, size.width.toFloat(), size.height.toFloat())
                    items.getOrNull(i ?: -1)?.let { open(it.record) }
                }
            },
    ) {
        if (tape) rack(spines, pal, text) else planks(spines, pal, text)
    }
}

// Vinyl -----------------------------------------------------------------------------------------------

private fun vinylGeometry(w: Float, h: Float): Triple<Float, Float, Float> {
    val side = w * 0.035f
    val row = h / VINYL_ROWS
    val slot = (w - 2 * side) / VINYL_COLS
    return Triple(side, row, slot)
}

private fun vinylSlot(p: Offset, w: Float, h: Float): Int? {
    val (side, row, slot) = vinylGeometry(w, h)
    val r = (p.y / row).toInt()
    val c = ((p.x - side) / slot).toInt()
    if (r !in 0 until VINYL_ROWS || c !in 0 until VINYL_COLS || p.x < side) return null
    return r * VINYL_COLS + c
}

private fun DrawScope.planks(spines: List<Spine>, pal: Palette, text: TextMeasurer) {
    val w = size.width
    val h = size.height
    val (side, row, slot) = vinylGeometry(w, h)
    val board = row * 0.07f
    // The cabinet's back and sides.
    drawRect(Brush.verticalGradient(listOf(pal.shelfDark, Color(0xFF120D09))))
    drawRect(pal.shelf, Offset.Zero, Size(side, h))
    drawRect(pal.shelf, Offset(w - side, 0f), Size(side, h))
    for (r in 0 until VINYL_ROWS) {
        val top = r * row
        val floor = top + row - board
        // Shadow at the back of the compartment.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), top, top + row * 0.3f), Offset(side, top), Size(w - 2 * side, row * 0.3f))
        for (c in 0 until VINYL_COLS) {
            val i = r * VINYL_COLS + c
            val x = side + c * slot
            val s = spines.getOrNull(i)
            if (s == null) {
                // An empty place: just the gap.
                drawRect(Color.Black.copy(alpha = 0.22f), Offset(x + slot * 0.06f, top + row * 0.1f), Size(slot * 0.88f, floor - top - row * 0.1f))
                continue
            }
            spine(s, Offset(x + slot * 0.05f, top + row * 0.06f), Size(slot * 0.9f, floor - top - row * 0.06f), text)
        }
        // The plank.
        drawRect(Brush.verticalGradient(listOf(pal.shelfLight, pal.shelf), floor, floor + board), Offset(0f, floor), Size(w, board))
        drawRect(Color.Black.copy(alpha = 0.3f), Offset(0f, floor + board * 0.85f), Size(w, board * 0.15f))
    }
}

/** A record's spine: its colour, the title running up it, cracks across it where it's been pulled out a hundred times. */
private fun DrawScope.spine(s: Spine, o: Offset, sz: Size, text: TextMeasurer) {
    val r = s.record
    val base = Sleeves.spine(r.seed)
    val p = Sleeves.palette(r.seed)
    drawRect(base, o, sz)
    drawRect(p[0], o + Offset(0f, sz.height * 0.04f), Size(sz.width, sz.height * 0.012f))
    drawRect(p[0], o + Offset(0f, sz.height * 0.944f), Size(sz.width, sz.height * 0.012f))
    drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.25f)), o.x, o.x + sz.width), o, sz)
    val ink = Sleeves.inkOn(base)
    val px = sz.width / density / fontScale
    val room = Constraints(maxWidth = (sz.height * 0.84f).toInt().coerceAtLeast(1))
    val title = text.measure(
        r.title,
        TextStyle(fontFamily = Wide, fontWeight = FontWeight.SemiBold, fontSize = TextUnit(px * 0.25f, TextUnitType.Sp), color = ink),
        overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = room,
    )
    val artist = text.measure(
        r.artist.uppercase(),
        TextStyle(fontFamily = Wide, fontWeight = FontWeight.Medium, fontSize = TextUnit(px * 0.17f, TextUnitType.Sp), letterSpacing = 0.08.em, color = ink.copy(alpha = 0.8f)),
        overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = room,
    )
    val cx = o.x + sz.width / 2
    val cy = o.y + sz.height / 2
    // Read bottom to top, as spines are in Europe: title, and the artist under it.
    rotate(-90f, Offset(cx, cy)) {
        val block = title.size.height + artist.size.height * 0.9f
        val top = cy - block / 2
        drawText(title, topLeft = Offset(cx - sz.height * 0.42f, top))
        if (r.artist.isNotBlank()) drawText(artist, topLeft = Offset(cx - sz.height * 0.42f, top + title.size.height * 0.92f))
    }
    if (s.wear > 0.05f) {
        val rnd = Random(r.seed)
        repeat((s.wear * 6).toInt() + 1) {
            val y = o.y + sz.height * (0.08f + 0.84f * rnd.nextFloat())
            drawLine(Color.White, Offset(o.x + sz.width * (0.1f + 0.3f * rnd.nextFloat()), y), Offset(o.x + sz.width * (0.6f + 0.3f * rnd.nextFloat()), y + sz.width * (rnd.nextFloat() - 0.5f) * 0.2f), strokeWidth = sz.width * 0.018f, alpha = 0.1f + 0.25f * s.wear)
        }
        drawRect(Color.White, o, sz, alpha = 0.08f * s.wear)
    }
    if (s.fresh) sticker(Offset(o.x + sz.width / 2, o.y + sz.height * 0.035f + sz.width * 0.25f), sz.width * 0.22f)
}

/** The shop's round orange price sticker, still on a record bought this week. */
private fun DrawScope.sticker(c: Offset, r: Float) {
    drawCircle(Color(0xFFFF8A2A), r, c)
    drawCircle(Color.White.copy(alpha = 0.5f), r * 0.55f, c - Offset(r * 0.2f, r * 0.2f), alpha = 0.4f)
}

// Tape ------------------------------------------------------------------------------------------------

private fun tapeGeometry(w: Float, h: Float): Triple<Float, Float, Float> {
    val pad = w * 0.04f
    val col = (w - pad * 3) / TAPE_COLS
    val row = (h - pad * 2) / TAPE_ROWS
    return Triple(pad, col, row)
}

private fun tapeSlot(p: Offset, w: Float, h: Float): Int? {
    val (pad, col, row) = tapeGeometry(w, h)
    val c = when {
        p.x in pad..(pad + col) -> 0
        p.x in (2 * pad + col)..(2 * pad + 2 * col) -> 1
        else -> return null
    }
    val r = ((p.y - pad) / row).toInt()
    if (r !in 0 until TAPE_ROWS) return null
    return c * TAPE_ROWS + r
}

private fun DrawScope.rack(spines: List<Spine>, pal: Palette, text: TextMeasurer) {
    val w = size.width
    val h = size.height
    val (pad, col, row) = tapeGeometry(w, h)
    drawRoundRect(Brush.verticalGradient(listOf(pal.shelfLight, pal.shelf, pal.shelfDark)), cornerRadius = CornerRadius(w * 0.02f))
    for (c in 0 until TAPE_COLS) {
        val x = pad + c * (col + pad)
        drawRect(Color.Black.copy(alpha = 0.55f), Offset(x, pad), Size(col, h - 2 * pad))
        for (r in 0 until TAPE_ROWS) {
            val y = pad + r * row
            val s = spines.getOrNull(c * TAPE_ROWS + r)
            if (s != null) caseSpine(s, Offset(x + col * 0.02f, y + row * 0.08f), Size(col * 0.96f, row * 0.84f), pal, text)
            // The rack's little ledge under every slot.
            drawRect(pal.shelfLight, Offset(x, y + row * 0.94f), Size(col, row * 0.06f), alpha = 0.6f)
        }
    }
}

/** A cassette case seen edge on: clear plastic over the J-card's spine, the title written in pen. */
private fun DrawScope.caseSpine(s: Spine, o: Offset, sz: Size, pal: Palette, text: TextMeasurer) {
    val r = s.record
    val p = Sleeves.palette(r.seed)
    drawRect(pal.paper, o, sz)
    drawRect(p[1], o, Size(sz.width * 0.08f, sz.height))
    drawRect(p[2], o + Offset(sz.width * 0.08f, 0f), Size(sz.width * 0.025f, sz.height))
    val px = sz.height / density / fontScale
    val line = text.measure(
        if (r.artist.isBlank()) r.title else "${r.artist} — ${r.title}",
        TextStyle(fontFamily = Hand, fontSize = TextUnit(px * 0.78f, TextUnitType.Sp), color = pal.pen),
        overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = (sz.width * 0.82f).toInt().coerceAtLeast(1)),
    )
    drawText(line, topLeft = Offset(o.x + sz.width * 0.14f, o.y + sz.height / 2 - line.size.height / 2f))
    if (s.wear > 0.05f) {
        drawRect(Color(0xFFD9B57A), o, sz, alpha = 0.3f * s.wear, blendMode = androidx.compose.ui.graphics.BlendMode.Multiply)
        val rnd = Random(r.seed)
        repeat((s.wear * 6).toInt()) {
            val x = o.x + sz.width * rnd.nextFloat()
            drawLine(Color.White, Offset(x, o.y), Offset(x + sz.width * 0.05f, o.y + sz.height), strokeWidth = sz.height * 0.04f, alpha = 0.35f * s.wear)
        }
    }
    // The case: a clear lid with its hinge.
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent, Color.White.copy(alpha = 0.1f)), o.y, o.y + sz.height), o, sz)
    drawRect(Color.Black.copy(alpha = 0.35f), o, Size(sz.width, sz.height * 0.08f))
    if (s.fresh) sticker(Offset(o.x + sz.width * 0.94f, o.y + sz.height / 2), sz.height * 0.28f)
}
