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

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ActionFileUpgradeUtil}. */
@RunWith(AndroidJUnit4.class)
public class ActionFileUpgradeUtilTest {

  private static final long NOW_MS = 1234;

  private File tempFile;
  private ExoDatabaseProvider databaseProvider;
  private DefaultDownloadIndex downloadIndex;

  @Before
  public void setUp() throws Exception {
    tempFile = Util.createTempFile(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    databaseProvider = new ExoDatabaseProvider(ApplicationProvider.getApplicationContext());
    downloadIndex = new DefaultDownloadIndex(databaseProvider);
  }

  @After
  public void tearDown() {
    databaseProvider.close();
    tempFile.delete();
  }

  @Test
  public void upgradeAndDelete_progressiveActionFile_createsDownloads() throws IOException {
    byte[] actionFileBytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(),
            "media/offline/action_file_for_download_index_upgrade_progressive.exi");
    try (FileOutputStream output = new FileOutputStream(tempFile)) {
      output.write(actionFileBytes);
    }
    DownloadRequest expectedRequest1 =
        new DownloadRequest.Builder(
                /* id= */ "http://www.test.com/1/video.mp4",
                Uri.parse("http://www.test.com/1/video.mp4"))
            .setMimeType(MimeTypes.VIDEO_UNKNOWN)
            .build();
    DownloadRequest expectedRequest2 =
        new DownloadRequest.Builder(
                /* id= */ "customCacheKey", Uri.parse("http://www.test.com/2/video.mp4"))
            .setMimeType(MimeTypes.VIDEO_UNKNOWN)
            .setCustomCacheKey("customCacheKey")
            .setData(new byte[] {0, 1, 2, 3})
            .build();

    ActionFileUpgradeUtil.upgradeAndDelete(
        tempFile,
        /* downloadIdProvider= */ null,
        downloadIndex,
        /* deleteOnFailure= */ true,
        /* addNewDownloadsAsCompleted= */ false);

    assertThat(tempFile.exists()).isFalse();
    assertDownloadIndexContainsRequest(expectedRequest1, Download.STATE_QUEUED);
    assertDownloadIndexContainsRequest(expectedRequest2, Download.STATE_QUEUED);
  }

