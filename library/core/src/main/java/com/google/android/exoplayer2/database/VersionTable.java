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
package com.google.android.exoplayer2.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.IntDef;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A table that holds version information about other ExoPlayer tables. This allows ExoPlayer tables
 * to be versioned independently to the version of the containing database.
 */
public final class VersionTable {

  /** Returned by {@link #getVersion(int)} if the version is unset. */
  public static final int VERSION_UNSET = -1;
  /** Version of tables used for offline functionality. */
  public static final int FEATURE_OFFLINE = 0;
  /** Version of tables used for cache functionality. */
  public static final int FEATURE_CACHE = 1;

  private static final String TABLE_NAME = DatabaseProvider.TABLE_PREFIX + "Versions";

  private static final String COLUMN_FEATURE = "feature";
  private static final String COLUMN_VERSION = "version";

  private static final String SQL_CREATE_TABLE_IF_NOT_EXISTS =
      "CREATE TABLE IF NOT EXISTS "
          + TABLE_NAME
          + " ("
          + COLUMN_FEATURE
          + " INTEGER PRIMARY KEY NOT NULL,"
          + COLUMN_VERSION
          + " INTEGER NOT NULL)";

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FEATURE_OFFLINE, FEATURE_CACHE})
  private @interface Feature {}

  private final DatabaseProvider databaseProvider;

  public VersionTable(DatabaseProvider databaseProvider) {
    this.databaseProvider = databaseProvider;
    // Check whether the table exists to avoid getting a writable database if we don't need one.
    if (!doesTableExist(databaseProvider, TABLE_NAME)) {
      databaseProvider.getWritableDatabase().execSQL(SQL_CREATE_TABLE_IF_NOT_EXISTS);
    }
  }

  /**
   * Sets the version of tables belonging to the specified feature.
   *
   * @param feature The feature.
   * @param version The version.
   */
  public void setVersion(@Feature int feature, int version) {
    ContentValues values = new ContentValues();
    values.put(COLUMN_FEATURE, feature);
    values.put(COLUMN_VERSION, version);
    SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
    writableDatabase.replace(TABLE_NAME, /* nullColumnHack= */ null, values);
  }

  /**
   * Returns the version of tables belonging to the specified feature, or {@link #VERSION_UNSET} if
   * no version information is available.
   */
  public int getVersion(@Feature int feature) {
    String selection = COLUMN_FEATURE + " = ?";
    String[] selectionArgs = {Integer.toString(feature)};
    try (Cursor cursor =
        databaseProvider
            .getReadableDatabase()
            .query(
                TABLE_NAME,
                new String[] {COLUMN_VERSION},
                selection,
                selectionArgs,
                /* groupBy= */ null,
                /* having= */ null,
                /* orderBy= */ null)) {
      if (cursor.getCount() == 0) {
        return VERSION_UNSET;
      }
      cursor.moveToNext();
      return cursor.getInt(/* COLUMN_VERSION index */ 0);
    }
  }

  /* package */ static boolean doesTableExist(DatabaseProvider databaseProvider, String tableName) {
    SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
    long count =
        DatabaseUtils.queryNumEntries(
            readableDatabase, "sqlite_master", "tbl_name = ?", new String[] {tableName});
    return count > 0;
  }
}
