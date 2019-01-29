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

import static com.google.android.exoplayer2.database.VersionTable.FEATURE_CACHE;
import static com.google.android.exoplayer2.database.VersionTable.FEATURE_OFFLINE;
import static com.google.common.truth.Truth.assertThat;

import android.database.sqlite.SQLiteDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link VersionTable}. */
@RunWith(RobolectricTestRunner.class)
public class VersionTableTest {

  private ExoDatabaseProvider databaseProvider;
  private SQLiteDatabase readableDatabase;
  private SQLiteDatabase writableDatabase;

  @Before
  public void setUp() {
    databaseProvider = new ExoDatabaseProvider(RuntimeEnvironment.application);
    readableDatabase = databaseProvider.getReadableDatabase();
    writableDatabase = databaseProvider.getWritableDatabase();
  }

  @After
  public void tearDown() {
    databaseProvider.close();
  }

  @Test
  public void getVersion_nonExistingTable_returnsVersionUnset() {
    int version = VersionTable.getVersion(readableDatabase, FEATURE_OFFLINE);
    assertThat(version).isEqualTo(VersionTable.VERSION_UNSET);
  }

  @Test
  public void getVersion_returnsSetVersion() {
    VersionTable.setVersion(writableDatabase, FEATURE_OFFLINE, 1);
    assertThat(VersionTable.getVersion(readableDatabase, FEATURE_OFFLINE)).isEqualTo(1);

    VersionTable.setVersion(writableDatabase, FEATURE_OFFLINE, 10);
    assertThat(VersionTable.getVersion(readableDatabase, FEATURE_OFFLINE)).isEqualTo(10);

    VersionTable.setVersion(writableDatabase, FEATURE_CACHE, 5);
    assertThat(VersionTable.getVersion(readableDatabase, FEATURE_CACHE)).isEqualTo(5);
    assertThat(VersionTable.getVersion(readableDatabase, FEATURE_OFFLINE)).isEqualTo(10);
  }

  @Test
  public void doesTableExist_nonExistingTable_returnsFalse() {
    assertThat(VersionTable.tableExists(readableDatabase, "NonExistingTable")).isFalse();
  }

  @Test
  public void doesTableExist_existingTable_returnsTrue() {
    String table = "TestTable";
    databaseProvider.getWritableDatabase().execSQL("CREATE TABLE " + table + " (dummy INTEGER)");
    assertThat(VersionTable.tableExists(readableDatabase, table)).isTrue();
  }
}
