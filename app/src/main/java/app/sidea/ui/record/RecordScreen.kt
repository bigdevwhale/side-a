package app.sidea.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.data.Note
import app.sidea.data.Place
import app.sidea.data.Record
import app.sidea.data.Source
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
import app.sidea.ui.theme.Hand
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import app.sidea.ui.w
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun RecordScreen(id: Long, home: Home, go: Go) {
    val c = ctx()
    val r = home.record(id) ?: run {
        LaunchedEffect(Unit) { go.back() }
        return
    }
    val tracks = home.tracks(id)
    val scratches = home.scratches(id)
    val notes = home.notes(id)
    val wear = home.wear(r)
    var menu by remember { mutableStateOf(false) }
    var letGo by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar("", go::back) {
            if (r.placeOf == Place.SHELF) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = U.c.muted) }
                DropdownMenu(menu, { menu = false }, containerColor = U.c.surface) {
                    DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menu = false; rename = true })
                    DropdownMenuItem({ Text(stringResource(R.string.let_go_title)) }, { menu = false; letGo = true })
                    if (home.returnable(r)) DropdownMenuItem({ Text(stringResource(R.string.return_shop)) }, {
                        menu = false
                        c.container.write { library.forget(r.id) }
                        go.back()
                    })
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = if (U.tape) 64.dp else 28.dp), contentAlignment = Alignment.Center) {
                Cover(r, wear, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(18.dp))
            Text(r.title, style = UType.Display, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(listOfNotNull(r.artist.ifBlank { null }, r.year?.toString()).joinToString(" · "), style = UType.Sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))

            val plays = home.plays(r)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(if (plays < 1f && r.listenedMs > 0) stringResource(R.string.plays_less) else T.plural(c, R.plurals.plays, plays.roundToInt()))
                if (r.listenedMs > 0) Stat(T.hours(c, r.listenedMs))
                Stat(
                    when (r.placeOf) {
                        Place.SHELF -> stringResource(if (r.gift) R.string.since_gift else R.string.since, T.date(c, r.at))
                        Place.HOLD -> stringResource(R.string.held_since, T.date(c, r.at))
                        Place.GONE -> stringResource(R.string.gone_since, T.date(c, r.goneAt))
                    },
                )
                if (scratches.isNotEmpty()) Stat(T.plural(c, if (U.tape) R.plurals.creases else R.plurals.scratches, scratches.size))
            }
            Spacer(Modifier.height(18.dp))

            when (r.placeOf) {
                Place.SHELF -> {
                    PrimaryButton(w(R.string.put_on_v, R.string.put_on_t), { go.room(r.id) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                    if (r.sourceOf == Source.FOLDER && !c.container.library.reachable(r)) {
                        Text(stringResource(R.string.folder_lost), style = UType.Small.copy(color = U.c.lamp), modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Place.HOLD -> {
                    if (home.allowance.canBuy && !home.full) PrimaryButton(stringResource(R.string.take_home), { c.container.write { library.bring(r.id) } }, Modifier.fillMaxWidth())
                    else Text(stringResource(R.string.held_wait), style = UType.Muted)
                }
                Place.GONE -> {
                    Farewell(r)
                    Spacer(Modifier.height(12.dp))
                    if (home.allowance.canBuy && !home.full) SoftButton(stringResource(R.string.buy_back), { c.container.write { library.bring(r.id) } }, Modifier.fillMaxWidth())
                    else Text(stringResource(R.string.buy_back_wait), style = UType.Muted)
                }
            }

            Tracklist(r, home)
            if (r.placeOf != Place.HOLD) InnerSleeve(notes, onAdd = { writing = true }, onDelete = { n -> c.container.write { db.dao().deleteNote(n.id) } })
            Spacer(Modifier.height(32.dp))
        }
    }

    if (letGo) LetGoDialog(home, r, pick = false, onDismiss = { letGo = false }) { rec, f ->
        letGo = false
        c.container.write { library.letGo(rec.id, f) }
    }
    if (rename) RenameDialog(r, { rename = false }) { t, a ->
        rename = false
        c.container.write { library.rename(r.id, t, a) }
    }
    if (writing) NoteDialog(onDismiss = { writing = false }) { text ->
        writing = false
        c.container.write { db.dao().note(Note(recordId = r.id, text = text)) }
    }
}

@Composable
private fun Stat(text: String) {
    Text(text, style = UType.Small.copy(color = U.c.muted), modifier = Modifier.background(U.c.surface, U.RSm).border(1.dp, U.c.line, U.RSm).padding(horizontal = 10.dp, vertical = 6.dp))
}

/** Both sides, song by song, with a mark wherever the needle (or the tape) took a knock. */
@Composable
private fun Tracklist(r: Record, home: Home) {
    val tracks = home.tracks(r.id)
    val scratches = home.scratches(r.id)
    if (tracks.isEmpty()) {
        if (r.sourceOf == Source.STREAM) {
            SectionTitle(stringResource(R.string.songs))
            Text(w(R.string.songs_later_v, R.string.songs_later_t), style = UType.Muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
        return
    }
    val split = Sides.split(tracks.map { it.durationMs })
    for (side in 0..1) {
        val range = Sides.range(side, tracks.size, split)
        if (range.isEmpty()) continue
        SectionTitle(stringResource(if (side == 0) R.string.side_a else R.string.side_b) + " · " + T.duration(tracks.slice(range).sumOf { it.durationMs }))
        tracks.slice(range).forEachIndexed { i, t ->
            val marks = scratches.count { it.number == range.first + i }
            Row(Modifier.padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", style = UType.Muted, modifier = Modifier.width(28.dp))
                Text(t.title, style = UType.Body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (marks > 0) Text("ϟ".repeat(marks.coerceAtMost(3)) + " ", style = UType.Small.copy(color = U.c.lamp))
                Text(T.duration(t.durationMs), style = UType.Counter)
            }
        }
    }
}

/** The inner sleeve: lines written after listening, in ballpoint, oldest first. */
@Composable
private fun InnerSleeve(notes: List<Note>, onAdd: () -> Unit, onDelete: (Note) -> Unit) {
    val c = ctx()
    SectionTitle(w(R.string.inner_sleeve_v, R.string.inner_sleeve_t))
    Column(Modifier.fillMaxWidth().background(U.c.paper, U.RMd).padding(horizontal = 18.dp, vertical = 14.dp)) {
        if (notes.isEmpty()) Text(stringResource(R.string.notes_none), fontFamily = Hand, fontSize = 21.sp, color = U.c.pen.copy(alpha = 0.55f))
        notes.forEach { n ->
            var ask by remember { mutableStateOf(false) }
            Text(T.date(c, n.at), style = UType.Small.copy(color = U.c.pen.copy(alpha = 0.6f)))
            Text(n.text, style = UType.Note, modifier = Modifier.padding(bottom = 10.dp))
            if (ask) AlertDialog(
                onDismissRequest = { ask = false },
                containerColor = U.c.surface,
                text = { Text(stringResource(R.string.note_delete), style = UType.Body) },
                confirmButton = { TextButton({ ask = false; onDelete(n) }) { Text(stringResource(R.string.delete), color = U.c.lamp) } },
                dismissButton = { TextButton({ ask = false }) { Text(stringResource(R.string.cancel), color = U.c.muted) } },
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    SoftButton(stringResource(R.string.note_add), onAdd, icon = Icons.Rounded.Add)
}

/** The line written when it went to the second-hand shop. */
@Composable
private fun Farewell(r: Record) {
    Column(Modifier.fillMaxWidth().background(U.c.paper, U.RMd).padding(16.dp)) {
        Text(stringResource(R.string.farewell).uppercase(), style = UType.Eyebrow.copy(color = U.c.pen.copy(alpha = 0.6f)))
        Text(r.farewell.ifBlank { "…" }, style = UType.Note)
    }
}

@Composable
private fun RenameDialog(r: Record, onDismiss: () -> Unit, onDone: (String, String) -> Unit) {
    var t by remember { mutableStateOf(r.title) }
    var a by remember { mutableStateOf(r.artist) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = U.c.surface,
        title = { Text(stringResource(R.string.rename), style = UType.Title) },
        text = {
            Column {
                Field(t, { t = it }, stringResource(R.string.album_title))
                Spacer(Modifier.height(8.dp))
                Field(a, { a = it }, stringResource(R.string.album_artist))
            }
        },
        confirmButton = { TextButton({ if (t.isNotBlank()) onDone(t, a) }) { Text(stringResource(R.string.save), color = U.c.lamp) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel), color = U.c.muted) } },
    )
}

/** A line for the inner sleeve: where you were, what it brought back. */
@Composable
fun NoteDialog(onDismiss: () -> Unit, onSkipLabel: String? = null, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = U.c.surface,
        title = { Text(w(R.string.note_title_v, R.string.note_title_t), style = UType.Title) },
        text = {
            Column {
                Text(stringResource(R.string.note_sub), style = UType.Muted)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.background(U.c.paper, U.RSm)) {
                    Field(text, { text = it.take(200) }, stringResource(R.string.note_hint), singleLine = false, style = UType.Note, minLines = 2)
                }
            }
        },
        confirmButton = { TextButton({ if (text.isNotBlank()) onDone(text.trim()) else onDismiss() }) { Text(stringResource(R.string.note_save), color = U.c.lamp) } },
        dismissButton = { TextButton(onDismiss) { Text(onSkipLabel ?: stringResource(R.string.cancel), color = U.c.muted) } },
    )
}
