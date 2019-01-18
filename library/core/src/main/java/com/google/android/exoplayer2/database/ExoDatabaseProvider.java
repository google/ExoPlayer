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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * An {@link SQLiteOpenHelper} that provides instances of a standalone ExoPlayer database.
 *
 * <p>Suitable for use by applications that do not already have their own database, or which would
 * prefer to keep ExoPlayer tables isolated in their own database. Other applications should prefer
 * to use {@link DefaultDatabaseProvider} with their own {@link SQLiteOpenHelper}.
 */
public final class ExoDatabaseProvider extends SQLiteOpenHelper implements DatabaseProvider {

  /** The file name used for the standalone ExoPlayer database. */
  public static final String DATABASE_NAME = "exoplayer_internal.db";

  private static final int VERSION = 1;

  public ExoDatabaseProvider(Context context) {
    super(context.getApplicationContext(), DATABASE_NAME, /* factory= */ null, VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    // Features create their own tables.
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // Features handle their own upgrades.
  }

  @Override
  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // TODO: Wipe the database.
    super.onDowngrade(db, oldVersion, newVersion);
  }
}
