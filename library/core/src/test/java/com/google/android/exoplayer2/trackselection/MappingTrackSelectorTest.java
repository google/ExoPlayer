/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.trackselection;

import static com.google.android.exoplayer2.util.MimeTypes.AUDIO_AAC;
import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererCapabilities.AdaptiveSupport;
import com.google.android.exoplayer2.RendererCapabilities.Capabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MappingTrackSelector}. */
@RunWith(AndroidJUnit4.class)
public final class MappingTrackSelectorTest {

  private static final RendererCapabilities VIDEO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_VIDEO);
  private static final RendererCapabilities AUDIO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_AUDIO);
  private static final RendererCapabilities METADATA_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_METADATA);

  private static final TrackGroup VIDEO_TRACK_GROUP = buildTrackGroup(MimeTypes.VIDEO_H264);
  private static final TrackGroup AUDIO_TRACK_GROUP = buildTrackGroup(AUDIO_AAC);
  private static final TrackGroup METADATA_TRACK_GROUP = buildTrackGroup(MimeTypes.APPLICATION_ID3);

  private static final Timeline TIMELINE = new FakeTimeline();

  private static MediaPeriodId periodId;

  @BeforeClass
  public static void setUpBeforeClass() {
    periodId = new MediaPeriodId(TIMELINE.getUidOfPeriod(/* periodIndex= */ 0));
  }

  @Test
  public void selectTracks_audioAndVideo_sameOrderAsRenderers_mappedToCorrectRenderer()
      throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    RendererCapabilities[] rendererCapabilities =
        new RendererCapabilities[] {VIDEO_CAPABILITIES, AUDIO_CAPABILITIES};
    TrackGroupArray trackGroups = new TrackGroupArray(VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP);

    trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);

    trackSelector.assertMappedTrackGroups(/* rendererIndex= */ 0, VIDEO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(/* rendererIndex= */ 1, AUDIO_TRACK_GROUP);
  }

  @Test
  public void selectTracks_audioAndVideo_reverseOrderToRenderers_mappedToCorrectRenderer()
      throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    TrackGroupArray trackGroups = new TrackGroupArray(VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP);
    RendererCapabilities[] reverseOrderRendererCapabilities =
        new RendererCapabilities[] {AUDIO_CAPABILITIES, VIDEO_CAPABILITIES};

    trackSelector.selectTracks(reverseOrderRendererCapabilities, trackGroups, periodId, TIMELINE);

    trackSelector.assertMappedTrackGroups(/* rendererIndex= */ 0, AUDIO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(/* rendererIndex= */ 1, VIDEO_TRACK_GROUP);
  }

  @Test
  public void selectTracks_multipleVideoAndAudioTracks_mappedToSameRenderer()
      throws ExoPlaybackException {
    TrackGroup videoGroup0 = VIDEO_TRACK_GROUP.copyWithId("0");
    TrackGroup videoGroup1 = VIDEO_TRACK_GROUP.copyWithId("1");
    TrackGroup audioGroup0 = AUDIO_TRACK_GROUP.copyWithId("0");
    TrackGroup audioGroup1 = AUDIO_TRACK_GROUP.copyWithId("1");
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    TrackGroupArray trackGroups =
        new TrackGroupArray(videoGroup0, audioGroup0, audioGroup1, videoGroup1);
    RendererCapabilities[] rendererCapabilities =
        new RendererCapabilities[] {
          VIDEO_CAPABILITIES, AUDIO_CAPABILITIES, VIDEO_CAPABILITIES, AUDIO_CAPABILITIES
        };

    trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);

    trackSelector.assertMappedTrackGroups(0, videoGroup0, videoGroup1);
    trackSelector.assertMappedTrackGroups(1, audioGroup0, audioGroup1);
  }

  @Test
  public void selectTracks_multipleMetadataTracks_mappedToDifferentRenderers()
      throws ExoPlaybackException {
    TrackGroup metadataGroup0 = METADATA_TRACK_GROUP.copyWithId("0");
    TrackGroup metadataGroup1 = METADATA_TRACK_GROUP.copyWithId("1");
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    TrackGroupArray trackGroups =
        new TrackGroupArray(VIDEO_TRACK_GROUP, metadataGroup0, metadataGroup1);
    RendererCapabilities[] rendererCapabilities =
        new RendererCapabilities[] {
          VIDEO_CAPABILITIES, METADATA_CAPABILITIES, METADATA_CAPABILITIES
        };

    trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);

    trackSelector.assertMappedTrackGroups(0, VIDEO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(1, metadataGroup0);
    trackSelector.assertMappedTrackGroups(2, metadataGroup1);
  }

  private static TrackGroup buildTrackGroup(String sampleMimeType) {
    return new TrackGroup(new Format.Builder().setSampleMimeType(sampleMimeType).build());
  }

  /**
   * A {@link MappingTrackSelector} that stashes the {@link MappedTrackInfo} passed to {@link
   * #selectTracks(MappedTrackInfo, int[][][], int[], MediaPeriodId, Timeline)}.
   */
  private static final class FakeMappingTrackSelector extends MappingTrackSelector {

    private MappedTrackInfo lastMappedTrackInfo;

    @Override
    protected Pair<RendererConfiguration[], ExoTrackSelection[]> selectTracks(
        MappedTrackInfo mappedTrackInfo,
        @Capabilities int[][][] rendererFormatSupports,
        @AdaptiveSupport int[] rendererMixedMimeTypeAdaptationSupport,
        MediaPeriodId mediaPeriodId,
        Timeline timeline) {
      int rendererCount = mappedTrackInfo.getRendererCount();
      lastMappedTrackInfo = mappedTrackInfo;
      return Pair.create(
          new RendererConfiguration[rendererCount], new ExoTrackSelection[rendererCount]);
    }

    public void assertMappedTrackGroups(int rendererIndex, TrackGroup... expected) {
      TrackGroupArray rendererTrackGroupArray = lastMappedTrackInfo.getTrackGroups(rendererIndex);
      assertThat(rendererTrackGroupArray.length).isEqualTo(expected.length);
      for (int i = 0; i < expected.length; i++) {
        assertThat(rendererTrackGroupArray.get(i)).isEqualTo(expected[i]);
      }
    }
  }

  /**
   * A {@link RendererCapabilities} that advertises adaptive support for all tracks of a given type.
   */
  private static final class FakeRendererCapabilities implements RendererCapabilities {

    private final int trackType;

    public FakeRendererCapabilities(int trackType) {
      this.trackType = trackType;
    }

    @Override
    public String getName() {
      return "FakeRenderer(" + Util.getTrackTypeString(trackType) + ")";
    }

    @Override
    public int getTrackType() {
      return trackType;
    }

    @Override
    public @Capabilities int supportsFormat(Format format) throws ExoPlaybackException {
      return MimeTypes.getTrackType(format.sampleMimeType) == trackType
          ? RendererCapabilities.create(
              C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED)
          : RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }

    @Override
    public @AdaptiveSupport int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
      return ADAPTIVE_SEAMLESS;
    }
  }
}
