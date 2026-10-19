package app.sidea.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A record as it comes off the shop shelf, before it's paid for. */
data class Draft(
    val title: String,
    val artist: String,
    val year: Int? = null,
    val source: Source,
    val folder: String? = null,
    val tracks: List<Track> = emptyList(),
    val cover: Bitmap? = null,
    val seed: Long = (1L..Long.MAX_VALUE / 2).random(),
)

/** Reads an album folder: its songs in order, their tags, and a sleeve picture if there is one. */
object FolderScan {
    private val audio = setOf("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav", "wma", "alac", "ape")
    private val pictures = listOf("cover", "folder", "front", "album", "artwork")

    private class Doc(val uri: Uri, val name: String, val mime: String)

    private data class Song(val uri: Uri, val name: String, val title: String?, val artist: String?, val album: String?, val year: Int?, val disc: Int, val number: Int, val ms: Long)

    suspend fun scan(c: Context, tree: Uri): Draft? = withContext(Dispatchers.IO) {
        val root = DocumentsContract.getTreeDocumentId(tree)
        val docs = list(c, tree, root, depth = 2)
        val songs = docs.filter { it.mime.startsWith("audio/") || it.name.substringAfterLast('.', "").lowercase() in audio }
        if (songs.isEmpty()) return@withContext null

        var picture: Bitmap? = null
        val read = songs.map { d ->
            val m = MediaMetadataRetriever()
            try {
                m.setDataSource(c, d.uri)
                if (picture == null) picture = m.embeddedPicture?.let(Covers::decode)
                Song(
                    uri = d.uri,
                    name = d.name,
                    title = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    year = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull(),
                    disc = leading(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)) ?: 1,
                    number = leading(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)) ?: leading(d.name) ?: 0,
                    ms = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                )
            } catch (_: Exception) {
                Song(d.uri, d.name, null, null, null, null, 1, leading(d.name) ?: 0, 0)
            } finally {
                m.release()
            }
        }.filter { it.ms > 0 }.sortedWith(compareBy<Song>({ it.disc }, { it.number }, { it.name.lowercase() }))
        if (read.isEmpty()) return@withContext null

        if (picture == null) {
            val image = docs.filter { it.mime.startsWith("image/") }
                .sortedBy { d -> pictures.indexOfFirst { d.name.lowercase().startsWith(it) }.let { if (it < 0) 99 else it } }
                .firstOrNull()
            picture = image?.let { d -> c.contentResolver.openInputStream(d.uri)?.use { Covers.decode(it.readBytes()) } }
        }

        val folderName = docs.firstOrNull()?.let { name(c, tree, root) } ?: ""
        val album = read.mapNotNull { it.album }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val artist = read.mapNotNull { it.artist }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        Draft(
            title = album ?: folderName,
            artist = artist ?: "",
            year = read.firstNotNullOfOrNull { it.year },
            source = Source.FOLDER,
            folder = tree.toString(),
            tracks = read.mapIndexed { i, s -> Track(recordId = 0, number = i, title = s.title ?: s.name.substringBeforeLast('.').trimStart('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ' ', '.', '-', '_').ifBlank { s.name }, durationMs = s.ms, uri = s.uri.toString()) },
            cover = picture,
        )
    }

    /** "3/12" → 3, "03 - Song.flac" → 3. */
    private fun leading(s: String?): Int? = s?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

    private fun list(c: Context, tree: Uri, parent: String, depth: Int): List<Doc> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val out = mutableListOf<Doc>()
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        c.contentResolver.query(children, cols, null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getString(0)
                val name = cur.getString(1) ?: ""
                val mime = cur.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth > 0) out += list(c, tree, id, depth - 1)
                } else {
                    out += Doc(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, mime)
                }
            }
        }
        return out
    }

    private fun name(c: Context, tree: Uri, id: String): String {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        return c.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: ""
    }
}
