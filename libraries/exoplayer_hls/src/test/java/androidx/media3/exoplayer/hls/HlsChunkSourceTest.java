/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.exoplayer.hls;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.test.utils.ExoPlayerTestRunner;
import androidx.media3.test.utils.FakeDataSource;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

/** Unit tests for {@link HlsChunkSource}. */
@RunWith(AndroidJUnit4.class)
public class HlsChunkSourceTest {

  private static final String PLAYLIST = "media/m3u8/media_playlist";
  private static final String PLAYLIST_INDEPENDENT_SEGMENTS =
      "media/m3u8/media_playlist_independent_segments";
  private static final String PLAYLIST_EMPTY = "media/m3u8/media_playlist_empty";
  private static final Uri PLAYLIST_URI = Uri.parse("http://example.com/");
  private static final long PLAYLIST_START_PERIOD_OFFSET_US = 8_000_000L;
  private static final Uri IFRAME_URI = Uri.parse("http://example.com/iframe");
  private static final Format IFRAME_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setAverageBitrate(30_000)
          .setWidth(1280)
          .setHeight(720)
          .setRoleFlags(C.ROLE_FLAG_TRICK_PLAY)
          .build();

  @Mock private HlsPlaylistTracker mockPlaylistTracker;

  @Before
  public void setup() throws IOException {
    mockPlaylistTracker = Mockito.mock(HlsPlaylistTracker.class);

    InputStream inputStream =
        TestUtil.getInputStream(
            ApplicationProvider.getApplicationContext(), PLAYLIST_INDEPENDENT_SEGMENTS);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);

    when(mockPlaylistTracker.isSnapshotValid(eq(PLAYLIST_URI))).thenReturn(true);
    // Mock that segments totalling PLAYLIST_START_PERIOD_OFFSET_US in duration have been removed
    // from the start of the playlist.
    when(mockPlaylistTracker.getInitialStartTimeUs())
        .thenReturn(playlist.startTimeUs - PLAYLIST_START_PERIOD_OFFSET_US);
  }

  @Test
  public void getAdjustedSeekPositionUs_previousSync() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.PREVIOUS_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(16_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_nextSync() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.NEXT_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(20_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_nextSyncAtEnd() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(24_000_000), SeekParameters.NEXT_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(24_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_closestSyncBefore() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.CLOSEST_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(16_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_closestSyncAfter() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(19_000_000), SeekParameters.CLOSEST_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(20_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_exact() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.EXACT);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(17_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_noIndependentSegments() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    InputStream inputStream =
        TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), PLAYLIST);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(100_000_000), SeekParameters.EXACT);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(100_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_emptyPlaylist() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    InputStream inputStream =
        TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), PLAYLIST_EMPTY);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(100_000_000), SeekParameters.EXACT);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(100_000_000);
  }

  @Test
  public void getNextChunk_chunkSourceWithDefaultCmcdConfiguration_setsCmcdLoggingHeaders()
      throws Exception {
    CmcdConfiguration.Factory cmcdConfigurationFactory = CmcdConfiguration.Factory.DEFAULT;
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();

    testChunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=800,tb=800,d=4000,ot=v",
            "CMCD-Request",
            "bl=0",
            "CMCD-Session",
            "cid=\"mediaId\",sid=\"" + cmcdConfiguration.sessionId + "\",sf=h,st=v");
  }

  @Test
  public void getNextChunk_chunkSourceWithCustomCmcdConfiguration_setsCmcdLoggingHeaders()
      throws Exception {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem -> {
          CmcdConfiguration.RequestConfig cmcdRequestConfig =
              new CmcdConfiguration.RequestConfig() {
                @Override
                public boolean isKeyAllowed(String key) {
                  return !key.equals(CmcdConfiguration.KEY_SESSION_ID);
                }

                @Override
                public int getRequestedMaximumThroughputKbps(int throughputKbps) {
                  return 5 * throughputKbps;
                }
              };

          return new CmcdConfiguration(
              /* sessionId= */ "sessionId",
              /* contentId= */ mediaItem.mediaId + "contentIdSuffix",
              cmcdRequestConfig);
        };
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();

    testChunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=800,tb=800,d=4000,ot=v",
            "CMCD-Request",
            "bl=0",
            "CMCD-Session",
            "cid=\"mediaIdcontentIdSuffix\",sf=h,st=v",
            "CMCD-Status",
            "rtp=4000");
  }

  @Test
  public void
      getNextChunk_chunkSourceWithCustomCmcdConfigurationAndCustomData_setsCmcdLoggingHeaders()
          throws Exception {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem -> {
          CmcdConfiguration.RequestConfig cmcdRequestConfig =
              new CmcdConfiguration.RequestConfig() {
                @Override
                public ImmutableMap<@CmcdConfiguration.HeaderKey String, String> getCustomData() {
                  return new ImmutableMap.Builder<@CmcdConfiguration.HeaderKey String, String>()
                      .put(CmcdConfiguration.KEY_CMCD_OBJECT, "key1=value1")
                      .put(CmcdConfiguration.KEY_CMCD_REQUEST, "key2=\"stringValue\"")
                      .put(CmcdConfiguration.KEY_CMCD_SESSION, "key3=1")
                      .put(CmcdConfiguration.KEY_CMCD_STATUS, "key4=5.0")
                      .buildOrThrow();
                }
              };

          return new CmcdConfiguration(
              /* sessionId= */ "sessionId", /* contentId= */ mediaItem.mediaId, cmcdRequestConfig);
        };
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();

    testChunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=800,tb=800,d=4000,ot=v,key1=value1",
            "CMCD-Request",
            "bl=0,key2=\"stringValue\"",
            "CMCD-Session",
            "cid=\"mediaId\",sid=\"" + cmcdConfiguration.sessionId + "\",sf=h,st=v,key3=1",
            "CMCD-Status",
            "key4=5.0");
  }

  private HlsChunkSource createHlsChunkSource(@Nullable CmcdConfiguration cmcdConfiguration) {
    return new HlsChunkSource(
        HlsExtractorFactory.DEFAULT,
        mockPlaylistTracker,
        new Uri[] {IFRAME_URI, PLAYLIST_URI},
        new Format[] {IFRAME_FORMAT, ExoPlayerTestRunner.VIDEO_FORMAT},
        new DefaultHlsDataSourceFactory(new FakeDataSource.Factory()),
        /* mediaTransferListener= */ null,
        new TimestampAdjusterProvider(),
        /* timestampAdjusterInitializationTimeoutMs= */ 0,
        /* muxedCaptionFormats= */ null,
        PlayerId.UNSET,
        cmcdConfiguration);
  }

  private static long playlistTimeToPeriodTimeUs(long playlistTimeUs) {
    return playlistTimeUs + PLAYLIST_START_PERIOD_OFFSET_US;
  }

  private static long periodTimeToPlaylistTimeUs(long periodTimeUs) {
    return periodTimeUs - PLAYLIST_START_PERIOD_OFFSET_US;
  }
}
