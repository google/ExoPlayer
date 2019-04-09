/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.offline;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link DownloadIndex} which uses SQLite to persist {@link DownloadState}s.
 *
 * <p class="caution">Database access may take a long time, do not call methods of this class from
 * the application main thread.
 */
public final class DefaultDownloadIndex implements DownloadIndex {

  private static final String TABLE_NAME = DatabaseProvider.TABLE_PREFIX + "Downloads";

  // TODO: Support multiple instances. Probably using the underlying cache UID.
  @VisibleForTesting /* package */ static final String INSTANCE_UID = "singleton";
  @VisibleForTesting /* package */ static final int TABLE_VERSION = 1;

  private static final String COLUMN_ID = "id";
  private static final String COLUMN_TYPE = "title";
  private static final String COLUMN_URI = "subtitle";
  private static final String COLUMN_STREAM_KEYS = "stream_keys";
  private static final String COLUMN_CUSTOM_CACHE_KEY = "cache_key";
  private static final String COLUMN_DATA = "custom_metadata";
  private static final String COLUMN_STATE = "state";
  private static final String COLUMN_DOWNLOAD_PERCENTAGE = "download_percentage";
  private static final String COLUMN_DOWNLOADED_BYTES = "downloaded_bytes";
  private static final String COLUMN_TOTAL_BYTES = "total_bytes";
  private static final String COLUMN_FAILURE_REASON = "failure_reason";
  private static final String COLUMN_NOT_MET_REQUIREMENTS = "not_met_requirements";
  private static final String COLUMN_MANUAL_STOP_REASON = "manual_stop_reason";
  private static final String COLUMN_START_TIME_MS = "start_time_ms";
  private static final String COLUMN_UPDATE_TIME_MS = "update_time_ms";

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  private static final String COLUMN_STOP_FLAGS = "stop_flags";

  private static final int COLUMN_INDEX_ID = 0;
  private static final int COLUMN_INDEX_TYPE = 1;
  private static final int COLUMN_INDEX_URI = 2;
  private static final int COLUMN_INDEX_STREAM_KEYS = 3;
  private static final int COLUMN_INDEX_CUSTOM_CACHE_KEY = 4;
  private static final int COLUMN_INDEX_DATA = 5;
  private static final int COLUMN_INDEX_STATE = 6;
  private static final int COLUMN_INDEX_DOWNLOAD_PERCENTAGE = 7;
  private static final int COLUMN_INDEX_DOWNLOADED_BYTES = 8;
  private static final int COLUMN_INDEX_TOTAL_BYTES = 9;
  private static final int COLUMN_INDEX_FAILURE_REASON = 10;
  private static final int COLUMN_INDEX_NOT_MET_REQUIREMENTS = 11;
  private static final int COLUMN_INDEX_MANUAL_STOP_REASON = 12;
  private static final int COLUMN_INDEX_START_TIME_MS = 13;
  private static final int COLUMN_INDEX_UPDATE_TIME_MS = 14;

  private static final String WHERE_ID_EQUALS = COLUMN_ID + " = ?";
  private static final String WHERE_STATE_TERMINAL =
      getStateQuery(DownloadState.STATE_COMPLETED, DownloadState.STATE_FAILED);

  private static final String[] COLUMNS =
      new String[] {
        COLUMN_ID,
        COLUMN_TYPE,
        COLUMN_URI,
        COLUMN_STREAM_KEYS,
        COLUMN_CUSTOM_CACHE_KEY,
        COLUMN_DATA,
        COLUMN_STATE,
        COLUMN_DOWNLOAD_PERCENTAGE,
        COLUMN_DOWNLOADED_BYTES,
        COLUMN_TOTAL_BYTES,
        COLUMN_FAILURE_REASON,
        COLUMN_NOT_MET_REQUIREMENTS,
        COLUMN_MANUAL_STOP_REASON,
        COLUMN_START_TIME_MS,
        COLUMN_UPDATE_TIME_MS
      };

