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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.data.Draft
import app.sidea.data.Source
import app.sidea.play.Spotify
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.components.Field
import app.sidea.ui.components.TopBar
import app.sidea.ui.ctx
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import com.spotify.protocol.types.ListItem

/** One level of Spotify's browse tree: the item it opens (null for the top) and what's in it. */
private class Level(val parent: ListItem?, val title: String) {
    var items by mutableStateOf<List<ListItem>?>(null)
    var failed by mutableStateOf(false)
}

/**
 * Spotify's own shelves, browsed from here without opening Spotify: recently played, your library,
 * what it suggests. Spotify's catalogue can't be searched without the internet, so the box narrows
 * down what's on the shelf in view. An album goes to the counter with its cover and its address.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SpotifyScreen(home: Home, go: Go) {
    val c = ctx()
    val container = c.container
    val spotify = container.spotify
    val id = home.settings.spotifyId
    val top = stringResource(R.string.spotify_browse)
    val levels = remember { mutableStateListOf(Level(null, top)) }
    var filter by remember { mutableStateOf("") }
    val level = levels.last()

    LaunchedEffect(level) {
        if (level.items != null) return@LaunchedEffect
        spotify.browse(c, id, level.parent) { list ->
            if (list == null) level.failed = true else level.items = list
        }
    }

    fun back() {
        if (levels.size > 1) {
            levels.removeAt(levels.lastIndex)
            filter = ""
        } else go.back()
    }
    androidx.activity.compose.BackHandler(levels.size > 1) { back() }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().imePadding()) {
        TopBar(level.title, ::back)
        Box(Modifier.padding(horizontal = 16.dp)) { Field(filter, { filter = it }, stringResource(R.string.spotify_filter)) }
        Spacer(Modifier.height(8.dp))
        val items = level.items
        when {
            level.failed -> Text(stringResource(R.string.spotify_browse_failed, spotify.error.value.orEmpty()), style = UType.Muted, modifier = Modifier.padding(16.dp))
            items == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = U.c.lamp) }
            else -> {
                val q = filter.trim().lowercase()
                val shown = items.filter { q.isEmpty() || it.title.orEmpty().lowercase().contains(q) || it.subtitle.orEmpty().lowercase().contains(q) }
                if (shown.isEmpty()) Text(stringResource(R.string.spotify_empty), style = UType.Muted, modifier = Modifier.padding(16.dp))
                LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
                    items(shown, key = { it.id + it.uri }) { item ->
                        Row(item, spotify) {
                            when {
                                Spotify.isAlbum(item.uri) -> spotify.image(item) { art ->
                                    container.draft.value = Draft(title = item.title.orEmpty(), artist = item.subtitle.orEmpty(), source = Source.STREAM, cover = art, link = item.uri)
                                    go.buy()
                                }
                                item.hasChildren -> {
                                    levels.add(Level(item, item.title.orEmpty()))
                                    filter = ""
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Row(item: ListItem, spotify: Spotify, onClick: () -> Unit) {
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
        Box(Modifier.size(52.dp).clip(U.RSm).background(U.c.surface2)) {
            art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
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
