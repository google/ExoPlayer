/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.dash;

import static com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy.DEFAULT_LOCATION_EXCLUSION_MS;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.BundledChunkExtractor;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.upstream.CmcdConfiguration;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowSystemClock;

/** Unit test for {@link DefaultDashChunkSource}. */
@RunWith(AndroidJUnit4.class)
public class DefaultDashChunkSourceTest {

  private static final String SAMPLE_MPD_LIVE_WITH_OFFSET_INSIDE_WINDOW =
      "media/mpd/sample_mpd_live_with_offset_inside_window";
  private static final String SAMPLE_MPD_VOD = "media/mpd/sample_mpd_vod";
  private static final String SAMPLE_MPD_VOD_LOCATION_FALLBACK =
      "media/mpd/sample_mpd_vod_location_fallback";

  @Test
  public void getNextChunk_forLowLatencyManifest_setsCorrectMayNotLoadAtFullNetworkSpeedFlag()
      throws Exception {
    long nowMs = 2_000_000_000_000L;
    SystemClock.setCurrentTimeMillis(nowMs);
    DashManifest manifest =
        new DashManifestParser()
            .parse(
                Uri.parse("https://example.com/test.mpd"),
                TestUtil.getInputStream(
                    ApplicationProvider.getApplicationContext(),
                    SAMPLE_MPD_LIVE_WITH_OFFSET_INSIDE_WINDOW));
    DefaultDashChunkSource chunkSource =
        new DefaultDashChunkSource(
            BundledChunkExtractor.FACTORY,
            new LoaderErrorThrower.Placeholder(),
            manifest,
            new BaseUrlExclusionList(),
            /* periodIndex= */ 0,
            /* adaptationSetIndices= */ new int[] {0},
            new FixedTrackSelection(new TrackGroup(new Format.Builder().build()), /* track= */ 0),
            C.TRACK_TYPE_VIDEO,
            new FakeDataSource(),
            /* elapsedRealtimeOffsetMs= */ 0,
            /* maxSegmentsPerLoad= */ 1,
            /* enableEventMessageTrack= */ false,
            /* closedCaptionFormats= */ ImmutableList.of(),
            /* playerTrackEmsgHandler= */ null,
            PlayerId.UNSET,
            /* cmcdConfiguration= */ null);

    long nowInPeriodUs = Util.msToUs(nowMs - manifest.availabilityStartTimeMs);
    ChunkHolder output = new ChunkHolder();

    chunkSource.getNextChunk(
        /* playbackPositionUs= */ nowInPeriodUs - 5 * C.MICROS_PER_SECOND,
        /* loadPositionUs= */ nowInPeriodUs - 5 * C.MICROS_PER_SECOND,
        /* queue= */ ImmutableList.of(),
        output);
    assertThat(output.chunk.dataSpec.flags & DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED)
        .isEqualTo(0);

    chunkSource.getNextChunk(
        /* playbackPositionUs= */ nowInPeriodUs,
        /* loadPositionUs= */ nowInPeriodUs,
        /* queue= */ ImmutableList.of(),
        output);
    assertThat(output.chunk.dataSpec.flags & DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED)
        .isNotEqualTo(0);
  }

  @Test
  public void getNextChunk_forVodManifest_doesNotSetMayNotLoadAtFullNetworkSpeedFlag()
      throws Exception {
    long nowMs = 2_000_000_000_000L;
    SystemClock.setCurrentTimeMillis(nowMs);
    DashManifest manifest =
        new DashManifestParser()
            .parse(
                Uri.parse("https://example.com/test.mpd"),
                TestUtil.getInputStream(
                    ApplicationProvider.getApplicationContext(), SAMPLE_MPD_VOD));
    DefaultDashChunkSource chunkSource =
        new DefaultDashChunkSource(
            BundledChunkExtractor.FACTORY,
            new LoaderErrorThrower.Placeholder(),
            manifest,
            new BaseUrlExclusionList(),
            /* periodIndex= */ 0,
            /* adaptationSetIndices= */ new int[] {0},
            new FixedTrackSelection(new TrackGroup(new Format.Builder().build()), /* track= */ 0),
            C.TRACK_TYPE_VIDEO,
            new FakeDataSource(),
            /* elapsedRealtimeOffsetMs= */ 0,
            /* maxSegmentsPerLoad= */ 1,
            /* enableEventMessageTrack= */ false,
            /* closedCaptionFormats= */ ImmutableList.of(),
            /* playerTrackEmsgHandler= */ null,
            PlayerId.UNSET,
            /* cmcdConfiguration= */ null);

    ChunkHolder output = new ChunkHolder();
    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);

