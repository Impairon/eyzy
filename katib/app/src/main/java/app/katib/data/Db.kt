package app.katib.data

import android.content.Context
import androidx.room.*
import app.katib.security.Crypto
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Status {
    const val RECORDING = "recording"
    const val QUEUED = "queued"
    const val TRANSCRIBING = "transcribing"
    const val DONE = "done"
    const val FAILED = "failed"
}

@Entity
data class Meeting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val durationSec: Float = 0f,
    val audioPath: String?,
    val status: String = Status.RECORDING,
    val progress: Float = 0f,
    val stage: String? = null,
    val error: String? = null,
    val language: String = "auto",
    val model: String? = null,
    val rtf: Float? = null,
    val transcribedAt: Long? = null,
    val audioDeleteAt: Long? = null,
    val audioDeleted: Boolean = false,
    val neverSync: Boolean = false,
    /** "meeting" (consent-gated) or "note" (personal, owner only). */
    @ColumnInfo(defaultValue = "meeting") val kind: String = "meeting",
    /** Personal note in which voices other than the owner were detected. */
    @ColumnInfo(defaultValue = "0") val otherVoices: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
    val locAccuracyM: Float? = null,
    val locAt: Long? = null,
    /** Stable id used in provenance manifests. */
    val uuid: String? = null,
    /** SHA-256 of the plaintext PCM, computed while recording and fixed at stop. */
    val audioSha256: String? = null,
)

/** One signed manifest in a meeting's append-only provenance chain. */
@Entity(indices = [Index("meetingId")])
data class ProvenanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val kind: String,           // "capture" | "transcript"
    val payload: String,        // exact signed bytes (UTF-8 JSON)
    val payloadSha256: String,
    val prevSha256: String?,
    val signature: String,      // base64 DER ECDSA
    val keyAlias: String,
    val certChain: String,      // PEM attestation chain
    val createdAt: Long,
)

@Entity(indices = [Index("meetingId")])
data class ConsentLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val participants: String,
    val method: String,
    val confirmedAt: Long,
)

@Entity(indices = [Index("meetingId")])
data class Segment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val startSec: Float,
    val endSec: Float,
    val text: String,
    val speakerIdx: Int = -1,
)

@Fts4(contentEntity = Segment::class)
@Entity
data class SegmentFts(val text: String)

/** One diarized speaker cluster inside one meeting. */
@Entity(indices = [Index("meetingId")])
data class MeetingSpeaker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val idx: Int,
    val personId: Long? = null,
    val score: Float = 0f,
    val speechSec: Float = 0f,
    val embedding: ByteArray? = null,
)

/** A remembered voice. The embedding is biometric data: opt-in, local, never synced. */
@Entity
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isOwner: Boolean = false,
    val embedding: ByteArray?,
    val samples: Int = 1,
    val consentAt: Long,
)

data class SearchHit(
    val segmentId: Long, val meetingId: Long, val title: String,
    val startSec: Float, val text: String, val createdAt: Long,
)

@Dao
interface KatibDao {
    @Insert suspend fun insertMeeting(m: Meeting): Long
    @Update suspend fun updateMeeting(m: Meeting)
    @Query("SELECT * FROM Meeting WHERE id=:id") suspend fun meeting(id: Long): Meeting?
    @Query("SELECT * FROM Meeting WHERE id=:id") fun meetingFlow(id: Long): Flow<Meeting?>
    @Query("SELECT * FROM Meeting ORDER BY createdAt DESC") fun meetings(): Flow<List<Meeting>>
    @Query("SELECT * FROM Meeting WHERE status IN ('queued','transcribing')") suspend fun pending(): List<Meeting>
    @Query("SELECT * FROM Meeting WHERE status='recording'") suspend fun orphaned(): List<Meeting>
    @Query("SELECT * FROM Meeting WHERE audioDeleted=0 AND audioDeleteAt IS NOT NULL AND audioDeleteAt < :now")
    suspend fun expiredAudio(now: Long): List<Meeting>
    @Query("UPDATE Meeting SET progress=:p, stage=:stage WHERE id=:id") suspend fun setProgress(id: Long, p: Float, stage: String)
    @Query("UPDATE Meeting SET title=:t WHERE id=:id") suspend fun rename(id: Long, t: String)
    @Delete suspend fun deleteMeeting(m: Meeting)

    @Insert suspend fun insertProvenance(p: ProvenanceRecord): Long
    @Query("SELECT * FROM ProvenanceRecord WHERE meetingId=:id ORDER BY id") suspend fun provenance(id: Long): List<ProvenanceRecord>
    @Query("SELECT * FROM ProvenanceRecord WHERE meetingId=:id ORDER BY id") fun provenanceFlow(id: Long): Flow<List<ProvenanceRecord>>
    @Query("DELETE FROM ProvenanceRecord WHERE meetingId=:id") suspend fun deleteProvenance(id: Long)

    @Insert suspend fun insertConsent(c: ConsentLog): Long
    @Query("SELECT * FROM ConsentLog WHERE meetingId=:id") suspend fun consent(id: Long): List<ConsentLog>

