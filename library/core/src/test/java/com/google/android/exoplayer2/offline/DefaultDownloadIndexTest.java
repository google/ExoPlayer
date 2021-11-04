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

import static com.google.android.exoplayer2.offline.Download.FAILURE_REASON_NONE;
import static com.google.android.exoplayer2.offline.Download.FAILURE_REASON_UNKNOWN;
import static com.google.android.exoplayer2.offline.Download.STATE_DOWNLOADING;
import static com.google.android.exoplayer2.offline.Download.STATE_STOPPED;
import static com.google.android.exoplayer2.offline.Download.STOP_REASON_NONE;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import com.google.android.exoplayer2.testutil.DownloadBuilder;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DefaultDownloadIndex}. */
@RunWith(AndroidJUnit4.class)
public class DefaultDownloadIndexTest {

  private static final String EMPTY_NAME = "";

  private StandaloneDatabaseProvider databaseProvider;
  private DefaultDownloadIndex downloadIndex;

  @Before
  public void setUp() {
    databaseProvider = new StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext());
    downloadIndex = new DefaultDownloadIndex(databaseProvider);
  }

  @After
  public void tearDown() {
    databaseProvider.close();
  }

  @Test
  public void getDownload_nonExistingId_returnsNull() throws DatabaseIOException {
    assertThat(downloadIndex.getDownload("non existing id")).isNull();
  }

  @Test
  public void addAndGetDownload_nonExistingId_returnsTheSameDownload() throws DatabaseIOException {
    String id = "id";
    Download download = new DownloadBuilder(id).build();

    downloadIndex.putDownload(download);
    Download readDownload = downloadIndex.getDownload(id);

    assertEqual(readDownload, download);
  }

  @Test
  public void addAndGetDownload_existingId_returnsUpdatedDownload() throws DatabaseIOException {
    String id = "id";
    DownloadBuilder downloadBuilder = new DownloadBuilder(id);
    downloadIndex.putDownload(downloadBuilder.build());

    Download download =
        downloadBuilder
            .setUri("different uri")
            .setMimeType(MimeTypes.APPLICATION_MP4)
            .setCacheKey("different cacheKey")
            .setState(Download.STATE_FAILED)
            .setPercentDownloaded(50)
            .setBytesDownloaded(200)
            .setContentLength(400)
            .setFailureReason(FAILURE_REASON_UNKNOWN)
            .setStopReason(0x12345678)
            .setStartTimeMs(10)
            .setUpdateTimeMs(20)
            .setStreamKeys(
                new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2),
                new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5))
            .setCustomMetadata(new byte[] {0, 1, 2, 3, 7, 8, 9, 10})
            .setKeySetId(new byte[] {0, 1, 2, 3})
            .build();
    downloadIndex.putDownload(download);
    Download readDownload = downloadIndex.getDownload(id);

    assertThat(readDownload).isNotNull();
    assertEqual(readDownload, download);
  }

  @Test
  public void releaseAndRecreateDownloadIndex_returnsTheSameDownload() throws DatabaseIOException {
    String id = "id";
    Download download = new DownloadBuilder(id).build();
    downloadIndex.putDownload(download);

    downloadIndex = new DefaultDownloadIndex(databaseProvider);
    Download readDownload = downloadIndex.getDownload(id);
    assertThat(readDownload).isNotNull();
    assertEqual(readDownload, download);
  }

  @Test
  public void removeDownload_nonExistingId_doesNotFail() throws DatabaseIOException {
    downloadIndex.removeDownload("non existing id");
  }

  @Test
  public void removeDownload_existingId_getDownloadReturnsNull() throws DatabaseIOException {
    String id = "id";
    Download download = new DownloadBuilder(id).build();
    downloadIndex.putDownload(download);
    downloadIndex.removeDownload(id);

    Download readDownload = downloadIndex.getDownload(id);
    assertThat(readDownload).isNull();
  }

  @Test
  public void getDownloads_emptyDownloadIndex_returnsEmptyArray() throws DatabaseIOException {
    assertThat(downloadIndex.getDownloads().getCount()).isEqualTo(0);
  }

  @Test
  public void getDownloads_noState_returnsAllDownloadStatusSortedByStartTime()
      throws DatabaseIOException {
    Download download1 = new DownloadBuilder("id1").setStartTimeMs(1).build();
    downloadIndex.putDownload(download1);
    Download download2 = new DownloadBuilder("id2").setStartTimeMs(0).build();
    downloadIndex.putDownload(download2);

    try (DownloadCursor cursor = downloadIndex.getDownloads()) {
      assertThat(cursor.getCount()).isEqualTo(2);
      cursor.moveToNext();
      assertEqual(cursor.getDownload(), download2);
      cursor.moveToNext();
      assertEqual(cursor.getDownload(), download1);
    }
  }

  @Test
  public void getDownloads_withStates_returnsAllDownloadStatusWithTheSameStates()
      throws DatabaseIOException {
    Download download1 =
        new DownloadBuilder("id1").setStartTimeMs(0).setState(Download.STATE_REMOVING).build();
    downloadIndex.putDownload(download1);
    Download download2 =
        new DownloadBuilder("id2").setStartTimeMs(1).setState(STATE_STOPPED).build();
    downloadIndex.putDownload(download2);
    Download download3 =
        new DownloadBuilder("id3").setStartTimeMs(2).setState(Download.STATE_COMPLETED).build();
    downloadIndex.putDownload(download3);

    try (DownloadCursor cursor =
        downloadIndex.getDownloads(Download.STATE_REMOVING, Download.STATE_COMPLETED)) {
      assertThat(cursor.getCount()).isEqualTo(2);
      cursor.moveToNext();
      assertEqual(cursor.getDownload(), download1);
      cursor.moveToNext();
      assertEqual(cursor.getDownload(), download3);
    }
  }

  @Test
  public void putDownload_setsVersion() throws DatabaseIOException {
    SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
    assertThat(VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, EMPTY_NAME))
        .isEqualTo(VersionTable.VERSION_UNSET);

    downloadIndex.putDownload(new DownloadBuilder("id1").build());

    assertThat(VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, EMPTY_NAME))
        .isEqualTo(DefaultDownloadIndex.TABLE_VERSION);
  }

  @Test
  public void downloadIndex_versionDowngradeWipesData() throws DatabaseIOException {
    Download download1 = new DownloadBuilder("id1").build();
    downloadIndex.putDownload(download1);
    DownloadCursor cursor = downloadIndex.getDownloads();
    assertThat(cursor.getCount()).isEqualTo(1);
    cursor.close();

    SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
    VersionTable.setVersion(
        writableDatabase, VersionTable.FEATURE_OFFLINE, EMPTY_NAME, Integer.MAX_VALUE);

    downloadIndex = new DefaultDownloadIndex(databaseProvider);

    cursor = downloadIndex.getDownloads();
    assertThat(cursor.getCount()).isEqualTo(0);
    cursor.close();
    assertThat(VersionTable.getVersion(writableDatabase, VersionTable.FEATURE_OFFLINE, EMPTY_NAME))
        .isEqualTo(DefaultDownloadIndex.TABLE_VERSION);
  }

  @Test
  public void downloadIndex_upgradesFromVersion2() throws IOException {
    Context context = ApplicationProvider.getApplicationContext();
    File databaseFile = context.getDatabasePath(StandaloneDatabaseProvider.DATABASE_NAME);
    try (FileOutputStream output = new FileOutputStream(databaseFile)) {
      output.write(TestUtil.getByteArray(context, "media/offline/exoplayer_internal_v2.db"));
    }
    Download dashDownload =
        createDownload(
            /* uri= */ "http://www.test.com/manifest.mpd",
            /* mimeType= */ MimeTypes.APPLICATION_MPD,
            ImmutableList.of(),
            /* customCacheKey= */ null);
    Download hlsDownload =
        createDownload(
            /* uri= */ "http://www.test.com/manifest.m3u8",
            /* mimeType= */ MimeTypes.APPLICATION_M3U8,
            ImmutableList.of(),
            /* customCacheKey= */ null);
    Download ssDownload =
        createDownload(
            /* uri= */ "http://www.test.com/video.ism/manifest",
            /* mimeType= */ MimeTypes.APPLICATION_SS,
            Arrays.asList(new StreamKey(0, 0), new StreamKey(1, 1)),
            /* customCacheKey= */ null);
    Download progressiveDownload =
        createDownload(
            /* uri= */ "http://www.test.com/video.mp4",
            /* mimeType= */ MimeTypes.VIDEO_UNKNOWN,
            ImmutableList.of(),
            /* customCacheKey= */ "customCacheKey");

    databaseProvider = new StandaloneDatabaseProvider(context);
    downloadIndex = new DefaultDownloadIndex(databaseProvider);

    assertEqual(downloadIndex.getDownload("http://www.test.com/manifest.mpd"), dashDownload);
    assertEqual(downloadIndex.getDownload("http://www.test.com/manifest.m3u8"), hlsDownload);
    assertEqual(downloadIndex.getDownload("http://www.test.com/video.ism/manifest"), ssDownload);
    assertEqual(downloadIndex.getDownload("http://www.test.com/video.mp4"), progressiveDownload);
  }

  @Test
  public void setStopReason_setReasonToNone() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(id).setState(Download.STATE_COMPLETED).setStopReason(0x12345678);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);

    downloadIndex.setStopReason(STOP_REASON_NONE);

    Download readDownload = downloadIndex.getDownload(id);
    Download expectedDownload = downloadBuilder.setStopReason(STOP_REASON_NONE).build();
    assertEqual(readDownload, expectedDownload);
  }

  @Test
  public void setStopReason_setReason() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(id)
            .setState(Download.STATE_FAILED)
            .setFailureReason(FAILURE_REASON_UNKNOWN);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);
    int stopReason = 0x12345678;

    downloadIndex.setStopReason(stopReason);

    Download readDownload = downloadIndex.getDownload(id);
    Download expectedDownload = downloadBuilder.setStopReason(stopReason).build();
    assertEqual(readDownload, expectedDownload);
  }

  @Test
  public void setStopReason_notTerminalState_doesNotSetStopReason() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder = new DownloadBuilder(id).setState(STATE_DOWNLOADING);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);
    int notMetRequirements = 0x12345678;

    downloadIndex.setStopReason(notMetRequirements);

    Download readDownload = downloadIndex.getDownload(id);
    assertEqual(readDownload, download);
  }

  @Test
  public void setStatesToRemoving_setsStateAndClearsFailureReason() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(id)
            .setState(Download.STATE_FAILED)
            .setFailureReason(FAILURE_REASON_UNKNOWN);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);

    downloadIndex.setStatesToRemoving();

    download = downloadIndex.getDownload(id);
    assertThat(download.state).isEqualTo(Download.STATE_REMOVING);
    assertThat(download.failureReason).isEqualTo(FAILURE_REASON_NONE);
  }

  @Test
  public void setSingleDownloadStopReason_setReasonToNone() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(id).setState(Download.STATE_COMPLETED).setStopReason(0x12345678);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);

    downloadIndex.setStopReason(id, STOP_REASON_NONE);

    Download readDownload = downloadIndex.getDownload(id);
    Download expectedDownload = downloadBuilder.setStopReason(STOP_REASON_NONE).build();
    assertEqual(readDownload, expectedDownload);
  }

  @Test
  public void setSingleDownloadStopReason_setReason() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(id)
            .setState(Download.STATE_FAILED)
            .setFailureReason(FAILURE_REASON_UNKNOWN);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);
    int stopReason = 0x12345678;

    downloadIndex.setStopReason(id, stopReason);

    Download readDownload = downloadIndex.getDownload(id);
    Download expectedDownload = downloadBuilder.setStopReason(stopReason).build();
    assertEqual(readDownload, expectedDownload);
  }

  @Test
  public void setSingleDownloadStopReason_notTerminalState_doesNotSetStopReason() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder = new DownloadBuilder(id).setState(STATE_DOWNLOADING);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);
    int notMetRequirements = 0x12345678;

    downloadIndex.setStopReason(id, notMetRequirements);

    Download readDownload = downloadIndex.getDownload(id);
    assertEqual(readDownload, download);
  }

  private static void assertEqual(Download download, Download that) {
    assertThat(download.request).isEqualTo(that.request);
    assertThat(download.state).isEqualTo(that.state);
    assertThat(download.startTimeMs).isEqualTo(that.startTimeMs);
    assertThat(download.updateTimeMs).isEqualTo(that.updateTimeMs);
    assertThat(download.contentLength).isEqualTo(that.contentLength);
    assertThat(download.stopReason).isEqualTo(that.stopReason);
    assertThat(download.failureReason).isEqualTo(that.failureReason);
    assertThat(download.getPercentDownloaded()).isEqualTo(that.getPercentDownloaded());
    assertThat(download.getBytesDownloaded()).isEqualTo(that.getBytesDownloaded());
  }

  private static Download createDownload(
      String uri, String mimeType, List<StreamKey> streamKeys, @Nullable String customCacheKey) {
    DownloadRequest downloadRequest =
        new DownloadRequest.Builder(uri, Uri.parse(uri))
            .setMimeType(mimeType)
            .setStreamKeys(streamKeys)
            .setCustomCacheKey(customCacheKey)
            .setData(new byte[] {0, 1, 2, 3})
            .build();
    return new Download(
        downloadRequest,
        /* state= */ STATE_STOPPED,
        /* startTimeMs= */ 1,
        /* updateTimeMs= */ 2,
        /* contentLength= */ 3,
        /* stopReason= */ 4,
        /* failureReason= */ FAILURE_REASON_NONE);
  }
}
