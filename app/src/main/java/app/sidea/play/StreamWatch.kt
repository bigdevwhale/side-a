package app.sidea.play

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import app.sidea.data.Record
import app.sidea.data.Scratch
import app.sidea.data.Session
import app.sidea.data.SettingsStore
import app.sidea.data.SideDatabase
import app.sidea.data.Track
import app.sidea.domain.Match
import app.sidea.domain.Needle
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

/** Only here so the app may read what other players are playing. It handles no notifications. */
class Listener : NotificationListenerService()

/** What another player is playing right now. */
data class Playing(
    val pkg: String,
    val album: String?,
    val artist: String?,
    val title: String?,
    val number: Int?,
    val durationMs: Long,
    val playing: Boolean,
    val art: Bitmap?,
)

data class StreamState(
    val granted: Boolean = false,
    val now: Playing? = null,
    /** The record being listened to, if there's a sitting. */
    val record: Record? = null,
    val tracks: List<Track> = emptyList(),
    val scratches: List<Scratch> = emptyList(),
    /** The player is playing this record. */
    val matched: Boolean = false,
    /** Position in the playing song. */
    val atMs: Long = 0,
    val listenedMs: Long = 0,
    val pickedUp: Boolean = false,
) {
    val index: Int get() = now?.title?.let { t -> tracks.indexOfFirst { Match.track(it.title, t) } } ?: -1
}

/**
 * A record on the shelf played in a streaming app. Through notification access the app sees the
 * player's media session: it learns the record's songs as they come, counts the time listened, and
 * scratches the record when a song is skipped or the position jumps.
 */
/** How an album was started in another player: from here, by opening the player, or not at all. */
enum class Started { HERE, THERE, NOWHERE }

/** Streaming players that take a search to play, most likely first. */
val PLAYERS = listOf("com.spotify.music", "ru.yandex.music", "com.google.android.apps.youtube.music", "deezer.android.app", "com.apple.android.music", "ru.mts.music.android", "com.vkontakte.android")

