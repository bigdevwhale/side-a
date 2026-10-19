package app.sidea.play

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.sidea.data.Covers
import app.sidea.data.Record
import app.sidea.data.Scratch
import app.sidea.data.Session
import app.sidea.data.SideDatabase
import app.sidea.data.SettingsStore
import app.sidea.data.Track
import app.sidea.domain.Sides
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Where the turntable is in playing a record. */
enum class Phase {
    /** Nothing on the platter. */
    IDLE,

    /** A side is on; the needle may be up or down. */
    SIDE,

    /** Side A has run out: turn the record over. */
    FLIP,

    /** Both sides played. */
    DONE,
}

/** What the turntable shows: the record on it, which side, where the needle is. */
data class DeckState(
    val record: Record? = null,
    val tracks: List<Track> = emptyList(),
    val scratches: List<Scratch> = emptyList(),
    val side: Int = 0,
    val split: Int = 0,
    val phase: Phase = Phase.IDLE,
    /** The needle is down and the music is playing. */
    val playing: Boolean = false,
    /** Index of the playing song on the record. */
    val index: Int = 0,
    /** Position on the current side, and the side's length. */
    val sideAtMs: Long = 0,
    val sideMs: Long = 0,
    /** This sitting's listening so far. */
    val listenedMs: Long = 0,
    val sides: Int = 0,
    /** The needle was lifted because the phone was picked up. */
    val pickedUp: Boolean = false,
    /** A cassette winding: 1 fast forward, -1 rewind, 0 still. */
    val winding: Int = 0,
) {
    val sideTracks: List<Track> get() = tracks.slice(Sides.range(side, tracks.size, split))
    val track: Track? get() = tracks.getOrNull(index)
}

/**
 * The turntable (or tape deck) for records in a folder on the phone: an ExoPlayer that plays one side
 * at a time, through [Surface]. Moving the needle while it's down (a seek, a skip from the headphones)
 * scratches a record where it lands, or creases a tape. A cassette can't jump: it winds, and winding
 * takes time. Listening time is written to the record as it goes.
 */
@UnstableApi
class Deck(private val context: Context, private val db: SideDatabase, private val settings: SettingsStore) {
    val surface = Surface()
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(DeckState())
    val state: StateFlow<DeckState> = _state.asStateFlow()

    private var built: ExoPlayer? = null
    val player: ExoPlayer
        get() = built ?: build().also { built = it }

    private var controller: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null
    private var startedAt = 0L
    private var unsaved = 0L
    private var lastTick = 0L
    private var lastIndex = -1
    private var lastAt = 0L

    /** The cassette look is on: wind instead of moving a needle. */
    private var tape = false

    init {
        main.launch(Dispatchers.Default) {
            settings.settings.collect {
                surface.amount = it.crackle.amount
                tape = it.tape
                surface.tape = it.tape
                surface.loop = if (it.recorded) Loops.get(context, it.tape) else null
            }
        }
    }

