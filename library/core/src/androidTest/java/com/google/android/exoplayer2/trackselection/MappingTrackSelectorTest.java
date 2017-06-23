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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.MimeTypes;
import junit.framework.TestCase;

/**
 * Unit tests for {@link MappingTrackSelector}.
 */
public final class MappingTrackSelectorTest extends TestCase {

  private static final RendererCapabilities VIDEO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_VIDEO);
  private static final RendererCapabilities AUDIO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_AUDIO);
  private static final RendererCapabilities[] RENDERER_CAPABILITIES = new RendererCapabilities[] {
      VIDEO_CAPABILITIES, AUDIO_CAPABILITIES
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

  /**
   * Tests that the video and audio track groups are mapped onto the correct renderers.
   */
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
  public void testSelectTracks() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(TRACK_SELECTIONS);
    TrackSelectorResult result = trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS);
    assertEquals(TRACK_SELECTIONS[0], result.selections.get(0));
    assertEquals(TRACK_SELECTIONS[1], result.selections.get(1));
  }

  /**
   * Tests that a null override clears a track selection.
   */
  public void testSelectTracksWithNullOverride() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(TRACK_SELECTIONS);
    trackSelector.setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null);
    TrackSelectorResult result = trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS);
    assertNull(result.selections.get(0));
    assertEquals(TRACK_SELECTIONS[1], result.selections.get(1));
  }

  /**
   * Tests that a null override can be cleared.
   */
  public void testSelectTracksWithClearedNullOverride() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(TRACK_SELECTIONS);
    trackSelector.setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null);
    trackSelector.clearSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP));
    TrackSelectorResult result = trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS);
    assertEquals(TRACK_SELECTIONS[0], result.selections.get(0));
    assertEquals(TRACK_SELECTIONS[1], result.selections.get(1));
  }

  /**
   * Tests that an override is not applied for a different set of available track groups.
   */
  public void testSelectTracksWithNullOverrideForDifferentTracks() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector(TRACK_SELECTIONS);
    trackSelector.setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null);
    TrackSelectorResult result = trackSelector.selectTracks(RENDERER_CAPABILITIES,
        new TrackGroupArray(VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP, VIDEO_TRACK_GROUP));
    assertEquals(TRACK_SELECTIONS[0], result.selections.get(0));
    assertEquals(TRACK_SELECTIONS[1], result.selections.get(1));
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
      return result == null ? new TrackSelection[rendererCapabilities.length] : result;
    }

    public void assertMappedTrackGroups(int rendererIndex, TrackGroup... expected) {
      assertEquals(expected.length, lastRendererTrackGroupArrays[rendererIndex].length);
      for (int i = 0; i < expected.length; i++) {
        assertEquals(expected[i], lastRendererTrackGroupArrays[rendererIndex].get(i));
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
