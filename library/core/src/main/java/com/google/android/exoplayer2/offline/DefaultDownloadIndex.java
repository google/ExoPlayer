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
import android.text.TextUtils;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.List;

/** A {@link DownloadIndex} that uses SQLite to persist {@link Download Downloads}. */
public final class DefaultDownloadIndex implements WritableDownloadIndex {

  private static final String TABLE_PREFIX = DatabaseProvider.TABLE_PREFIX + "Downloads";

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
  private static final String COLUMN_STOP_REASON = "manual_stop_reason";
  private static final String COLUMN_START_TIME_MS = "start_time_ms";
  private static final String COLUMN_UPDATE_TIME_MS = "update_time_ms";

  /** @deprecated No longer used. */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  private static final String COLUMN_STOP_FLAGS = "stop_flags";

  /** @deprecated No longer used. */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  private static final String COLUMN_NOT_MET_REQUIREMENTS = "not_met_requirements";

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
  private static final int COLUMN_INDEX_STOP_REASON = 11;
  private static final int COLUMN_INDEX_START_TIME_MS = 12;
  private static final int COLUMN_INDEX_UPDATE_TIME_MS = 13;

  private static final String WHERE_ID_EQUALS = COLUMN_ID + " = ?";
  private static final String WHERE_STATE_TERMINAL =
      getStateQuery(Download.STATE_COMPLETED, Download.STATE_FAILED);

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
        COLUMN_STOP_REASON,
        COLUMN_START_TIME_MS,
        COLUMN_UPDATE_TIME_MS
      };

  private static final String TABLE_SCHEMA =
      "("
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
          + COLUMN_STOP_REASON
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

  private final String name;
  private final String tableName;
  private final DatabaseProvider databaseProvider;

  private boolean initialized;

  /**
   * Creates an instance that stores the {@link Download Downloads} in an SQLite database provided
   * by a {@link DatabaseProvider}.
   *
   * <p>Equivalent to calling {@link #DefaultDownloadIndex(DatabaseProvider, String)} with {@code
   * name=""}.
   *
   * <p>Applications that only have one download index may use this constructor. Applications that
   * have multiple download indices should call {@link #DefaultDownloadIndex(DatabaseProvider,
   * String)} to specify a unique name for each index.
   *
   * @param databaseProvider Provides the SQLite database in which downloads are persisted.
   */
  public DefaultDownloadIndex(DatabaseProvider databaseProvider) {
    this(databaseProvider, "");
  }

  /**
   * Creates an instance that stores the {@link Download Downloads} in an SQLite database provided
   * by a {@link DatabaseProvider}.
   *
   * @param databaseProvider Provides the SQLite database in which downloads are persisted.
   * @param name The name of the index. This name is incorporated into the names of the SQLite
   *     tables in which downloads are persisted.
   */
  public DefaultDownloadIndex(DatabaseProvider databaseProvider, String name) {
    // TODO: Remove this backward compatibility hack for launch.
    this.name = TextUtils.isEmpty(name) ? "singleton" : name;
    this.databaseProvider = databaseProvider;
    tableName = TABLE_PREFIX + name;
  }

  @Override
  @Nullable
  public Download getDownload(String id) throws DatabaseIOException {
    ensureInitialized();
    try (Cursor cursor = getCursor(WHERE_ID_EQUALS, new String[] {id})) {
      if (cursor.getCount() == 0) {
        return null;
      }
      cursor.moveToNext();
      return getDownloadForCurrentRow(cursor);
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public DownloadCursor getDownloads(@Download.State int... states) throws DatabaseIOException {
    ensureInitialized();
    Cursor cursor = getCursor(getStateQuery(states), /* selectionArgs= */ null);
    return new DownloadCursorImpl(cursor);
  }

  @Override
  public void putDownload(Download download) throws DatabaseIOException {
    ensureInitialized();
    ContentValues values = new ContentValues();
    values.put(COLUMN_ID, download.request.id);
    values.put(COLUMN_TYPE, download.request.type);
    values.put(COLUMN_URI, download.request.uri.toString());
    values.put(COLUMN_STREAM_KEYS, encodeStreamKeys(download.request.streamKeys));
    values.put(COLUMN_CUSTOM_CACHE_KEY, download.request.customCacheKey);
    values.put(COLUMN_DATA, download.request.data);
    values.put(COLUMN_STATE, download.state);
    values.put(COLUMN_DOWNLOAD_PERCENTAGE, download.getDownloadPercentage());
    values.put(COLUMN_DOWNLOADED_BYTES, download.getDownloadedBytes());
    values.put(COLUMN_TOTAL_BYTES, download.getTotalBytes());
    values.put(COLUMN_FAILURE_REASON, download.failureReason);
    values.put(COLUMN_STOP_FLAGS, 0);
    values.put(COLUMN_NOT_MET_REQUIREMENTS, 0);
    values.put(COLUMN_STOP_REASON, download.stopReason);
    values.put(COLUMN_START_TIME_MS, download.startTimeMs);
    values.put(COLUMN_UPDATE_TIME_MS, download.updateTimeMs);
    try {
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.replaceOrThrow(tableName, /* nullColumnHack= */ null, values);
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public void removeDownload(String id) throws DatabaseIOException {
    ensureInitialized();
    try {
      databaseProvider.getWritableDatabase().delete(tableName, WHERE_ID_EQUALS, new String[] {id});
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public void setStopReason(int stopReason) throws DatabaseIOException {
    ensureInitialized();
    try {
      ContentValues values = new ContentValues();
      values.put(COLUMN_STOP_REASON, stopReason);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.update(tableName, values, WHERE_STATE_TERMINAL, /* whereArgs= */ null);
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public void setStopReason(String id, int stopReason) throws DatabaseIOException {
    ensureInitialized();
    try {
      ContentValues values = new ContentValues();
      values.put(COLUMN_STOP_REASON, stopReason);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.update(
          tableName, values, WHERE_STATE_TERMINAL + " AND " + WHERE_ID_EQUALS, new String[] {id});
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
      int version = VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, name);
      if (version == VersionTable.VERSION_UNSET || version > TABLE_VERSION) {
        SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
          VersionTable.setVersion(
              writableDatabase, VersionTable.FEATURE_OFFLINE, name, TABLE_VERSION);
          writableDatabase.execSQL("DROP TABLE IF EXISTS " + tableName);
          writableDatabase.execSQL("CREATE TABLE " + tableName + " " + TABLE_SCHEMA);
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
              tableName,
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

  private static String getStateQuery(@Download.State int... states) {
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

  private static Download getDownloadForCurrentRow(Cursor cursor) {
    DownloadRequest request =
        new DownloadRequest(
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
    return new Download(
        request,
        cursor.getInt(COLUMN_INDEX_STATE),
        cursor.getInt(COLUMN_INDEX_FAILURE_REASON),
        cursor.getInt(COLUMN_INDEX_STOP_REASON),
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

  private static final class DownloadCursorImpl implements DownloadCursor {

    private final Cursor cursor;

    private DownloadCursorImpl(Cursor cursor) {
      this.cursor = cursor;
    }

    @Override
    public Download getDownload() {
      return getDownloadForCurrentRow(cursor);
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