    private fun build(): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(surface)).build()
        }
        val attrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        return ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply { addListener(listener) }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) surface.quiet()
            _state.update { it.copy(playing = isPlaying, pickedUp = if (isPlaying) false else it.pickedUp) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            val s = _state.value
            if (s.phase != Phase.SIDE) return
            val flip = s.side == 0 && s.split < s.tracks.size
            _state.update { it.copy(phase = if (flip) Phase.FLIP else Phase.DONE, sides = it.sides + 1, playing = false, sideAtMs = it.sideMs) }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            // The needle moved while it was down: a mark where it lands.
            if (player.playWhenReady) scratch(recordIndex(newPosition.mediaItemIndex), newPosition.positionMs)
            lastIndex = recordIndex(newPosition.mediaItemIndex)
            lastAt = newPosition.positionMs
        }
    }

    private companion object {
        /** How many times faster than playing a cassette winds. */
        const val WIND = 20
    }

    private fun recordIndex(item: Int): Int = Sides.range(_state.value.side, _state.value.tracks.size, _state.value.split).first + item

    /** Puts [record] on the platter, side A up, needle raised. */
    fun load(record: Record, tracks: List<Track>, scratches: List<Scratch>) {
        if (_state.value.record?.id == record.id && _state.value.phase != Phase.IDLE) return
        val length = tracks.sumOf { it.durationMs }
        surface.wear = Sides.wear(Sides.plays(record.listenedMs, length))
        surface.tape = tape
        _state.value = DeckState(record = record, tracks = tracks, scratches = scratches, split = Sides.split(tracks.map { it.durationMs }), phase = Phase.SIDE)
        startedAt = System.currentTimeMillis()
        unsaved = 0
        side(0)
        bind()
        tick()
    }

    private fun side(side: Int) {
        val s = _state.value
        val range = Sides.range(side, s.tracks.size, s.split)
        val r = s.record ?: return
        val art = r.cover?.let { Uri.fromFile(Covers.file(context, it)) }
        val items = s.tracks.slice(range).map { t ->
            MediaItem.Builder()
                .setUri(t.uri)
                .setMediaId("${r.id}/${t.number}")
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(t.title).setArtist(r.artist).setAlbumTitle(r.title).setArtworkUri(art).setTrackNumber(t.number + 1).build(),
                )
                .build()
        }
        player.setMediaItems(items)
        player.prepare()
        player.playWhenReady = false
        lastIndex = range.first
        lastAt = 0
        _state.update { it.copy(side = side, phase = Phase.SIDE, index = range.first, sideAtMs = 0, sideMs = it.tracks.slice(range).sumOf { t -> t.durationMs }) }
    }

    /** Lowers the needle (presses play). */
    fun drop() {
        if (_state.value.phase != Phase.SIDE) return
        stopWinding()
        player.play()
    }

    /** Raises it (presses stop): the music stops where it is. */
    fun lift() {
        stopWinding()
        player.pause()
    }

    /** The phone was picked up mid-side: up goes the needle, and the room says why. */
    fun pickedUp() {
        if (!player.isPlaying) return
        player.pause()
        _state.update { it.copy(pickedUp = true) }
    }

    /** Turns the record over and plays side B. A cassette can be turned over at any point, mid-side too. */
    fun flip() {
        val s = _state.value
        if (s.phase == Phase.FLIP || (tape && s.phase == Phase.SIDE && s.split < s.tracks.size)) {
            stopWinding()
            // The tape runs both ways: turned over mid-side, it plays on from the mirrored spot.
            val lengths = (0..1).map { side -> s.tracks.slice(Sides.range(side, s.tracks.size, s.split)).sumOf { it.durationMs } }
            val at = if (s.phase == Phase.SIDE) (lengths.max() - _state.value.sideAtMs).coerceIn(0, lengths[1 - s.side]) else 0L
            side(1 - s.side)
            if (at > 0) moveTo(at.toFloat() / lengths[1 - s.side].coerceAtLeast(1))
            player.play()
        }
    }

    private var winder: Job? = null

    /**
     * Fast forward ([dir] 1) or rewind (-1) a cassette, at the speed of a real deck: a side takes the
     * better part of a minute to wind through. The music stops while it winds.
     */
    fun wind(dir: Int) {
        val s = _state.value
        if (!tape || s.phase != Phase.SIDE) return
        if (s.winding == dir) return stopWinding()
        player.pause()
        winder?.cancel()
        _state.update { it.copy(winding = dir) }
        winder = main.launch {
            var at = _state.value.sideAtMs
            while (isActive) {
                delay(50)
                at += dir * 50 * WIND
                if (at <= 0 || at >= _state.value.sideMs) {
                    at = at.coerceIn(0, _state.value.sideMs)
                    _state.update { it.copy(sideAtMs = at) }
                    break
                }
                _state.update { it.copy(sideAtMs = at) }
            }
            stopWinding()
        }
    }

    /** Stops winding and leaves the tape where it got to. */
    private fun stopWinding() {
        val s = _state.value
        if (s.winding == 0) return
        winder?.cancel()
        winder = null
        _state.update { it.copy(winding = 0) }
        moveTo(s.sideAtMs.toFloat() / s.sideMs.coerceAtLeast(1))
    }

    /** Moves the arm to [fraction] of the way through the side (0 at the edge). Scratches if the needle is down. */
    fun moveTo(fraction: Float) {
        val s = _state.value
        if (s.phase != Phase.SIDE) return
        var at = (fraction.coerceIn(0f, 0.999f) * s.sideMs).toLong()
        for ((i, t) in s.sideTracks.withIndex()) {
            if (at < t.durationMs) {
                player.seekTo(i, at)
                return
            }
            at -= t.durationMs
        }
    }

    private fun scratch(index: Int, atMs: Long) {
        val r = _state.value.record ?: return
        val s = Scratch(recordId = r.id, number = index, atMs = atMs, angle = Random.nextFloat() * 360f)
        _state.update { it.copy(scratches = it.scratches + s) }
        surface.click(1)
        main.launch(Dispatchers.IO) { db.dao().scratch(s) }
    }

    private fun tick() {
        ticker?.cancel()
        lastTick = System.currentTimeMillis()
        ticker = main.launch {
            while (isActive) {
                delay(100)
                val now = System.currentTimeMillis()
                val s = _state.value
                if (s.phase == Phase.IDLE) break
                val gap = now - lastTick
                lastTick = now
                if (s.phase != Phase.SIDE || s.winding != 0) continue
                val item = player.currentMediaItemIndex
                val pos = player.currentPosition
                val index = recordIndex(item)
                val before = s.tracks.slice(Sides.range(s.side, s.tracks.size, s.split)).take(item).sumOf { it.durationMs }
                if (player.isPlaying) {
                    unsaved += gap
                    // Crossing a scratch: click, click, click.
                    if (index == lastIndex && pos > lastAt && pos - lastAt < 1_500 && s.scratches.any { it.number == index && it.atMs in (lastAt + 1)..pos }) surface.click()
                }
                lastIndex = index
                lastAt = pos
                _state.update { it.copy(index = index, sideAtMs = before + pos, listenedMs = it.listenedMs + if (player.isPlaying) gap else 0) }
                if (unsaved >= 30_000) save()
            }
        }
    }

    private fun save() {
        val id = _state.value.record?.id ?: return
        val ms = unsaved
        unsaved = 0
        if (ms > 0) main.launch(Dispatchers.IO) { db.dao().listened(id, ms) }
    }

    /** Takes the record off: saves the sitting and returns how long it lasted. */
    fun end(): DeckState {
        val s = _state.value
        if (s.phase == Phase.IDLE) return s
        built?.let {
            it.stop()
            it.clearMediaItems()
        }
        ticker?.cancel()
        save()
        val r = s.record
        if (r != null && s.listenedMs >= 60_000) {
            val session = Session(recordId = r.id, startedAt = startedAt, endedAt = System.currentTimeMillis(), listenedMs = s.listenedMs, sides = s.sides)
            main.launch(Dispatchers.IO) { db.dao().session(session) }
        }
        surface.quiet()
        _state.value = DeckState()
        unbind()
        return s
    }

    /** A controller bound to the playback service keeps the music going with the screen off. */
    private fun bind() {
        if (controller != null) return
        controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
    }

    private fun unbind() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
    }
}
