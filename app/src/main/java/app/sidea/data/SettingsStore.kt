package app.sidea.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.sidea.domain.Shop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.dataStore by preferencesDataStore("settings")

/**
 * The look of the whole app, and how music behaves in it, picked at the start and changeable in settings.
 */
enum class Format {
    /** Records: a walnut hi-fi, a turntable, a needle that scratches the vinyl when you skip. */
    VINYL,

    /** Cassettes: a silver tape deck, winding that takes real time, tape that stretches and creases. */
    TAPE,
}

/** Light or dark: each look's own (records dark, cassettes light), either one always, or the phone's. */
enum class Shade { NATIVE, LIGHT, DARK, SYSTEM }

/** How loud the medium itself is: the hiss and crackle (or the tape's hiss and wobble) under the music. */
enum class Crackle(val amount: Float) { OFF(0f), SOFT(0.5f), REAL(1f) }

data class Settings(
    val onboarded: Boolean = false,
    val format: Format = Format.VINYL,
    val shade: Shade = Shade.NATIVE,
    /** Free records left in the first crate. */
    val gifts: Int = Shop.GIFTS,
    /** The shop's delivery day, 1 (Monday) to 7. */
    val storeDay: Int = DayOfWeek.SATURDAY.value,
    /** Seconds of quiet before the needle drops. */
    val silence: Int = 30,
    /** Picking up the phone lifts the needle. */
    val strict: Boolean = true,
    val crackle: Crackle = Crackle.SOFT,
    /** Lay a real recording of vinyl crackle or tape hiss under the music, here and over other players. */
    val recorded: Boolean = false,
    /** Previous and next buttons in the room, changing songs without a scratch or a wait. */
    val skips: Boolean = false,
    /** The person's own Spotify Client ID, for starting albums in Spotify without opening it. */
    val spotifyId: String = "",
    /** The streaming player last heard, to open it from the turntable. */
    val player: String = "",
) {
    val tape: Boolean get() = format == Format.TAPE

    /** Is the room dark, given whether the phone is? */
    fun dark(system: Boolean): Boolean = when (shade) {
        Shade.NATIVE -> format == Format.VINYL
        Shade.LIGHT -> false
        Shade.DARK -> true
        Shade.SYSTEM -> system
    }
    val day: DayOfWeek get() = DayOfWeek.of(storeDay.coerceIn(1, 7))
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        onboarded = this[ONBOARDED] ?: false,
        format = this[FORMAT]?.let { f -> Format.entries.firstOrNull { it.name == f } } ?: Format.VINYL,
        shade = this[SHADE]?.let { v -> Shade.entries.firstOrNull { it.name == v } } ?: Shade.NATIVE,
        gifts = this[GIFTS] ?: Shop.GIFTS,
        storeDay = this[STORE_DAY] ?: DayOfWeek.SATURDAY.value,
        silence = this[SILENCE] ?: 30,
        strict = this[STRICT] ?: true,
        crackle = this[CRACKLE]?.let { c -> Crackle.entries.firstOrNull { it.name == c } } ?: Crackle.SOFT,
        player = this[PLAYER] ?: "",
        recorded = this[RECORDED] ?: false,
        skips = this[SKIPS] ?: false,
        spotifyId = this[SPOTIFY_ID] ?: "",
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[ONBOARDED] = s.onboarded
            it[FORMAT] = s.format.name
            it[SHADE] = s.shade.name
            it[GIFTS] = s.gifts
            it[STORE_DAY] = s.storeDay
            it[SILENCE] = s.silence
            it[STRICT] = s.strict
            it[CRACKLE] = s.crackle.name
            it[PLAYER] = s.player
            it[RECORDED] = s.recorded
            it[SKIPS] = s.skips
            it[SPOTIFY_ID] = s.spotifyId
        }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val FORMAT = stringPreferencesKey("format")
        val SHADE = stringPreferencesKey("shade")
        val GIFTS = intPreferencesKey("gifts")
        val STORE_DAY = intPreferencesKey("store_day")
        val SILENCE = intPreferencesKey("silence")
        val STRICT = booleanPreferencesKey("strict")
        val CRACKLE = stringPreferencesKey("crackle")
        val PLAYER = stringPreferencesKey("player")
        val RECORDED = booleanPreferencesKey("recorded")
        val SKIPS = booleanPreferencesKey("skips")
        val SPOTIFY_ID = stringPreferencesKey("spotify_id")
    }
}
