/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream.cache;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import com.google.android.exoplayer2.util.Assertions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Maintains an index of cache file metadata. */
/* package */ final class CacheFileMetadataIndex {

  private static final String TABLE_PREFIX = DatabaseProvider.TABLE_PREFIX + "CacheFileMetadata";
  private static final int TABLE_VERSION = 1;

  private static final String COLUMN_NAME = "name";
  private static final String COLUMN_LENGTH = "length";
  private static final String COLUMN_LAST_ACCESS_TIMESTAMP = "last_access_timestamp";

  private static final int COLUMN_INDEX_NAME = 0;
  private static final int COLUMN_INDEX_LENGTH = 1;
  private static final int COLUMN_INDEX_LAST_ACCESS_TIMESTAMP = 2;

  private static final String WHERE_NAME_EQUALS = COLUMN_INDEX_NAME + " = ?";

  private static final String[] COLUMNS =
      new String[] {
        COLUMN_NAME, COLUMN_LENGTH, COLUMN_LAST_ACCESS_TIMESTAMP,
      };
  private static final String TABLE_SCHEMA =
      "("
          + COLUMN_NAME
          + " TEXT PRIMARY KEY NOT NULL,"
          + COLUMN_LENGTH
          + " INTEGER NOT NULL,"
          + COLUMN_LAST_ACCESS_TIMESTAMP
          + " INTEGER NOT NULL)";

  private final DatabaseProvider databaseProvider;

  @MonotonicNonNull private String tableName;

  public CacheFileMetadataIndex(DatabaseProvider databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  /** Initializes the index for the given cache UID. */
  public void initialize(long uid) {
    String hexUid = Long.toHexString(uid);
    tableName = TABLE_PREFIX + hexUid;
    SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
    int version =
        VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_CACHE_FILE_METADATA, hexUid);
    if (version == VersionTable.VERSION_UNSET || version > TABLE_VERSION) {
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.beginTransaction();
      try {
        VersionTable.setVersion(
            writableDatabase, VersionTable.FEATURE_CACHE_FILE_METADATA, hexUid, TABLE_VERSION);
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
  }

  /**
   * Returns all file metadata keyed by file name. The returned map is mutable and may be modified
   * by the caller.
   */
  public Map<String, CacheFileMetadata> getAll() {
    try (Cursor cursor = getCursor()) {
      Map<String, CacheFileMetadata> fileMetadata = new HashMap<>(cursor.getCount());
      while (cursor.moveToNext()) {
        String name = cursor.getString(COLUMN_INDEX_NAME);
        long length = cursor.getLong(COLUMN_INDEX_LENGTH);
        long lastAccessTimestamp = cursor.getLong(COLUMN_INDEX_LAST_ACCESS_TIMESTAMP);
        fileMetadata.put(name, new CacheFileMetadata(length, lastAccessTimestamp));
      }
      return fileMetadata;
    }
  }

  /**
   * Sets metadata for a given file.
   *
   * @param name The name of the file.
   * @param length The file length.
   * @param lastAccessTimestamp The file last access timestamp.
   */
  public void set(String name, long length, long lastAccessTimestamp) {
    Assertions.checkNotNull(tableName);
    SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(COLUMN_NAME, name);
    values.put(COLUMN_LENGTH, length);
    values.put(COLUMN_LAST_ACCESS_TIMESTAMP, lastAccessTimestamp);
    writableDatabase.replace(tableName, /* nullColumnHack= */ null, values);
  }

  /**
   * Removes metadata.
   *
   * @param name The name of the file whose metadata is to be removed.
   */
  public void remove(String name) {
    Assertions.checkNotNull(tableName);
    SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
    writableDatabase.delete(tableName, WHERE_NAME_EQUALS, new String[] {name});
  }

  /**
   * Removes metadata.
   *
   * @param names The names of the files whose metadata is to be removed.
   */
  public void removeAll(Set<String> names) {
    Assertions.checkNotNull(tableName);
    SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
    writableDatabase.beginTransaction();
    try {
      for (String name : names) {
        writableDatabase.delete(tableName, WHERE_NAME_EQUALS, new String[] {name});
      }
      writableDatabase.setTransactionSuccessful();
    } finally {
      writableDatabase.endTransaction();
    }
  }

  private Cursor getCursor() {
    Assertions.checkNotNull(tableName);
    return databaseProvider
        .getReadableDatabase()
        .query(
            tableName,
            COLUMNS,
            /* selection */ null,
            /* selectionArgs= */ null,
            /* groupBy= */ null,
            /* having= */ null,
            /* orderBy= */ null);
  }
}