  private static final String SQL_DROP_TABLE_IF_EXISTS = "DROP TABLE IF EXISTS " + TABLE_NAME;
  private static final String SQL_CREATE_TABLE =
      "CREATE TABLE "
          + TABLE_NAME
          + " ("
          + COLUMN_ID
          + " TEXT PRIMARY KEY NOT NULL,"
          + COLUMN_TYPE
          + " TEXT NOT NULL,"
          + COLUMN_URI
          + " TEXT NOT NULL,"
          + COLUMN_CUSTOM_CACHE_KEY
          + " TEXT,"
          + COLUMN_STATE
          + " INTEGER NOT NULL,"
          + COLUMN_DOWNLOAD_PERCENTAGE
          + " REAL NOT NULL,"
          + COLUMN_DOWNLOADED_BYTES
          + " INTEGER NOT NULL,"
          + COLUMN_TOTAL_BYTES
          + " INTEGER NOT NULL,"
          + COLUMN_FAILURE_REASON
          + " INTEGER NOT NULL,"
          + COLUMN_STOP_FLAGS
          + " INTEGER NOT NULL,"
          + COLUMN_NOT_MET_REQUIREMENTS
          + " INTEGER NOT NULL,"
          + COLUMN_MANUAL_STOP_REASON
          + " INTEGER NOT NULL,"
          + COLUMN_START_TIME_MS
          + " INTEGER NOT NULL,"
          + COLUMN_UPDATE_TIME_MS
          + " INTEGER NOT NULL,"
          + COLUMN_STREAM_KEYS
          + " TEXT NOT NULL,"
          + COLUMN_DATA
          + " BLOB NOT NULL)";

  private static final String TRUE = "1";

  private final DatabaseProvider databaseProvider;

  private boolean initialized;

