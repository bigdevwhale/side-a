package app.sidea.ui.shop

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.data.Draft
import app.sidea.data.Source
import app.sidea.play.Spotify
import app.sidea.play.SpotifyWeb
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.components.Field
import app.sidea.ui.components.TopBar
import app.sidea.ui.ctx
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import com.spotify.protocol.types.ListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One level of Spotify's browse tree: the item it opens (null for the top) and what's in it. */
private class Level(val parent: ListItem?, val title: String) {
    var items by mutableStateOf<List<ListItem>?>(null)
    var failed by mutableStateOf(false)
}

/**
 * Spotify from the shop. At the top, a search of Spotify's whole catalogue (over its Web API) and,
 * below it, Spotify's own shelves to browse (recently played, your library, picks), which need no
 * internet. Inside a shelf the box narrows down what's on it. An album goes to the counter with its
 * cover, year, songs and address.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SpotifyScreen(home: Home, go: Go) {
    val c = ctx()
    val container = c.container
    val spotify = container.spotify
    val id = home.settings.spotifyId
    val catalog = rememberCatalog(id)
    val scope = rememberCoroutineScope()
    val top = stringResource(R.string.spotify_browse)
    val levels = remember { mutableStateListOf(Level(null, top)) }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<SpotifyWeb.Album>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var taking by remember { mutableStateOf(false) }
    val error by spotify.error.collectAsStateWithLifecycle()
    val level = levels.last()
    val atTop = levels.size == 1

    LaunchedEffect(level) {
        if (level.items != null) return@LaunchedEffect
        spotify.browse(c, id, level.parent) { list ->
            if (list == null) level.failed = true else level.items = list
        }
    }
    // Search the catalogue a moment after typing stops.
    LaunchedEffect(query, atTop) {
        val q = query.trim()
        if (!atTop || q.length < 2) {
            found = null
            return@LaunchedEffect
        }
        delay(450)
        searching = true
        problem = null
        found = try {
            catalog.withKey { SpotifyWeb.search(it, q) } ?: run {
                problem = c.getString(R.string.spotify_search_key)
                null
            }
        } catch (e: Exception) {
            problem = c.getString(R.string.spotify_search_failed, e.message ?: e.javaClass.simpleName)
            null
        }
        searching = false
    }

    fun take(albumId: String) {
        if (taking) return
        taking = true
        scope.launch {
            try {
                catalog.withKey { albumDraft(it, albumId) }?.let {
                    container.draft.value = it
                    go.buy()
                }
            } catch (e: Exception) {
                problem = c.getString(R.string.spotify_search_failed, e.message ?: e.javaClass.simpleName)
            }
            taking = false
        }
    }

    fun back() {
        if (levels.size > 1) {
            levels.removeAt(levels.lastIndex)
            query = ""
        } else go.back()
    }
    androidx.activity.compose.BackHandler(levels.size > 1) { back() }

    Box(Modifier.fillMaxSize().background(U.c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            TopBar(level.title, ::back)
            Box(Modifier.padding(horizontal = 16.dp)) {
                Field(query, { query = it }, stringResource(if (atTop) R.string.spotify_search else R.string.spotify_filter))
            }
            Spacer(Modifier.height(8.dp))
            problem?.let { Text(it, style = UType.Small.copy(color = U.c.lamp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            val results = found
            when {
                atTop && (results != null || searching) -> {
                    if (searching && results == null) Spinner()
                    LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
                        items(results.orEmpty(), key = { it.id }) { a -> Found(a) { take(a.id) } }
                    }
                }
                level.failed -> Text(stringResource(R.string.spotify_browse_failed, error.orEmpty()), style = UType.Muted, modifier = Modifier.padding(16.dp))
                level.items == null -> Spinner()
                else -> {
                    val q = if (atTop) "" else query.trim().lowercase()
                    val shown = level.items.orEmpty().filter { q.isEmpty() || it.title.orEmpty().lowercase().contains(q) || it.subtitle.orEmpty().lowercase().contains(q) }
                    if (shown.isEmpty()) Text(stringResource(R.string.spotify_empty), style = UType.Muted, modifier = Modifier.padding(16.dp))
                    LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
                        items(shown, key = { it.id + it.uri }) { item ->
                            Shelf(item, spotify) {
                                when {
                                    Spotify.isAlbum(item.uri) -> {
                                        val albumId = item.uri.removePrefix("spotify:album:")
                                        if (id.isNotBlank()) take(albumId)
                                        else spotify.image(item) { art ->
                                            container.draft.value = Draft(title = item.title.orEmpty(), artist = item.subtitle.orEmpty(), source = Source.STREAM, cover = art, link = item.uri)
                                            go.buy()
                                        }
                                    }
                                    item.hasChildren -> {
                                        levels.add(Level(item, item.title.orEmpty()))
                                        query = ""
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (taking) Box(Modifier.fillMaxSize().background(U.c.bg.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = U.c.lamp) }
    }
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = U.c.lamp) }
}

@Composable
private fun Cover(art: Bitmap?) {
    Box(Modifier.size(52.dp).clip(U.RSm).background(U.c.surface2)) {
        art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** An album found in Spotify's catalogue. */
@Composable
private fun Found(a: SpotifyWeb.Album, onClick: () -> Unit) {
    var art by remember(a.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(a.id) { art = SpotifyWeb.cover(a.image) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(art)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val kind = when (a.kind) {
                "single" -> stringResource(R.string.spotify_single)
                "compilation" -> stringResource(R.string.spotify_compilation)
                else -> stringResource(R.string.spotify_album)
            }
            Text(listOfNotNull(a.artist, a.year?.toString(), kind).joinToString(" · "), style = UType.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.spotify_take), style = UType.Small.copy(color = U.c.lamp))
    }
}

/** Something on one of Spotify's shelves. */
@Composable
private fun Shelf(item: ListItem, spotify: Spotify, onClick: () -> Unit) {
    val album = Spotify.isAlbum(item.uri)
    val enabled = album || item.hasChildren
    var art by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id) { spotify.image(item) { art = it } }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(art)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title.orEmpty(), style = UType.Strong.copy(color = if (enabled) U.c.ink else U.c.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(item.subtitle?.takeIf { it.isNotBlank() }, if (album) stringResource(R.string.spotify_album) else null).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = UType.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.hasChildren && !album) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = U.c.faint)
        if (album) Text(stringResource(R.string.spotify_take), style = UType.Small.copy(color = U.c.lamp))
    }
}
