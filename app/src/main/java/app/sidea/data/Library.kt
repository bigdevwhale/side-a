package app.sidea.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Bringing records home, putting them aside, and letting them go. */
class Library(private val context: Context, private val db: SideDatabase, private val settings: SettingsStore) {
    private val dao get() = db.dao()

    /**
     * Brings [draft] home ([Place.SHELF]) or puts it on the counter ([Place.HOLD]). A record bought
     * while gifts are left is one of them.
     */
    suspend fun add(draft: Draft, place: Place): Long {
        val gift = place == Place.SHELF && settings.now().gifts > 0
        draft.folder?.let { keep(Uri.parse(it)) }
        val id = dao.insert(
            Record(
                title = draft.title.trim(),
                artist = draft.artist.trim(),
                year = draft.year,
                source = draft.source.name,
                folder = draft.folder,
                cover = draft.cover?.let { Covers.save(context, it) },
                seed = draft.seed,
                place = place.name,
                gift = gift,
            ),
        )
        if (draft.tracks.isNotEmpty()) dao.insertTracks(draft.tracks.mapIndexed { i, t -> t.copy(id = 0, recordId = id, number = i) })
        if (gift) settings.update { it.copy(gifts = (it.gifts - 1).coerceAtLeast(0)) }
        return id
    }

    /** A held record (or one from the second-hand shop) comes home: it counts as this week's. */
    suspend fun bring(id: Long) {
        val r = dao.get(id) ?: return
        val gift = settings.now().gifts > 0
        dao.update(r.copy(place = Place.SHELF.name, at = System.currentTimeMillis(), gift = gift, goneAt = 0))
        if (gift) settings.update { it.copy(gifts = (it.gifts - 1).coerceAtLeast(0)) }
    }

    /** To the second-hand shop, with a line to remember it by. It keeps its notes and its scratches. */
    suspend fun letGo(id: Long, farewell: String) {
        val r = dao.get(id) ?: return
        dao.update(r.copy(place = Place.GONE.name, goneAt = System.currentTimeMillis(), farewell = farewell.trim()))
    }

    /** Taken back to the shop the same day, or a held record dropped: gone entirely. A gift goes back to the crate. */
    suspend fun forget(id: Long) {
        val r = dao.get(id) ?: return
        dao.forget(id)
        Covers.delete(context, r.cover)
        r.folder?.let { release(Uri.parse(it)) }
        if (r.gift && r.placeOf == Place.SHELF) settings.update { it.copy(gifts = it.gifts + 1) }
    }

    suspend fun rename(id: Long, title: String, artist: String) {
        val r = dao.get(id) ?: return
        dao.update(r.copy(title = title.trim(), artist = artist.trim()))
    }

    private fun keep(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }

    private fun release(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }

    /** Is a folder record's music still there to play? */
    fun reachable(r: Record): Boolean {
        val f = r.folder ?: return false
        return context.contentResolver.persistedUriPermissions.any { it.uri.toString() == f && it.isReadPermission }
    }
}
