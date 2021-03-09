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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.BundledChunkExtractor;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultDashChunkSource}. */
@RunWith(AndroidJUnit4.class)
public class DefaultDashChunkSourceTest {

  private static final String SAMPLE_MPD_LIVE_WITH_OFFSET_INSIDE_WINDOW =
      "media/mpd/sample_mpd_live_with_offset_inside_window";
  private static final String SAMPLE_MPD_VOD = "media/mpd/sample_mpd_vod";

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
            new LoaderErrorThrower.Dummy(),
            manifest,
            /* periodIndex= */ 0,
            /* adaptationSetIndices= */ new int[] {0},
            new FixedTrackSelection(new TrackGroup(new Format.Builder().build()), /* track= */ 0),
            C.TRACK_TYPE_VIDEO,
            new FakeDataSource(),
            /* elapsedRealtimeOffsetMs= */ 0,
            /* maxSegmentsPerLoad= */ 1,
            /* enableEventMessageTrack= */ false,
            /* closedCaptionFormats */ ImmutableList.of(),
            /* playerTrackEmsgHandler= */ null);

    long nowInPeriodUs = C.msToUs(nowMs - manifest.availabilityStartTimeMs);
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
            new LoaderErrorThrower.Dummy(),
            manifest,
            /* periodIndex= */ 0,
            /* adaptationSetIndices= */ new int[] {0},
            new FixedTrackSelection(new TrackGroup(new Format.Builder().build()), /* track= */ 0),
            C.TRACK_TYPE_VIDEO,
            new FakeDataSource(),
            /* elapsedRealtimeOffsetMs= */ 0,
            /* maxSegmentsPerLoad= */ 1,
            /* enableEventMessageTrack= */ false,
            /* closedCaptionFormats */ ImmutableList.of(),
            /* playerTrackEmsgHandler= */ null);

    ChunkHolder output = new ChunkHolder();
    chunkSource.getNextChunk(
        /* playbackPositionUs= */ 0,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        output);

    assertThat(output.chunk.dataSpec.flags & DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED)
        .isEqualTo(0);
  }
}
