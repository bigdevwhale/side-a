package app.sidea.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where a record's music comes from. */
enum class Source {
    /** Audio files in a folder on the phone, played here on the turntable. */
    FOLDER,

    /** Any other player (a streaming app): this app listens along and keeps the record. */
    STREAM;

    companion object {
        fun of(s: String) = entries.firstOrNull { it.name == s } ?: STREAM
    }
}

/** Where a record is. */
enum class Place {
    /** On the shelf at home. */
    SHELF,

    /** Put aside on the shop counter, to buy on a later week. */
    HOLD,

    /** Taken to the second-hand shop, with a farewell line. */
    GONE;

    companion object {
        fun of(s: String) = entries.firstOrNull { it.name == s } ?: SHELF
    }
}

/**
 * One album, a record or a cassette depending on the look picked in settings. [listenedMs] is all the time it has turned under the needle, and wear follows from that.
 * [cover] is a picture file in the app's covers folder; without one the sleeve is drawn from [seed].
 */
@Entity(tableName = "records", indices = [Index("place")])
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val source: String = Source.STREAM.name,
    /** For a folder record: the folder's tree URI, kept with a persistable grant. */
    val folder: String? = null,
    val cover: String? = null,
    val seed: Long = 0,
    val place: String = Place.SHELF.name,
    /** When it came home (or was put on the counter, while held). */
    val at: Long = System.currentTimeMillis(),
    /** One of the first free records: doesn't use up a week. */
    val gift: Boolean = false,
    val goneAt: Long = 0,
    val farewell: String = "",
    val listenedMs: Long = 0,
    val lastAt: Long = 0,
    /** Where the album lives in a streaming player (spotify:album:…), once known: to start it from here. */
    val link: String? = null,
) {
    val sourceOf: Source get() = Source.of(source)
    val placeOf: Place get() = Place.of(place)
    fun day(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}

/** A song on a record, in order. A streamed record learns its songs as they play. */
@Entity(tableName = "tracks", indices = [Index("recordId")])
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val number: Int,
    val title: String,
    val durationMs: Long,
    /** A folder record's audio file. */
    val uri: String? = null,
)

/**
 * A mark from moving the music under the needle: song [number] (its index on the record), [atMs] into
 * it. On vinyl it's a scratch and [angle] places it round the disc; on a cassette it's a crease in the tape.
 */
@Entity(tableName = "scratches", indices = [Index("recordId")])
data class Scratch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val number: Int,
    val atMs: Long,
    val angle: Float,
    val at: Long = System.currentTimeMillis(),
)

/** A line written on the inner sleeve after listening. */
@Entity(tableName = "notes", indices = [Index("recordId")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val text: String,
    val at: Long = System.currentTimeMillis(),
)

/** One sitting with a record. [sides]: how many sides were played to the end. */
@Entity(tableName = "sessions", indices = [Index("recordId")])
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val listenedMs: Long,
    val sides: Int,
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY at")
    fun observe(): Flow<List<Record>>

    @Query("SELECT * FROM records ORDER BY at")
    suspend fun all(): List<Record>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun get(id: Long): Record?

    @Insert
    suspend fun insert(r: Record): Long

    @Update
    suspend fun update(r: Record)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE records SET link = :link WHERE id = :id")
    suspend fun link(id: Long, link: String)

    @Query("UPDATE records SET listenedMs = listenedMs + :ms, lastAt = :at WHERE id = :id")
    suspend fun listened(id: Long, ms: Long, at: Long = System.currentTimeMillis())

    @Query("SELECT * FROM tracks ORDER BY recordId, number")
    fun observeTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE recordId = :id ORDER BY number")
    suspend fun tracks(id: Long): List<Track>

    @Insert
    suspend fun insertTracks(t: List<Track>)

    @Query("SELECT * FROM scratches ORDER BY at")
    fun observeScratches(): Flow<List<Scratch>>

    @Query("SELECT * FROM scratches WHERE recordId = :id")
    suspend fun scratches(id: Long): List<Scratch>

    @Insert
    suspend fun scratch(s: Scratch): Long

    @Query("SELECT * FROM notes ORDER BY at")
    fun observeNotes(): Flow<List<Note>>

    @Insert
    suspend fun note(n: Note): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("SELECT * FROM sessions ORDER BY startedAt")
    fun observeSessions(): Flow<List<Session>>

    @Insert
    suspend fun session(s: Session): Long

    @Insert
    suspend fun insertScratches(s: List<Scratch>)

    @Insert
    suspend fun insertNotes(n: List<Note>)

    @Insert
    suspend fun insertSessions(s: List<Session>)

    @Query("DELETE FROM tracks WHERE recordId = :id")
    suspend fun deleteTracks(id: Long)

    @Query("DELETE FROM scratches WHERE recordId = :id")
    suspend fun deleteScratches(id: Long)

    @Query("DELETE FROM notes WHERE recordId = :id")
    suspend fun deleteNotes(id: Long)

    @Query("DELETE FROM sessions WHERE recordId = :id")
    suspend fun deleteSessions(id: Long)

    /** A record taken back to the shop the day it was bought: gone without a trace. */
    @Transaction
    suspend fun forget(id: Long) {
        deleteTracks(id)
        deleteScratches(id)
        deleteNotes(id)
        deleteSessions(id)
        delete(id)
    }
}

@Database(entities = [Record::class, Track::class, Scratch::class, Note::class, Session::class], version = 2, exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2)])
abstract class SideDatabase : RoomDatabase() {
    abstract fun dao(): RecordDao

    companion object {
        fun create(context: Context): SideDatabase =
            Room.databaseBuilder(context.applicationContext, SideDatabase::class.java, "side-a.db").build()
    }
}
