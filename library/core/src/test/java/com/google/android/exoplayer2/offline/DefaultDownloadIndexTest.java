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
import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import java.util.Arrays;
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

    assertEqual(readDownloadState, downloadState);
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
    assertEqual(readDownloadState, downloadState);
  }

  @Test
  public void releaseAndRecreateDownloadIndex_returnsTheSameDownloadState() {
    String id = "id";
    DownloadState downloadState = new DownloadStateBuilder(id).build();
    downloadIndex.putDownloadState(downloadState);

    downloadIndex = new DefaultDownloadIndex(databaseProvider);
    DownloadState readDownloadState = downloadIndex.getDownloadState(id);
    assertThat(readDownloadState).isNotNull();
    assertEqual(readDownloadState, downloadState);
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
    assertEqual(cursor.getDownloadState(), downloadState2);
    cursor.moveToNext();
    assertEqual(cursor.getDownloadState(), downloadState1);
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
    assertEqual(cursor.getDownloadState(), downloadState1);
    cursor.moveToNext();
    assertEqual(cursor.getDownloadState(), downloadState3);
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

  private static void assertEqual(DownloadState downloadState, DownloadState expected) {
    assertThat(areEqual(downloadState, expected)).isTrue();
  }

  private static boolean areEqual(DownloadState downloadState, DownloadState that) {
    if (downloadState.state != that.state) {
      return false;
    }
    if (Float.compare(that.downloadPercentage, downloadState.downloadPercentage) != 0) {
      return false;
    }
    if (downloadState.downloadedBytes != that.downloadedBytes) {
      return false;
    }
    if (downloadState.totalBytes != that.totalBytes) {
      return false;
    }
    if (downloadState.startTimeMs != that.startTimeMs) {
      return false;
    }
    if (downloadState.updateTimeMs != that.updateTimeMs) {
      return false;
    }
    if (downloadState.failureReason != that.failureReason) {
      return false;
    }
    if (downloadState.stopFlags != that.stopFlags) {
      return false;
    }
    if (downloadState.notMetRequirements != that.notMetRequirements) {
      return false;
    }
    if (!downloadState.id.equals(that.id)) {
      return false;
    }
    if (!downloadState.type.equals(that.type)) {
      return false;
    }
    if (!downloadState.uri.equals(that.uri)) {
      return false;
    }
    if (downloadState.cacheKey != null
        ? !downloadState.cacheKey.equals(that.cacheKey)
        : that.cacheKey != null) {
      return false;
    }
    if (!Arrays.equals(downloadState.streamKeys, that.streamKeys)) {
      return false;
    }
    return Arrays.equals(downloadState.customMetadata, that.customMetadata);
  }

  private static class DownloadStateBuilder {
    private String id;
    private String type;
    private String uri;
    @Nullable private String cacheKey;
    private int state;
    private float downloadPercentage;
    private long downloadedBytes;
    private long totalBytes;
    private int failureReason;
    private int stopFlags;
    private int notMetRequirements;
    private long startTimeMs;
    private long updateTimeMs;
    private StreamKey[] streamKeys;
    private byte[] customMetadata;

    private DownloadStateBuilder(String id) {
      this.id = id;
      this.type = "type";
      this.uri = "uri";
      this.cacheKey = null;
      this.state = DownloadState.STATE_QUEUED;
      this.downloadPercentage = (float) C.PERCENTAGE_UNSET;
      this.downloadedBytes = (long) 0;
      this.totalBytes = (long) C.LENGTH_UNSET;
      this.failureReason = DownloadState.FAILURE_REASON_NONE;
      this.stopFlags = 0;
      this.startTimeMs = (long) 0;
      this.updateTimeMs = (long) 0;
      this.streamKeys = new StreamKey[0];
      this.customMetadata = new byte[0];
    }

    public DownloadStateBuilder setId(String id) {
      this.id = id;
      return this;
    }

    public DownloadStateBuilder setType(String type) {
      this.type = type;
      return this;
    }

    public DownloadStateBuilder setUri(String uri) {
      this.uri = uri;
      return this;
    }

    public DownloadStateBuilder setCacheKey(@Nullable String cacheKey) {
      this.cacheKey = cacheKey;
      return this;
    }

    public DownloadStateBuilder setState(int state) {
      this.state = state;
      return this;
    }

    public DownloadStateBuilder setDownloadPercentage(float downloadPercentage) {
      this.downloadPercentage = downloadPercentage;
      return this;
    }

    public DownloadStateBuilder setDownloadedBytes(long downloadedBytes) {
      this.downloadedBytes = downloadedBytes;
      return this;
    }

    public DownloadStateBuilder setTotalBytes(long totalBytes) {
      this.totalBytes = totalBytes;
      return this;
    }

    public DownloadStateBuilder setFailureReason(int failureReason) {
      this.failureReason = failureReason;
      return this;
    }

    public DownloadStateBuilder setStopFlags(int stopFlags) {
      this.stopFlags = stopFlags;
      return this;
    }

    public DownloadStateBuilder setNotMetRequirements(int notMetRequirements) {
      this.notMetRequirements = notMetRequirements;
      return this;
    }

    public DownloadStateBuilder setStartTimeMs(long startTimeMs) {
      this.startTimeMs = startTimeMs;
      return this;
    }

    public DownloadStateBuilder setUpdateTimeMs(long updateTimeMs) {
      this.updateTimeMs = updateTimeMs;
      return this;
    }

    public DownloadStateBuilder setStreamKeys(StreamKey... streamKeys) {
      this.streamKeys = streamKeys;
      return this;
    }

    public DownloadStateBuilder setCustomMetadata(byte[] customMetadata) {
      this.customMetadata = customMetadata;
      return this;
    }

    public DownloadState build() {
      return new DownloadState(
          id,
          type,
          Uri.parse(uri),
          cacheKey,
          state,
          downloadPercentage,
          downloadedBytes,
          totalBytes,
          failureReason,
          stopFlags,
          notMetRequirements,
          startTimeMs,
          updateTimeMs,
          streamKeys,
          customMetadata);
    }
  }
}
