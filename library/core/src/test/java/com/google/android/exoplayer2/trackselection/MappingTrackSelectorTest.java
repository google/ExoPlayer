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
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.TracksInfo.TrackGroupInfo;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
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
  private static final TrackGroup AUDIO_TRACK_GROUP = buildTrackGroup(MimeTypes.AUDIO_AAC);
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
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    TrackGroupArray trackGroups =
        new TrackGroupArray(
            VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP, AUDIO_TRACK_GROUP, VIDEO_TRACK_GROUP);
    RendererCapabilities[] rendererCapabilities =
        new RendererCapabilities[] {
          VIDEO_CAPABILITIES, AUDIO_CAPABILITIES, VIDEO_CAPABILITIES, AUDIO_CAPABILITIES
        };

    trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);

    trackSelector.assertMappedTrackGroups(0, VIDEO_TRACK_GROUP, VIDEO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(1, AUDIO_TRACK_GROUP, AUDIO_TRACK_GROUP);
  }

  @Test
  public void selectTracks_multipleMetadataTracks_mappedToDifferentRenderers()
      throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    TrackGroupArray trackGroups =
        new TrackGroupArray(VIDEO_TRACK_GROUP, METADATA_TRACK_GROUP, METADATA_TRACK_GROUP);
    RendererCapabilities[] rendererCapabilities =
        new RendererCapabilities[] {
          VIDEO_CAPABILITIES, METADATA_CAPABILITIES, METADATA_CAPABILITIES
        };

    trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);

    trackSelector.assertMappedTrackGroups(0, VIDEO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(1, METADATA_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(2, METADATA_TRACK_GROUP);
  }

  private static TrackGroup buildTrackGroup(String sampleMimeType) {
    return new TrackGroup(new Format.Builder().setSampleMimeType(sampleMimeType).build());
  }

  @Test
  public void buildTrackInfos_withTestValues_isAsExpected() {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
        new MappingTrackSelector.MappedTrackInfo(
            new String[] {"1", "2"},
            new int[] {C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO},
            new TrackGroupArray[] {
              new TrackGroupArray(
                  new TrackGroup(new Format.Builder().build()),
                  new TrackGroup(new Format.Builder().build())),
              new TrackGroupArray(
                  new TrackGroup(new Format.Builder().build(), new Format.Builder().build()))
            },
            new int[] {
              RendererCapabilities.ADAPTIVE_SEAMLESS, RendererCapabilities.ADAPTIVE_NOT_SUPPORTED
            },
            new int[][][] {
              new int[][] {new int[] {C.FORMAT_HANDLED}, new int[] {C.FORMAT_UNSUPPORTED_SUBTYPE}},
              new int[][] {new int[] {C.FORMAT_UNSUPPORTED_DRM, C.FORMAT_EXCEEDS_CAPABILITIES}}
            },
            new TrackGroupArray(new TrackGroup(new Format.Builder().build())));
    TrackSelection[] selections =
        new TrackSelection[] {
          new FixedTrackSelection(mappedTrackInfo.getTrackGroups(0).get(1), 0),
          new FixedTrackSelection(mappedTrackInfo.getTrackGroups(1).get(0), 1)
        };

    TracksInfo tracksInfo = MappingTrackSelector.buildTracksInfo(selections, mappedTrackInfo);

    ImmutableList<TrackGroupInfo> trackGroupInfos = tracksInfo.getTrackGroupInfos();
    assertThat(trackGroupInfos).hasSize(4);
    assertThat(trackGroupInfos.get(0).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getTrackGroups(0).get(0));
    assertThat(trackGroupInfos.get(1).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getTrackGroups(0).get(1));
    assertThat(trackGroupInfos.get(2).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getTrackGroups(1).get(0));
    assertThat(trackGroupInfos.get(3).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getUnmappedTrackGroups().get(0));
    assertThat(trackGroupInfos.get(0).getTrackSupport(0)).isEqualTo(C.FORMAT_HANDLED);
    assertThat(trackGroupInfos.get(1).getTrackSupport(0)).isEqualTo(C.FORMAT_UNSUPPORTED_SUBTYPE);
    assertThat(trackGroupInfos.get(2).getTrackSupport(0)).isEqualTo(C.FORMAT_UNSUPPORTED_DRM);
    assertThat(trackGroupInfos.get(2).getTrackSupport(1)).isEqualTo(C.FORMAT_EXCEEDS_CAPABILITIES);
    assertThat(trackGroupInfos.get(3).getTrackSupport(0)).isEqualTo(C.FORMAT_UNSUPPORTED_TYPE);
    assertThat(trackGroupInfos.get(0).isTrackSelected(0)).isFalse();
    assertThat(trackGroupInfos.get(1).isTrackSelected(0)).isTrue();
    assertThat(trackGroupInfos.get(2).isTrackSelected(0)).isFalse();
    assertThat(trackGroupInfos.get(2).isTrackSelected(1)).isTrue();
    assertThat(trackGroupInfos.get(3).isTrackSelected(0)).isFalse();
    assertThat(trackGroupInfos.get(0).getTrackType()).isEqualTo(C.TRACK_TYPE_AUDIO);
    assertThat(trackGroupInfos.get(1).getTrackType()).isEqualTo(C.TRACK_TYPE_AUDIO);
    assertThat(trackGroupInfos.get(2).getTrackType()).isEqualTo(C.TRACK_TYPE_VIDEO);
    assertThat(trackGroupInfos.get(3).getTrackType()).isEqualTo(C.TRACK_TYPE_UNKNOWN);
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
        @AdaptiveSupport int[] rendererMixedMimeTypeAdaptationSupports,
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
    @Capabilities
    public int supportsFormat(Format format) throws ExoPlaybackException {
      return MimeTypes.getTrackType(format.sampleMimeType) == trackType
          ? RendererCapabilities.create(
              C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED)
          : RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }

    @Override
    @AdaptiveSupport
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
      return ADAPTIVE_SEAMLESS;
    }
  }
}
