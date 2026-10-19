package app.sidea.domain

import kotlin.math.abs

/**
 * When the needle leaves the groove. On a real record, skipping a song means dragging the arm across
 * the vinyl, and that leaves a mark. Here every skip and every jump within a song scratches the record
 * where the needle comes down.
 */
object Needle {
    /** A song left this long before its end counts as played through, not skipped. */
    const val TAIL_MS = 12_000L

    /** A jump smaller than this is just players catching up, not a hand on the arm. */
    const val JUMP_MS = 8_000L

    /** One revolution at 33⅓ rpm: a scratch clicks once each time round. */
    const val TURN_MS = 1_800L

    /** How many times a scratch clicks as the needle crosses it. */
    const val CLICKS = 3

    /** A song changed while it was at [atMs] of [durationMs]: was it skipped? */
    fun skipped(atMs: Long, durationMs: Long): Boolean = durationMs > 0 && atMs < durationMs - TAIL_MS

    /** The position should have been [expectedMs] and is [actualMs]: did someone move the arm? */
    fun jumped(expectedMs: Long, actualMs: Long): Boolean = abs(actualMs - expectedMs) > JUMP_MS

    /**
     * Where on the disc a moment of a side lies, from 0 (the outer edge, where the side starts) to 1
     * (the run-out by the label).
     */
    fun radius(atMs: Long, sideMs: Long): Float = if (sideMs <= 0) 0f else (atMs.toFloat() / sideMs).coerceIn(0f, 1f)
}
