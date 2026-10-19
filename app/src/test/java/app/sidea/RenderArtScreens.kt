package app.sidea

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.sidea.data.DemoSeed
import app.sidea.data.Format
import app.sidea.domain.Sides
import app.sidea.play.DeckState
import app.sidea.play.Phase
import app.sidea.play.Playing
import app.sidea.play.StreamState
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.archive.ArchiveScreen
import app.sidea.ui.art.Art
import app.sidea.ui.home.HomeScreen
import app.sidea.ui.onboarding.WelcomeScreen
import app.sidea.ui.record.RecordScreen
import app.sidea.ui.room.RoomPreview
import app.sidea.ui.room.RoomScreen
import app.sidea.ui.settings.SettingsScreen
import app.sidea.ui.shop.ShopScreen
import app.sidea.ui.theme.SideTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every main screen over the demo shelf, drawn to PNGs (run with `-Prender`), in both looks and both
 * languages: a look without a phone.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class ScreensBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    abstract val lang: String
    abstract val format: Format
    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang-${format.name.lowercase()}").apply { mkdirs() } }

    @Before fun still() {
        Art.still = true
    }

    private fun home(): Home = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SideApp>()
        val c = app.container
        DemoSeed.seedIfEmpty(app, c)
        c.settings.update { it.copy(format = format) }
        val dao = c.db.dao()
        Home(dao.all(), dao.observeTracks().first(), dao.observeScratches().first(), dao.observeNotes().first(), dao.observeSessions().first(), c.settings.now(), app.ru)
    }

    private fun shot(name: String, h: Home, settle: Long = 3_000, content: @Composable (Home, Go) -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val nav = rememberNavController()
            SideTheme(format) { content(h, Go(nav)) }
        }
        rule.mainClock.advanceTimeBy(settle)
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File(out, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** The most loved record on the shelf. */
    private fun loved(h: Home) = h.shelf.maxBy { h.plays(it) }

    @Test fun welcome() = home().let { shot("0-welcome", it) { _, _ -> WelcomeScreen {} } }
    @Test fun shelf() = home().let { shot("1-shelf", it) { h, go -> HomeScreen(h, go) } }
    @Test fun record() = home().let { h -> shot("2-record", h) { hh, go -> RecordScreen(loved(hh).id, hh, go) } }
    @Test fun room() = home().let { h ->
        val r = loved(h)
        val tracks = h.tracks(r.id)
        val split = Sides.split(tracks.map { it.durationMs })
        val deck = DeckState(record = r, tracks = tracks, scratches = h.scratches(r.id), split = split, phase = Phase.SIDE, playing = true, index = 2, sideAtMs = tracks.take(2).sumOf { it.durationMs } + 95_000, sideMs = tracks.take(split).sumOf { it.durationMs })
        val stream = StreamState(
            granted = true,
            now = Playing("", r.title, r.artist, tracks[2].title, 3, tracks[2].durationMs, playing = true, art = null),
            record = r, tracks = tracks, scratches = h.scratches(r.id), matched = true, atMs = 95_000,
        )
        shot("3-room", h) { hh, go -> RoomScreen(r.id, hh, go, RoomPreview(deck = deck, stream = stream)) }
    }
    @Test fun shop() = home().let { shot("4-shop", it) { h, go -> ShopScreen(h, go) } }
    @Test fun archive() = home().let { shot("5-archive", it) { h, go -> ArchiveScreen(h, go) } }
    @Test fun settings() = home().let { shot("6-settings", it) { h, go -> SettingsScreen(h, go) } }
}

@UnstableApi
class RenderArtScreensEn : ScreensBase() {
    override val lang = "en"
    override val format = Format.VINYL
}

@UnstableApi
@Config(qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderArtScreensRu : ScreensBase() {
    override val lang = "ru"
    override val format = Format.VINYL
}

@UnstableApi
class RenderArtScreensTapeEn : ScreensBase() {
    override val lang = "en"
    override val format = Format.TAPE
}

@UnstableApi
@Config(qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderArtScreensTapeRu : ScreensBase() {
    override val lang = "ru"
    override val format = Format.TAPE
}
