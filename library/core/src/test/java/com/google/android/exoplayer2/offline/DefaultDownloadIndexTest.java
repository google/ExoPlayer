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

import static com.google.common.truth.Truth.assertThat;

import android.database.sqlite.SQLiteDatabase;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link DefaultDownloadIndex}. */
@RunWith(RobolectricTestRunner.class)
public class DefaultDownloadIndexTest {

  private ExoDatabaseProvider databaseProvider;
  private DefaultDownloadIndex downloadIndex;

  @Before
  public void setUp() {
    databaseProvider = new ExoDatabaseProvider(RuntimeEnvironment.application);
    downloadIndex = new DefaultDownloadIndex(databaseProvider);
  }

  @After
  public void tearDown() {
    databaseProvider.close();
  }

  @Test
  public void getDownloadState_nonExistingId_returnsNull() {
    assertThat(downloadIndex.getDownloadState("non existing id")).isNull();
  }

  @Test
  public void addAndGetDownloadState_nonExistingId_returnsTheSameDownloadState() {
    String id = "id";
    DownloadState downloadState = new DownloadStateBuilder(id).build();

    downloadIndex.putDownloadState(downloadState);
    DownloadState readDownloadState = downloadIndex.getDownloadState(id);

    DownloadStateTest.assertEqual(readDownloadState, downloadState);
  }

  @Test
  public void addAndGetDownloadState_existingId_returnsUpdatedDownloadState() {
    String id = "id";
    DownloadStateBuilder downloadStateBuilder = new DownloadStateBuilder(id);
    downloadIndex.putDownloadState(downloadStateBuilder.build());

    DownloadState downloadState =
        downloadStateBuilder
            .setType("different type")
            .setUri("different uri")
            .setCacheKey("different cacheKey")
            .setState(DownloadState.STATE_FAILED)
            .setDownloadPercentage(50)
            .setDownloadedBytes(200)
            .setTotalBytes(400)
            .setFailureReason(DownloadState.FAILURE_REASON_UNKNOWN)
            .setStopFlags(DownloadState.STOP_FLAG_REQUIREMENTS_NOT_MET)
            .setNotMetRequirements(0x87654321)
            .setStartTimeMs(10)
            .setUpdateTimeMs(20)
            .setStreamKeys(
                new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2),
                new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5))
            .setCustomMetadata(new byte[] {0, 1, 2, 3, 7, 8, 9, 10})
            .build();
    downloadIndex.putDownloadState(downloadState);
    DownloadState readDownloadState = downloadIndex.getDownloadState(id);

    assertThat(readDownloadState).isNotNull();
    DownloadStateTest.assertEqual(readDownloadState, downloadState);
  }

  @Test
  public void releaseAndRecreateDownloadIndex_returnsTheSameDownloadState() {
    String id = "id";
    DownloadState downloadState = new DownloadStateBuilder(id).build();
    downloadIndex.putDownloadState(downloadState);

    downloadIndex = new DefaultDownloadIndex(databaseProvider);
    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    assertThat(readDownloadState).isNotNull();
    DownloadStateTest.assertEqual(readDownloadState, downloadState);
  }

  @Test
  public void removeDownloadState_nonExistingId_doesNotFail() {
    downloadIndex.removeDownloadState("non existing id");
  }

  @Test
  public void removeDownloadState_existingId_getDownloadStateReturnsNull() {
    String id = "id";
    DownloadState downloadState = new DownloadStateBuilder(id).build();
    downloadIndex.putDownloadState(downloadState);
    downloadIndex.removeDownloadState(id);

    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    assertThat(readDownloadState).isNull();
  }

  @Test
  public void getDownloadStates_emptyDownloadIndex_returnsEmptyArray() {
    assertThat(downloadIndex.getDownloadStates().getCount()).isEqualTo(0);
  }

  @Test
  public void getDownloadStates_noState_returnsAllDownloadStatusSortedByStartTime() {
    DownloadState downloadState1 = new DownloadStateBuilder("id1").setStartTimeMs(1).build();
    downloadIndex.putDownloadState(downloadState1);
    DownloadState downloadState2 = new DownloadStateBuilder("id2").setStartTimeMs(0).build();
    downloadIndex.putDownloadState(downloadState2);

    DownloadStateCursor cursor = downloadIndex.getDownloadStates();

    assertThat(cursor.getCount()).isEqualTo(2);
    cursor.moveToNext();
    DownloadStateTest.assertEqual(cursor.getDownloadState(), downloadState2);
    cursor.moveToNext();
    DownloadStateTest.assertEqual(cursor.getDownloadState(), downloadState1);
    cursor.close();
  }

  @Test
  public void getDownloadStates_withStates_returnsAllDownloadStatusWithTheSameStates() {
    DownloadState downloadState1 =
        new DownloadStateBuilder("id1")
            .setStartTimeMs(0)
            .setState(DownloadState.STATE_REMOVED)
            .build();
    downloadIndex.putDownloadState(downloadState1);
    DownloadState downloadState2 =
        new DownloadStateBuilder("id2")
            .setStartTimeMs(1)
            .setState(DownloadState.STATE_STOPPED)
            .build();
    downloadIndex.putDownloadState(downloadState2);
    DownloadState downloadState3 =
        new DownloadStateBuilder("id3")
            .setStartTimeMs(2)
            .setState(DownloadState.STATE_COMPLETED)
            .build();
    downloadIndex.putDownloadState(downloadState3);

    DownloadStateCursor cursor =
        downloadIndex.getDownloadStates(DownloadState.STATE_REMOVED, DownloadState.STATE_COMPLETED);

    assertThat(cursor.getCount()).isEqualTo(2);
    cursor.moveToNext();
    DownloadStateTest.assertEqual(cursor.getDownloadState(), downloadState1);
    cursor.moveToNext();
    DownloadStateTest.assertEqual(cursor.getDownloadState(), downloadState3);
    cursor.close();
  }

  @Test
  public void putDownloadState_setsVersion() {
    SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
    assertThat(VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE))
        .isEqualTo(VersionTable.VERSION_UNSET);

    downloadIndex.putDownloadState(new DownloadStateBuilder("id1").build());

    assertThat(VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE))
        .isEqualTo(DefaultDownloadIndex.TABLE_VERSION);
  }

  @Test
  public void downloadIndex_versionDowngradeWipesData() {
    DownloadState downloadState1 = new DownloadStateBuilder("id1").build();
    downloadIndex.putDownloadState(downloadState1);
    DownloadStateCursor cursor = downloadIndex.getDownloadStates();
    assertThat(cursor.getCount()).isEqualTo(1);
    cursor.close();

    SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
    VersionTable.setVersion(writableDatabase, VersionTable.FEATURE_OFFLINE, Integer.MAX_VALUE);

    downloadIndex = new DefaultDownloadIndex(databaseProvider);

    cursor = downloadIndex.getDownloadStates();
    assertThat(cursor.getCount()).isEqualTo(0);
    cursor.close();
    assertThat(VersionTable.getVersion(writableDatabase, VersionTable.FEATURE_OFFLINE))
        .isEqualTo(DefaultDownloadIndex.TABLE_VERSION);
  }

}
