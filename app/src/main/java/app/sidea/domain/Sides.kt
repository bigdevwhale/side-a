package app.sidea.domain

import kotlin.math.abs
import kotlin.math.exp

/** Splitting an album over two sides of a record, and what a lot of listening does to it. */
object Sides {
    /** A record of unknown length is taken to be this long: a typical LP. */
    const val TYPICAL_MS = 40L * 60_000

    /**
     * Where side B begins: the track index that splits the running time most evenly, with at least
     * one track on each side. A single track is all side A (returns its size).
     */
    fun split(durations: List<Long>): Int {
        if (durations.size < 2) return durations.size
        val total = durations.sum()
        var best = 1
        var bestGap = Long.MAX_VALUE
        var run = 0L
        for (i in 0 until durations.size - 1) {
            run += durations[i]
            val gap = abs(total - 2 * run)
            if (gap < bestGap) {
                bestGap = gap
                best = i + 1
            }
        }
        return best
    }

    /** Side 0 is A, 1 is B: the indices of its tracks. */
    fun range(side: Int, size: Int, split: Int): IntRange = if (side == 0) 0 until split else split until size

    /** How many times the whole record has been played, from the time spent listening. */
    fun plays(listenedMs: Long, lengthMs: Long): Float = listenedMs.toFloat() / (if (lengthMs > 0) lengthMs else TYPICAL_MS)

    /**
     * Wear from 0 (sealed) to 1 (loved to bits). It comes quickly at first and then slowly: twenty
     * plays make a record look clearly lived with.
     */
    fun wear(plays: Float): Float = (1 - exp(-plays / 20f)).coerceIn(0f, 1f)
}
