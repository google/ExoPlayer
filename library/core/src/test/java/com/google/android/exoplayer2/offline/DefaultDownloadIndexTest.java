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

import static com.google.android.exoplayer2.offline.DefaultDownloadIndex.INSTANCE_UID;
import static com.google.common.truth.Truth.assertThat;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DefaultDownloadIndex}. */
@RunWith(AndroidJUnit4.class)
public class DefaultDownloadIndexTest {

  private ExoDatabaseProvider databaseProvider;
  private DefaultDownloadIndex downloadIndex;

  @Before
  public void setUp() {
    databaseProvider = new ExoDatabaseProvider(ApplicationProvider.getApplicationContext());
    downloadIndex = new DefaultDownloadIndex(databaseProvider);
  }

  @After
  public void tearDown() {
    databaseProvider.close();
  }

  @Test
  public void getDownloadState_nonExistingId_returnsNull() throws DatabaseIOException {
    assertThat(downloadIndex.getDownloadState("non existing id")).isNull();
  }

  @Test
  public void addAndGetDownloadState_nonExistingId_returnsTheSameDownloadState()
      throws DatabaseIOException {
    String id = "id";
    DownloadState downloadState = new DownloadStateBuilder(id).build();

    downloadIndex.putDownloadState(downloadState);
    DownloadState readDownloadState = downloadIndex.getDownloadState(id);

    DownloadStateTest.assertEqual(readDownloadState, downloadState);
  }

  @Test
  public void addAndGetDownloadState_existingId_returnsUpdatedDownloadState()
      throws DatabaseIOException {
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
            .setNotMetRequirements(0x87654321)
            .setManualStopReason(0x12345678)
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
  public void releaseAndRecreateDownloadIndex_returnsTheSameDownloadState()
      throws DatabaseIOException {
    String id = "id";
    DownloadState downloadState = new DownloadStateBuilder(id).build();
    downloadIndex.putDownloadState(downloadState);

    downloadIndex = new DefaultDownloadIndex(databaseProvider);
    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    assertThat(readDownloadState).isNotNull();
    DownloadStateTest.assertEqual(readDownloadState, downloadState);
  }

  @Test
  public void removeDownloadState_nonExistingId_doesNotFail() throws DatabaseIOException {
    downloadIndex.removeDownloadState("non existing id");
  }

  @Test
  public void removeDownloadState_existingId_getDownloadStateReturnsNull()
      throws DatabaseIOException {
    String id = "id";
    DownloadState downloadState = new DownloadStateBuilder(id).build();
    downloadIndex.putDownloadState(downloadState);
    downloadIndex.removeDownloadState(id);

    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    assertThat(readDownloadState).isNull();
  }

  @Test
  public void getDownloadStates_emptyDownloadIndex_returnsEmptyArray() throws DatabaseIOException {
    assertThat(downloadIndex.getDownloadStates().getCount()).isEqualTo(0);
  }

  @Test
  public void getDownloadStates_noState_returnsAllDownloadStatusSortedByStartTime()
      throws DatabaseIOException {
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
  public void getDownloadStates_withStates_returnsAllDownloadStatusWithTheSameStates()
      throws DatabaseIOException {
    DownloadState downloadState1 =
        new DownloadStateBuilder("id1")
            .setStartTimeMs(0)
            .setState(DownloadState.STATE_REMOVING)
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
        downloadIndex.getDownloadStates(
            DownloadState.STATE_REMOVING, DownloadState.STATE_COMPLETED);

    assertThat(cursor.getCount()).isEqualTo(2);
    cursor.moveToNext();
    DownloadStateTest.assertEqual(cursor.getDownloadState(), downloadState1);
    cursor.moveToNext();
    DownloadStateTest.assertEqual(cursor.getDownloadState(), downloadState3);
    cursor.close();
  }

  @Test
  public void putDownloadState_setsVersion() throws DatabaseIOException {
    SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
    assertThat(
            VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, INSTANCE_UID))
        .isEqualTo(VersionTable.VERSION_UNSET);

    downloadIndex.putDownloadState(new DownloadStateBuilder("id1").build());

    assertThat(
            VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, INSTANCE_UID))
        .isEqualTo(DefaultDownloadIndex.TABLE_VERSION);
  }