class StreamWatch(private val context: Context, private val db: SideDatabase, private val settings: SettingsStore) {
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(StreamState())
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, Listener::class.java)
    private var controller: MediaController? = null
    private var watching = 0
    private var ticker: Job? = null
    private var startedAt = 0L
    private var unsaved = 0L

    private val overlay = Overlay(context)
    private var prefs = app.sidea.data.Settings()

    init {
        main.launch {
            settings.settings.collect {
                prefs = it
                sync()
            }
        }
    }

    /**
     * With "real recording" on, the crackle (or hiss) plays over the other player whenever it's
     * playing this record, and stops the moment it isn't.
     */
    private fun sync() {
        val s = _state.value
        val r = s.record
        val on = prefs.recorded && prefs.crackle.amount > 0f && r != null && s.matched && s.now?.playing == true
        if (!on || r == null) return overlay.stop()
        val wear = app.sidea.domain.Sides.wear(app.sidea.domain.Sides.plays(r.listenedMs, s.tracks.sumOf { it.durationMs }))
        overlay.gain = prefs.crackle.amount * (0.2f + 0.3f * wear)
        overlay.start(prefs.tape)
    }

    /** The last position report: where, when (elapsed realtime) and how fast. */
    private var lastState: PlaybackState? = null

    fun granted(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    /** Starts looking at other players; each call needs a matching [unwatch]. */
    fun watch() {
        val ok = granted()
        _state.update { it.copy(granted = ok) }
        if (!ok || watching++ > 0) return
        try {
            manager.addOnActiveSessionsChangedListener(sessions, component, Handler(Looper.getMainLooper()))
            pick(manager.getActiveSessions(component))
        } catch (_: SecurityException) {
            watching = 0
            _state.update { it.copy(granted = false) }
        }
    }

    fun unwatch() {
        if (watching == 0 || --watching > 0) return
        manager.removeOnActiveSessionsChangedListener(sessions)
        controller?.unregisterCallback(callback)
        controller = null
        _state.update { it.copy(now = null, matched = false) }
    }

    private fun pick(list: List<MediaController>) {
        val mine = context.packageName
        val best = list.filter { it.packageName != mine }.let { c -> c.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: c.firstOrNull() }
        if (best?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(callback)
        controller = best
        best?.registerCallback(callback, Handler(Looper.getMainLooper()))
        lastState = best?.playbackState
        update(best?.metadata, best?.playbackState, changed = true)
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update(metadata, controller?.playbackState, changed = true)

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val before = lastState
            // The same song, but the position isn't where it should be: someone moved the arm.
            if (before != null && state != null && _state.value.matched && state.state == PlaybackState.STATE_PLAYING) {
                val expected = at(before, state.lastPositionUpdateTime)
                if (state.position >= 0 && Needle.jumped(expected, state.position)) scratchHere(state.position)
            }
            lastState = state
            update(controller?.metadata, state, changed = false)
        }

        override fun onSessionDestroyed() = pick(emptyList())
    }

    /** Where [s] puts the song at elapsed-realtime [time]. */
    private fun at(s: PlaybackState, time: Long = SystemClock.elapsedRealtime()): Long =
        if (s.state == PlaybackState.STATE_PLAYING) s.position + ((time - s.lastPositionUpdateTime) * s.playbackSpeed).toLong() else s.position

    private fun update(m: MediaMetadata?, ps: PlaybackState?, changed: Boolean) {
        val c = controller
        val now = if (c == null || m == null) null else Playing(
            pkg = c.packageName,
            album = m.getString(MediaMetadata.METADATA_KEY_ALBUM),
            artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ARTIST),
            title = m.getString(MediaMetadata.METADATA_KEY_TITLE),
            number = m.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER).toInt().takeIf { it > 0 },
            durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION),
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART),
        )
        val old = _state.value
        val r = old.record
        val matched = r != null && now != null && Match.album(r.title, r.artist, now.album, now.artist)
        // The song changed before its end: skipped. The needle lands on the new one.
        val skipped = changed && matched && old.matched && old.now != null && now?.title != old.now.title &&
            Needle.skipped(lastState?.let { at(it) } ?: 0L, old.now.durationMs)
        _state.update { it.copy(now = now, matched = matched, atMs = ps?.let { s -> at(s) } ?: 0, pickedUp = if (now?.playing == true) false else it.pickedUp) }
        sync()
        if (matched && now != null) {
            learn(now)
            if (skipped) scratchHere(0)
            if (now.pkg != "") main.launch { if (settings.now().player != now.pkg) settings.update { it.copy(player = now.pkg) } }
        }
    }

    /** A song of the record heard for the first time joins its track list. */
    private fun learn(now: Playing) {
        val s = _state.value
        val r = s.record ?: return
        val title = now.title ?: return
        if (s.tracks.any { Match.track(it.title, title) }) return
        val t = Track(recordId = r.id, number = s.tracks.size, title = title, durationMs = now.durationMs.coerceAtLeast(0))
        _state.update { it.copy(tracks = it.tracks + t) }
        main.launch(Dispatchers.IO) { db.dao().insertTracks(listOf(t)) }
    }

    private fun scratchHere(atMs: Long) {
        val s = _state.value
        val r = s.record ?: return
        val i = s.index
        if (i < 0) return
        val mark = Scratch(recordId = r.id, number = i, atMs = atMs, angle = Random.nextFloat() * 360f)
        _state.update { it.copy(scratches = it.scratches + mark) }
        main.launch(Dispatchers.IO) { db.dao().scratch(mark) }
    }

    /** Sits down with [record]: from now on its time is counted. */
    fun begin(record: Record, tracks: List<Track>, scratches: List<Scratch>) {
        if (_state.value.record?.id == record.id) return
        watch()
        startedAt = System.currentTimeMillis()
        unsaved = 0
        _state.update { it.copy(record = record, tracks = tracks, scratches = scratches, listenedMs = 0, pickedUp = false) }
        controller?.let { update(it.metadata, it.playbackState, changed = false) }
        ticker?.cancel()
        ticker = main.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(500)
                val t = SystemClock.elapsedRealtime()
                val gap = t - last
                last = t
                val s = _state.value
                val ps = controller?.playbackState
                if (s.matched && s.now?.playing == true) {
                    unsaved += gap
                    _state.update { it.copy(listenedMs = it.listenedMs + gap, atMs = ps?.let { p -> at(p) } ?: it.atMs) }
                }
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

    /** Pauses the other player when the phone is picked up in the middle of listening. */
    fun pickedUp() {
        val s = _state.value
        if (s.record == null || !s.matched || s.now?.playing != true) return
        controller?.transportControls?.pause()
        _state.update { it.copy(pickedUp = true) }
    }

    fun play() = controller?.transportControls?.play()

    /**
     * Starts [record] in the streaming player without opening it, the way voice assistants do: a
     * search for the album, sent to the player's media session. Prefers [preferred] (the player last
     * heard), then any player that's around. With no session alive, or with [open] set, asks the player
     * app itself to do the same search, which brings it to the front. Some players (Spotify among them)
     * only take such requests from their partners and ignore them from here; the room then opens the
     * player instead.
     */
    fun playAlbum(record: Record, preferred: String = "", open: Boolean = false): Started {
        val wanted = preferred.ifBlank { prefs.player }
        val query = listOf(record.title, record.artist).filter { it.isNotBlank() }.joinToString(" ")
        val extras = android.os.Bundle().apply {
            putString(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, android.provider.MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE)
            putString(android.provider.MediaStore.EXTRA_MEDIA_ALBUM, record.title)
            if (record.artist.isNotBlank()) putString(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, record.artist)
        }
        val sessions = try {
            if (granted()) manager.getActiveSessions(component) else emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
        val searchable = sessions.filter { (it.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_SEARCH != 0L }
        val target = searchable.firstOrNull { it.packageName == wanted } ?: searchable.firstOrNull { it.packageName in PLAYERS } ?: searchable.firstOrNull()
        if (target != null && !open) {
            target.transportControls.playFromSearch(query, extras)
            return Started.HERE
        }
        val pkg = wanted.ifBlank { target?.packageName ?: PLAYERS.firstOrNull { installed(it) } } ?: return Started.NOWHERE
        val intent = android.content.Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(pkg)
            .putExtras(extras)
            .putExtra(android.app.SearchManager.QUERY, query)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Started.THERE
        } catch (_: android.content.ActivityNotFoundException) {
            Started.NOWHERE
        }
    }

    private fun installed(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun pause() = controller?.transportControls?.pause()

    /** Gets up from the record: saves the sitting and returns it. */
    fun end(): StreamState {
        val s = _state.value
        val r = s.record ?: return s
        ticker?.cancel()
        save()
        if (s.listenedMs >= 60_000) {
            val session = Session(recordId = r.id, startedAt = startedAt, endedAt = System.currentTimeMillis(), listenedMs = s.listenedMs, sides = 0)
            main.launch(Dispatchers.IO) { db.dao().session(session) }
        }
        _state.update { it.copy(record = null, tracks = emptyList(), scratches = emptyList(), matched = false, listenedMs = 0, pickedUp = false) }
        sync()
        unwatch()
        return s
    }
}
