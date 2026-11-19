package app.katib.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.FtsTableInfo;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class KatibDb_Impl extends KatibDb {
  private volatile KatibDao _katibDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(3) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `Meeting` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `durationSec` REAL NOT NULL, `audioPath` TEXT, `status` TEXT NOT NULL, `progress` REAL NOT NULL, `stage` TEXT, `error` TEXT, `language` TEXT NOT NULL, `model` TEXT, `rtf` REAL, `transcribedAt` INTEGER, `audioDeleteAt` INTEGER, `audioDeleted` INTEGER NOT NULL, `neverSync` INTEGER NOT NULL, `kind` TEXT NOT NULL DEFAULT 'meeting', `otherVoices` INTEGER NOT NULL DEFAULT 0, `lat` REAL, `lon` REAL, `locAccuracyM` REAL, `locAt` INTEGER, `uuid` TEXT, `audioSha256` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `ProvenanceRecord` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `meetingId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `payload` TEXT NOT NULL, `payloadSha256` TEXT NOT NULL, `prevSha256` TEXT, `signature` TEXT NOT NULL, `keyAlias` TEXT NOT NULL, `certChain` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ProvenanceRecord_meetingId` ON `ProvenanceRecord` (`meetingId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `ConsentLog` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `meetingId` INTEGER NOT NULL, `participants` TEXT NOT NULL, `method` TEXT NOT NULL, `confirmedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ConsentLog_meetingId` ON `ConsentLog` (`meetingId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `Segment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `meetingId` INTEGER NOT NULL, `startSec` REAL NOT NULL, `endSec` REAL NOT NULL, `text` TEXT NOT NULL, `speakerIdx` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Segment_meetingId` ON `Segment` (`meetingId`)");
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `SegmentFts` USING FTS4(`text` TEXT NOT NULL, content=`Segment`)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_SegmentFts_BEFORE_UPDATE BEFORE UPDATE ON `Segment` BEGIN DELETE FROM `SegmentFts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_SegmentFts_BEFORE_DELETE BEFORE DELETE ON `Segment` BEGIN DELETE FROM `SegmentFts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_SegmentFts_AFTER_UPDATE AFTER UPDATE ON `Segment` BEGIN INSERT INTO `SegmentFts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_SegmentFts_AFTER_INSERT AFTER INSERT ON `Segment` BEGIN INSERT INTO `SegmentFts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END");
        db.execSQL("CREATE TABLE IF NOT EXISTS `MeetingSpeaker` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `meetingId` INTEGER NOT NULL, `idx` INTEGER NOT NULL, `personId` INTEGER, `score` REAL NOT NULL, `speechSec` REAL NOT NULL, `embedding` BLOB)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_MeetingSpeaker_meetingId` ON `MeetingSpeaker` (`meetingId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `Person` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isOwner` INTEGER NOT NULL, `embedding` BLOB, `samples` INTEGER NOT NULL, `consentAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '64f791b532d9074b1a14711cdf984f3d')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `Meeting`");
        db.execSQL("DROP TABLE IF EXISTS `ProvenanceRecord`");
        db.execSQL("DROP TABLE IF EXISTS `ConsentLog`");
        db.execSQL("DROP TABLE IF EXISTS `Segment`");
        db.execSQL("DROP TABLE IF EXISTS `SegmentFts`");
        db.execSQL("DROP TABLE IF EXISTS `MeetingSpeaker`");
        db.execSQL("DROP TABLE IF EXISTS `Person`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_SegmentFts_BEFORE_UPDATE BEFORE UPDATE ON `Segment` BEGIN DELETE FROM `SegmentFts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_SegmentFts_BEFORE_DELETE BEFORE DELETE ON `Segment` BEGIN DELETE FROM `SegmentFts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_SegmentFts_AFTER_UPDATE AFTER UPDATE ON `Segment` BEGIN INSERT INTO `SegmentFts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_SegmentFts_AFTER_INSERT AFTER INSERT ON `Segment` BEGIN INSERT INTO `SegmentFts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END");
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsMeeting = new HashMap<String, TableInfo.Column>(24);
        _columnsMeeting.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("durationSec", new TableInfo.Column("durationSec", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("audioPath", new TableInfo.Column("audioPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("progress", new TableInfo.Column("progress", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("stage", new TableInfo.Column("stage", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("error", new TableInfo.Column("error", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("language", new TableInfo.Column("language", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("model", new TableInfo.Column("model", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("rtf", new TableInfo.Column("rtf", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("transcribedAt", new TableInfo.Column("transcribedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("audioDeleteAt", new TableInfo.Column("audioDeleteAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("audioDeleted", new TableInfo.Column("audioDeleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("neverSync", new TableInfo.Column("neverSync", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("kind", new TableInfo.Column("kind", "TEXT", true, 0, "'meeting'", TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("otherVoices", new TableInfo.Column("otherVoices", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("lat", new TableInfo.Column("lat", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("lon", new TableInfo.Column("lon", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("locAccuracyM", new TableInfo.Column("locAccuracyM", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("locAt", new TableInfo.Column("locAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("uuid", new TableInfo.Column("uuid", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeeting.put("audioSha256", new TableInfo.Column("audioSha256", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMeeting = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMeeting = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMeeting = new TableInfo("Meeting", _columnsMeeting, _foreignKeysMeeting, _indicesMeeting);
        final TableInfo _existingMeeting = TableInfo.read(db, "Meeting");
        if (!_infoMeeting.equals(_existingMeeting)) {
          return new RoomOpenHelper.ValidationResult(false, "Meeting(app.katib.data.Meeting).\n"
                  + " Expected:\n" + _infoMeeting + "\n"
                  + " Found:\n" + _existingMeeting);
        }
        final HashMap<String, TableInfo.Column> _columnsProvenanceRecord = new HashMap<String, TableInfo.Column>(10);
        _columnsProvenanceRecord.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProvenanceRecord.put("meetingId", new TableInfo.Column("meetingId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProvenanceRecord.put("kind", new TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProvenanceRecord.put("payload", new TableInfo.Column("payload", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProvenanceRecord.put("payloadSha256", new TableInfo.Column("payloadSha256", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProvenanceRecord.put("prevSha256", new TableInfo.Column("prevSha256", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProvenanceRecord.put("signature", new TableInfo.Column("signature", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProvenanceRecord.put("keyAlias", new TableInfo.Column("keyAlias", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProvenanceRecord.put("certChain", new TableInfo.Column("certChain", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProvenanceRecord.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysProvenanceRecord = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesProvenanceRecord = new HashSet<TableInfo.Index>(1);
        _indicesProvenanceRecord.add(new TableInfo.Index("index_ProvenanceRecord_meetingId", false, Arrays.asList("meetingId"), Arrays.asList("ASC")));
        final TableInfo _infoProvenanceRecord = new TableInfo("ProvenanceRecord", _columnsProvenanceRecord, _foreignKeysProvenanceRecord, _indicesProvenanceRecord);
        final TableInfo _existingProvenanceRecord = TableInfo.read(db, "ProvenanceRecord");
        if (!_infoProvenanceRecord.equals(_existingProvenanceRecord)) {
          return new RoomOpenHelper.ValidationResult(false, "ProvenanceRecord(app.katib.data.ProvenanceRecord).\n"
                  + " Expected:\n" + _infoProvenanceRecord + "\n"
                  + " Found:\n" + _existingProvenanceRecord);
        }
        final HashMap<String, TableInfo.Column> _columnsConsentLog = new HashMap<String, TableInfo.Column>(5);
        _columnsConsentLog.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConsentLog.put("meetingId", new TableInfo.Column("meetingId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConsentLog.put("participants", new TableInfo.Column("participants", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConsentLog.put("method", new TableInfo.Column("method", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConsentLog.put("confirmedAt", new TableInfo.Column("confirmedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysConsentLog = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesConsentLog = new HashSet<TableInfo.Index>(1);
        _indicesConsentLog.add(new TableInfo.Index("index_ConsentLog_meetingId", false, Arrays.asList("meetingId"), Arrays.asList("ASC")));
        final TableInfo _infoConsentLog = new TableInfo("ConsentLog", _columnsConsentLog, _foreignKeysConsentLog, _indicesConsentLog);
        final TableInfo _existingConsentLog = TableInfo.read(db, "ConsentLog");
        if (!_infoConsentLog.equals(_existingConsentLog)) {
          return new RoomOpenHelper.ValidationResult(false, "ConsentLog(app.katib.data.ConsentLog).\n"
                  + " Expected:\n" + _infoConsentLog + "\n"
                  + " Found:\n" + _existingConsentLog);
        }
        final HashMap<String, TableInfo.Column> _columnsSegment = new HashMap<String, TableInfo.Column>(6);
        _columnsSegment.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSegment.put("meetingId", new TableInfo.Column("meetingId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSegment.put("startSec", new TableInfo.Column("startSec", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSegment.put("endSec", new TableInfo.Column("endSec", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSegment.put("text", new TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSegment.put("speakerIdx", new TableInfo.Column("speakerIdx", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSegment = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSegment = new HashSet<TableInfo.Index>(1);
        _indicesSegment.add(new TableInfo.Index("index_Segment_meetingId", false, Arrays.asList("meetingId"), Arrays.asList("ASC")));
        final TableInfo _infoSegment = new TableInfo("Segment", _columnsSegment, _foreignKeysSegment, _indicesSegment);
        final TableInfo _existingSegment = TableInfo.read(db, "Segment");
        if (!_infoSegment.equals(_existingSegment)) {
          return new RoomOpenHelper.ValidationResult(false, "Segment(app.katib.data.Segment).\n"
                  + " Expected:\n" + _infoSegment + "\n"
                  + " Found:\n" + _existingSegment);
        }
        final HashSet<String> _columnsSegmentFts = new HashSet<String>(1);
        _columnsSegmentFts.add("text");
        final FtsTableInfo _infoSegmentFts = new FtsTableInfo("SegmentFts", _columnsSegmentFts, "CREATE VIRTUAL TABLE IF NOT EXISTS `SegmentFts` USING FTS4(`text` TEXT NOT NULL, content=`Segment`)");
        final FtsTableInfo _existingSegmentFts = FtsTableInfo.read(db, "SegmentFts");
        if (!_infoSegmentFts.equals(_existingSegmentFts)) {
          return new RoomOpenHelper.ValidationResult(false, "SegmentFts(app.katib.data.SegmentFts).\n"
                  + " Expected:\n" + _infoSegmentFts + "\n"
                  + " Found:\n" + _existingSegmentFts);
        }
        final HashMap<String, TableInfo.Column> _columnsMeetingSpeaker = new HashMap<String, TableInfo.Column>(7);
        _columnsMeetingSpeaker.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeetingSpeaker.put("meetingId", new TableInfo.Column("meetingId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeetingSpeaker.put("idx", new TableInfo.Column("idx", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeetingSpeaker.put("personId", new TableInfo.Column("personId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeetingSpeaker.put("score", new TableInfo.Column("score", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeetingSpeaker.put("speechSec", new TableInfo.Column("speechSec", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeetingSpeaker.put("embedding", new TableInfo.Column("embedding", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMeetingSpeaker = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMeetingSpeaker = new HashSet<TableInfo.Index>(1);
        _indicesMeetingSpeaker.add(new TableInfo.Index("index_MeetingSpeaker_meetingId", false, Arrays.asList("meetingId"), Arrays.asList("ASC")));
        final TableInfo _infoMeetingSpeaker = new TableInfo("MeetingSpeaker", _columnsMeetingSpeaker, _foreignKeysMeetingSpeaker, _indicesMeetingSpeaker);
        final TableInfo _existingMeetingSpeaker = TableInfo.read(db, "MeetingSpeaker");
        if (!_infoMeetingSpeaker.equals(_existingMeetingSpeaker)) {
          return new RoomOpenHelper.ValidationResult(false, "MeetingSpeaker(app.katib.data.MeetingSpeaker).\n"
                  + " Expected:\n" + _infoMeetingSpeaker + "\n"
                  + " Found:\n" + _existingMeetingSpeaker);
        }
        final HashMap<String, TableInfo.Column> _columnsPerson = new HashMap<String, TableInfo.Column>(6);
        _columnsPerson.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPerson.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPerson.put("isOwner", new TableInfo.Column("isOwner", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPerson.put("embedding", new TableInfo.Column("embedding", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPerson.put("samples", new TableInfo.Column("samples", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPerson.put("consentAt", new TableInfo.Column("consentAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPerson = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPerson = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPerson = new TableInfo("Person", _columnsPerson, _foreignKeysPerson, _indicesPerson);
        final TableInfo _existingPerson = TableInfo.read(db, "Person");
        if (!_infoPerson.equals(_existingPerson)) {
          return new RoomOpenHelper.ValidationResult(false, "Person(app.katib.data.Person).\n"
                  + " Expected:\n" + _infoPerson + "\n"
                  + " Found:\n" + _existingPerson);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "64f791b532d9074b1a14711cdf984f3d", "ac2c3ced4b3939e8af284421fbbf4d09");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(1);
    _shadowTablesMap.put("SegmentFts", "Segment");
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "Meeting","ProvenanceRecord","ConsentLog","Segment","SegmentFts","MeetingSpeaker","Person");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `Meeting`");
      _db.execSQL("DELETE FROM `ProvenanceRecord`");
      _db.execSQL("DELETE FROM `ConsentLog`");
      _db.execSQL("DELETE FROM `Segment`");
      _db.execSQL("DELETE FROM `SegmentFts`");
      _db.execSQL("DELETE FROM `MeetingSpeaker`");
      _db.execSQL("DELETE FROM `Person`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(KatibDao.class, KatibDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public KatibDao dao() {
    if (_katibDao != null) {
      return _katibDao;
    } else {
      synchronized(this) {
        if(_katibDao == null) {
          _katibDao = new KatibDao_Impl(this);
        }
        return _katibDao;
      }
    }
  }
}
