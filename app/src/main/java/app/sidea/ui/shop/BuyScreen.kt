package app.sidea.ui.shop

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.data.Draft
import app.sidea.data.Place
import app.sidea.data.Record
import app.sidea.data.Source
import app.sidea.domain.Allowance
import app.sidea.domain.Shop
import app.sidea.domain.Sides
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.T
import app.sidea.ui.art.Cover
import app.sidea.ui.components.Field
import app.sidea.ui.components.PrimaryButton
import app.sidea.ui.components.SectionTitle
import app.sidea.ui.components.SoftButton
import app.sidea.ui.components.TopBar
import app.sidea.ui.ctx
import app.sidea.ui.home.allowanceText
import app.sidea.ui.record.LetGoDialog
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import app.sidea.ui.w
import kotlinx.coroutines.launch

/** At the counter with a record: check the name, then take it home or put it aside. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun BuyScreen(home: Home, go: Go) {
    val c = ctx()
    val container = c.container
    val draft by container.draft.collectAsStateWithLifecycle()
    val d = draft ?: run {
        LaunchedEffect(Unit) { go.back() }
        return
    }
    var title by remember { mutableStateOf(d.title) }
    var artist by remember { mutableStateOf(d.artist) }
    var makeRoom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ready = title.isNotBlank()

    fun add(place: Place, letGo: Pair<Record, String>? = null) = scope.launch {
        letGo?.let { (r, f) -> container.library.letGo(r.id, f) }
        val id = container.library.add(d.copy(title = title, artist = artist), place)
        container.draft.value = null
        go.home()
        if (place == Place.SHELF) go.record(id) else go.shop()
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().imePadding()) {
        TopBar(stringResource(R.string.counter), go::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Preview(d, title, artist)
            }
            Spacer(Modifier.height(12.dp))
            Field(title, { title = it }, stringResource(R.string.album_title))
            Spacer(Modifier.height(8.dp))
            Field(artist, { artist = it }, stringResource(R.string.album_artist))
            Spacer(Modifier.height(10.dp))
            Text(
                if (d.source == Source.FOLDER) w(R.string.source_folder_v, R.string.source_folder_t, T.plural(c, R.plurals.songs, d.tracks.size), T.duration(d.tracks.sumOf { it.durationMs }))
                else stringResource(R.string.source_stream),
                style = UType.Muted,
            )
            if (d.tracks.size > 1) {
                val split = Sides.split(d.tracks.map { it.durationMs })
                for (side in 0..1) {
                    val range = Sides.range(side, d.tracks.size, split)
                    if (range.isEmpty()) continue
                    SectionTitle(stringResource(if (side == 0) R.string.side_a else R.string.side_b) + " · " + T.duration(d.tracks.slice(range).sumOf { it.durationMs }))
                    d.tracks.slice(range).forEachIndexed { i, t ->
                        Row(Modifier.padding(vertical = 3.dp, horizontal = 4.dp)) {
                            Text("${i + 1}.", style = UType.Muted, modifier = Modifier.width(28.dp))
                            Text(t.title, style = UType.Body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(T.duration(t.durationMs), style = UType.Counter)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(allowanceText(home), style = UType.Strong.copy(color = if (home.allowance.canBuy) U.c.lamp else U.c.muted))
            Spacer(Modifier.height(12.dp))
            if (home.allowance.canBuy) {
                PrimaryButton(
                    if (home.allowance is Allowance.Gift) stringResource(R.string.take_gift) else stringResource(R.string.take_home),
                    { if (home.full) makeRoom = true else add(Place.SHELF) },
                    Modifier.fillMaxWidth(),
                    enabled = ready,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (home.held.size < Shop.HOLDS && home.allowance !is Allowance.Gift) {
                SoftButton(stringResource(R.string.hold), { add(Place.HOLD) }, Modifier.fillMaxWidth(), enabled = ready)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.hold_sub), style = UType.Small, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    if (makeRoom) LetGoDialog(home, null, pick = true, onDismiss = { makeRoom = false }) { r, f ->
        makeRoom = false
        add(Place.SHELF, r to f)
    }
}

@Composable
private fun Preview(d: Draft, title: String, artist: String) {
    val picture = remember(d.cover) { d.cover?.asImageBitmap() }
    Cover(Record(title = title.ifBlank { "…" }, artist = artist, seed = d.seed), 0f, Modifier.fillMaxWidth(if (U.tape) 0.7f else 1f), picture = picture)
}
