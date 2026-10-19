package app.sidea.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.sidea.AppContainer
import app.sidea.ru
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * A lived-in shelf for the demo build and the screenshots: seventeen made-up albums bought over five
 * months, worn to different degrees, a few scratches, lines on the inner sleeves, two records on the
 * counter and three in the second-hand shop. Every name here is invented.
 */
object DemoSeed {
    private class Album(val title: String, val artist: String, val year: Int, val plays: Float, val daysAgo: Int, val gift: Boolean = false)

    private val shelf = listOf(
        Album("Harbour Lights", "Low Tide Radio", 1978, 38f, 150, gift = true),
        Album("Paper Moons", "Marta Vell", 1974, 26f, 150, gift = true),
        Album("Blue Hour Sessions", "Theo Lark Trio", 1962, 31f, 150, gift = true),
        Album("Copper Field", "The Sundays Off", 1981, 14f, 140),
        Album("Salt Road", "Inès Moreau", 1972, 19f, 133),
        Album("Night Bus Home", "Ezra Pike", 1986, 22f, 126),
        Album("Velvet Static", "Orla & the Wires", 1979, 9f, 119),
        Album("Northern Window", "Halvard", 1976, 12f, 112),
        Album("Long Division", "Kettle Club", 1983, 6f, 98),
        Album("Glass Orchard", "Mirela Dunn", 1984, 8f, 84),
        Album("Morning Ferry", "Ada Sorensen", 1977, 11f, 70),
        Album("Orange Grove", "Los Amables", 1975, 5f, 63),
        Album("Lighthouse Keeper", "Fenwick", 1973, 4f, 49),
        Album("Radio Silence", "Neon Atlas", 1982, 3f, 35),
        Album("Summer of Small Things", "Dandelion Post", 1988, 2.2f, 21),
        Album("Slow Train South", "Brothers Hale", 1970, 1.4f, 9),
        Album("Kitchen Tapes", "June Arden", 1990, 0.4f, 2),
    )

    private val words = listOf(
        "Morning", "Harbour", "Paper", "Lanterns", "Small Hours", "Tin Roof", "Undertow", "Postcard", "Late Train", "Orchard", "Fog Lamp",
        "Weather Vane", "Kitchen Light", "Old Coat", "Ferris Wheel", "Low Sun", "Driftwood", "Sundial", "Blue Door", "Quiet Street", "Stone Steps",
        "Second Floor", "Balcony", "Cassiopeia", "August", "Telegram", "Winter Coat", "Rooftops", "Slow Dance", "Last Bus", "Paper Boats",
    )

    private val notesRu = listOf(
        "Дача, дождь по крыше, вторая сторона на повторе.",
        "Первый вечер в новой квартире. Коробки, чай, эта пластинка.",
        "Слушали вдвоём, не сказали ни слова. Хорошо.",
        "Третья песня — про папу. Не знаю почему.",
        "Ночной поезд, наушники, всё сошлось.",
        "Сегодня наконец-то слышно бас.",
    )
    private val notesEn = listOf(
        "The cabin, rain on the roof, side two on repeat.",
        "First night in the new flat. Boxes, tea, this record.",
        "We listened together and didn't say a word. Good.",
        "Track three is about Dad. No idea why.",
        "Night train, headphones, everything fell into place.",
        "Today I finally heard the bass line.",
    )

    @UnstableApi
    suspend fun seedIfEmpty(c: Context, container: AppContainer) {
        val dao = container.db.dao()
        if (dao.all().isNotEmpty()) return
        val rnd = Random(1972)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun at(daysAgo: Int, hour: Int = 19) = today.minusDays(daysAgo.toLong()).atTime(hour, 30).atZone(zone).toInstant().toEpochMilli()
        val notes = if (c.ru) notesRu else notesEn
        var note = 0

        suspend fun put(a: Album, place: Place, farewell: String = "", goneDaysAgo: Int = 0): Long {
            val tracks = (0 until 8 + rnd.nextInt(4)).map { i ->
                Track(recordId = 0, number = i, title = words[(a.title.hashCode().and(0xffff) + i * 7) % words.size], durationMs = (160 + rnd.nextInt(230)) * 1000L)
            }
            val length = tracks.sumOf { it.durationMs }
            val id = dao.insert(
                Record(
                    title = a.title, artist = a.artist, year = a.year, source = Source.STREAM.name,
                    seed = (a.title.hashCode().toLong() * 31 + a.artist.hashCode()), place = place.name, at = at(a.daysAgo, 12), gift = a.gift,
                    goneAt = if (place == Place.GONE) at(goneDaysAgo) else 0, farewell = farewell,
                    listenedMs = (a.plays * length).toLong(), lastAt = if (a.plays > 0) at(rnd.nextInt(a.daysAgo.coerceAtLeast(1))) else 0,
                ),
            )
            dao.insertTracks(tracks.map { it.copy(recordId = id) })
            // The well-played ones have taken a knock or two.
            val marks = (a.plays / 9).toInt().coerceAtMost(4)
            if (marks > 0) dao.insertScratches((0 until marks).map { Scratch(recordId = id, number = rnd.nextInt(tracks.size), atMs = rnd.nextLong(4_000, 150_000), angle = rnd.nextFloat() * 360f, at = at(rnd.nextInt(a.daysAgo.coerceAtLeast(1)))) })
            if (a.plays >= 5 && note < notes.size) {
                dao.insertNotes(listOf(Note(recordId = id, text = notes[note++], at = at((a.daysAgo - 5).coerceAtLeast(1), 22))))
                if (a.plays >= 25 && note < notes.size) dao.insertNotes(listOf(Note(recordId = id, text = notes[note++], at = at((a.daysAgo / 3).coerceAtLeast(1), 23))))
            }
            val sittings = (a.plays * 1.6f).toInt().coerceAtMost(30)
            if (sittings > 0) dao.insertSessions((0 until sittings).map {
                val start = at(rnd.nextInt(a.daysAgo.coerceAtLeast(1)), 18 + rnd.nextInt(4))
                Session(recordId = id, startedAt = start, endedAt = start + length / 2, listenedMs = length / 2, sides = 1)
            })
            return id
        }

        shelf.forEach { put(it, Place.SHELF) }
        put(Album("Second Spring", "Marta Vell", 1977, 0f, 6), Place.HOLD)
        put(Album("Afterglow Motel", "Ezra Pike", 1989, 0f, 3), Place.HOLD)
        val ru = c.ru
        put(Album("Too Loud Summer", "Pop Rocket", 2009, 7f, 145), Place.GONE, if (ru) "Мы из неё выросли. Спасибо за то лето." else "We grew out of it. Thanks for that summer.", 90)
        put(Album("Gym Mix Vol. 3", "Various Artists", 2015, 3f, 138), Place.GONE, if (ru) "Слушалась только на бегу. Пусть найдёт бегуна." else "Only ever played on a run. May it find a runner.", 60)
        put(Album("Big City Lights", "Metro Choir", 1985, 1f, 100), Place.GONE, if (ru) "Не моя. Но обложка красивая." else "Not mine. Lovely sleeve, though.", 30)

        container.settings.update { it.copy(onboarded = true, gifts = 0, player = "") }
    }
}
