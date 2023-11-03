/*
 * Copyright (C) 2021 The Android Open Source Project
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
package androidx.media3.common;

import static androidx.media3.common.MimeTypes.AUDIO_AAC;
import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Tracks}. */
@RunWith(AndroidJUnit4.class)
public class TracksTest {

  @Test
  public void roundTripViaBundle_ofEmptyTracks_yieldsEqualInstance() {
    Tracks before = Tracks.EMPTY;
    Tracks after = Tracks.fromBundle(before.toBundle());
    assertThat(after).isEqualTo(before);
  }

  @Test
  public void roundTripViaBundle_ofTracks_yieldsEqualInstance() {
    Tracks before =
        new Tracks(
            ImmutableList.of(
                new Tracks.Group(
                    new TrackGroup(new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
                    /* adaptiveSupported= */ false,
                    new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
                    /* trackSelected= */ new boolean[] {true}),
                new Tracks.Group(
                    new TrackGroup(
                        new Format.Builder().setSampleMimeType(VIDEO_H264).build(),
                        new Format.Builder().setSampleMimeType(VIDEO_H264).build()),
                    /* adaptiveSupported= */ true,
                    new int[] {C.FORMAT_UNSUPPORTED_DRM, C.FORMAT_UNSUPPORTED_TYPE},
                    /* trackSelected= */ new boolean[] {false, true})));
    Tracks after = Tracks.fromBundle(before.toBundle());
    assertThat(after).isEqualTo(before);
  }

  @Test
  public void getters_withoutTrack_returnExpectedValues() {
    Tracks tracks = new Tracks(ImmutableList.of());

    assertThat(tracks.containsType(C.TRACK_TYPE_AUDIO)).isFalse();
    assertThat(tracks.isTypeSupported(C.TRACK_TYPE_AUDIO)).isFalse();
    assertThat(tracks.isTypeSupported(C.TRACK_TYPE_AUDIO, /* allowExceedsCapabilities= */ true))
        .isFalse();
    assertThat(tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)).isFalse();
    ImmutableList<Tracks.Group> trackGroups = tracks.getGroups();
    assertThat(trackGroups).isEmpty();
  }

  @Test
  public void emptyStaticInstance_isEmpty() {
    Tracks tracks = Tracks.EMPTY;

    assertThat(tracks.getGroups()).isEmpty();
    assertThat(tracks).isEqualTo(new Tracks(ImmutableList.of()));
  }

  @Test
  public void getters_ofComplexTracks_returnExpectedValues() {
    Tracks.Group trackGroup0 =
        new Tracks.Group(
            new TrackGroup(new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
            /* adaptiveSupported= */ false,
            new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
            /* trackSelected= */ new boolean[] {false});
    Tracks.Group trackGroup1 =
        new Tracks.Group(
            new TrackGroup(
                new Format.Builder().setSampleMimeType(VIDEO_H264).build(),
                new Format.Builder().setSampleMimeType(VIDEO_H264).build()),
            /* adaptiveSupported= */ true,
            new int[] {C.FORMAT_UNSUPPORTED_DRM, C.FORMAT_HANDLED},
            /* trackSelected= */ new boolean[] {false, true});
    Tracks tracks = new Tracks(ImmutableList.of(trackGroup0, trackGroup1));

    assertThat(tracks.containsType(C.TRACK_TYPE_AUDIO)).isTrue();
    assertThat(tracks.containsType(C.TRACK_TYPE_VIDEO)).isTrue();
    assertThat(tracks.containsType(C.TRACK_TYPE_TEXT)).isFalse();
    assertThat(tracks.isTypeSupported(C.TRACK_TYPE_AUDIO)).isFalse();
    assertThat(tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)).isTrue();
    assertThat(tracks.isTypeSupported(C.TRACK_TYPE_TEXT)).isFalse();
    assertThat(tracks.isTypeSupported(C.TRACK_TYPE_AUDIO, /* allowExceedsCapabilities= */ true))
        .isTrue();
    assertThat(tracks.isTypeSupported(C.TRACK_TYPE_VIDEO, /* allowExceedsCapabilities= */ true))
        .isTrue();
    assertThat(tracks.isTypeSupported(C.TRACK_TYPE_TEXT, /* allowExceedsCapabilities= */ true))
        .isFalse();
    assertThat(tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)).isFalse();
    assertThat(tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)).isTrue();
    ImmutableList<Tracks.Group> trackGroups = tracks.getGroups();
    assertThat(trackGroups).hasSize(2);
    assertThat(trackGroups.get(0)).isSameInstanceAs(trackGroup0);
    assertThat(trackGroups.get(1)).isSameInstanceAs(trackGroup1);
    assertThat(trackGroups.get(0).isTrackSupported(0)).isFalse();
    assertThat(trackGroups.get(1).isTrackSupported(0)).isFalse();
    assertThat(trackGroups.get(1).isTrackSupported(1)).isTrue();
    assertThat(trackGroups.get(0).getTrackSupport(0)).isEqualTo(C.FORMAT_EXCEEDS_CAPABILITIES);
    assertThat(trackGroups.get(1).getTrackSupport(0)).isEqualTo(C.FORMAT_UNSUPPORTED_DRM);
    assertThat(trackGroups.get(1).getTrackSupport(1)).isEqualTo(C.FORMAT_HANDLED);
    assertThat(trackGroups.get(0).isTrackSelected(0)).isFalse();
    assertThat(trackGroups.get(1).isTrackSelected(0)).isFalse();
    assertThat(trackGroups.get(1).isTrackSelected(1)).isTrue();
    assertThat(trackGroups.get(0).getType()).isEqualTo(C.TRACK_TYPE_AUDIO);
    assertThat(trackGroups.get(1).getType()).isEqualTo(C.TRACK_TYPE_VIDEO);
  }

  /**
   * Tests that {@link Tracks.Group#isAdaptiveSupported} returns false if the group only contains a
   * single track, even if true is passed to the constructor.
   */
  @Test
  public void groupWithSingleTrack_isNotAdaptive() {
    Tracks.Group trackGroup =
        new Tracks.Group(
            new TrackGroup(new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
            /* adaptiveSupported= */ true,
            new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
            /* trackSelected= */ new boolean[] {false});
    assertThat(trackGroup.isAdaptiveSupported()).isFalse();
  }
}
