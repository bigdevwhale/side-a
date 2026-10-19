package app.sidea.play

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.sidea.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Spotify's catalogue over its Web API, for the one thing that needs the internet: finding any album
 * (by search, or from a link shared out of Spotify). It asks only api.spotify.com and i.scdn.co (the
 * covers), only when you search or share, with the short-lived key Spotify hands over when you allow
 * Side A in it.
 */
object SpotifyWeb {
    /** The key ran out or was refused: ask Spotify for a fresh one and try again. */
    class Expired : Exception()

    data class Album(
        val id: String,
        val name: String,
        val artist: String,
        val year: Int?,
        val image: String?,
        val tracks: Int,
        val kind: String,
    ) {
        val uri: String get() = "spotify:album:$id"
    }

    private const val API = "https://api.spotify.com/v1"
    private val covers = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun get(token: String, path: String): JSONObject {
        val c = URL("$API$path").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (c.responseCode == 401) throw Expired()
            if (c.responseCode !in 200..299) {
                val why = runCatching { JSONObject(c.errorStream.bufferedReader().use { it.readText() }).getJSONObject("error").optString("message") }.getOrNull()
                throw IllegalStateException(listOfNotNull("Spotify ${c.responseCode}", why?.takeIf { it.isNotBlank() }).joinToString(": "))
            }
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    private fun album(o: JSONObject): Album {
        val artists = o.optJSONArray("artists")
        val images = o.optJSONArray("images")
        return Album(
            id = o.getString("id"),
            name = o.optString("name"),
            artist = (0 until (artists?.length() ?: 0)).joinToString(", ") { artists!!.getJSONObject(it).optString("name") },
            year = o.optString("release_date").take(4).toIntOrNull(),
            // Spotify lists the biggest picture first.
            image = images?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("url"),
            tracks = o.optInt("total_tracks"),
            kind = o.optString("album_type"),
        )
    }

    /** Albums matching [query], best first. */
    suspend fun search(token: String, query: String): List<Album> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val items = get(token, "/search?type=album&limit=10&q=$q").getJSONObject("albums").getJSONArray("items")
        (0 until items.length()).map { album(items.getJSONObject(it)) }
    }

    /** One album, with its songs in order (as a record's tracks, numbered from 0). */
    suspend fun album(token: String, id: String): Pair<Album, List<Track>> = withContext(Dispatchers.IO) {
        val o = get(token, "/albums/$id")
        val tracks = mutableListOf<Track>()
        var page: JSONObject? = o.getJSONObject("tracks")
        while (page != null) {
            val items = page.getJSONArray("items")
            for (i in 0 until items.length()) {
                val t = items.getJSONObject(i)
                tracks += Track(recordId = 0, number = tracks.size, title = t.optString("name"), durationMs = t.optLong("duration_ms"))
            }
            val next = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
            page = next?.let { get(token, it.removePrefix(API)) }
        }
        album(o) to tracks
    }

    /** The album a song is on. */
    suspend fun albumOf(token: String, trackId: String): String = withContext(Dispatchers.IO) {
        get(token, "/tracks/$trackId").getJSONObject("album").getString("id")
    }

    /** A cover picture, kept in memory once fetched. */
    suspend fun cover(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        covers.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    try {
                        inputStream.use { BitmapFactory.decodeStream(it) }
                    } finally {
                        disconnect()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }?.also { covers.put(url, it) }
    }

    /**
     * Spotify's app shares short spotify.link addresses: follow them to the real one. Null without
     * a short link in [text] or if it leads nowhere.
     */
    suspend fun expand(text: String): String? {
        val short = Regex("""https?://(?:spotify\.link|spoti\.fi)/\S+""").find(text)?.value ?: return null
        return withContext(Dispatchers.IO) {
            try {
                var url = short
                repeat(5) {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 10_000
                    c.readTimeout = 15_000
                    try {
                        val to = c.getHeaderField("Location")
                        if (c.responseCode in 300..399 && to != null) {
                            url = URL(URL(url), to).toString()
                            if (link(url) != null) return@withContext url
                        } else {
                            // Some short links answer with a page that names the real address.
                            val page = c.inputStream.bufferedReader().use { it.readText() }
                            return@withContext Regex("""https://open\.spotify\.com/[^"'\s<>]+""").find(page)?.value
                        }
                    } finally {
                        c.disconnect()
                    }
                }
                url
            } catch (_: Exception) {
                null
            }
        }
    }

    /** What a shared text points at in Spotify: an album or a song, and its id. */
    fun link(text: String): Pair<String, String>? {
        Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?(album|track)/([A-Za-z0-9]{22})""").find(text)?.let { return it.groupValues[1] to it.groupValues[2] }
        Regex("""spotify:(album|track):([A-Za-z0-9]{22})""").find(text)?.let { return it.groupValues[1] to it.groupValues[2] }
        return null
    }
}
