package app.sidea.ui.room

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.data.Note
import app.sidea.data.Record
import app.sidea.data.Source
import app.sidea.domain.Sides
import app.sidea.play.DeckState
import app.sidea.play.Phase
import app.sidea.play.StreamState
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.art.Cassette
import app.sidea.ui.art.Mark
import app.sidea.ui.art.Meters
import app.sidea.ui.art.Turntable
import app.sidea.ui.components.PrimaryButton
import app.sidea.ui.components.SoftButton
import app.sidea.ui.components.TextLink
import app.sidea.ui.components.TopBar
import app.sidea.ui.ctx
import app.sidea.ui.record.NoteDialog
import app.sidea.ui.shop.openListenerSettings
import app.sidea.ui.theme.Mono
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import app.sidea.ui.theme.Wide
import app.sidea.ui.w
import kotlinx.coroutines.delay

/** A still room for the screenshots: what's on the platter, and the silence count if it's showing. */
data class RoomPreview(val deck: DeckState? = null, val stream: StreamState? = null, val silence: Int = 0)

/** What the room shows, whichever way the music reaches it. */
private class Now(
    val playing: Boolean,
    /** Where the needle is through the side, or null if it's on its rest. */
    val at: Float?,
    val side: Int,
    val sideTracks: Int,
    val index: Int,
    val title: String?,
    val gaps: List<Float>,
    val marks: List<Mark>,
    val phase: Phase,
    val pickedUp: Boolean,
    val winding: Int,
    val sideAtMs: Long,
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun RoomScreen(id: Long, home: Home, go: Go, preview: RoomPreview? = null) {
    val c = ctx()
    val container = c.container
    val r = home.record(id) ?: run {
        LaunchedEffect(Unit) { go.back() }
        return
    }
    val folder = r.sourceOf == Source.FOLDER
    val liveDeck by container.deck.state.collectAsStateWithLifecycle()
    val liveStream by container.stream.state.collectAsStateWithLifecycle()
    val deck = preview?.deck ?: liveDeck
    val stream = preview?.stream ?: liveStream
    // Coming back to a record already on: no second silence.
    val seated = remember { preview == null && container.room.value == id }
    var silence by remember { mutableIntStateOf(preview?.silence ?: if (seated) 0 else home.settings.silence) }
    var noteFor by remember { mutableStateOf<Long?>(null) }
    val missing = folder && preview == null && !container.library.reachable(r)

    LaunchedEffect(id) {
        if (preview != null || missing) return@LaunchedEffect
        // Another record on the platter comes off first.
        val other = container.room.value
        if (other != null && other != id) {
            container.deck.end()
            container.stream.end()
        }
        container.room.value = id
        val tracks = home.tracks(id)
        val scratches = home.scratches(id)
        if (folder) container.deck.load(r, tracks, scratches) else container.stream.begin(r, tracks, scratches)
    }
    LaunchedEffect(silence > 0) {
        if (preview != null) return@LaunchedEffect
        while (silence > 0) {
            delay(1000)
            silence--
        }
        // The quiet is over: down goes the needle, from the start of side A.
        if (!seated && folder) container.deck.drop()
    }

    /** Takes the record off; a long enough sitting asks for a line on the sleeve. */
    fun takeOff() {
        val listened = if (folder) container.deck.end().listenedMs else container.stream.end().listenedMs
        container.room.value = null
        if (listened >= 5 * 60_000) noteFor = id else go.back()
    }

    fun openPlayer() {
        container.excused = true
        val pkg = home.settings.player.ifBlank { stream.now?.pkg.orEmpty() }
        val launch = pkg.takeIf { it.isNotBlank() }?.let { c.packageManager.getLaunchIntentForPackage(it) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        try {
            c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            container.excused = false
        }
    }

    val now = if (folder) fromDeck(deck) else fromStream(stream)
    val sideName = stringResource(if (now.side == 0) R.string.side_a else R.string.side_b)
    val wear = home.wear(r)

    BackHandler(enabled = noteFor == null) { go.back() }

    Box(Modifier.fillMaxSize().background(U.c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(stringResource(R.string.room), go::back) {
                TextLink(w(R.string.take_off_v, R.string.take_off_t), ::takeOff, color = U.c.lamp)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
                if (U.tape) {
                    DeckFace(r, now, wear, levels = if (folder && preview == null) ({ container.deck.surface.left to container.deck.surface.right }) else null)
                } else {
                    Turntable(
                        record = r,
                        side = now.side,
                        sideName = sideName,
                        gaps = now.gaps,
                        marks = now.marks,
                        wear = wear,
                        spinning = now.playing,
                        needle = now.at,
                        lifted = !now.playing,
                        onMove = if (folder && now.phase == Phase.SIDE && preview == null) container.deck::moveTo else null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f / 0.86f),
                        onCue = if (preview != null) null else ({
                            when {
                                folder && now.playing -> container.deck.lift()
                                folder -> container.deck.drop()
                                now.playing -> container.stream.pause()
                                else -> container.stream.play()
                            }
                        }),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    (sideName + if (now.sideTracks > 0 && now.index >= 0) " · ${now.index + 1} / ${now.sideTracks}" else "").uppercase(),
                    style = UType.Eyebrow.copy(color = U.c.lamp),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(now.title ?: r.title, style = UType.Title.copy(fontSize = 20.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(r.artist, r.title).filter { it.isNotBlank() }.joinToString(" — "), style = UType.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!U.tape) {
                    Spacer(Modifier.height(16.dp))
                    Meters(if (folder && preview == null) ({ container.deck.surface.left to container.deck.surface.right }) else null, now.playing, Modifier.fillMaxWidth().height(96.dp))
                }
                Spacer(Modifier.height(18.dp))

                when {
                    missing -> Text(stringResource(R.string.folder_lost), style = UType.Body.copy(color = U.c.lamp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    now.pickedUp -> PickedUp { if (folder) container.deck.drop() else container.stream.play() }
                    folder -> FolderControls(now, container)
                    else -> StreamControls(r, stream, now, ::openPlayer)
                }
                // Songs by button, for those who want them.
                if (home.settings.skips && !missing && !now.pickedUp && (folder || stream.matched)) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton(stringResource(R.string.song_prev), { if (folder) container.deck.skip(-1) else container.stream.skip(-1) }, Modifier.weight(1f), icon = Icons.Rounded.SkipPrevious)
                        SoftButton(stringResource(R.string.song_next), { if (folder) container.deck.skip(1) else container.stream.skip(1) }, Modifier.weight(1f), icon = Icons.Rounded.SkipNext)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (silence > 0) Silence(silence) { silence = 0 }
    }

    noteFor?.let { rid ->
        NoteDialog(onDismiss = { noteFor = null; go.back() }, onSkipLabel = stringResource(R.string.note_skip)) { text ->
            noteFor = null
            container.write { db.dao().note(Note(recordId = rid, text = text)) }
            go.back()
        }
    }
}

private fun fromDeck(d: DeckState): Now {
    val side = d.sideTracks
    val first = Sides.range(d.side, d.tracks.size, d.split).first
    var run = 0L
    val starts = side.map { t -> run.also { run += t.durationMs } }
    val sideMs = d.sideMs.coerceAtLeast(1)
    return Now(
        playing = d.playing,
        at = if (d.phase == Phase.IDLE) null else if (d.phase == Phase.SIDE && (d.playing || d.sideAtMs > 0)) d.sideAtMs.toFloat() / sideMs else if (d.phase != Phase.SIDE) 1f else null,
        side = d.side,
        sideTracks = side.size,
        index = d.index - first,
        title = d.track?.title,
        gaps = starts.drop(1).map { it.toFloat() / sideMs },
        marks = d.scratches.mapNotNull { s ->
            val i = s.number - first
            if (i !in side.indices) null else Mark((starts[i] + s.atMs).toFloat() / sideMs, s.angle)
        },
        phase = d.phase,
        pickedUp = d.pickedUp,
        winding = d.winding,
        sideAtMs = d.sideAtMs,
    )
}

private fun fromStream(s: StreamState): Now {
    val tracks = s.tracks
    var run = 0L
    val starts = tracks.map { t -> run.also { run += t.durationMs } }
    val total = run.coerceAtLeast(1)
    val i = s.index
    val playing = s.matched && s.now?.playing == true
    return Now(
        playing = playing,
        at = if (s.matched && i >= 0) ((starts[i] + s.atMs).toFloat() / total).coerceIn(0f, 1f) else null,
        side = 0,
        sideTracks = tracks.size,
        index = i,
        title = if (s.matched) s.now?.title else null,
        gaps = starts.drop(1).map { it.toFloat() / total },
        marks = s.scratches.mapNotNull { m -> starts.getOrNull(m.number)?.let { Mark((it + m.atMs).toFloat() / total, m.angle) } },
        phase = Phase.SIDE,
        pickedUp = s.pickedUp,
        winding = 0,
        sideAtMs = if (i >= 0) starts[i] + s.atMs else 0,
    )
}

/** The quiet before the music: a count down, ears resting. */
@Composable
private fun Silence(left: Int, onSkip: () -> Unit) {
    Box(Modifier.fillMaxSize().background(U.c.bg).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(w(R.string.silence_v, R.string.silence_t).uppercase(), style = UType.Eyebrow)
            Spacer(Modifier.height(18.dp))
            Text(left.toString(), fontFamily = Wide, fontSize = 88.sp, color = U.c.lamp)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.silence_sub), style = UType.Sub, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            TextLink(stringResource(R.string.silence_skip), onSkip)
        }
    }
}

/** The phone was picked up and the needle went up. */
@Composable
private fun PickedUp(onDrop: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(U.c.lampSoft, U.RMd).padding(16.dp)) {
        Text(w(R.string.picked_up_v, R.string.picked_up_t), style = UType.Strong)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.picked_up_sub), style = UType.Muted)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(w(R.string.drop_v, R.string.drop_t), onDrop, Modifier.fillMaxWidth())
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FolderControls(now: Now, container: app.sidea.AppContainer) {
    val deck = container.deck
    when (now.phase) {
        Phase.FLIP -> {
            Text(w(R.string.side_over_v, R.string.side_over_t), style = UType.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            PrimaryButton(w(R.string.flip_v, R.string.flip_t), deck::flip, Modifier.fillMaxWidth())
        }
        Phase.DONE -> Text(stringResource(R.string.both_sides), style = UType.Sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        else -> if (U.tape) {
            Keys(now, onRew = { deck.wind(-1) }, onPlay = deck::drop, onStop = deck::lift, onFf = { deck.wind(1) }, onFlip = deck::flip)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tape_hint), style = UType.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            if (now.playing) SoftButton(stringResource(R.string.lift), deck::lift, Modifier.fillMaxWidth(), icon = Icons.Rounded.KeyboardArrowUp)
            else PrimaryButton(stringResource(R.string.drop_v), deck::drop, Modifier.fillMaxWidth(), icon = Icons.Rounded.KeyboardArrowDown)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.arm_hint), style = UType.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun StreamControls(r: Record, s: StreamState, now: Now, openPlayer: () -> Unit) {
    val c = ctx()
    when {
        !s.granted -> {
            Text(stringResource(R.string.player_access), style = UType.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            PrimaryButton(stringResource(R.string.player_allow), { openListenerSettings(c) }, Modifier.fillMaxWidth())
        }
        !s.matched -> {
            Text(stringResource(R.string.stream_start, r.title), style = UType.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            s.now?.album?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.stream_other, it), style = UType.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            var failed by remember { mutableStateOf(false) }
            var asked by remember { mutableStateOf(0L) }
            // Asked quietly and nothing came of it: the player ignores such requests, so open its search.
            LaunchedEffect(asked) {
                if (asked == 0L) return@LaunchedEffect
                delay(2_500)
                if (!c.container.stream.state.value.matched) {
                    c.container.excused = true
                    if (c.container.stream.playAlbum(r, s.now?.pkg ?: "", open = true) == app.sidea.play.Started.NOWHERE) {
                        c.container.excused = false
                        failed = true
                    }
                }
            }
            PrimaryButton(stringResource(R.string.play_album), {
                // The phone stays here if the player takes the album through its media session.
                when (c.container.stream.playAlbum(r, s.now?.pkg ?: "")) {
                    app.sidea.play.Started.HERE -> asked = System.currentTimeMillis()
                    app.sidea.play.Started.THERE -> c.container.excused = true
                    app.sidea.play.Started.NOWHERE -> failed = true
                }
            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
            if (failed) Text(stringResource(R.string.play_album_failed), style = UType.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            SoftButton(stringResource(R.string.open_player), openPlayer, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.OpenInNew)
        }
        else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (now.playing) SoftButton(stringResource(R.string.pause), { c.container.stream.pause() }, Modifier.weight(1f), icon = Icons.Rounded.Pause)
                else PrimaryButton(stringResource(R.string.play), { c.container.stream.play() }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
                SoftButton(stringResource(R.string.player), openPlayer, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
            Spacer(Modifier.height(10.dp))
            Text(w(R.string.stream_hint_v, R.string.stream_hint_t), style = UType.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The tape deck's face: the cassette behind its door, the counter, the LED meters. */
@Composable
private fun DeckFace(r: Record, now: Now, wear: Float, levels: (() -> Pair<Float, Float>)?) {
    val pal = U.c
    Column(
        Modifier
            .fillMaxWidth()
            .clip(U.RLg)
            .background(Brush.verticalGradient(listOf(pal.metal, pal.metalDark)))
            .border(1.dp, Color.Black.copy(alpha = 0.2f), U.RLg)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AUTO REVERSE · TYPE II", fontFamily = Mono, fontSize = 10.sp, color = Color(0xFF3A3C40), modifier = Modifier.weight(1f))
            Box(Modifier.clip(U.RSm).background(if (now.playing) pal.lamp else Color(0xFF6E3A33)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("PLAY", fontFamily = Mono, fontSize = 10.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(10.dp))
        // The door: dark smoked glass with the cassette behind it.
        Box(Modifier.fillMaxWidth().clip(U.RMd).background(Color(0xFF1C1D20)).padding(12.dp)) {
            val progress = if (now.at != null) now.at else 0f
            Cassette(r, if (now.side == 0) "A" else "B", progress, if (now.winding != 0) 2 * now.winding else if (now.playing) 1 else 0, wear, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Counter(((now.sideAtMs / 1000) * 3 / 5).toInt() % 1000)
            Spacer(Modifier.width(12.dp))
            Meters(levels, now.playing, Modifier.weight(1f).height(54.dp))
        }
    }
}

/** The three-wheel tape counter. */
@Composable
private fun Counter(n: Int) {
    Row(Modifier.clip(U.RSm).background(Color(0xFF15171A)).padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        "%03d".format(n).forEach { d ->
            Box(Modifier.width(20.dp).background(Brush.verticalGradient(listOf(Color(0xFF2B2E33), Color(0xFF0E0F11), Color(0xFF2B2E33))), U.RSm), contentAlignment = Alignment.Center) {
                Text(d.toString(), fontFamily = Mono, fontSize = 20.sp, color = Color(0xFFEDEBE6))
            }
        }
    }
}

/** The piano keys: rewind, play, stop, fast forward, and eject to turn it over. */
@Composable
private fun Keys(now: Now, onRew: () -> Unit, onPlay: () -> Unit, onStop: () -> Unit, onFf: () -> Unit, onFlip: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Key("◀◀", stringResource(R.string.key_rew), now.winding == -1, Modifier.weight(1f), onRew)
        Key("▶", stringResource(R.string.key_play), now.playing, Modifier.weight(1f), onPlay)
        Key("■", stringResource(R.string.key_stop), false, Modifier.weight(1f), onStop)
        Key("▶▶", stringResource(R.string.key_ff), now.winding == 1, Modifier.weight(1f), onFf)
        Key("⏏", stringResource(R.string.key_flip), false, Modifier.weight(1f), onFlip)
    }
}

@Composable
private fun Key(symbol: String, label: String, down: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = U.c
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (down) 50.dp else 56.dp)
                .clip(U.RSm)
                .background(Brush.verticalGradient(if (down) listOf(pal.metalDark, pal.metal) else listOf(if (pal.dark) pal.metal.copy(red = pal.metal.red * 1.15f, green = pal.metal.green * 1.15f, blue = pal.metal.blue * 1.15f) else Color.White, pal.metal)))
                .border(1.dp, Color.Black.copy(alpha = 0.25f), U.RSm)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, fontSize = 16.sp, color = if (down) pal.lamp else Color(0xFF2B2D31))
        }
        Spacer(Modifier.height(if (down) 10.dp else 4.dp))
        Text(label.uppercase(), fontFamily = Mono, fontSize = 9.sp, color = pal.faint, maxLines = 1)
    }
}