  @Test
  public void upgradeAndDelete_dashActionFile_createsDownloads() throws IOException {
    byte[] actionFileBytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(),
            "media/offline/action_file_for_download_index_upgrade_dash.exi");
    try (FileOutputStream output = new FileOutputStream(tempFile)) {
      output.write(actionFileBytes);
    }
    DownloadRequest expectedRequest1 =
        new DownloadRequest.Builder(
                /* id= */ "http://www.test.com/1/manifest.mpd",
                Uri.parse("http://www.test.com/1/manifest.mpd"))
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build();
    DownloadRequest expectedRequest2 =
        new DownloadRequest.Builder(
                /* id= */ "http://www.test.com/2/manifest.mpd",
                Uri.parse("http://www.test.com/2/manifest.mpd"))
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .setStreamKeys(
                ImmutableList.of(
                    new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0),
                    new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1)))
            .setData(new byte[] {0, 1, 2, 3})
            .build();

    ActionFileUpgradeUtil.upgradeAndDelete(
        tempFile,
        /* downloadIdProvider= */ null,
        downloadIndex,
        /* deleteOnFailure= */ true,
        /* addNewDownloadsAsCompleted= */ false);

    assertThat(tempFile.exists()).isFalse();
    assertDownloadIndexContainsRequest(expectedRequest1, Download.STATE_QUEUED);
    assertDownloadIndexContainsRequest(expectedRequest2, Download.STATE_QUEUED);
  }

  @Test
  public void upgradeAndDelete_hlsActionFile_createsDownloads() throws IOException {
    byte[] actionFileBytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(),
            "media/offline/action_file_for_download_index_upgrade_hls.exi");
    try (FileOutputStream output = new FileOutputStream(tempFile)) {
      output.write(actionFileBytes);
    }
    DownloadRequest expectedRequest1 =
        new DownloadRequest.Builder(
                /* id= */ "http://www.test.com/1/manifest.m3u8",
                Uri.parse("http://www.test.com/1/manifest.m3u8"))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build();
    DownloadRequest expectedRequest2 =
        new DownloadRequest.Builder(
                /* id= */ "http://www.test.com/2/manifest.m3u8",
                Uri.parse("http://www.test.com/2/manifest.m3u8"))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setStreamKeys(
                ImmutableList.of(
                    new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0),
                    new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1)))
            .setData(new byte[] {0, 1, 2, 3})
            .build();

    ActionFileUpgradeUtil.upgradeAndDelete(
        tempFile,
        /* downloadIdProvider= */ null,
        downloadIndex,
        /* deleteOnFailure= */ true,
        /* addNewDownloadsAsCompleted= */ false);

    assertThat(tempFile.exists()).isFalse();
    assertDownloadIndexContainsRequest(expectedRequest1, Download.STATE_QUEUED);
    assertDownloadIndexContainsRequest(expectedRequest2, Download.STATE_QUEUED);
  }

  @Test
  public void upgradeAndDelete_smoothStreamingActionFile_createsDownloads() throws IOException {
    byte[] actionFileBytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(),
            "media/offline/action_file_for_download_index_upgrade_ss.exi");
    try (FileOutputStream output = new FileOutputStream(tempFile)) {
      output.write(actionFileBytes);
    }
    DownloadRequest expectedRequest1 =
        new DownloadRequest.Builder(
                /* id= */ "http://www.test.com/1/video.ism/manifest",
                Uri.parse("http://www.test.com/1/video.ism/manifest"))
            .setMimeType(MimeTypes.APPLICATION_SS)
            .build();
    DownloadRequest expectedRequest2 =
        new DownloadRequest.Builder(
                /* id= */ "http://www.test.com/2/video.ism/manifest",
                Uri.parse("http://www.test.com/2/video.ism/manifest"))
            .setMimeType(MimeTypes.APPLICATION_SS)
            .setStreamKeys(
                ImmutableList.of(
                    new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0),
                    new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1)))
            .setData(new byte[] {0, 1, 2, 3})
            .build();

    ActionFileUpgradeUtil.upgradeAndDelete(
        tempFile,
        /* downloadIdProvider= */ null,
        downloadIndex,
        /* deleteOnFailure= */ true,
        /* addNewDownloadsAsCompleted= */ false);

    assertThat(tempFile.exists()).isFalse();
    assertDownloadIndexContainsRequest(expectedRequest1, Download.STATE_QUEUED);
    assertDownloadIndexContainsRequest(expectedRequest2, Download.STATE_QUEUED);
  }

  @Test
  public void mergeRequest_nonExistingDownload_createsNewDownload() throws IOException {
    DownloadRequest request =
        new DownloadRequest.Builder(/* id= */ "id", Uri.parse("https://www.test.com/download"))
            .setStreamKeys(
                ImmutableList.of(
                    new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2),
                    new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5)))
            .setKeySetId(new byte[] {1, 2, 3, 4})
            .setCustomCacheKey("key123")
            .setData(new byte[] {1, 2, 3, 4})
            .build();

    ActionFileUpgradeUtil.mergeRequest(
        request, downloadIndex, /* addNewDownloadAsCompleted= */ false, NOW_MS);

    assertDownloadIndexContainsRequest(request, Download.STATE_QUEUED);
  }

  @Test
  public void mergeRequest_existingDownload_createsMergedDownload() throws IOException {
    StreamKey streamKey1 =
        new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5);
    StreamKey streamKey2 =
        new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2);
    DownloadRequest request1 =
        new DownloadRequest.Builder(/* id= */ "id", Uri.parse("https://www.test.com/download1"))
            .setStreamKeys(ImmutableList.of(streamKey1))
            .setKeySetId(new byte[] {1, 2, 3, 4})
            .setCustomCacheKey("key123")
            .setData(new byte[] {1, 2, 3, 4})
            .build();
    DownloadRequest request2 =
        new DownloadRequest.Builder(/* id= */ "id", Uri.parse("https://www.test.com/download2"))
            .setMimeType(MimeTypes.APPLICATION_MP4)
            .setStreamKeys(ImmutableList.of(streamKey2))
            .setKeySetId(new byte[] {5, 4, 3, 2, 1})
            .setCustomCacheKey("key345")
            .setData(new byte[] {5, 4, 3, 2, 1})
            .build();

    ActionFileUpgradeUtil.mergeRequest(
        request1, downloadIndex, /* addNewDownloadAsCompleted= */ false, NOW_MS);
    ActionFileUpgradeUtil.mergeRequest(
        request2, downloadIndex, /* addNewDownloadAsCompleted= */ false, NOW_MS);
    Download download = downloadIndex.getDownload(request2.id);

    assertThat(download).isNotNull();
    assertThat(download.request.mimeType).isEqualTo(MimeTypes.APPLICATION_MP4);
    assertThat(download.request.customCacheKey).isEqualTo(request2.customCacheKey);
    assertThat(download.request.data).isEqualTo(request2.data);
    assertThat(download.request.uri).isEqualTo(request2.uri);
    assertThat(download.request.streamKeys).containsExactly(streamKey1, streamKey2);
    assertThat(download.request.keySetId).isEqualTo(request2.keySetId);
    assertThat(download.state).isEqualTo(Download.STATE_QUEUED);
  }

  @Test
  public void mergeRequest_addNewDownloadAsCompleted() throws IOException {
    StreamKey streamKey1 =
        new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5);
    StreamKey streamKey2 =
        new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2);
    DownloadRequest request1 =
        new DownloadRequest.Builder(/* id= */ "id1", Uri.parse("https://www.test.com/download1"))
            .setStreamKeys(ImmutableList.of(streamKey1))
            .setKeySetId(new byte[] {1, 2, 3, 4})
            .setCustomCacheKey("key123")
            .setData(new byte[] {1, 2, 3, 4})
            .build();
    DownloadRequest request2 =
        new DownloadRequest.Builder(/* id= */ "id2", Uri.parse("https://www.test.com/download2"))
            .setStreamKeys(ImmutableList.of(streamKey2))
            .setKeySetId(new byte[] {5, 4, 3, 2, 1})
            .setCustomCacheKey("key456")
            .setData(new byte[] {5, 4, 3, 2, 1})
            .build();
    ActionFileUpgradeUtil.mergeRequest(
        request1, downloadIndex, /* addNewDownloadAsCompleted= */ false, NOW_MS);

    // Merging existing download, keeps it queued.
    ActionFileUpgradeUtil.mergeRequest(
        request1, downloadIndex, /* addNewDownloadAsCompleted= */ true, NOW_MS);
    assertThat(downloadIndex.getDownload(request1.id).state).isEqualTo(Download.STATE_QUEUED);

    // New download is merged as completed.
    ActionFileUpgradeUtil.mergeRequest(
        request2, downloadIndex, /* addNewDownloadAsCompleted= */ true, NOW_MS);
    assertThat(downloadIndex.getDownload(request2.id).state).isEqualTo(Download.STATE_COMPLETED);
  }

  private void assertDownloadIndexContainsRequest(DownloadRequest request, int state)
      throws IOException {
    Download download = downloadIndex.getDownload(request.id);
    assertThat(download.request).isEqualTo(request);
    assertThat(download.state).isEqualTo(state);
  }
}
