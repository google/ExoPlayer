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

import static com.google.android.exoplayer2.RendererConfiguration.DEFAULT;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link MappingTrackSelector}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public final class MappingTrackSelectorTest {

  private static final RendererCapabilities VIDEO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_VIDEO);
  private static final RendererCapabilities AUDIO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_AUDIO);
  private static final RendererCapabilities NO_SAMPLE_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_NONE);
  private static final RendererCapabilities[] RENDERER_CAPABILITIES = new RendererCapabilities[] {
      VIDEO_CAPABILITIES, AUDIO_CAPABILITIES
  };
  private static final RendererCapabilities[] RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER =
      new RendererCapabilities[] {
          VIDEO_CAPABILITIES, AUDIO_CAPABILITIES, NO_SAMPLE_CAPABILITIES
      };

  private static final TrackGroup VIDEO_TRACK_GROUP = new TrackGroup(
      Format.createVideoSampleFormat("video", MimeTypes.VIDEO_H264, null, Format.NO_VALUE,
          Format.NO_VALUE, 1024, 768, Format.NO_VALUE, null, null));
  private static final TrackGroup AUDIO_TRACK_GROUP = new TrackGroup(
      Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
          Format.NO_VALUE, 2, 44100, null, null, 0, null));
  private static final TrackGroupArray TRACK_GROUPS = new TrackGroupArray(
      VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP);

  private static final TrackSelection[] TRACK_SELECTIONS = new TrackSelection[] {
      new FixedTrackSelection(VIDEO_TRACK_GROUP, 0),
      new FixedTrackSelection(AUDIO_TRACK_GROUP, 0)
  };

  private static final TrackSelection[] TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER =
      new TrackSelection[] {
          new FixedTrackSelection(VIDEO_TRACK_GROUP, 0),
          new FixedTrackSelection(AUDIO_TRACK_GROUP, 0),
          null
      };

  /**
   * Tests that the video and audio track groups are mapped onto the correct renderers.
   */
  @Test
  public void testMapping() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS);
    trackSelector.assertMappedTrackGroups(0, VIDEO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(1, AUDIO_TRACK_GROUP);
  }

  /**
   * Tests that the video and audio track groups are mapped onto the correct renderers when the
   * renderer ordering is reversed.
   */
  @Test
  public void testMappingReverseOrder() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    RendererCapabilities[] reverseOrderRendererCapabilities = new RendererCapabilities[] {
        AUDIO_CAPABILITIES, VIDEO_CAPABILITIES};
    trackSelector.selectTracks(reverseOrderRendererCapabilities, TRACK_GROUPS);
    trackSelector.assertMappedTrackGroups(0, AUDIO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(1, VIDEO_TRACK_GROUP);
  }

  /**
   * Tests video and audio track groups are mapped onto the correct renderers when there are
   * multiple track groups of the same type.
   */
  @Test
  public void testMappingMulti() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    TrackGroupArray multiTrackGroups = new TrackGroupArray(VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP,
        VIDEO_TRACK_GROUP);
    trackSelector.selectTracks(RENDERER_CAPABILITIES, multiTrackGroups);
    trackSelector.assertMappedTrackGroups(0, VIDEO_TRACK_GROUP, VIDEO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(1, AUDIO_TRACK_GROUP);
  }

  /**
   * Tests the result of {@link MappingTrackSelector#selectTracks(RendererCapabilities[],
   * TrackGroupArray[], int[][][])} is propagated correctly to the result of
   * {@link MappingTrackSelector#selectTracks(RendererCapabilities[], TrackGroupArray)}.
   */
  @Test
  public void testSelectTracks() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(
        TRACK_SELECTIONS);
    TrackSelectorResult result = trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS);
    assertThat(result.selections.get(0)).isEqualTo(TRACK_SELECTIONS[0]);
    assertThat(result.selections.get(1)).isEqualTo(TRACK_SELECTIONS[1]);
    assertThat(new boolean[] {true, true}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests that a null override clears a track selection.
   */
  @Test
  public void testSelectTracksWithNullOverride() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(
        TRACK_SELECTIONS);
    trackSelector.setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null);
    TrackSelectorResult result = trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS);
    assertThat(result.selections.get(0)).isNull();
    assertThat(result.selections.get(1)).isEqualTo(TRACK_SELECTIONS[1]);
    assertThat(new boolean[] {false, true}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {null, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests that a null override can be cleared.
   */
  @Test
  public void testSelectTracksWithClearedNullOverride() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(
        TRACK_SELECTIONS);
    trackSelector.setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null);
    trackSelector.clearSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP));
    TrackSelectorResult result = trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS);
    assertThat(result.selections.get(0)).isEqualTo(TRACK_SELECTIONS[0]);
    assertThat(result.selections.get(1)).isEqualTo(TRACK_SELECTIONS[1]);
    assertThat(new boolean[] {true, true}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests that an override is not applied for a different set of available track groups.
   */
  @Test
  public void testSelectTracksWithNullOverrideForDifferentTracks() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(
        TRACK_SELECTIONS);
    trackSelector.setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null);
    TrackSelectorResult result = trackSelector.selectTracks(RENDERER_CAPABILITIES,
        new TrackGroupArray(VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP, VIDEO_TRACK_GROUP));
    assertThat(result.selections.get(0)).isEqualTo(TRACK_SELECTIONS[0]);
    assertThat(result.selections.get(1)).isEqualTo(TRACK_SELECTIONS[1]);
    assertThat(new boolean[] {true, true}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests the result of {@link MappingTrackSelector#selectTracks(RendererCapabilities[],
   * TrackGroupArray[], int[][][])} is propagated correctly to the result of
   * {@link MappingTrackSelector#selectTracks(RendererCapabilities[], TrackGroupArray)}
   * when there is no-sample renderer.
   */
  @Test
  public void testSelectTracksWithNoSampleRenderer() throws ExoPlaybackException {
    TrackSelection[] expectedTrackSelection = TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER;
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(expectedTrackSelection);
    TrackSelectorResult result = trackSelector.selectTracks(
        RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER, TRACK_GROUPS);
    assertThat(result.selections.get(0)).isEqualTo(expectedTrackSelection[0]);
    assertThat(result.selections.get(1)).isEqualTo(expectedTrackSelection[1]);
    assertThat(result.selections.get(2)).isNull();
    assertThat(new boolean[] {true, true, true}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {DEFAULT, DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests that a null override clears a track selection when there is no-sample renderer.
   */
  @Test
  public void testSelectTracksWithNoSampleRendererWithNullOverride() throws ExoPlaybackException {
    TrackSelection[] expectedTrackSelection = TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER;
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(expectedTrackSelection);
    trackSelector.setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null);
    TrackSelectorResult result = trackSelector.selectTracks(
        RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER, TRACK_GROUPS);
    assertThat(result.selections.get(0)).isNull();
    assertThat(result.selections.get(1)).isEqualTo(expectedTrackSelection[1]);
    assertThat(result.selections.get(2)).isNull();
    assertThat(new boolean[] {false, true, true}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {null, DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests that a null override can be cleared when there is no-sample renderer.
   */
  @Test
  public void testSelectTracksWithNoSampleRendererWithClearedNullOverride()
      throws ExoPlaybackException {
    TrackSelection[] expectedTrackSelection = TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER;
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(expectedTrackSelection);
    trackSelector.setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null);
    trackSelector.clearSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP));
    TrackSelectorResult result = trackSelector.selectTracks(
        RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER, TRACK_GROUPS);
    assertThat(result.selections.get(0)).isEqualTo(expectedTrackSelection[0]);
    assertThat(result.selections.get(1)).isEqualTo(expectedTrackSelection[1]);
    assertThat(result.selections.get(2)).isNull();
    assertThat(new boolean[] {true, true, true}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {DEFAULT, DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests that an override is not applied for a different set of available track groups
   * when there is no-sample renderer.
   */
  @Test
  public void testSelectTracksWithNoSampleRendererWithNullOverrideForDifferentTracks()
      throws ExoPlaybackException {
    TrackSelection[] expectedTrackSelection = TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER;
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(expectedTrackSelection);
    trackSelector.setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null);
    TrackSelectorResult result = trackSelector.selectTracks(
        RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER,
        new TrackGroupArray(VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP, VIDEO_TRACK_GROUP));
    assertThat(result.selections.get(0)).isEqualTo(expectedTrackSelection[0]);
    assertThat(result.selections.get(1)).isEqualTo(expectedTrackSelection[1]);
    assertThat(result.selections.get(2)).isNull();
    assertThat(new boolean[] {true, true, true}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {DEFAULT, DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests that disabling another renderer works when there is no-sample renderer.
   */
  @Test
  public void testSelectTracksDisablingNormalRendererWithNoSampleRenderer()
      throws ExoPlaybackException {
    TrackSelection[] expectedTrackSelection = TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER;
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(expectedTrackSelection);
    trackSelector.setRendererDisabled(0, true);
    TrackSelectorResult result = trackSelector.selectTracks(
        RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER, TRACK_GROUPS);
    assertThat(result.selections.get(0)).isNull();
    assertThat(result.selections.get(1)).isEqualTo(expectedTrackSelection[1]);
    assertThat(result.selections.get(2)).isNull();
    assertThat(new boolean[] {false, true, true}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {null, DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests that disabling no-sample renderer work.
   */
  @Test
  public void testSelectTracksDisablingNoSampleRenderer()
      throws ExoPlaybackException {
    TrackSelection[] expectedTrackSelection = TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER;
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(expectedTrackSelection);
    trackSelector.setRendererDisabled(2, true);
    TrackSelectorResult result = trackSelector.selectTracks(
        RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER, TRACK_GROUPS);
    assertThat(result.selections.get(0)).isEqualTo(expectedTrackSelection[0]);
    assertThat(result.selections.get(1)).isEqualTo(expectedTrackSelection[1]);
    assertThat(result.selections.get(2)).isNull();
    assertThat(new boolean[] {true, true, false}).isEqualTo(result.renderersEnabled);
    assertThat(new RendererConfiguration[] {DEFAULT, DEFAULT, null})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * A {@link MappingTrackSelector} that returns a fixed result from
   * {@link #selectTracks(RendererCapabilities[], TrackGroupArray[], int[][][])}.
   */
  private static final class FakeMappingTrackSelector extends MappingTrackSelector {

    private final TrackSelection[] result;
    private TrackGroupArray[] lastRendererTrackGroupArrays;

    public FakeMappingTrackSelector(TrackSelection... result) {
      this.result = result.length == 0 ? null : result;
    }

    @Override
    protected TrackSelection[] selectTracks(RendererCapabilities[] rendererCapabilities,
        TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports)
        throws ExoPlaybackException {
      lastRendererTrackGroupArrays = rendererTrackGroupArrays;
      TrackSelection[] trackSelectionResult = new TrackSelection[rendererCapabilities.length];
      return result == null ? trackSelectionResult
          // return a copy of the provided result, because MappingTrackSelector
          // might modify the returned array here, and we don't want that to affect
          // the original array.
          : Arrays.asList(result).toArray(trackSelectionResult);
    }

    public void assertMappedTrackGroups(int rendererIndex, TrackGroup... expected) {
      assertThat(lastRendererTrackGroupArrays[rendererIndex].length).isEqualTo(expected.length);
      for (int i = 0; i < expected.length; i++) {
        assertThat(lastRendererTrackGroupArrays[rendererIndex].get(i)).isEqualTo(expected[i]);
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
    public int getTrackType() {
      return trackType;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
      return MimeTypes.getTrackType(format.sampleMimeType) == trackType
          ? (FORMAT_HANDLED | ADAPTIVE_SEAMLESS) : FORMAT_UNSUPPORTED_TYPE;
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
      return ADAPTIVE_SEAMLESS;
    }

  }

}
