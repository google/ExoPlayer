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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.util.MimeTypes.AUDIO_AAC;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_H264;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.TracksInfo.TrackGroupInfo;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TracksInfo}. */
@RunWith(AndroidJUnit4.class)
public class TracksInfoTest {

  @Test
  public void roundTripViaBundle_ofEmptyTracksInfo_yieldsEqualInstance() {
    TracksInfo before = TracksInfo.EMPTY;
    TracksInfo after = TracksInfo.CREATOR.fromBundle(before.toBundle());
    assertThat(after).isEqualTo(before);
  }

  @Test
  public void roundTripViaBundle_ofTracksInfo_yieldsEqualInstance() {
    TracksInfo before =
        new TracksInfo(
            ImmutableList.of(
                new TrackGroupInfo(
                    new TrackGroup(new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
                    /* adaptiveSupported= */ false,
                    new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
                    /* tracksSelected= */ new boolean[] {true}),
                new TrackGroupInfo(
                    new TrackGroup(
                        new Format.Builder().setSampleMimeType(VIDEO_H264).build(),
                        new Format.Builder().setSampleMimeType(VIDEO_H264).build()),
                    /* adaptiveSupported= */ true,
                    new int[] {C.FORMAT_UNSUPPORTED_DRM, C.FORMAT_UNSUPPORTED_TYPE},
                    /* tracksSelected= */ new boolean[] {false, true})));
    TracksInfo after = TracksInfo.CREATOR.fromBundle(before.toBundle());
    assertThat(after).isEqualTo(before);
  }

  @Test
  public void tracksInfoGetters_withoutTrack_returnExpectedValues() {
    TracksInfo tracksInfo = new TracksInfo(ImmutableList.of());

    assertThat(tracksInfo.containsType(C.TRACK_TYPE_AUDIO)).isFalse();
    assertThat(tracksInfo.isTypeSupported(C.TRACK_TYPE_AUDIO)).isFalse();
    assertThat(tracksInfo.isTypeSupported(C.TRACK_TYPE_AUDIO, /* allowExceedsCapabilities= */ true))
        .isFalse();
    assertThat(tracksInfo.isTypeSelected(C.TRACK_TYPE_AUDIO)).isFalse();
    ImmutableList<TrackGroupInfo> trackGroupInfos = tracksInfo.getTrackGroupInfos();
    assertThat(trackGroupInfos).isEmpty();
  }

  @Test
  public void tracksInfo_emptyStaticInstance_isEmpty() {
    TracksInfo tracksInfo = TracksInfo.EMPTY;

    assertThat(tracksInfo.getTrackGroupInfos()).isEmpty();
    assertThat(tracksInfo).isEqualTo(new TracksInfo(ImmutableList.of()));
  }

  @Test
  public void tracksInfoGetters_ofComplexTracksInfo_returnExpectedValues() {
    TrackGroupInfo trackGroupInfo0 =
        new TrackGroupInfo(
            new TrackGroup(new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
            /* adaptiveSupported= */ false,
            new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
            /* tracksSelected= */ new boolean[] {false});
    TrackGroupInfo trackGroupInfo1 =
        new TrackGroupInfo(
            new TrackGroup(
                new Format.Builder().setSampleMimeType(VIDEO_H264).build(),
                new Format.Builder().setSampleMimeType(VIDEO_H264).build()),
            /* adaptiveSupported= */ true,
            new int[] {C.FORMAT_UNSUPPORTED_DRM, C.FORMAT_HANDLED},
            /* tracksSelected= */ new boolean[] {false, true});
    TracksInfo tracksInfo = new TracksInfo(ImmutableList.of(trackGroupInfo0, trackGroupInfo1));

    assertThat(tracksInfo.containsType(C.TRACK_TYPE_AUDIO)).isTrue();
    assertThat(tracksInfo.containsType(C.TRACK_TYPE_VIDEO)).isTrue();
    assertThat(tracksInfo.containsType(C.TRACK_TYPE_TEXT)).isFalse();
    assertThat(tracksInfo.isTypeSupported(C.TRACK_TYPE_AUDIO)).isFalse();
    assertThat(tracksInfo.isTypeSupported(C.TRACK_TYPE_VIDEO)).isTrue();
    assertThat(tracksInfo.isTypeSupported(C.TRACK_TYPE_TEXT)).isFalse();
    assertThat(tracksInfo.isTypeSupported(C.TRACK_TYPE_AUDIO, /* allowExceedsCapabilities= */ true))
        .isTrue();
    assertThat(tracksInfo.isTypeSupported(C.TRACK_TYPE_VIDEO, /* allowExceedsCapabilities= */ true))
        .isTrue();
    assertThat(tracksInfo.isTypeSupported(C.TRACK_TYPE_TEXT, /* allowExceedsCapabilities= */ true))
        .isFalse();
    assertThat(tracksInfo.isTypeSelected(C.TRACK_TYPE_AUDIO)).isFalse();
    assertThat(tracksInfo.isTypeSelected(C.TRACK_TYPE_VIDEO)).isTrue();
    ImmutableList<TrackGroupInfo> trackGroupInfos = tracksInfo.getTrackGroupInfos();
    assertThat(trackGroupInfos).hasSize(2);
    assertThat(trackGroupInfos.get(0)).isSameInstanceAs(trackGroupInfo0);
    assertThat(trackGroupInfos.get(1)).isSameInstanceAs(trackGroupInfo1);
    assertThat(trackGroupInfos.get(0).isTrackSupported(0)).isFalse();
    assertThat(trackGroupInfos.get(1).isTrackSupported(0)).isFalse();
    assertThat(trackGroupInfos.get(1).isTrackSupported(1)).isTrue();
    assertThat(trackGroupInfos.get(0).getTrackSupport(0)).isEqualTo(C.FORMAT_EXCEEDS_CAPABILITIES);
    assertThat(trackGroupInfos.get(1).getTrackSupport(0)).isEqualTo(C.FORMAT_UNSUPPORTED_DRM);
    assertThat(trackGroupInfos.get(1).getTrackSupport(1)).isEqualTo(C.FORMAT_HANDLED);
    assertThat(trackGroupInfos.get(0).isTrackSelected(0)).isFalse();
    assertThat(trackGroupInfos.get(1).isTrackSelected(0)).isFalse();
    assertThat(trackGroupInfos.get(1).isTrackSelected(1)).isTrue();
    assertThat(trackGroupInfos.get(0).getTrackType()).isEqualTo(C.TRACK_TYPE_AUDIO);
    assertThat(trackGroupInfos.get(1).getTrackType()).isEqualTo(C.TRACK_TYPE_VIDEO);
  }

  /**
   * Tests that {@link TrackGroupInfo#isAdaptiveSupported} returns false if the group only contains
   * a single track, even if true is passed to the constructor.
   */
  @Test
  public void trackGroupInfo_withSingleTrack_isNotAdaptive() {
    TrackGroupInfo trackGroupInfo0 =
        new TrackGroupInfo(
            new TrackGroup(new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
            /* adaptiveSupported= */ true,
            new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
            /* tracksSelected= */ new boolean[] {false});
    assertThat(trackGroupInfo0.isAdaptiveSupported()).isFalse();
  }
}