  /**
   * Creates a DefaultDownloadIndex which stores the {@link DownloadState}s on a SQLite database
   * provided by {@code databaseProvider}.
   *
   * @param databaseProvider A DatabaseProvider which provides the database which will be used to
   *     store DownloadStatus table.
   */
  public DefaultDownloadIndex(DatabaseProvider databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  @Nullable
  public DownloadState getDownloadState(String id) throws DatabaseIOException {
    ensureInitialized();
    try (Cursor cursor = getCursor(WHERE_ID_EQUALS, new String[] {id})) {
      if (cursor.getCount() == 0) {
        return null;
      }
      cursor.moveToNext();
      return getDownloadStateForCurrentRow(cursor);
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public DownloadStateCursor getDownloadStates(@DownloadState.State int... states)
      throws DatabaseIOException {
    ensureInitialized();
    Cursor cursor = getCursor(getStateQuery(states), /* selectionArgs= */ null);
    return new DownloadStateCursorImpl(cursor);
  }

  /**
   * Adds or replaces a {@link DownloadState}.
   *
   * @param downloadState The {@link DownloadState} to be added.
   * @throws DatabaseIOException If an error occurs setting the state.
   */
  public void putDownloadState(DownloadState downloadState) throws DatabaseIOException {
    ensureInitialized();
    ContentValues values = new ContentValues();
    values.put(COLUMN_ID, downloadState.action.id);
    values.put(COLUMN_TYPE, downloadState.action.type);
    values.put(COLUMN_URI, downloadState.action.uri.toString());
    values.put(COLUMN_STREAM_KEYS, encodeStreamKeys(downloadState.action.streamKeys));
    values.put(COLUMN_CUSTOM_CACHE_KEY, downloadState.action.customCacheKey);
    values.put(COLUMN_DATA, downloadState.action.data);
    values.put(COLUMN_STATE, downloadState.state);
    values.put(COLUMN_DOWNLOAD_PERCENTAGE, downloadState.getDownloadPercentage());
    values.put(COLUMN_DOWNLOADED_BYTES, downloadState.getDownloadedBytes());
    values.put(COLUMN_TOTAL_BYTES, downloadState.getTotalBytes());
    values.put(COLUMN_FAILURE_REASON, downloadState.failureReason);
    values.put(COLUMN_STOP_FLAGS, 0);
    values.put(COLUMN_NOT_MET_REQUIREMENTS, downloadState.notMetRequirements);
    values.put(COLUMN_MANUAL_STOP_REASON, downloadState.manualStopReason);
    values.put(COLUMN_START_TIME_MS, downloadState.startTimeMs);
    values.put(COLUMN_UPDATE_TIME_MS, downloadState.updateTimeMs);
    try {
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.replaceOrThrow(TABLE_NAME, /* nullColumnHack= */ null, values);
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  /**
   * Removes the {@link DownloadState} with the given {@code id}.
   *
   * @param id ID of a {@link DownloadState}.
   * @throws DatabaseIOException If an error occurs removing the state.
   */
  public void removeDownloadState(String id) throws DatabaseIOException {
    ensureInitialized();
    try {
      databaseProvider.getWritableDatabase().delete(TABLE_NAME, WHERE_ID_EQUALS, new String[] {id});
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  /**
   * Sets the manual stop reason of the downloads in a terminal state ({@link
   * DownloadState#STATE_COMPLETED}, {@link DownloadState#STATE_FAILED}).
   *
   * @param manualStopReason The manual stop reason.
   * @throws DatabaseIOException If an error occurs updating the state.
   */
  public void setManualStopReason(int manualStopReason) throws DatabaseIOException {
    ensureInitialized();
    try {
      ContentValues values = new ContentValues();
      values.put(COLUMN_MANUAL_STOP_REASON, manualStopReason);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.update(TABLE_NAME, values, WHERE_STATE_TERMINAL, /* whereArgs= */ null);
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  /**
   * Sets the manual stop reason of the download with the given {@code id} in a terminal state
   * ({@link DownloadState#STATE_COMPLETED}, {@link DownloadState#STATE_FAILED}).
   *
   * <p>If there's no {@link DownloadState} with the given {@code id} or it isn't in a terminal
   * state, then nothing happens.
   *
   * @param id ID of a {@link DownloadState}.
   * @param manualStopReason The manual stop reason.
   * @throws DatabaseIOException If an error occurs updating the state.
   */
  public void setManualStopReason(String id, int manualStopReason) throws DatabaseIOException {
    ensureInitialized();
    try {
      ContentValues values = new ContentValues();
      values.put(COLUMN_MANUAL_STOP_REASON, manualStopReason);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.update(
          TABLE_NAME, values, WHERE_STATE_TERMINAL + " AND " + WHERE_ID_EQUALS, new String[] {id});
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  private void ensureInitialized() throws DatabaseIOException {
    if (initialized) {
      return;
    }
    try {
      SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
      int version =
          VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, INSTANCE_UID);
      if (version == VersionTable.VERSION_UNSET || version > TABLE_VERSION) {
        SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
          VersionTable.setVersion(
              writableDatabase, VersionTable.FEATURE_OFFLINE, INSTANCE_UID, TABLE_VERSION);
          writableDatabase.execSQL(SQL_DROP_TABLE_IF_EXISTS);
          writableDatabase.execSQL(SQL_CREATE_TABLE);
          writableDatabase.setTransactionSuccessful();
        } finally {
          writableDatabase.endTransaction();
        }
      } else if (version < TABLE_VERSION) {
        // There is no previous version currently.
        throw new IllegalStateException();
      }
      initialized = true;
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  private Cursor getCursor(String selection, @Nullable String[] selectionArgs)
      throws DatabaseIOException {
    try {
      String sortOrder = COLUMN_START_TIME_MS + " ASC";
      return databaseProvider
          .getReadableDatabase()
          .query(
              TABLE_NAME,
              COLUMNS,
              selection,
              selectionArgs,
              /* groupBy= */ null,
              /* having= */ null,
              sortOrder);
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  private static String getStateQuery(@DownloadState.State int... states) {
    if (states.length == 0) {
      return TRUE;
    }
    StringBuilder selectionBuilder = new StringBuilder();
    selectionBuilder.append(COLUMN_STATE).append(" IN (");
    for (int i = 0; i < states.length; i++) {
      if (i > 0) {
        selectionBuilder.append(',');
      }
      selectionBuilder.append(states[i]);
    }
    selectionBuilder.append(')');
    return selectionBuilder.toString();
  }

  private static DownloadState getDownloadStateForCurrentRow(Cursor cursor) {
    DownloadAction action =
        new DownloadAction(
            cursor.getString(COLUMN_INDEX_ID),
            cursor.getString(COLUMN_INDEX_TYPE),
            Uri.parse(cursor.getString(COLUMN_INDEX_URI)),
            decodeStreamKeys(cursor.getString(COLUMN_INDEX_STREAM_KEYS)),
            cursor.getString(COLUMN_INDEX_CUSTOM_CACHE_KEY),
            cursor.getBlob(COLUMN_INDEX_DATA));
    CachingCounters cachingCounters = new CachingCounters();
    cachingCounters.alreadyCachedBytes = cursor.getLong(COLUMN_INDEX_DOWNLOADED_BYTES);
    cachingCounters.contentLength = cursor.getLong(COLUMN_INDEX_TOTAL_BYTES);
    cachingCounters.percentage = cursor.getFloat(COLUMN_INDEX_DOWNLOAD_PERCENTAGE);
    return new DownloadState(
        action,
        cursor.getInt(COLUMN_INDEX_STATE),
        cursor.getInt(COLUMN_INDEX_FAILURE_REASON),
        cursor.getInt(COLUMN_INDEX_NOT_MET_REQUIREMENTS),
        cursor.getInt(COLUMN_INDEX_MANUAL_STOP_REASON),
        cursor.getLong(COLUMN_INDEX_START_TIME_MS),
        cursor.getLong(COLUMN_INDEX_UPDATE_TIME_MS),
        cachingCounters);
  }

  private static String encodeStreamKeys(List<StreamKey> streamKeys) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < streamKeys.size(); i++) {
      StreamKey streamKey = streamKeys.get(i);
      stringBuilder
          .append(streamKey.periodIndex)
          .append('.')
          .append(streamKey.groupIndex)
          .append('.')
          .append(streamKey.trackIndex)
          .append(',');
    }
    if (stringBuilder.length() > 0) {
      stringBuilder.setLength(stringBuilder.length() - 1);
    }
    return stringBuilder.toString();
  }

  private static List<StreamKey> decodeStreamKeys(String encodedStreamKeys) {
    ArrayList<StreamKey> streamKeys = new ArrayList<>();
    if (encodedStreamKeys.isEmpty()) {
      return streamKeys;
    }
    String[] streamKeysStrings = Util.split(encodedStreamKeys, ",");
    for (String streamKeysString : streamKeysStrings) {
      String[] indices = Util.split(streamKeysString, "\\.");
      Assertions.checkState(indices.length == 3);
      streamKeys.add(
          new StreamKey(
              Integer.parseInt(indices[0]),
              Integer.parseInt(indices[1]),
              Integer.parseInt(indices[2])));
    }
    return streamKeys;
  }

  private static final class DownloadStateCursorImpl implements DownloadStateCursor {

    private final Cursor cursor;

    private DownloadStateCursorImpl(Cursor cursor) {
      this.cursor = cursor;
    }

    @Override
    public DownloadState getDownloadState() {
      return getDownloadStateForCurrentRow(cursor);
    }

    @Override
    public int getCount() {
      return cursor.getCount();
    }

    @Override
    public int getPosition() {
      return cursor.getPosition();
    }

    @Override
    public boolean moveToPosition(int position) {
      return cursor.moveToPosition(position);
    }

    @Override
    public void close() {
      cursor.close();
    }

    @Override
    public boolean isClosed() {
      return cursor.isClosed();
    }
  }
}
