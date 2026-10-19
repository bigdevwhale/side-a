package app.sidea.ui.shop

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.data.Draft
import app.sidea.data.FolderScan
import app.sidea.data.Record
import app.sidea.data.Source
import app.sidea.domain.Shop
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.art.Cover
import app.sidea.ui.components.IconDisc
import app.sidea.ui.components.SectionTitle
import app.sidea.ui.components.SoftButton
import app.sidea.ui.components.TopBar
import app.sidea.ui.components.UCard
import app.sidea.ui.ctx
import app.sidea.ui.home.Awning
import app.sidea.ui.home.allowanceText
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import app.sidea.ui.w
import kotlinx.coroutines.launch

/** Opens the system page where the app may be allowed to see what other players play. */
fun openListenerSettings(c: android.content.Context) {
    try {
        c.startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        c.startActivity(Intent(SystemSettings.ACTION_SETTINGS))
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ShopScreen(home: Home, go: Go) {
    val c = ctx()
    val container = c.container
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var empty by remember { mutableStateOf(false) }
    val stream by container.stream.state.collectAsStateWithLifecycle()
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    DisposableEffect(resumed) {
        container.stream.watch()
        onDispose { container.stream.unwatch() }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        empty = false
        scope.launch {
            val draft = try { FolderScan.scan(c, uri) } catch (_: Exception) { null }
            busy = false
            if (draft == null) empty = true
            else {
                container.draft.value = draft
                go.buy()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(U.c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(w(R.string.shop_v, R.string.shop_t), go::back)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
                UCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Awning(Modifier.fillMaxWidth().height(22.dp))
                    Column(Modifier.padding(16.dp)) {
                        Text(allowanceText(home), style = UType.Title.copy(color = if (home.allowance.canBuy) U.c.lamp else U.c.ink))
                        Spacer(Modifier.height(6.dp))
                        Text(w(R.string.shop_rules_v, R.string.shop_rules_t), style = UType.Muted)
                    }
                }

                SectionTitle(stringResource(R.string.find_album))
                Option(Icons.Rounded.GraphicEq, stringResource(R.string.from_player), w(R.string.from_player_sub_v, R.string.from_player_sub_t), null)
                PlayerNow(stream.granted, stream.now?.takeIf { !it.album.isNullOrBlank() }) { now ->
                    // With Spotify connected, its own word on which album this is, to start it later without opening Spotify.
                    val known = container.spotify.album.value?.takeIf { now.pkg == app.sidea.play.Spotify.PACKAGE && app.sidea.domain.Match.album(now.album.orEmpty(), "", it.first, null) }
                    container.draft.value = Draft(title = now.album.orEmpty(), artist = now.artist.orEmpty(), source = Source.STREAM, cover = now.art, link = known?.second)
                    go.buy()
                }
                if (home.settings.spotifyId.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Option(Icons.Rounded.LibraryMusic, stringResource(R.string.from_spotify), stringResource(R.string.from_spotify_sub)) { go.spotify() }
                }
                Spacer(Modifier.height(10.dp))
                Option(Icons.Rounded.Folder, stringResource(R.string.from_folder), w(R.string.from_folder_sub_v, R.string.from_folder_sub_t)) { pick.launch(null) }
                if (empty) Text(stringResource(R.string.folder_empty), style = UType.Muted.copy(color = U.c.lamp), modifier = Modifier.padding(4.dp))
                Spacer(Modifier.height(10.dp))
                Option(Icons.Rounded.Edit, stringResource(R.string.by_hand), stringResource(R.string.by_hand_sub)) {
                    container.draft.value = Draft(title = "", artist = "", source = Source.STREAM)
                    go.buy()
                }

                SectionTitle(stringResource(R.string.held_title, home.held.size, Shop.HOLDS))
                if (home.held.isEmpty()) Text(stringResource(R.string.held_none), style = UType.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                home.held.forEach { r -> Held(r, home, go) }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (busy) {
            Box(Modifier.fillMaxSize().background(U.c.bg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = U.c.lamp)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.scanning), style = UType.Sub)
                }
            }
        }
    }
}

@Composable
private fun Option(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clip(U.RMd).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconDisc(icon, 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = UType.Strong)
            Text(sub, style = UType.Muted)
        }
    }
}

/** What the streaming player is playing, ready to be taken to the counter. */
@Composable
private fun PlayerNow(granted: Boolean, now: app.sidea.play.Playing?, onTake: (app.sidea.play.Playing) -> Unit) {
    val c = ctx()
    Column(Modifier.padding(start = 60.dp, end = 4.dp)) {
        when {
            !granted -> {
                Text(stringResource(R.string.player_access), style = UType.Muted)
                Spacer(Modifier.height(8.dp))
                SoftButton(stringResource(R.string.player_allow), { openListenerSettings(c) })
            }
            now == null -> Text(stringResource(R.string.player_nothing), style = UType.Muted.copy(color = U.c.faint))
            else -> UCard(onClick = { onTake(now) }, padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val art = now.art
                    if (art != null) Image(art.asImageBitmap(), null, Modifier.size(52.dp).clip(U.RSm), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.player_now).uppercase(), style = UType.Eyebrow.copy(color = U.c.lamp))
                        Text(now.album.orEmpty(), style = UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(now.artist.orEmpty(), style = UType.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** A record waiting on the counter: take it home if this week allows, or put it back. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun Held(r: Record, home: Home, go: Go) {
    val c = ctx()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(52.dp).clickable { go.record(r.id) }) { Cover(r, 0f, Modifier.fillMaxWidth(), lettering = false) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable { go.record(r.id) }) {
            Text(r.title, style = UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.artist, style = UType.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (home.allowance.canBuy && !home.full) {
            SoftButton(stringResource(R.string.take_home), { c.container.write { library.bring(r.id) } })
        }
        IconButton(onClick = { c.container.write { library.forget(r.id) } }) { Icon(Icons.Rounded.Close, stringResource(R.string.put_back), tint = U.c.faint) }
    }
}
