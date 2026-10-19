package app.sidea.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.sidea.MainActivity
import app.sidea.container
import app.sidea.data.Note
import app.sidea.data.Place
import app.sidea.data.Record
import app.sidea.data.Scratch
import app.sidea.data.Session
import app.sidea.data.Settings
import app.sidea.data.Track
import app.sidea.domain.Allowance
import app.sidea.domain.Shop
import app.sidea.domain.Sides
import app.sidea.ru
import app.sidea.ui.archive.ArchiveScreen
import app.sidea.ui.components.rememberNow
import app.sidea.ui.home.HomeScreen
import app.sidea.ui.onboarding.WelcomeScreen
import app.sidea.ui.record.RecordScreen
import app.sidea.ui.room.RoomScreen
import app.sidea.ui.settings.SettingsScreen
import app.sidea.ui.shop.BuyScreen
import app.sidea.ui.shop.ShopScreen
import app.sidea.ui.theme.U
import java.time.LocalDate
import java.time.ZoneId

/** Everything stored, plus what is derived from it. Screens read through here. */
@Immutable
class Home(
    val records: List<Record>,
    tracks: List<Track>,
    scratches: List<Scratch>,
    notes: List<Note>,
    sessions: List<Session>,
    val settings: Settings,
    val ru: Boolean,
) {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate = LocalDate.now(zone)

    private val tracksBy = tracks.groupBy { it.recordId }
    private val scratchesBy = scratches.groupBy { it.recordId }
    private val notesBy = notes.groupBy { it.recordId }
    private val sessionsBy = sessions.groupBy { it.recordId }

    val shelf: List<Record> = records.filter { it.placeOf == Place.SHELF }
    val held: List<Record> = records.filter { it.placeOf == Place.HOLD }
    val gone: List<Record> = records.filter { it.placeOf == Place.GONE }.sortedByDescending { it.goneAt }

    fun record(id: Long) = records.firstOrNull { it.id == id }
    fun tracks(id: Long) = tracksBy[id].orEmpty().sortedBy { it.number }
    fun scratches(id: Long) = scratchesBy[id].orEmpty()
    fun notes(id: Long) = notesBy[id].orEmpty().sortedBy { it.at }
    fun sessions(id: Long) = sessionsBy[id].orEmpty()

    fun length(id: Long): Long = tracks(id).sumOf { it.durationMs }
    fun plays(r: Record): Float = Sides.plays(r.listenedMs, length(r.id))
    fun wear(r: Record): Float = Sides.wear(plays(r))

    /** Bought (not given) this shop week: the sticker's still on. */
    fun fresh(r: Record): Boolean = !r.gift && r.day(zone) >= Shop.weekStart(today, settings.day)

    /** Brought back from the shop today: can still be returned. */
    fun returnable(r: Record): Boolean = r.placeOf == Place.SHELF && r.day(zone) == today

    private val lastBuy: LocalDate? = records.filter { !it.gift && it.placeOf != Place.HOLD }.maxOfOrNull { it.at }?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val allowance: Allowance = Shop.allowance(settings.gifts, lastBuy, today, settings.day)
    val full: Boolean get() = shelf.size >= Shop.SHELF
    val listenedMs: Long get() = records.sumOf { it.listenedMs }
}

/** Everything a screen can open. */
class Go(private val nav: NavHostController) {
    fun record(id: Long) = nav.navigate("record/$id") { launchSingleTop = true }
    fun shop() = nav.navigate("shop") { launchSingleTop = true }
    fun buy() = nav.navigate("buy") { launchSingleTop = true }
    fun room(id: Long) = nav.navigate("room/$id") { launchSingleTop = true }
    fun archive() = nav.navigate("archive") { launchSingleTop = true }
    fun settings() = nav.navigate("settings") { launchSingleTop = true }
    fun back() { nav.popBackStack() }
    fun home() { nav.popBackStack("main", false) }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SideRoot(settings: Settings) {
    val context = ctx()
    val container = context.container
    val nav = rememberNavController()
    val start = remember { if (settings.onboarded) "main" else "welcome" }

    val dao = container.db.dao()
    val records by dao.observe().collectAsStateWithLifecycle(initialValue = null)
    val tracks by dao.observeTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val scratches by dao.observeScratches().collectAsStateWithLifecycle(initialValue = emptyList())
    val notes by dao.observeNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by dao.observeSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    // Past midnight the day rolls over.
    val tick = rememberNow(60_000) / 3_600_000
    val ru = context.ru
    val home = remember(records, tracks, scratches, notes, sessions, settings, tick, ru) {
        Home(records.orEmpty(), tracks, scratches, notes, sessions, settings, ru)
    }
    val go = remember(nav) { Go(nav) }

    // Onboarded elsewhere (the demo seed): leave the welcome screens.
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(settings.onboarded, route) {
        if (settings.onboarded && route == "welcome") nav.navigate("main") { popUpTo("welcome") { inclusive = true } }
    }
    // The media notification was tapped: back to the room.
    val open by container.open.collectAsStateWithLifecycle()
    val room by container.room.collectAsStateWithLifecycle()
    LaunchedEffect(open, settings.onboarded) {
        val (what, _) = open ?: return@LaunchedEffect
        if (!settings.onboarded) return@LaunchedEffect
        container.open.value = null
        if (what == MainActivity.OPEN_ROOM) room?.let { if (route?.startsWith("room") != true) go.room(it) }
    }

    Box(Modifier.fillMaxSize().background(U.c.bg)) {
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.background(U.c.bg),
            enterTransition = { fadeIn(tween(260)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(260)) },
            popExitTransition = { fadeOut(tween(180)) },
        ) {
            composable("welcome") { WelcomeScreen { nav.navigate("main") { popUpTo("welcome") { inclusive = true } } } }
            composable("main") { HomeScreen(home, go) }
            composable("record/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                RecordScreen(e.arguments?.getLong("id") ?: 0L, home, go)
            }
            composable("shop") { ShopScreen(home, go) }
            composable("buy") { BuyScreen(home, go) }
            composable("room/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                RoomScreen(e.arguments?.getLong("id") ?: 0L, home, go)
            }
            composable("archive") { ArchiveScreen(home, go) }
            composable("settings") { SettingsScreen(home, go) }
        }
    }
}