    assertThat(output.chunk.dataSpec.flags & DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED)
        .isEqualTo(0);
  }

  @Test
  public void getNextChunk_onChunkLoadErrorLocationExclusionEnabled_correctFallbackBehavior()
      throws Exception {
    DefaultLoadErrorHandlingPolicy loadErrorHandlingPolicy =
        new DefaultLoadErrorHandlingPolicy() {
          @Override
          public FallbackSelection getFallbackSelectionFor(
              FallbackOptions fallbackOptions, LoadErrorInfo loadErrorInfo) {
            return new FallbackSelection(FALLBACK_TYPE_LOCATION, DEFAULT_LOCATION_EXCLUSION_MS);
          }
        };
    List<Chunk> chunks = new ArrayList<>();
    DashChunkSource chunkSource =
        createDashChunkSource(/* numberOfTracks= */ 1, /* cmcdConfiguration= */ null);
    ChunkHolder output = new ChunkHolder();

    boolean requestReplacementChunk = true;
    while (requestReplacementChunk) {
      chunkSource.getNextChunk(
          /* playbackPositionUs= */ 0,
          /* loadPositionUs= */ 0,
          /* queue= */ ImmutableList.of(),
          output);
      chunks.add(output.chunk);
      requestReplacementChunk =
          chunkSource.onChunkLoadError(
              checkNotNull(output.chunk),
              /* cancelable= */ true,
              createFakeLoadErrorInfo(
                  output.chunk.dataSpec, /* httpResponseCode= */ 404, /* errorCount= */ 1),
              loadErrorHandlingPolicy);
    }

    assertThat(Lists.transform(chunks, (chunk) -> chunk.dataSpec.uri.toString()))
        .containsExactly(
            "http://video.com/baseUrl/a/video/video_0_1300000.m4s",
            "http://video.com/baseUrl/b/video/video_0_1300000.m4s",
            "http://video.com/baseUrl/d/video/video_0_1300000.m4s")
        .inOrder();

    // Assert expiration of exclusions.
    ShadowSystemClock.advanceBy(Duration.ofMillis(DEFAULT_LOCATION_EXCLUSION_MS));
    chunkSource.onChunkLoadError(
        checkNotNull(output.chunk),
        /* cancelable= */ true,
        createFakeLoadErrorInfo(
            output.chunk.dataSpec, /* httpResponseCode= */ 404, /* errorCount= */ 1),
        loadErrorHandlingPolicy);
    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);
    assertThat(output.chunk.dataSpec.uri.toString())
        .isEqualTo("http://video.com/baseUrl/a/video/video_0_1300000.m4s");
  }

  @Test
  public void getNextChunk_onChunkLoadErrorTrackExclusionEnabled_correctFallbackBehavior()
      throws Exception {
    DefaultLoadErrorHandlingPolicy loadErrorHandlingPolicy =
        new DefaultLoadErrorHandlingPolicy() {
          @Override
          public FallbackSelection getFallbackSelectionFor(
              FallbackOptions fallbackOptions, LoadErrorInfo loadErrorInfo) {
            // Exclude tracks only.
            return new FallbackSelection(
                FALLBACK_TYPE_TRACK, DefaultLoadErrorHandlingPolicy.DEFAULT_TRACK_EXCLUSION_MS);
          }
        };
    DashChunkSource chunkSource =
        createDashChunkSource(/* numberOfTracks= */ 4, /* cmcdConfiguration= */ null);
    ChunkHolder output = new ChunkHolder();
    List<Chunk> chunks = new ArrayList<>();
    boolean requestReplacementChunk = true;
    while (requestReplacementChunk) {
      chunkSource.getNextChunk(
          /* playbackPositionUs= */ 0,
          /* loadPositionUs= */ 0,
          /* queue= */ ImmutableList.of(),
          output);
      chunks.add(output.chunk);
      requestReplacementChunk =
          chunkSource.onChunkLoadError(
              checkNotNull(output.chunk),
              /* cancelable= */ true,
              createFakeLoadErrorInfo(
                  output.chunk.dataSpec, /* httpResponseCode= */ 404, /* errorCount= */ 1),
              loadErrorHandlingPolicy);
    }
    assertThat(Lists.transform(chunks, (chunk) -> chunk.dataSpec.uri.toString()))
        .containsExactly(
            "http://video.com/baseUrl/a/video/video_0_700000.m4s",
            "http://video.com/baseUrl/a/video/video_0_452000.m4s",
            "http://video.com/baseUrl/a/video/video_0_250000.m4s",
            "http://video.com/baseUrl/a/video/video_0_1300000.m4s")
        .inOrder();
  }

  @Test
  public void getNextChunk_onChunkLoadErrorExclusionDisabled_neverRequestReplacementChunk()
      throws Exception {
    DefaultLoadErrorHandlingPolicy loadErrorHandlingPolicy =
        new DefaultLoadErrorHandlingPolicy() {
          @Override
          @Nullable
          public FallbackSelection getFallbackSelectionFor(
              FallbackOptions fallbackOptions, LoadErrorInfo loadErrorInfo) {
            // Never exclude, neither tracks nor locations.
            return null;
          }
        };
    DashChunkSource chunkSource =
        createDashChunkSource(/* numberOfTracks= */ 2, /* cmcdConfiguration= */ null);
    ChunkHolder output = new ChunkHolder();
    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);

    boolean requestReplacementChunk =
        chunkSource.onChunkLoadError(
            checkNotNull(output.chunk),
            /* cancelable= */ true,
            createFakeLoadErrorInfo(
                output.chunk.dataSpec, /* httpResponseCode= */ 404, /* errorCount= */ 1),
            loadErrorHandlingPolicy);

    assertThat(requestReplacementChunk).isFalse();
  }

  @Test
  public void getNextChunk_chunkSourceWithDefaultCmcdConfiguration_setsCmcdLoggingHeaders()
      throws Exception {
    CmcdConfiguration.Factory cmcdConfigurationFactory = CmcdConfiguration.Factory.DEFAULT;
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    DashChunkSource chunkSource = createDashChunkSource(/* numberOfTracks= */ 2, cmcdConfiguration);
    ChunkHolder output = new ChunkHolder();

    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=700,tb=1300,d=4000,ot=v",
            "CMCD-Request",
            "bl=0,mtp=1000",
            "CMCD-Session",
            "cid=\"mediaId\",sid=\"" + cmcdConfiguration.sessionId + "\",sf=d,st=v");
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
    DashChunkSource chunkSource = createDashChunkSource(/* numberOfTracks= */ 2, cmcdConfiguration);
    ChunkHolder output = new ChunkHolder();

    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=700,tb=1300,d=4000,ot=v",
            "CMCD-Request",
            "bl=0,mtp=1000",
            "CMCD-Session",
            "cid=\"mediaIdcontentIdSuffix\",sf=d,st=v",
            "CMCD-Status",
            "rtp=3500");
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
    DashChunkSource chunkSource = createDashChunkSource(/* numberOfTracks= */ 2, cmcdConfiguration);
    ChunkHolder output = new ChunkHolder();

    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=700,tb=1300,d=4000,ot=v,key1=value1",
            "CMCD-Request",
            "bl=0,mtp=1000,key2=\"stringValue\"",
            "CMCD-Session",
            "cid=\"mediaId\",sid=\"" + cmcdConfiguration.sessionId + "\",sf=d,st=v,key3=1",
            "CMCD-Status",
            "key4=5.0");
  }

  @Test
  public void
      getNextChunk_afterLastAvailableButBeforeEndOfLiveManifestWithKnownDuration_doesNotReturnEndOfStream()
          throws Exception {
    DashManifest manifest =
        new DashManifestParser()
            .parse(
                Uri.parse("https://example.com/test.mpd"),
                TestUtil.getInputStream(
                    ApplicationProvider.getApplicationContext(),
                    "media/mpd/sample_mpd_live_known_duration_not_ended"));
    DefaultDashChunkSource chunkSource =
        new DefaultDashChunkSource(
            BundledChunkExtractor.FACTORY,
            new LoaderErrorThrower.Placeholder(),
            manifest,
            new BaseUrlExclusionList(),
            /* periodIndex= */ 0,
            /* adaptationSetIndices= */ new int[] {0},
            new FixedTrackSelection(new TrackGroup(new Format.Builder().build()), /* track= */ 0),
            C.TRACK_TYPE_VIDEO,
            new FakeDataSource(),
            /* elapsedRealtimeOffsetMs= */ 0,
            /* maxSegmentsPerLoad= */ 1,
            /* enableEventMessageTrack= */ false,
            /* closedCaptionFormats= */ ImmutableList.of(),
            /* playerTrackEmsgHandler= */ null,
            PlayerId.UNSET,
            /* cmcdConfiguration= */ null);
    ChunkHolder output = new ChunkHolder();
    // Populate with last available media chunk
    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);
    Chunk previousChunk = output.chunk;
    output.clear();

    // Request another chunk
    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 4_000_000,
        /* queue= */ ImmutableList.of((MediaChunk) previousChunk),
        output);

    assertThat(output.endOfStream).isFalse();
    assertThat(output.chunk).isNull();
  }

  @Test
  public void getNextChunk_atEndOfLiveManifestWithKnownDuration_returnsEndOfStream()
      throws Exception {
    DashManifest manifest =
        new DashManifestParser()
            .parse(
                Uri.parse("https://example.com/test.mpd"),
                TestUtil.getInputStream(
                    ApplicationProvider.getApplicationContext(),
                    "media/mpd/sample_mpd_live_known_duration_ended"));
    DefaultDashChunkSource chunkSource =
        new DefaultDashChunkSource(
            BundledChunkExtractor.FACTORY,
            new LoaderErrorThrower.Placeholder(),
            manifest,
            new BaseUrlExclusionList(),
            /* periodIndex= */ 0,
            /* adaptationSetIndices= */ new int[] {0},
            new FixedTrackSelection(new TrackGroup(new Format.Builder().build()), /* track= */ 0),
            C.TRACK_TYPE_VIDEO,
            new FakeDataSource(),
            /* elapsedRealtimeOffsetMs= */ 0,
            /* maxSegmentsPerLoad= */ 1,
            /* enableEventMessageTrack= */ false,
            /* closedCaptionFormats= */ ImmutableList.of(),
            /* playerTrackEmsgHandler= */ null,
            PlayerId.UNSET,
            /* cmcdConfiguration= */ null);
    ChunkHolder output = new ChunkHolder();
    // Populate with last media chunk
    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 4_000_000,
        /* queue= */ ImmutableList.of(),
        output);
    Chunk previousChunk = output.chunk;
    output.clear();

    // Request next chunk
    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 8_000_000,
        /* queue= */ ImmutableList.of((MediaChunk) previousChunk),
        output);

    assertThat(output.endOfStream).isTrue();
  }

  private DashChunkSource createDashChunkSource(
      int numberOfTracks, @Nullable CmcdConfiguration cmcdConfiguration) throws IOException {
    Assertions.checkArgument(numberOfTracks < 6);
    DashManifest manifest =
        new DashManifestParser()
            .parse(
                Uri.parse("https://example.com/test.mpd"),
                TestUtil.getInputStream(
                    ApplicationProvider.getApplicationContext(), SAMPLE_MPD_VOD_LOCATION_FALLBACK));
    int[] adaptationSetIndices = new int[] {0};
    int[] selectedTracks = new int[numberOfTracks];
    Format[] formats = new Format[numberOfTracks];
    for (int i = 0; i < numberOfTracks; i++) {
      selectedTracks[i] = i;
      formats[i] =
          manifest
              .getPeriod(0)
              .adaptationSets
              .get(adaptationSetIndices[0])
              .representations
              .get(i)
              .format;
    }
    AdaptiveTrackSelection adaptiveTrackSelection =
        new AdaptiveTrackSelection(
            new TrackGroup(formats),
            selectedTracks,
            new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build());
    return new DefaultDashChunkSource(
        BundledChunkExtractor.FACTORY,
        new LoaderErrorThrower.Placeholder(),
        manifest,
        new BaseUrlExclusionList(new Random(/* seed= */ 1234)),
        /* periodIndex= */ 0,
        /* adaptationSetIndices= */ adaptationSetIndices,
        adaptiveTrackSelection,
        C.TRACK_TYPE_VIDEO,
        new FakeDataSource(),
        /* elapsedRealtimeOffsetMs= */ 0,
        /* maxSegmentsPerLoad= */ 1,
        /* enableEventMessageTrack= */ false,
        /* closedCaptionFormats= */ ImmutableList.of(),
        /* playerTrackEmsgHandler= */ null,
        PlayerId.UNSET,
        cmcdConfiguration);
  }

  private LoadErrorHandlingPolicy.LoadErrorInfo createFakeLoadErrorInfo(
      DataSpec dataSpec, int httpResponseCode, int errorCount) {
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(/* loadTaskId= */ 0, dataSpec, SystemClock.elapsedRealtime());
    MediaLoadData mediaLoadData = new MediaLoadData(C.DATA_TYPE_MEDIA);
    HttpDataSource.InvalidResponseCodeException invalidResponseCodeException =
        new HttpDataSource.InvalidResponseCodeException(
            httpResponseCode,
            /* responseMessage= */ null,
            /* cause= */ null,
            ImmutableMap.of(),
            dataSpec,
            new byte[0]);
    return new LoadErrorHandlingPolicy.LoadErrorInfo(
        loadEventInfo, mediaLoadData, invalidResponseCodeException, errorCount);
  }
}
