package app.sidea.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.data.Draft
import app.sidea.data.Source
import app.sidea.play.SpotifyWeb
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.components.PrimaryButton
import app.sidea.ui.components.TopBar
import app.sidea.ui.ctx
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType

/**
 * Something shared from Spotify ("Share → Side A"): an album, or a song (then its album). With a
 * Spotify Client ID it's looked up in the catalogue and comes to the counter complete; without one,
 * it comes with its address and the name is written by hand.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SharedScreen(text: String, home: Home, go: Go) {
    val c = ctx()
    val container = c.container
    val catalog = rememberCatalog(home.settings.spotifyId)
    var problem by remember { mutableStateOf<String?>(null) }
    var bare by remember { mutableStateOf<Draft?>(null) }

    LaunchedEffect(text) {
        val full = SpotifyWeb.link(text)?.let { text } ?: SpotifyWeb.expand(text) ?: text
        val (kind, id) = SpotifyWeb.link(full) ?: run {
            problem = c.getString(R.string.shared_not_album)
            return@LaunchedEffect
        }
        // What's left of the shared text once the link is gone is often the title.
        val words = text.replace(Regex("""https?://\S+"""), "").trim().trim('-', '—', ':', '·').trim()
        val fallback = Draft(title = words, artist = "", source = Source.STREAM, link = if (kind == "album") "spotify:album:$id" else null)
        try {
            val draft = catalog.withKey { key ->
                val albumId = if (kind == "album") id else SpotifyWeb.albumOf(key, id)
                albumDraft(key, albumId)
            }
            if (draft != null) {
                container.draft.value = draft
                go.buyInstead()
                return@LaunchedEffect
            }
            problem = c.getString(R.string.shared_no_key)
        } catch (e: Exception) {
            problem = c.getString(R.string.spotify_search_failed, e.message ?: e.javaClass.simpleName)
        }
        bare = fallback.takeIf { it.link != null }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.counter), go::back)
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            val p = problem
            if (p == null) {
                CircularProgressIndicator(color = U.c.lamp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.shared_fetching), style = UType.Sub, textAlign = TextAlign.Center)
            } else {
                Text(p, style = UType.Body, textAlign = TextAlign.Center)
                bare?.let { d ->
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(stringResource(R.string.shared_by_hand), {
                        container.draft.value = d
                        go.buyInstead()
                    }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