    @Insert suspend fun insertSegments(s: List<Segment>)
    @Query("SELECT * FROM Segment WHERE meetingId=:id ORDER BY startSec") fun segments(id: Long): Flow<List<Segment>>
    @Query("SELECT * FROM Segment WHERE meetingId=:id ORDER BY startSec") suspend fun segmentsNow(id: Long): List<Segment>
    @Query("UPDATE Segment SET text=:text WHERE id=:id") suspend fun editSegment(id: Long, text: String)
    @Query("DELETE FROM Segment WHERE meetingId=:id") suspend fun deleteSegments(id: Long)
    @Query("SELECT meetingId FROM Segment WHERE id=:id") suspend fun segmentMeeting(id: Long): Long?

    @Query("""SELECT s.id AS segmentId, s.meetingId, m.title, s.startSec, s.text, m.createdAt
              FROM SegmentFts f JOIN Segment s ON s.rowid = f.rowid JOIN Meeting m ON m.id = s.meetingId
              WHERE SegmentFts MATCH :q ORDER BY m.createdAt DESC, s.startSec LIMIT 200""")
    suspend fun searchFts(q: String): List<SearchHit>
    @Query("""SELECT s.id AS segmentId, s.meetingId, m.title, s.startSec, s.text, m.createdAt
              FROM Segment s JOIN Meeting m ON m.id = s.meetingId
              WHERE s.text LIKE '%' || :q || '%' ORDER BY m.createdAt DESC, s.startSec LIMIT 200""")
    suspend fun searchLike(q: String): List<SearchHit>

    @Insert suspend fun insertSpeakers(s: List<MeetingSpeaker>)
    @Query("SELECT * FROM MeetingSpeaker WHERE meetingId=:id ORDER BY idx") fun speakers(id: Long): Flow<List<MeetingSpeaker>>
    @Query("SELECT * FROM MeetingSpeaker WHERE meetingId=:id ORDER BY idx") suspend fun speakersNow(id: Long): List<MeetingSpeaker>
    @Query("UPDATE MeetingSpeaker SET personId=:pid, score=1 WHERE id=:id") suspend fun assignSpeaker(id: Long, pid: Long?)
    @Query("DELETE FROM MeetingSpeaker WHERE meetingId=:id") suspend fun deleteSpeakers(id: Long)
    @Query("UPDATE MeetingSpeaker SET personId=NULL WHERE personId=:pid") suspend fun unlinkPerson(pid: Long)

    @Insert suspend fun insertPerson(p: Person): Long
    @Update suspend fun updatePerson(p: Person)
    @Query("SELECT * FROM Person ORDER BY isOwner DESC, name") fun people(): Flow<List<Person>>
    @Query("SELECT * FROM Person") suspend fun peopleNow(): List<Person>
    @Query("SELECT * FROM Person WHERE id=:id") suspend fun person(id: Long): Person?
    @Delete suspend fun deletePerson(p: Person)
    @Query("""SELECT COUNT(DISTINCT meetingId) FROM MeetingSpeaker WHERE personId=:pid""") suspend fun meetingsWith(pid: Long): Int
}

@Database(
    entities = [Meeting::class, ProvenanceRecord::class, ConsentLog::class, Segment::class, SegmentFts::class, MeetingSpeaker::class, Person::class],
    version = 3, exportSchema = false,
)
abstract class KatibDb : RoomDatabase() {
    abstract fun dao(): KatibDao

    companion object {
        @Volatile private var inst: KatibDb? = null
        fun get(ctx: Context): KatibDb = inst ?: synchronized(this) {
            inst ?: run {
                System.loadLibrary("sqlcipher")
                Room.databaseBuilder(ctx.applicationContext, KatibDb::class.java, "katib.db")
                    .openHelperFactory(SupportOpenHelperFactory(Crypto.dbPassphrase(ctx)))
                    .addMigrations(M1_2, M2_3)
                    .build().also { inst = it }
            }
        }
        private val M1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Meeting ADD COLUMN kind TEXT NOT NULL DEFAULT 'meeting'")
                db.execSQL("ALTER TABLE Meeting ADD COLUMN otherVoices INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Meeting ADD COLUMN lat REAL")
                db.execSQL("ALTER TABLE Meeting ADD COLUMN lon REAL")
                db.execSQL("ALTER TABLE Meeting ADD COLUMN locAccuracyM REAL")
                db.execSQL("ALTER TABLE Meeting ADD COLUMN locAt INTEGER")
            }
        }
        private val M2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Meeting ADD COLUMN uuid TEXT")
                db.execSQL("ALTER TABLE Meeting ADD COLUMN audioSha256 TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ProvenanceRecord` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`meetingId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `payload` TEXT NOT NULL, `payloadSha256` TEXT NOT NULL, " +
                    "`prevSha256` TEXT, `signature` TEXT NOT NULL, `keyAlias` TEXT NOT NULL, `certChain` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ProvenanceRecord_meetingId` ON `ProvenanceRecord` (`meetingId`)")
            }
        }
        fun closeAndForget() { inst?.close(); inst = null }
    }
}

fun FloatArray.toBytes(): ByteArray =
    ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).also { b -> forEach { b.putFloat(it) } }.array()

fun ByteArray.toFloats(): FloatArray {
    val b = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { b.getFloat() }
}