  @Test
  public void downloadIndex_versionDowngradeWipesData() throws DatabaseIOException {
    DownloadState downloadState1 = new DownloadStateBuilder("id1").build();
    downloadIndex.putDownloadState(downloadState1);
    DownloadStateCursor cursor = downloadIndex.getDownloadStates();
    assertThat(cursor.getCount()).isEqualTo(1);
    cursor.close();

    SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
    VersionTable.setVersion(
        writableDatabase, VersionTable.FEATURE_OFFLINE, INSTANCE_UID, Integer.MAX_VALUE);

    downloadIndex = new DefaultDownloadIndex(databaseProvider);

    cursor = downloadIndex.getDownloadStates();
    assertThat(cursor.getCount()).isEqualTo(0);
    cursor.close();
    assertThat(
            VersionTable.getVersion(writableDatabase, VersionTable.FEATURE_OFFLINE, INSTANCE_UID))
        .isEqualTo(DefaultDownloadIndex.TABLE_VERSION);
  }

  @Test
  public void setManualStopReason_setReasonToNone() throws Exception {
    String id = "id";
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(id)
            .setState(DownloadState.STATE_COMPLETED)
            .setManualStopReason(0x12345678);
    DownloadState downloadState = downloadStateBuilder.build();
    downloadIndex.putDownloadState(downloadState);

    downloadIndex.setManualStopReason(DownloadState.MANUAL_STOP_REASON_NONE);

    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    DownloadState expectedDownloadState =
        downloadStateBuilder.setManualStopReason(DownloadState.MANUAL_STOP_REASON_NONE).build();
    DownloadStateTest.assertEqual(readDownloadState, expectedDownloadState);
  }

  @Test
  public void setManualStopReason_setReason() throws Exception {
    String id = "id";
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(id)
            .setState(DownloadState.STATE_FAILED)
            .setFailureReason(DownloadState.FAILURE_REASON_UNKNOWN);
    DownloadState downloadState = downloadStateBuilder.build();
    downloadIndex.putDownloadState(downloadState);
    int manualStopReason = 0x12345678;

    downloadIndex.setManualStopReason(manualStopReason);

    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    DownloadState expectedDownloadState =
        downloadStateBuilder.setManualStopReason(manualStopReason).build();
    DownloadStateTest.assertEqual(readDownloadState, expectedDownloadState);
  }

  @Test
  public void setManualStopReason_notTerminalState_doesNotSetManualStopReason() throws Exception {
    String id = "id";
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(id).setState(DownloadState.STATE_DOWNLOADING);
    DownloadState downloadState = downloadStateBuilder.build();
    downloadIndex.putDownloadState(downloadState);
    int notMetRequirements = 0x12345678;

    downloadIndex.setManualStopReason(notMetRequirements);

    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    DownloadStateTest.assertEqual(readDownloadState, downloadState);
  }

  @Test
  public void setSingleDownloadManualStopReason_setReasonToNone() throws Exception {
    String id = "id";
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(id)
            .setState(DownloadState.STATE_COMPLETED)
            .setManualStopReason(0x12345678);
    DownloadState downloadState = downloadStateBuilder.build();
    downloadIndex.putDownloadState(downloadState);

    downloadIndex.setManualStopReason(id, DownloadState.MANUAL_STOP_REASON_NONE);

    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    DownloadState expectedDownloadState =
        downloadStateBuilder.setManualStopReason(DownloadState.MANUAL_STOP_REASON_NONE).build();
    DownloadStateTest.assertEqual(readDownloadState, expectedDownloadState);
  }

  @Test
  public void setSingleDownloadManualStopReason_setReason() throws Exception {
    String id = "id";
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(id)
            .setState(DownloadState.STATE_FAILED)
            .setFailureReason(DownloadState.FAILURE_REASON_UNKNOWN);
    DownloadState downloadState = downloadStateBuilder.build();
    downloadIndex.putDownloadState(downloadState);
    int manualStopReason = 0x12345678;

    downloadIndex.setManualStopReason(id, manualStopReason);

    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    DownloadState expectedDownloadState =
        downloadStateBuilder.setManualStopReason(manualStopReason).build();
    DownloadStateTest.assertEqual(readDownloadState, expectedDownloadState);
  }

  @Test
  public void setSingleDownloadManualStopReason_notTerminalState_doesNotSetManualStopReason()
      throws Exception {
    String id = "id";
    DownloadStateBuilder downloadStateBuilder =
        new DownloadStateBuilder(id).setState(DownloadState.STATE_DOWNLOADING);
    DownloadState downloadState = downloadStateBuilder.build();
    downloadIndex.putDownloadState(downloadState);
    int notMetRequirements = 0x12345678;

    downloadIndex.setManualStopReason(id, notMetRequirements);

    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    DownloadStateTest.assertEqual(readDownloadState, downloadState);
  }
}
