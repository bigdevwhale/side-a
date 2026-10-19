package app.sidea

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.media3.common.util.UnstableApi
import app.sidea.data.DemoSeed
import app.sidea.data.Draft
import app.sidea.data.Library
import app.sidea.data.SettingsStore
import app.sidea.data.SideDatabase
import app.sidea.play.Deck
import app.sidea.play.Spotify
import app.sidea.play.StreamWatch
import app.sidea.domain.Match
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@UnstableApi
class AppContainer(context: Context) {
    val db = SideDatabase.create(context)
    val settings = SettingsStore(context)
    val library = Library(context, db, settings)
    val deck = Deck(context, db, settings)
    val stream = StreamWatch(context, db, settings)
    val spotify = Spotify(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A record picked in the shop, on its way to the counter. */
    val draft = MutableStateFlow<Draft?>(null)

    /** The record in the listening room, if someone's sitting there. */
    val room = MutableStateFlow<Long?>(null)

    /** Where a tap from outside wants to go ("room" from the media notification) and when. */
    val open = MutableStateFlow<Pair<String, Long>?>(null)

    /** Text shared into the app (a Spotify link), waiting to be looked at. */
    val shared = MutableStateFlow<String?>(null)

    /** The room itself sent you to another app (the streaming player): leaving doesn't lift the needle. */
    @Volatile var excused = false

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch { block() }
    }

    init {
        // While a record plays in Spotify, remember where the album lives there, to start it from here next time.
        scope.launch {
            combine(stream.state, spotify.album) { s, a -> s to a }.collect { (s, a) ->
                val r = s.record ?: return@collect
                if (a == null || !s.matched || s.now?.pkg != Spotify.PACKAGE || Spotify.isAlbum(r.link)) return@collect
                if (Match.album(r.title, "", a.first, null)) db.dao().link(r.id, a.second)
            }
        }
    }

    /** The phone was picked up while music was playing: the needle goes up. */
    fun pickedUp() {
        deck.pickedUp()
        stream.pickedUp()
    }
}

@UnstableApi
class SideApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (BuildConfig.DEMO) container.scope.launch { DemoSeed.seedIfEmpty(this@SideApp, container) }
    }
}

@get:UnstableApi
val Context.container: AppContainer get() = (applicationContext as SideApp).container

/** Resources in the in-app language, which AppCompat doesn't localise outside activities before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}

/** Is this context in Russian? */
val Context.ru: Boolean get() = resources.configuration.locales[0].language == "ru"
