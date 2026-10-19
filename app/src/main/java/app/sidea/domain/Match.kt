package app.sidea.domain

import java.text.Normalizer

/**
 * Telling whether what another player is playing belongs to a record on the shelf. Streaming services
 * decorate album names ("(Remastered 2011)", "[Deluxe Edition]", " - Live"), so both sides are folded
 * to plain letters and digits first.
 */
object Match {
    private val brackets = Regex("""\s*[(\[{][^)\]}]*[)\]}]""")
    private val suffix = Regex("""\s+[-–—]\s+(.*\b(remaster|remastered|deluxe|edition|version|live|mono|stereo|expanded|anniversary)\b.*)$""", RegexOption.IGNORE_CASE)
    private val junk = Regex("""[^\p{L}\p{N}]+""")

    fun norm(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val plain = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
        return plain.replace(brackets, "").replace(suffix, "").lowercase().replace('ё', 'е').replace(junk, " ").trim()
    }

    /** Same words, or one is the start of the other ("Abbey Road" and "Abbey Road Super Deluxe"). */
    private fun close(a: String, b: String): Boolean = a.isNotEmpty() && b.isNotEmpty() && (a == b || a.startsWith("$b ") || b.startsWith("$a "))

    /**
     * Does the playing [album] by [artist] look like the record [title] by [byArtist]? An artist that
     * isn't known on either side doesn't count against it; a different one does.
     */
    fun album(title: String, byArtist: String, album: String?, artist: String?): Boolean {
        if (!close(norm(title), norm(album))) return false
        val a = norm(byArtist)
        val b = norm(artist)
        if (a.isEmpty() || b.isEmpty()) return true
        return a == b || a in b || b in a
    }

    /** The same song, as titles go. */
    fun track(a: String?, b: String?): Boolean = norm(a).let { it.isNotEmpty() && it == norm(b) }
}
