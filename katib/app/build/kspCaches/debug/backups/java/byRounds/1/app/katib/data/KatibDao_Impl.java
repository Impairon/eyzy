package app.katib.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Float;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class KatibDao_Impl implements KatibDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Meeting> __insertionAdapterOfMeeting;

  private final EntityInsertionAdapter<ProvenanceRecord> __insertionAdapterOfProvenanceRecord;

  private final EntityInsertionAdapter<ConsentLog> __insertionAdapterOfConsentLog;

  private final EntityInsertionAdapter<Segment> __insertionAdapterOfSegment;

  private final EntityInsertionAdapter<MeetingSpeaker> __insertionAdapterOfMeetingSpeaker;

  private final EntityInsertionAdapter<Person> __insertionAdapterOfPerson;

  private final EntityDeletionOrUpdateAdapter<Meeting> __deletionAdapterOfMeeting;

  private final EntityDeletionOrUpdateAdapter<Person> __deletionAdapterOfPerson;

  private final EntityDeletionOrUpdateAdapter<Meeting> __updateAdapterOfMeeting;

  private final EntityDeletionOrUpdateAdapter<Person> __updateAdapterOfPerson;

  private final SharedSQLiteStatement __preparedStmtOfSetProgress;

  private final SharedSQLiteStatement __preparedStmtOfRename;

  private final SharedSQLiteStatement __preparedStmtOfDeleteProvenance;

  private final SharedSQLiteStatement __preparedStmtOfEditSegment;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSegments;

  private final SharedSQLiteStatement __preparedStmtOfAssignSpeaker;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSpeakers;

  private final SharedSQLiteStatement __preparedStmtOfUnlinkPerson;

  public KatibDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMeeting = new EntityInsertionAdapter<Meeting>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `Meeting` (`id`,`title`,`createdAt`,`durationSec`,`audioPath`,`status`,`progress`,`stage`,`error`,`language`,`model`,`rtf`,`transcribedAt`,`audioDeleteAt`,`audioDeleted`,`neverSync`,`kind`,`otherVoices`,`lat`,`lon`,`locAccuracyM`,`locAt`,`uuid`,`audioSha256`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Meeting entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindLong(3, entity.getCreatedAt());
        statement.bindDouble(4, entity.getDurationSec());
        if (entity.getAudioPath() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getAudioPath());
        }
        statement.bindString(6, entity.getStatus());
        statement.bindDouble(7, entity.getProgress());
        if (entity.getStage() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getStage());
        }
        if (entity.getError() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getError());
        }
        statement.bindString(10, entity.getLanguage());
        if (entity.getModel() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getModel());
        }
        if (entity.getRtf() == null) {
          statement.bindNull(12);
        } else {
          statement.bindDouble(12, entity.getRtf());
        }
        if (entity.getTranscribedAt() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getTranscribedAt());
        }
        if (entity.getAudioDeleteAt() == null) {
          statement.bindNull(14);
        } else {
          statement.bindLong(14, entity.getAudioDeleteAt());
        }
        final int _tmp = entity.getAudioDeleted() ? 1 : 0;
        statement.bindLong(15, _tmp);
        final int _tmp_1 = entity.getNeverSync() ? 1 : 0;
        statement.bindLong(16, _tmp_1);
        statement.bindString(17, entity.getKind());
        final int _tmp_2 = entity.getOtherVoices() ? 1 : 0;
        statement.bindLong(18, _tmp_2);
        if (entity.getLat() == null) {
          statement.bindNull(19);
        } else {
          statement.bindDouble(19, entity.getLat());
        }
        if (entity.getLon() == null) {
          statement.bindNull(20);
        } else {
          statement.bindDouble(20, entity.getLon());
        }
        if (entity.getLocAccuracyM() == null) {
          statement.bindNull(21);
        } else {
          statement.bindDouble(21, entity.getLocAccuracyM());
        }
        if (entity.getLocAt() == null) {
          statement.bindNull(22);
        } else {
          statement.bindLong(22, entity.getLocAt());
        }
        if (entity.getUuid() == null) {
          statement.bindNull(23);
        } else {
          statement.bindString(23, entity.getUuid());
        }
        if (entity.getAudioSha256() == null) {
          statement.bindNull(24);
        } else {
          statement.bindString(24, entity.getAudioSha256());
        }
      }
    };
    this.__insertionAdapterOfProvenanceRecord = new EntityInsertionAdapter<ProvenanceRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `ProvenanceRecord` (`id`,`meetingId`,`kind`,`payload`,`payloadSha256`,`prevSha256`,`signature`,`keyAlias`,`certChain`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProvenanceRecord entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getMeetingId());
        statement.bindString(3, entity.getKind());
        statement.bindString(4, entity.getPayload());
        statement.bindString(5, entity.getPayloadSha256());
        if (entity.getPrevSha256() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getPrevSha256());
        }
        statement.bindString(7, entity.getSignature());
        statement.bindString(8, entity.getKeyAlias());
        statement.bindString(9, entity.getCertChain());
        statement.bindLong(10, entity.getCreatedAt());
      }
    };
    this.__insertionAdapterOfConsentLog = new EntityInsertionAdapter<ConsentLog>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `ConsentLog` (`id`,`meetingId`,`participants`,`method`,`confirmedAt`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ConsentLog entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getMeetingId());
        statement.bindString(3, entity.getParticipants());
        statement.bindString(4, entity.getMethod());
        statement.bindLong(5, entity.getConfirmedAt());
      }
    };
    this.__insertionAdapterOfSegment = new EntityInsertionAdapter<Segment>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `Segment` (`id`,`meetingId`,`startSec`,`endSec`,`text`,`speakerIdx`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Segment entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getMeetingId());
        statement.bindDouble(3, entity.getStartSec());
        statement.bindDouble(4, entity.getEndSec());
        statement.bindString(5, entity.getText());
        statement.bindLong(6, entity.getSpeakerIdx());
      }
    };
    this.__insertionAdapterOfMeetingSpeaker = new EntityInsertionAdapter<MeetingSpeaker>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `MeetingSpeaker` (`id`,`meetingId`,`idx`,`personId`,`score`,`speechSec`,`embedding`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MeetingSpeaker entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getMeetingId());
        statement.bindLong(3, entity.getIdx());
        if (entity.getPersonId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getPersonId());
        }
        statement.bindDouble(5, entity.getScore());
        statement.bindDouble(6, entity.getSpeechSec());
        if (entity.getEmbedding() == null) {
          statement.bindNull(7);
        } else {
          statement.bindBlob(7, entity.getEmbedding());
        }
      }
    };
    this.__insertionAdapterOfPerson = new EntityInsertionAdapter<Person>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `Person` (`id`,`name`,`isOwner`,`embedding`,`samples`,`consentAt`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Person entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        final int _tmp = entity.isOwner() ? 1 : 0;
        statement.bindLong(3, _tmp);
        if (entity.getEmbedding() == null) {
          statement.bindNull(4);
        } else {
          statement.bindBlob(4, entity.getEmbedding());
        }
        statement.bindLong(5, entity.getSamples());
        statement.bindLong(6, entity.getConsentAt());
      }
    };
    this.__deletionAdapterOfMeeting = new EntityDeletionOrUpdateAdapter<Meeting>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `Meeting` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Meeting entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__deletionAdapterOfPerson = new EntityDeletionOrUpdateAdapter<Person>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `Person` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Person entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfMeeting = new EntityDeletionOrUpdateAdapter<Meeting>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `Meeting` SET `id` = ?,`title` = ?,`createdAt` = ?,`durationSec` = ?,`audioPath` = ?,`status` = ?,`progress` = ?,`stage` = ?,`error` = ?,`language` = ?,`model` = ?,`rtf` = ?,`transcribedAt` = ?,`audioDeleteAt` = ?,`audioDeleted` = ?,`neverSync` = ?,`kind` = ?,`otherVoices` = ?,`lat` = ?,`lon` = ?,`locAccuracyM` = ?,`locAt` = ?,`uuid` = ?,`audioSha256` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Meeting entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindLong(3, entity.getCreatedAt());
        statement.bindDouble(4, entity.getDurationSec());
        if (entity.getAudioPath() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getAudioPath());
        }
        statement.bindString(6, entity.getStatus());
        statement.bindDouble(7, entity.getProgress());
        if (entity.getStage() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getStage());
        }
        if (entity.getError() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getError());
        }
        statement.bindString(10, entity.getLanguage());
        if (entity.getModel() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getModel());
        }
        if (entity.getRtf() == null) {
          statement.bindNull(12);
        } else {
          statement.bindDouble(12, entity.getRtf());
        }
        if (entity.getTranscribedAt() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getTranscribedAt());
        }
        if (entity.getAudioDeleteAt() == null) {
          statement.bindNull(14);
        } else {
          statement.bindLong(14, entity.getAudioDeleteAt());
        }
        final int _tmp = entity.getAudioDeleted() ? 1 : 0;
        statement.bindLong(15, _tmp);
        final int _tmp_1 = entity.getNeverSync() ? 1 : 0;
        statement.bindLong(16, _tmp_1);
        statement.bindString(17, entity.getKind());
        final int _tmp_2 = entity.getOtherVoices() ? 1 : 0;
        statement.bindLong(18, _tmp_2);
        if (entity.getLat() == null) {
          statement.bindNull(19);
        } else {
          statement.bindDouble(19, entity.getLat());
        }
        if (entity.getLon() == null) {
          statement.bindNull(20);
        } else {
          statement.bindDouble(20, entity.getLon());
        }
        if (entity.getLocAccuracyM() == null) {
          statement.bindNull(21);
        } else {
          statement.bindDouble(21, entity.getLocAccuracyM());
        }
        if (entity.getLocAt() == null) {
          statement.bindNull(22);
        } else {
          statement.bindLong(22, entity.getLocAt());
        }
        if (entity.getUuid() == null) {
          statement.bindNull(23);
        } else {
          statement.bindString(23, entity.getUuid());
        }
        if (entity.getAudioSha256() == null) {
          statement.bindNull(24);
        } else {
          statement.bindString(24, entity.getAudioSha256());
        }
        statement.bindLong(25, entity.getId());
      }
    };
    this.__updateAdapterOfPerson = new EntityDeletionOrUpdateAdapter<Person>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `Person` SET `id` = ?,`name` = ?,`isOwner` = ?,`embedding` = ?,`samples` = ?,`consentAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Person entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        final int _tmp = entity.isOwner() ? 1 : 0;
        statement.bindLong(3, _tmp);
        if (entity.getEmbedding() == null) {
          statement.bindNull(4);
        } else {
          statement.bindBlob(4, entity.getEmbedding());
        }
        statement.bindLong(5, entity.getSamples());
        statement.bindLong(6, entity.getConsentAt());
        statement.bindLong(7, entity.getId());
      }
    };
    this.__preparedStmtOfSetProgress = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE Meeting SET progress=?, stage=? WHERE id=?";
        return _query;
      }
    };
    this.__preparedStmtOfRename = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE Meeting SET title=? WHERE id=?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteProvenance = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM ProvenanceRecord WHERE meetingId=?";
        return _query;
      }
    };
    this.__preparedStmtOfEditSegment = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE Segment SET text=? WHERE id=?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSegments = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM Segment WHERE meetingId=?";
        return _query;
      }
    };
    this.__preparedStmtOfAssignSpeaker = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE MeetingSpeaker SET personId=?, score=1 WHERE id=?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSpeakers = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM MeetingSpeaker WHERE meetingId=?";
        return _query;
      }
    };
    this.__preparedStmtOfUnlinkPerson = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE MeetingSpeaker SET personId=NULL WHERE personId=?";
        return _query;
      }
    };
  }

  @Override
  public Object insertMeeting(final Meeting m, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfMeeting.insertAndReturnId(m);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertProvenance(final ProvenanceRecord p,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfProvenanceRecord.insertAndReturnId(p);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertConsent(final ConsentLog c, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfConsentLog.insertAndReturnId(c);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSegments(final List<Segment> s,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSegment.insert(s);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSpeakers(final List<MeetingSpeaker> s,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMeetingSpeaker.insert(s);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertPerson(final Person p, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfPerson.insertAndReturnId(p);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteMeeting(final Meeting m, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfMeeting.handle(m);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deletePerson(final Person p, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfPerson.handle(p);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateMeeting(final Meeting m, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfMeeting.handle(m);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updatePerson(final Person p, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfPerson.handle(p);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object setProgress(final long id, final float p, final String stage,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetProgress.acquire();
        int _argIndex = 1;
        _stmt.bindDouble(_argIndex, p);
        _argIndex = 2;
        _stmt.bindString(_argIndex, stage);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetProgress.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object rename(final long id, final String t,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRename.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, t);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRename.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteProvenance(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteProvenance.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteProvenance.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object editSegment(final long id, final String text,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfEditSegment.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, text);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfEditSegment.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSegments(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSegments.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteSegments.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object assignSpeaker(final long id, final Long pid,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfAssignSpeaker.acquire();
        int _argIndex = 1;
        if (pid == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, pid);
        }
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfAssignSpeaker.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSpeakers(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSpeakers.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteSpeakers.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object unlinkPerson(final long pid, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUnlinkPerson.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, pid);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUnlinkPerson.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object meeting(final long id, final Continuation<? super Meeting> $completion) {
    final String _sql = "SELECT * FROM Meeting WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Meeting>() {
      @Override
      @Nullable
      public Meeting call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDurationSec = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSec");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfStage = CursorUtil.getColumnIndexOrThrow(_cursor, "stage");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfRtf = CursorUtil.getColumnIndexOrThrow(_cursor, "rtf");
          final int _cursorIndexOfTranscribedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "transcribedAt");
          final int _cursorIndexOfAudioDeleteAt = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleteAt");
          final int _cursorIndexOfAudioDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleted");
          final int _cursorIndexOfNeverSync = CursorUtil.getColumnIndexOrThrow(_cursor, "neverSync");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfOtherVoices = CursorUtil.getColumnIndexOrThrow(_cursor, "otherVoices");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLon = CursorUtil.getColumnIndexOrThrow(_cursor, "lon");
          final int _cursorIndexOfLocAccuracyM = CursorUtil.getColumnIndexOrThrow(_cursor, "locAccuracyM");
          final int _cursorIndexOfLocAt = CursorUtil.getColumnIndexOrThrow(_cursor, "locAt");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfAudioSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "audioSha256");
          final Meeting _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final float _tmpDurationSec;
            _tmpDurationSec = _cursor.getFloat(_cursorIndexOfDurationSec);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final float _tmpProgress;
            _tmpProgress = _cursor.getFloat(_cursorIndexOfProgress);
            final String _tmpStage;
            if (_cursor.isNull(_cursorIndexOfStage)) {
              _tmpStage = null;
            } else {
              _tmpStage = _cursor.getString(_cursorIndexOfStage);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpModel;
            if (_cursor.isNull(_cursorIndexOfModel)) {
              _tmpModel = null;
            } else {
              _tmpModel = _cursor.getString(_cursorIndexOfModel);
            }
            final Float _tmpRtf;
            if (_cursor.isNull(_cursorIndexOfRtf)) {
              _tmpRtf = null;
            } else {
              _tmpRtf = _cursor.getFloat(_cursorIndexOfRtf);
            }
            final Long _tmpTranscribedAt;
            if (_cursor.isNull(_cursorIndexOfTranscribedAt)) {
              _tmpTranscribedAt = null;
            } else {
              _tmpTranscribedAt = _cursor.getLong(_cursorIndexOfTranscribedAt);
            }
            final Long _tmpAudioDeleteAt;
            if (_cursor.isNull(_cursorIndexOfAudioDeleteAt)) {
              _tmpAudioDeleteAt = null;
            } else {
              _tmpAudioDeleteAt = _cursor.getLong(_cursorIndexOfAudioDeleteAt);
            }
            final boolean _tmpAudioDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAudioDeleted);
            _tmpAudioDeleted = _tmp != 0;
            final boolean _tmpNeverSync;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfNeverSync);
            _tmpNeverSync = _tmp_1 != 0;
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final boolean _tmpOtherVoices;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfOtherVoices);
            _tmpOtherVoices = _tmp_2 != 0;
            final Double _tmpLat;
            if (_cursor.isNull(_cursorIndexOfLat)) {
              _tmpLat = null;
            } else {
              _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            }
            final Double _tmpLon;
            if (_cursor.isNull(_cursorIndexOfLon)) {
              _tmpLon = null;
            } else {
              _tmpLon = _cursor.getDouble(_cursorIndexOfLon);
            }
            final Float _tmpLocAccuracyM;
            if (_cursor.isNull(_cursorIndexOfLocAccuracyM)) {
              _tmpLocAccuracyM = null;
            } else {
              _tmpLocAccuracyM = _cursor.getFloat(_cursorIndexOfLocAccuracyM);
            }
            final Long _tmpLocAt;
            if (_cursor.isNull(_cursorIndexOfLocAt)) {
              _tmpLocAt = null;
            } else {
              _tmpLocAt = _cursor.getLong(_cursorIndexOfLocAt);
            }
            final String _tmpUuid;
            if (_cursor.isNull(_cursorIndexOfUuid)) {
              _tmpUuid = null;
            } else {
              _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            }
            final String _tmpAudioSha256;
            if (_cursor.isNull(_cursorIndexOfAudioSha256)) {
              _tmpAudioSha256 = null;
            } else {
              _tmpAudioSha256 = _cursor.getString(_cursorIndexOfAudioSha256);
            }
            _result = new Meeting(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpDurationSec,_tmpAudioPath,_tmpStatus,_tmpProgress,_tmpStage,_tmpError,_tmpLanguage,_tmpModel,_tmpRtf,_tmpTranscribedAt,_tmpAudioDeleteAt,_tmpAudioDeleted,_tmpNeverSync,_tmpKind,_tmpOtherVoices,_tmpLat,_tmpLon,_tmpLocAccuracyM,_tmpLocAt,_tmpUuid,_tmpAudioSha256);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Meeting> meetingFlow(final long id) {
    final String _sql = "SELECT * FROM Meeting WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"Meeting"}, new Callable<Meeting>() {
      @Override
      @Nullable
      public Meeting call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDurationSec = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSec");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfStage = CursorUtil.getColumnIndexOrThrow(_cursor, "stage");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfRtf = CursorUtil.getColumnIndexOrThrow(_cursor, "rtf");
          final int _cursorIndexOfTranscribedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "transcribedAt");
          final int _cursorIndexOfAudioDeleteAt = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleteAt");
          final int _cursorIndexOfAudioDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleted");
          final int _cursorIndexOfNeverSync = CursorUtil.getColumnIndexOrThrow(_cursor, "neverSync");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfOtherVoices = CursorUtil.getColumnIndexOrThrow(_cursor, "otherVoices");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLon = CursorUtil.getColumnIndexOrThrow(_cursor, "lon");
          final int _cursorIndexOfLocAccuracyM = CursorUtil.getColumnIndexOrThrow(_cursor, "locAccuracyM");
          final int _cursorIndexOfLocAt = CursorUtil.getColumnIndexOrThrow(_cursor, "locAt");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfAudioSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "audioSha256");
          final Meeting _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final float _tmpDurationSec;
            _tmpDurationSec = _cursor.getFloat(_cursorIndexOfDurationSec);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final float _tmpProgress;
            _tmpProgress = _cursor.getFloat(_cursorIndexOfProgress);
            final String _tmpStage;
            if (_cursor.isNull(_cursorIndexOfStage)) {
              _tmpStage = null;
            } else {
              _tmpStage = _cursor.getString(_cursorIndexOfStage);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpModel;
            if (_cursor.isNull(_cursorIndexOfModel)) {
              _tmpModel = null;
            } else {
              _tmpModel = _cursor.getString(_cursorIndexOfModel);
            }
            final Float _tmpRtf;
            if (_cursor.isNull(_cursorIndexOfRtf)) {
              _tmpRtf = null;
            } else {
              _tmpRtf = _cursor.getFloat(_cursorIndexOfRtf);
            }
            final Long _tmpTranscribedAt;
            if (_cursor.isNull(_cursorIndexOfTranscribedAt)) {
              _tmpTranscribedAt = null;
            } else {
              _tmpTranscribedAt = _cursor.getLong(_cursorIndexOfTranscribedAt);
            }
            final Long _tmpAudioDeleteAt;
            if (_cursor.isNull(_cursorIndexOfAudioDeleteAt)) {
              _tmpAudioDeleteAt = null;
            } else {
              _tmpAudioDeleteAt = _cursor.getLong(_cursorIndexOfAudioDeleteAt);
            }
            final boolean _tmpAudioDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAudioDeleted);
            _tmpAudioDeleted = _tmp != 0;
            final boolean _tmpNeverSync;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfNeverSync);
            _tmpNeverSync = _tmp_1 != 0;
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final boolean _tmpOtherVoices;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfOtherVoices);
            _tmpOtherVoices = _tmp_2 != 0;
            final Double _tmpLat;
            if (_cursor.isNull(_cursorIndexOfLat)) {
              _tmpLat = null;
            } else {
              _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            }
            final Double _tmpLon;
            if (_cursor.isNull(_cursorIndexOfLon)) {
              _tmpLon = null;
            } else {
              _tmpLon = _cursor.getDouble(_cursorIndexOfLon);
            }
            final Float _tmpLocAccuracyM;
            if (_cursor.isNull(_cursorIndexOfLocAccuracyM)) {
              _tmpLocAccuracyM = null;
            } else {
              _tmpLocAccuracyM = _cursor.getFloat(_cursorIndexOfLocAccuracyM);
            }
            final Long _tmpLocAt;
            if (_cursor.isNull(_cursorIndexOfLocAt)) {
              _tmpLocAt = null;
            } else {
              _tmpLocAt = _cursor.getLong(_cursorIndexOfLocAt);
            }
            final String _tmpUuid;
            if (_cursor.isNull(_cursorIndexOfUuid)) {
              _tmpUuid = null;
            } else {
              _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            }
            final String _tmpAudioSha256;
            if (_cursor.isNull(_cursorIndexOfAudioSha256)) {
              _tmpAudioSha256 = null;
            } else {
              _tmpAudioSha256 = _cursor.getString(_cursorIndexOfAudioSha256);
            }
            _result = new Meeting(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpDurationSec,_tmpAudioPath,_tmpStatus,_tmpProgress,_tmpStage,_tmpError,_tmpLanguage,_tmpModel,_tmpRtf,_tmpTranscribedAt,_tmpAudioDeleteAt,_tmpAudioDeleted,_tmpNeverSync,_tmpKind,_tmpOtherVoices,_tmpLat,_tmpLon,_tmpLocAccuracyM,_tmpLocAt,_tmpUuid,_tmpAudioSha256);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<Meeting>> meetings() {
    final String _sql = "SELECT * FROM Meeting ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"Meeting"}, new Callable<List<Meeting>>() {
      @Override
      @NonNull
      public List<Meeting> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDurationSec = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSec");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfStage = CursorUtil.getColumnIndexOrThrow(_cursor, "stage");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfRtf = CursorUtil.getColumnIndexOrThrow(_cursor, "rtf");
          final int _cursorIndexOfTranscribedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "transcribedAt");
          final int _cursorIndexOfAudioDeleteAt = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleteAt");
          final int _cursorIndexOfAudioDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleted");
          final int _cursorIndexOfNeverSync = CursorUtil.getColumnIndexOrThrow(_cursor, "neverSync");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfOtherVoices = CursorUtil.getColumnIndexOrThrow(_cursor, "otherVoices");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLon = CursorUtil.getColumnIndexOrThrow(_cursor, "lon");
          final int _cursorIndexOfLocAccuracyM = CursorUtil.getColumnIndexOrThrow(_cursor, "locAccuracyM");
          final int _cursorIndexOfLocAt = CursorUtil.getColumnIndexOrThrow(_cursor, "locAt");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfAudioSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "audioSha256");
          final List<Meeting> _result = new ArrayList<Meeting>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Meeting _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final float _tmpDurationSec;
            _tmpDurationSec = _cursor.getFloat(_cursorIndexOfDurationSec);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final float _tmpProgress;
            _tmpProgress = _cursor.getFloat(_cursorIndexOfProgress);
            final String _tmpStage;
            if (_cursor.isNull(_cursorIndexOfStage)) {
              _tmpStage = null;
            } else {
              _tmpStage = _cursor.getString(_cursorIndexOfStage);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpModel;
            if (_cursor.isNull(_cursorIndexOfModel)) {
              _tmpModel = null;
            } else {
              _tmpModel = _cursor.getString(_cursorIndexOfModel);
            }
            final Float _tmpRtf;
            if (_cursor.isNull(_cursorIndexOfRtf)) {
              _tmpRtf = null;
            } else {
              _tmpRtf = _cursor.getFloat(_cursorIndexOfRtf);
            }
            final Long _tmpTranscribedAt;
            if (_cursor.isNull(_cursorIndexOfTranscribedAt)) {
              _tmpTranscribedAt = null;
            } else {
              _tmpTranscribedAt = _cursor.getLong(_cursorIndexOfTranscribedAt);
            }
            final Long _tmpAudioDeleteAt;
            if (_cursor.isNull(_cursorIndexOfAudioDeleteAt)) {
              _tmpAudioDeleteAt = null;
            } else {
              _tmpAudioDeleteAt = _cursor.getLong(_cursorIndexOfAudioDeleteAt);
            }
            final boolean _tmpAudioDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAudioDeleted);
            _tmpAudioDeleted = _tmp != 0;
            final boolean _tmpNeverSync;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfNeverSync);
            _tmpNeverSync = _tmp_1 != 0;
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final boolean _tmpOtherVoices;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfOtherVoices);
            _tmpOtherVoices = _tmp_2 != 0;
            final Double _tmpLat;
            if (_cursor.isNull(_cursorIndexOfLat)) {
              _tmpLat = null;
            } else {
              _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            }
            final Double _tmpLon;
            if (_cursor.isNull(_cursorIndexOfLon)) {
              _tmpLon = null;
            } else {
              _tmpLon = _cursor.getDouble(_cursorIndexOfLon);
            }
            final Float _tmpLocAccuracyM;
            if (_cursor.isNull(_cursorIndexOfLocAccuracyM)) {
              _tmpLocAccuracyM = null;
            } else {
              _tmpLocAccuracyM = _cursor.getFloat(_cursorIndexOfLocAccuracyM);
            }
            final Long _tmpLocAt;
            if (_cursor.isNull(_cursorIndexOfLocAt)) {
              _tmpLocAt = null;
            } else {
              _tmpLocAt = _cursor.getLong(_cursorIndexOfLocAt);
            }
            final String _tmpUuid;
            if (_cursor.isNull(_cursorIndexOfUuid)) {
              _tmpUuid = null;
            } else {
              _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            }
            final String _tmpAudioSha256;
            if (_cursor.isNull(_cursorIndexOfAudioSha256)) {
              _tmpAudioSha256 = null;
            } else {
              _tmpAudioSha256 = _cursor.getString(_cursorIndexOfAudioSha256);
            }
            _item = new Meeting(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpDurationSec,_tmpAudioPath,_tmpStatus,_tmpProgress,_tmpStage,_tmpError,_tmpLanguage,_tmpModel,_tmpRtf,_tmpTranscribedAt,_tmpAudioDeleteAt,_tmpAudioDeleted,_tmpNeverSync,_tmpKind,_tmpOtherVoices,_tmpLat,_tmpLon,_tmpLocAccuracyM,_tmpLocAt,_tmpUuid,_tmpAudioSha256);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object pending(final Continuation<? super List<Meeting>> $completion) {
    final String _sql = "SELECT * FROM Meeting WHERE status IN ('queued','transcribing')";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Meeting>>() {
      @Override
      @NonNull
      public List<Meeting> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDurationSec = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSec");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfStage = CursorUtil.getColumnIndexOrThrow(_cursor, "stage");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfRtf = CursorUtil.getColumnIndexOrThrow(_cursor, "rtf");
          final int _cursorIndexOfTranscribedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "transcribedAt");
          final int _cursorIndexOfAudioDeleteAt = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleteAt");
          final int _cursorIndexOfAudioDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleted");
          final int _cursorIndexOfNeverSync = CursorUtil.getColumnIndexOrThrow(_cursor, "neverSync");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfOtherVoices = CursorUtil.getColumnIndexOrThrow(_cursor, "otherVoices");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLon = CursorUtil.getColumnIndexOrThrow(_cursor, "lon");
          final int _cursorIndexOfLocAccuracyM = CursorUtil.getColumnIndexOrThrow(_cursor, "locAccuracyM");
          final int _cursorIndexOfLocAt = CursorUtil.getColumnIndexOrThrow(_cursor, "locAt");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfAudioSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "audioSha256");
          final List<Meeting> _result = new ArrayList<Meeting>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Meeting _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final float _tmpDurationSec;
            _tmpDurationSec = _cursor.getFloat(_cursorIndexOfDurationSec);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final float _tmpProgress;
            _tmpProgress = _cursor.getFloat(_cursorIndexOfProgress);
            final String _tmpStage;
            if (_cursor.isNull(_cursorIndexOfStage)) {
              _tmpStage = null;
            } else {
              _tmpStage = _cursor.getString(_cursorIndexOfStage);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpModel;
            if (_cursor.isNull(_cursorIndexOfModel)) {
              _tmpModel = null;
            } else {
              _tmpModel = _cursor.getString(_cursorIndexOfModel);
            }
            final Float _tmpRtf;
            if (_cursor.isNull(_cursorIndexOfRtf)) {
              _tmpRtf = null;
            } else {
              _tmpRtf = _cursor.getFloat(_cursorIndexOfRtf);
            }
            final Long _tmpTranscribedAt;
            if (_cursor.isNull(_cursorIndexOfTranscribedAt)) {
              _tmpTranscribedAt = null;
            } else {
              _tmpTranscribedAt = _cursor.getLong(_cursorIndexOfTranscribedAt);
            }
            final Long _tmpAudioDeleteAt;
            if (_cursor.isNull(_cursorIndexOfAudioDeleteAt)) {
              _tmpAudioDeleteAt = null;
            } else {
              _tmpAudioDeleteAt = _cursor.getLong(_cursorIndexOfAudioDeleteAt);
            }
            final boolean _tmpAudioDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAudioDeleted);
            _tmpAudioDeleted = _tmp != 0;
            final boolean _tmpNeverSync;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfNeverSync);
            _tmpNeverSync = _tmp_1 != 0;
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final boolean _tmpOtherVoices;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfOtherVoices);
            _tmpOtherVoices = _tmp_2 != 0;
            final Double _tmpLat;
            if (_cursor.isNull(_cursorIndexOfLat)) {
              _tmpLat = null;
            } else {
              _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            }
            final Double _tmpLon;
            if (_cursor.isNull(_cursorIndexOfLon)) {
              _tmpLon = null;
            } else {
              _tmpLon = _cursor.getDouble(_cursorIndexOfLon);
            }
            final Float _tmpLocAccuracyM;
            if (_cursor.isNull(_cursorIndexOfLocAccuracyM)) {
              _tmpLocAccuracyM = null;
            } else {
              _tmpLocAccuracyM = _cursor.getFloat(_cursorIndexOfLocAccuracyM);
            }
            final Long _tmpLocAt;
            if (_cursor.isNull(_cursorIndexOfLocAt)) {
              _tmpLocAt = null;
            } else {
              _tmpLocAt = _cursor.getLong(_cursorIndexOfLocAt);
            }
            final String _tmpUuid;
            if (_cursor.isNull(_cursorIndexOfUuid)) {
              _tmpUuid = null;
            } else {
              _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            }
            final String _tmpAudioSha256;
            if (_cursor.isNull(_cursorIndexOfAudioSha256)) {
              _tmpAudioSha256 = null;
            } else {
              _tmpAudioSha256 = _cursor.getString(_cursorIndexOfAudioSha256);
            }
            _item = new Meeting(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpDurationSec,_tmpAudioPath,_tmpStatus,_tmpProgress,_tmpStage,_tmpError,_tmpLanguage,_tmpModel,_tmpRtf,_tmpTranscribedAt,_tmpAudioDeleteAt,_tmpAudioDeleted,_tmpNeverSync,_tmpKind,_tmpOtherVoices,_tmpLat,_tmpLon,_tmpLocAccuracyM,_tmpLocAt,_tmpUuid,_tmpAudioSha256);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object orphaned(final Continuation<? super List<Meeting>> $completion) {
    final String _sql = "SELECT * FROM Meeting WHERE status='recording'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Meeting>>() {
      @Override
      @NonNull
      public List<Meeting> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDurationSec = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSec");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfStage = CursorUtil.getColumnIndexOrThrow(_cursor, "stage");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfRtf = CursorUtil.getColumnIndexOrThrow(_cursor, "rtf");
          final int _cursorIndexOfTranscribedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "transcribedAt");
          final int _cursorIndexOfAudioDeleteAt = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleteAt");
          final int _cursorIndexOfAudioDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleted");
          final int _cursorIndexOfNeverSync = CursorUtil.getColumnIndexOrThrow(_cursor, "neverSync");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfOtherVoices = CursorUtil.getColumnIndexOrThrow(_cursor, "otherVoices");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLon = CursorUtil.getColumnIndexOrThrow(_cursor, "lon");
          final int _cursorIndexOfLocAccuracyM = CursorUtil.getColumnIndexOrThrow(_cursor, "locAccuracyM");
          final int _cursorIndexOfLocAt = CursorUtil.getColumnIndexOrThrow(_cursor, "locAt");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfAudioSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "audioSha256");
          final List<Meeting> _result = new ArrayList<Meeting>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Meeting _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final float _tmpDurationSec;
            _tmpDurationSec = _cursor.getFloat(_cursorIndexOfDurationSec);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final float _tmpProgress;
            _tmpProgress = _cursor.getFloat(_cursorIndexOfProgress);
            final String _tmpStage;
            if (_cursor.isNull(_cursorIndexOfStage)) {
              _tmpStage = null;
            } else {
              _tmpStage = _cursor.getString(_cursorIndexOfStage);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpModel;
            if (_cursor.isNull(_cursorIndexOfModel)) {
              _tmpModel = null;
            } else {
              _tmpModel = _cursor.getString(_cursorIndexOfModel);
            }
            final Float _tmpRtf;
            if (_cursor.isNull(_cursorIndexOfRtf)) {
              _tmpRtf = null;
            } else {
              _tmpRtf = _cursor.getFloat(_cursorIndexOfRtf);
            }
            final Long _tmpTranscribedAt;
            if (_cursor.isNull(_cursorIndexOfTranscribedAt)) {
              _tmpTranscribedAt = null;
            } else {
              _tmpTranscribedAt = _cursor.getLong(_cursorIndexOfTranscribedAt);
            }
            final Long _tmpAudioDeleteAt;
            if (_cursor.isNull(_cursorIndexOfAudioDeleteAt)) {
              _tmpAudioDeleteAt = null;
            } else {
              _tmpAudioDeleteAt = _cursor.getLong(_cursorIndexOfAudioDeleteAt);
            }
            final boolean _tmpAudioDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAudioDeleted);
            _tmpAudioDeleted = _tmp != 0;
            final boolean _tmpNeverSync;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfNeverSync);
            _tmpNeverSync = _tmp_1 != 0;
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final boolean _tmpOtherVoices;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfOtherVoices);
            _tmpOtherVoices = _tmp_2 != 0;
            final Double _tmpLat;
            if (_cursor.isNull(_cursorIndexOfLat)) {
              _tmpLat = null;
            } else {
              _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            }
            final Double _tmpLon;
            if (_cursor.isNull(_cursorIndexOfLon)) {
              _tmpLon = null;
            } else {
              _tmpLon = _cursor.getDouble(_cursorIndexOfLon);
            }
            final Float _tmpLocAccuracyM;
            if (_cursor.isNull(_cursorIndexOfLocAccuracyM)) {
              _tmpLocAccuracyM = null;
            } else {
              _tmpLocAccuracyM = _cursor.getFloat(_cursorIndexOfLocAccuracyM);
            }
            final Long _tmpLocAt;
            if (_cursor.isNull(_cursorIndexOfLocAt)) {
              _tmpLocAt = null;
            } else {
              _tmpLocAt = _cursor.getLong(_cursorIndexOfLocAt);
            }
            final String _tmpUuid;
            if (_cursor.isNull(_cursorIndexOfUuid)) {
              _tmpUuid = null;
            } else {
              _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            }
            final String _tmpAudioSha256;
            if (_cursor.isNull(_cursorIndexOfAudioSha256)) {
              _tmpAudioSha256 = null;
            } else {
              _tmpAudioSha256 = _cursor.getString(_cursorIndexOfAudioSha256);
            }
            _item = new Meeting(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpDurationSec,_tmpAudioPath,_tmpStatus,_tmpProgress,_tmpStage,_tmpError,_tmpLanguage,_tmpModel,_tmpRtf,_tmpTranscribedAt,_tmpAudioDeleteAt,_tmpAudioDeleted,_tmpNeverSync,_tmpKind,_tmpOtherVoices,_tmpLat,_tmpLon,_tmpLocAccuracyM,_tmpLocAt,_tmpUuid,_tmpAudioSha256);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object expiredAudio(final long now,
      final Continuation<? super List<Meeting>> $completion) {
    final String _sql = "SELECT * FROM Meeting WHERE audioDeleted=0 AND audioDeleteAt IS NOT NULL AND audioDeleteAt < ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, now);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Meeting>>() {
      @Override
      @NonNull
      public List<Meeting> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDurationSec = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSec");
          final int _cursorIndexOfAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioPath");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfStage = CursorUtil.getColumnIndexOrThrow(_cursor, "stage");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfRtf = CursorUtil.getColumnIndexOrThrow(_cursor, "rtf");
          final int _cursorIndexOfTranscribedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "transcribedAt");
          final int _cursorIndexOfAudioDeleteAt = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleteAt");
          final int _cursorIndexOfAudioDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "audioDeleted");
          final int _cursorIndexOfNeverSync = CursorUtil.getColumnIndexOrThrow(_cursor, "neverSync");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfOtherVoices = CursorUtil.getColumnIndexOrThrow(_cursor, "otherVoices");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLon = CursorUtil.getColumnIndexOrThrow(_cursor, "lon");
          final int _cursorIndexOfLocAccuracyM = CursorUtil.getColumnIndexOrThrow(_cursor, "locAccuracyM");
          final int _cursorIndexOfLocAt = CursorUtil.getColumnIndexOrThrow(_cursor, "locAt");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfAudioSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "audioSha256");
          final List<Meeting> _result = new ArrayList<Meeting>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Meeting _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final float _tmpDurationSec;
            _tmpDurationSec = _cursor.getFloat(_cursorIndexOfDurationSec);
            final String _tmpAudioPath;
            if (_cursor.isNull(_cursorIndexOfAudioPath)) {
              _tmpAudioPath = null;
            } else {
              _tmpAudioPath = _cursor.getString(_cursorIndexOfAudioPath);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final float _tmpProgress;
            _tmpProgress = _cursor.getFloat(_cursorIndexOfProgress);
            final String _tmpStage;
            if (_cursor.isNull(_cursorIndexOfStage)) {
              _tmpStage = null;
            } else {
              _tmpStage = _cursor.getString(_cursorIndexOfStage);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpModel;
            if (_cursor.isNull(_cursorIndexOfModel)) {
              _tmpModel = null;
            } else {
              _tmpModel = _cursor.getString(_cursorIndexOfModel);
            }
            final Float _tmpRtf;
            if (_cursor.isNull(_cursorIndexOfRtf)) {
              _tmpRtf = null;
            } else {
              _tmpRtf = _cursor.getFloat(_cursorIndexOfRtf);
            }
            final Long _tmpTranscribedAt;
            if (_cursor.isNull(_cursorIndexOfTranscribedAt)) {
              _tmpTranscribedAt = null;
            } else {
              _tmpTranscribedAt = _cursor.getLong(_cursorIndexOfTranscribedAt);
            }
            final Long _tmpAudioDeleteAt;
            if (_cursor.isNull(_cursorIndexOfAudioDeleteAt)) {
              _tmpAudioDeleteAt = null;
            } else {
              _tmpAudioDeleteAt = _cursor.getLong(_cursorIndexOfAudioDeleteAt);
            }
            final boolean _tmpAudioDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAudioDeleted);
            _tmpAudioDeleted = _tmp != 0;
            final boolean _tmpNeverSync;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfNeverSync);
            _tmpNeverSync = _tmp_1 != 0;
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final boolean _tmpOtherVoices;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfOtherVoices);
            _tmpOtherVoices = _tmp_2 != 0;
            final Double _tmpLat;
            if (_cursor.isNull(_cursorIndexOfLat)) {
              _tmpLat = null;
            } else {
              _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            }
            final Double _tmpLon;
            if (_cursor.isNull(_cursorIndexOfLon)) {
              _tmpLon = null;
            } else {
              _tmpLon = _cursor.getDouble(_cursorIndexOfLon);
            }
            final Float _tmpLocAccuracyM;
            if (_cursor.isNull(_cursorIndexOfLocAccuracyM)) {
              _tmpLocAccuracyM = null;
            } else {
              _tmpLocAccuracyM = _cursor.getFloat(_cursorIndexOfLocAccuracyM);
            }
            final Long _tmpLocAt;
            if (_cursor.isNull(_cursorIndexOfLocAt)) {
              _tmpLocAt = null;
            } else {
              _tmpLocAt = _cursor.getLong(_cursorIndexOfLocAt);
            }
            final String _tmpUuid;
            if (_cursor.isNull(_cursorIndexOfUuid)) {
              _tmpUuid = null;
            } else {
              _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            }
            final String _tmpAudioSha256;
            if (_cursor.isNull(_cursorIndexOfAudioSha256)) {
              _tmpAudioSha256 = null;
            } else {
              _tmpAudioSha256 = _cursor.getString(_cursorIndexOfAudioSha256);
            }
            _item = new Meeting(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpDurationSec,_tmpAudioPath,_tmpStatus,_tmpProgress,_tmpStage,_tmpError,_tmpLanguage,_tmpModel,_tmpRtf,_tmpTranscribedAt,_tmpAudioDeleteAt,_tmpAudioDeleted,_tmpNeverSync,_tmpKind,_tmpOtherVoices,_tmpLat,_tmpLon,_tmpLocAccuracyM,_tmpLocAt,_tmpUuid,_tmpAudioSha256);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object provenance(final long id,
      final Continuation<? super List<ProvenanceRecord>> $completion) {
    final String _sql = "SELECT * FROM ProvenanceRecord WHERE meetingId=? ORDER BY id";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ProvenanceRecord>>() {
      @Override
      @NonNull
      public List<ProvenanceRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMeetingId = CursorUtil.getColumnIndexOrThrow(_cursor, "meetingId");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfPayloadSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "payloadSha256");
          final int _cursorIndexOfPrevSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "prevSha256");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfKeyAlias = CursorUtil.getColumnIndexOrThrow(_cursor, "keyAlias");
          final int _cursorIndexOfCertChain = CursorUtil.getColumnIndexOrThrow(_cursor, "certChain");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<ProvenanceRecord> _result = new ArrayList<ProvenanceRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ProvenanceRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMeetingId;
            _tmpMeetingId = _cursor.getLong(_cursorIndexOfMeetingId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpPayload;
            _tmpPayload = _cursor.getString(_cursorIndexOfPayload);
            final String _tmpPayloadSha256;
            _tmpPayloadSha256 = _cursor.getString(_cursorIndexOfPayloadSha256);
            final String _tmpPrevSha256;
            if (_cursor.isNull(_cursorIndexOfPrevSha256)) {
              _tmpPrevSha256 = null;
            } else {
              _tmpPrevSha256 = _cursor.getString(_cursorIndexOfPrevSha256);
            }
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final String _tmpKeyAlias;
            _tmpKeyAlias = _cursor.getString(_cursorIndexOfKeyAlias);
            final String _tmpCertChain;
            _tmpCertChain = _cursor.getString(_cursorIndexOfCertChain);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new ProvenanceRecord(_tmpId,_tmpMeetingId,_tmpKind,_tmpPayload,_tmpPayloadSha256,_tmpPrevSha256,_tmpSignature,_tmpKeyAlias,_tmpCertChain,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ProvenanceRecord>> provenanceFlow(final long id) {
    final String _sql = "SELECT * FROM ProvenanceRecord WHERE meetingId=? ORDER BY id";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"ProvenanceRecord"}, new Callable<List<ProvenanceRecord>>() {
      @Override
      @NonNull
      public List<ProvenanceRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMeetingId = CursorUtil.getColumnIndexOrThrow(_cursor, "meetingId");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfPayloadSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "payloadSha256");
          final int _cursorIndexOfPrevSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "prevSha256");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfKeyAlias = CursorUtil.getColumnIndexOrThrow(_cursor, "keyAlias");
          final int _cursorIndexOfCertChain = CursorUtil.getColumnIndexOrThrow(_cursor, "certChain");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<ProvenanceRecord> _result = new ArrayList<ProvenanceRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ProvenanceRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMeetingId;
            _tmpMeetingId = _cursor.getLong(_cursorIndexOfMeetingId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpPayload;
            _tmpPayload = _cursor.getString(_cursorIndexOfPayload);
            final String _tmpPayloadSha256;
            _tmpPayloadSha256 = _cursor.getString(_cursorIndexOfPayloadSha256);
            final String _tmpPrevSha256;
            if (_cursor.isNull(_cursorIndexOfPrevSha256)) {
              _tmpPrevSha256 = null;
            } else {
              _tmpPrevSha256 = _cursor.getString(_cursorIndexOfPrevSha256);
            }
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final String _tmpKeyAlias;
            _tmpKeyAlias = _cursor.getString(_cursorIndexOfKeyAlias);
            final String _tmpCertChain;
            _tmpCertChain = _cursor.getString(_cursorIndexOfCertChain);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new ProvenanceRecord(_tmpId,_tmpMeetingId,_tmpKind,_tmpPayload,_tmpPayloadSha256,_tmpPrevSha256,_tmpSignature,_tmpKeyAlias,_tmpCertChain,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object consent(final long id, final Continuation<? super List<ConsentLog>> $completion) {
    final String _sql = "SELECT * FROM ConsentLog WHERE meetingId=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ConsentLog>>() {
      @Override
      @NonNull
      public List<ConsentLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMeetingId = CursorUtil.getColumnIndexOrThrow(_cursor, "meetingId");
          final int _cursorIndexOfParticipants = CursorUtil.getColumnIndexOrThrow(_cursor, "participants");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final int _cursorIndexOfConfirmedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "confirmedAt");
          final List<ConsentLog> _result = new ArrayList<ConsentLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ConsentLog _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMeetingId;
            _tmpMeetingId = _cursor.getLong(_cursorIndexOfMeetingId);
            final String _tmpParticipants;
            _tmpParticipants = _cursor.getString(_cursorIndexOfParticipants);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            final long _tmpConfirmedAt;
            _tmpConfirmedAt = _cursor.getLong(_cursorIndexOfConfirmedAt);
            _item = new ConsentLog(_tmpId,_tmpMeetingId,_tmpParticipants,_tmpMethod,_tmpConfirmedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Segment>> segments(final long id) {
    final String _sql = "SELECT * FROM Segment WHERE meetingId=? ORDER BY startSec";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"Segment"}, new Callable<List<Segment>>() {
      @Override
      @NonNull
      public List<Segment> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMeetingId = CursorUtil.getColumnIndexOrThrow(_cursor, "meetingId");
          final int _cursorIndexOfStartSec = CursorUtil.getColumnIndexOrThrow(_cursor, "startSec");
          final int _cursorIndexOfEndSec = CursorUtil.getColumnIndexOrThrow(_cursor, "endSec");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfSpeakerIdx = CursorUtil.getColumnIndexOrThrow(_cursor, "speakerIdx");
          final List<Segment> _result = new ArrayList<Segment>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Segment _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMeetingId;
            _tmpMeetingId = _cursor.getLong(_cursorIndexOfMeetingId);
            final float _tmpStartSec;
            _tmpStartSec = _cursor.getFloat(_cursorIndexOfStartSec);
            final float _tmpEndSec;
            _tmpEndSec = _cursor.getFloat(_cursorIndexOfEndSec);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final int _tmpSpeakerIdx;
            _tmpSpeakerIdx = _cursor.getInt(_cursorIndexOfSpeakerIdx);
            _item = new Segment(_tmpId,_tmpMeetingId,_tmpStartSec,_tmpEndSec,_tmpText,_tmpSpeakerIdx);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object segmentsNow(final long id, final Continuation<? super List<Segment>> $completion) {
    final String _sql = "SELECT * FROM Segment WHERE meetingId=? ORDER BY startSec";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Segment>>() {
      @Override
      @NonNull
      public List<Segment> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMeetingId = CursorUtil.getColumnIndexOrThrow(_cursor, "meetingId");
          final int _cursorIndexOfStartSec = CursorUtil.getColumnIndexOrThrow(_cursor, "startSec");
          final int _cursorIndexOfEndSec = CursorUtil.getColumnIndexOrThrow(_cursor, "endSec");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfSpeakerIdx = CursorUtil.getColumnIndexOrThrow(_cursor, "speakerIdx");
          final List<Segment> _result = new ArrayList<Segment>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Segment _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMeetingId;
            _tmpMeetingId = _cursor.getLong(_cursorIndexOfMeetingId);
            final float _tmpStartSec;
            _tmpStartSec = _cursor.getFloat(_cursorIndexOfStartSec);
            final float _tmpEndSec;
            _tmpEndSec = _cursor.getFloat(_cursorIndexOfEndSec);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final int _tmpSpeakerIdx;
            _tmpSpeakerIdx = _cursor.getInt(_cursorIndexOfSpeakerIdx);
            _item = new Segment(_tmpId,_tmpMeetingId,_tmpStartSec,_tmpEndSec,_tmpText,_tmpSpeakerIdx);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object segmentMeeting(final long id, final Continuation<? super Long> $completion) {
    final String _sql = "SELECT meetingId FROM Segment WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @Nullable
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getLong(0);
            }
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object searchFts(final String q, final Continuation<? super List<SearchHit>> $completion) {
    final String _sql = "SELECT s.id AS segmentId, s.meetingId, m.title, s.startSec, s.text, m.createdAt\n"
            + "              FROM SegmentFts f JOIN Segment s ON s.rowid = f.rowid JOIN Meeting m ON m.id = s.meetingId\n"
            + "              WHERE SegmentFts MATCH ? ORDER BY m.createdAt DESC, s.startSec LIMIT 200";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, q);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SearchHit>>() {
      @Override
      @NonNull
      public List<SearchHit> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSegmentId = 0;
          final int _cursorIndexOfMeetingId = 1;
          final int _cursorIndexOfTitle = 2;
          final int _cursorIndexOfStartSec = 3;
          final int _cursorIndexOfText = 4;
          final int _cursorIndexOfCreatedAt = 5;
          final List<SearchHit> _result = new ArrayList<SearchHit>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SearchHit _item;
            final long _tmpSegmentId;
            _tmpSegmentId = _cursor.getLong(_cursorIndexOfSegmentId);
            final long _tmpMeetingId;
            _tmpMeetingId = _cursor.getLong(_cursorIndexOfMeetingId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final float _tmpStartSec;
            _tmpStartSec = _cursor.getFloat(_cursorIndexOfStartSec);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new SearchHit(_tmpSegmentId,_tmpMeetingId,_tmpTitle,_tmpStartSec,_tmpText,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object searchLike(final String q,
      final Continuation<? super List<SearchHit>> $completion) {
    final String _sql = "SELECT s.id AS segmentId, s.meetingId, m.title, s.startSec, s.text, m.createdAt\n"
            + "              FROM Segment s JOIN Meeting m ON m.id = s.meetingId\n"
            + "              WHERE s.text LIKE '%' || ? || '%' ORDER BY m.createdAt DESC, s.startSec LIMIT 200";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, q);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SearchHit>>() {
      @Override
      @NonNull
      public List<SearchHit> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSegmentId = 0;
          final int _cursorIndexOfMeetingId = 1;
          final int _cursorIndexOfTitle = 2;
          final int _cursorIndexOfStartSec = 3;
          final int _cursorIndexOfText = 4;
          final int _cursorIndexOfCreatedAt = 5;
          final List<SearchHit> _result = new ArrayList<SearchHit>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SearchHit _item;
            final long _tmpSegmentId;
            _tmpSegmentId = _cursor.getLong(_cursorIndexOfSegmentId);
            final long _tmpMeetingId;
            _tmpMeetingId = _cursor.getLong(_cursorIndexOfMeetingId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final float _tmpStartSec;
            _tmpStartSec = _cursor.getFloat(_cursorIndexOfStartSec);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new SearchHit(_tmpSegmentId,_tmpMeetingId,_tmpTitle,_tmpStartSec,_tmpText,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<MeetingSpeaker>> speakers(final long id) {
    final String _sql = "SELECT * FROM MeetingSpeaker WHERE meetingId=? ORDER BY idx";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"MeetingSpeaker"}, new Callable<List<MeetingSpeaker>>() {
      @Override
      @NonNull
      public List<MeetingSpeaker> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMeetingId = CursorUtil.getColumnIndexOrThrow(_cursor, "meetingId");
          final int _cursorIndexOfIdx = CursorUtil.getColumnIndexOrThrow(_cursor, "idx");
          final int _cursorIndexOfPersonId = CursorUtil.getColumnIndexOrThrow(_cursor, "personId");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfSpeechSec = CursorUtil.getColumnIndexOrThrow(_cursor, "speechSec");
          final int _cursorIndexOfEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding");
          final List<MeetingSpeaker> _result = new ArrayList<MeetingSpeaker>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MeetingSpeaker _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMeetingId;
            _tmpMeetingId = _cursor.getLong(_cursorIndexOfMeetingId);
            final int _tmpIdx;
            _tmpIdx = _cursor.getInt(_cursorIndexOfIdx);
            final Long _tmpPersonId;
            if (_cursor.isNull(_cursorIndexOfPersonId)) {
              _tmpPersonId = null;
            } else {
              _tmpPersonId = _cursor.getLong(_cursorIndexOfPersonId);
            }
            final float _tmpScore;
            _tmpScore = _cursor.getFloat(_cursorIndexOfScore);
            final float _tmpSpeechSec;
            _tmpSpeechSec = _cursor.getFloat(_cursorIndexOfSpeechSec);
            final byte[] _tmpEmbedding;
            if (_cursor.isNull(_cursorIndexOfEmbedding)) {
              _tmpEmbedding = null;
            } else {
              _tmpEmbedding = _cursor.getBlob(_cursorIndexOfEmbedding);
            }
            _item = new MeetingSpeaker(_tmpId,_tmpMeetingId,_tmpIdx,_tmpPersonId,_tmpScore,_tmpSpeechSec,_tmpEmbedding);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object speakersNow(final long id,
      final Continuation<? super List<MeetingSpeaker>> $completion) {
    final String _sql = "SELECT * FROM MeetingSpeaker WHERE meetingId=? ORDER BY idx";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MeetingSpeaker>>() {
      @Override
      @NonNull
      public List<MeetingSpeaker> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMeetingId = CursorUtil.getColumnIndexOrThrow(_cursor, "meetingId");
          final int _cursorIndexOfIdx = CursorUtil.getColumnIndexOrThrow(_cursor, "idx");
          final int _cursorIndexOfPersonId = CursorUtil.getColumnIndexOrThrow(_cursor, "personId");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfSpeechSec = CursorUtil.getColumnIndexOrThrow(_cursor, "speechSec");
          final int _cursorIndexOfEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding");
          final List<MeetingSpeaker> _result = new ArrayList<MeetingSpeaker>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MeetingSpeaker _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMeetingId;
            _tmpMeetingId = _cursor.getLong(_cursorIndexOfMeetingId);
            final int _tmpIdx;
            _tmpIdx = _cursor.getInt(_cursorIndexOfIdx);
            final Long _tmpPersonId;
            if (_cursor.isNull(_cursorIndexOfPersonId)) {
              _tmpPersonId = null;
            } else {
              _tmpPersonId = _cursor.getLong(_cursorIndexOfPersonId);
            }
            final float _tmpScore;
            _tmpScore = _cursor.getFloat(_cursorIndexOfScore);
            final float _tmpSpeechSec;
            _tmpSpeechSec = _cursor.getFloat(_cursorIndexOfSpeechSec);
            final byte[] _tmpEmbedding;
            if (_cursor.isNull(_cursorIndexOfEmbedding)) {
              _tmpEmbedding = null;
            } else {
              _tmpEmbedding = _cursor.getBlob(_cursorIndexOfEmbedding);
            }
            _item = new MeetingSpeaker(_tmpId,_tmpMeetingId,_tmpIdx,_tmpPersonId,_tmpScore,_tmpSpeechSec,_tmpEmbedding);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Person>> people() {
    final String _sql = "SELECT * FROM Person ORDER BY isOwner DESC, name";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"Person"}, new Callable<List<Person>>() {
      @Override
      @NonNull
      public List<Person> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsOwner = CursorUtil.getColumnIndexOrThrow(_cursor, "isOwner");
          final int _cursorIndexOfEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding");
          final int _cursorIndexOfSamples = CursorUtil.getColumnIndexOrThrow(_cursor, "samples");
          final int _cursorIndexOfConsentAt = CursorUtil.getColumnIndexOrThrow(_cursor, "consentAt");
          final List<Person> _result = new ArrayList<Person>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Person _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsOwner;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsOwner);
            _tmpIsOwner = _tmp != 0;
            final byte[] _tmpEmbedding;
            if (_cursor.isNull(_cursorIndexOfEmbedding)) {
              _tmpEmbedding = null;
            } else {
              _tmpEmbedding = _cursor.getBlob(_cursorIndexOfEmbedding);
            }
            final int _tmpSamples;
            _tmpSamples = _cursor.getInt(_cursorIndexOfSamples);
            final long _tmpConsentAt;
            _tmpConsentAt = _cursor.getLong(_cursorIndexOfConsentAt);
            _item = new Person(_tmpId,_tmpName,_tmpIsOwner,_tmpEmbedding,_tmpSamples,_tmpConsentAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object peopleNow(final Continuation<? super List<Person>> $completion) {
    final String _sql = "SELECT * FROM Person";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Person>>() {
      @Override
      @NonNull
      public List<Person> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsOwner = CursorUtil.getColumnIndexOrThrow(_cursor, "isOwner");
          final int _cursorIndexOfEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding");
          final int _cursorIndexOfSamples = CursorUtil.getColumnIndexOrThrow(_cursor, "samples");
          final int _cursorIndexOfConsentAt = CursorUtil.getColumnIndexOrThrow(_cursor, "consentAt");
          final List<Person> _result = new ArrayList<Person>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Person _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsOwner;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsOwner);
            _tmpIsOwner = _tmp != 0;
            final byte[] _tmpEmbedding;
            if (_cursor.isNull(_cursorIndexOfEmbedding)) {
              _tmpEmbedding = null;
            } else {
              _tmpEmbedding = _cursor.getBlob(_cursorIndexOfEmbedding);
            }
            final int _tmpSamples;
            _tmpSamples = _cursor.getInt(_cursorIndexOfSamples);
            final long _tmpConsentAt;
            _tmpConsentAt = _cursor.getLong(_cursorIndexOfConsentAt);
            _item = new Person(_tmpId,_tmpName,_tmpIsOwner,_tmpEmbedding,_tmpSamples,_tmpConsentAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object person(final long id, final Continuation<? super Person> $completion) {
    final String _sql = "SELECT * FROM Person WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Person>() {
      @Override
      @Nullable
      public Person call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsOwner = CursorUtil.getColumnIndexOrThrow(_cursor, "isOwner");
          final int _cursorIndexOfEmbedding = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding");
          final int _cursorIndexOfSamples = CursorUtil.getColumnIndexOrThrow(_cursor, "samples");
          final int _cursorIndexOfConsentAt = CursorUtil.getColumnIndexOrThrow(_cursor, "consentAt");
          final Person _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsOwner;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsOwner);
            _tmpIsOwner = _tmp != 0;
            final byte[] _tmpEmbedding;
            if (_cursor.isNull(_cursorIndexOfEmbedding)) {
              _tmpEmbedding = null;
            } else {
              _tmpEmbedding = _cursor.getBlob(_cursorIndexOfEmbedding);
            }
            final int _tmpSamples;
            _tmpSamples = _cursor.getInt(_cursorIndexOfSamples);
            final long _tmpConsentAt;
            _tmpConsentAt = _cursor.getLong(_cursorIndexOfConsentAt);
            _result = new Person(_tmpId,_tmpName,_tmpIsOwner,_tmpEmbedding,_tmpSamples,_tmpConsentAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object meetingsWith(final long pid, final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(DISTINCT meetingId) FROM MeetingSpeaker WHERE personId=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, pid);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
