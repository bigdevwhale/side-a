package app.sidea.ui.shop

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import app.sidea.container
import app.sidea.data.Draft
import app.sidea.data.Source
import app.sidea.play.SpotifyWeb
import app.sidea.ui.ctx
import kotlinx.coroutines.CompletableDeferred

/**
 * Getting at Spotify's catalogue from a screen. [key] hands out the Web API key; with fresh set it
 * asks Spotify for a new one (its permission screen, which returns straight away once allowed).
 */
class Catalog(private val key: suspend (fresh: Boolean) -> String?) {
    /** Runs [block] with a good key, asking again once if Spotify turns it down. Null without a key. */
    suspend fun <T> withKey(block: suspend (String) -> T): T? {
        val k = key(false) ?: return null
        return try {
            block(k)
        } catch (_: SpotifyWeb.Expired) {
            block(key(true) ?: return null)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberCatalog(clientId: String): Catalog {
    val c = ctx()
    val spotify = c.container.spotify
    val pending = remember { arrayOfNulls<CompletableDeferred<String?>>(1) }
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        spotify.refused(res.resultCode, res.data)
        pending[0]?.complete(spotify.key())
    }
    return remember(clientId) {
        Catalog { fresh ->
            if (!fresh) spotify.key()?.let { return@Catalog it }
            if (clientId.isBlank()) return@Catalog null
            spotify.expired()
            val wait = CompletableDeferred<String?>()
            pending[0] = wait
            try {
                allow.launch(spotify.permission(clientId))
                wait.await()
            } catch (_: ActivityNotFoundException) {
                null
            }
        }
    }
}

/** An album from Spotify's catalogue, made ready for the counter: name, year, songs, cover and address. */
suspend fun albumDraft(key: String, id: String): Draft {
    val (album, tracks) = SpotifyWeb.album(key, id)
    return Draft(
        title = album.name,
        artist = album.artist,
        year = album.year,
        source = Source.STREAM,
        tracks = tracks,
        cover = SpotifyWeb.cover(album.image),
        link = album.uri,
    )
}
