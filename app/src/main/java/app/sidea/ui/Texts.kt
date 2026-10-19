package app.sidea.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import app.sidea.ui.theme.U
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Words and dates in the app's language. */
object T {
    private fun locale(c: Context): Locale = c.resources.configuration.locales[0]

    fun plural(c: Context, id: Int, n: Int): String = c.resources.getQuantityString(id, n, n)

    /** "Saturday, 24 October". */
    fun weekday(c: Context, d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale(c))).replaceFirstChar { it.titlecase(locale(c)) }

    /** "24 October 2026", or without the year for this year. */
    fun date(c: Context, d: LocalDate, today: LocalDate = LocalDate.now()): String =
        d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMMM" else "d MMMM yyyy", locale(c)))

    fun date(c: Context, at: Long): String = date(c, Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate())

    /** "Saturday". */
    fun dayName(c: Context, d: java.time.DayOfWeek): String =
        d.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, locale(c)).replaceFirstChar { it.titlecase(locale(c)) }

    /** "Sat". */
    fun dayShort(c: Context, d: java.time.DayOfWeek): String =
        d.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale(c)).trimEnd('.').replaceFirstChar { it.titlecase(locale(c)) }

    /** "4:05" or "1:02:37". */
    fun duration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /** "3 h 20 min" in the app's words. */
    fun hours(c: Context, ms: Long): String {
        val min = (ms / 60_000).toInt()
        return if (min < 60) c.getString(app.sidea.R.string.minutes_short, min)
        else c.getString(app.sidea.R.string.hours_short, min / 60, min % 60)
    }
}

object Language {
    const val SYSTEM = ""
    const val EN = "en"
    const val RU = "ru"

    fun current(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').substringBefore(',')

    fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(if (tag == SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
    }
}

@Composable
fun ctx(): Context = LocalContext.current

/** The word for the look in use: "record" or "cassette", "turntable" or "tape deck". */
@Composable
fun w(vinyl: Int, tape: Int): String = stringResource(if (U.tape) tape else vinyl)

/** The same, with format arguments. */
@Composable
fun w(vinyl: Int, tape: Int, vararg args: Any): String = stringResource(if (U.tape) tape else vinyl, *args)
