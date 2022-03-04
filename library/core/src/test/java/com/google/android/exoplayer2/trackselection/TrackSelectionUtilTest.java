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
package com.google.android.exoplayer2.trackselection;

import static com.google.android.exoplayer2.C.FORMAT_EXCEEDS_CAPABILITIES;
import static com.google.android.exoplayer2.C.FORMAT_HANDLED;
import static com.google.android.exoplayer2.C.FORMAT_UNSUPPORTED_DRM;
import static com.google.android.exoplayer2.C.FORMAT_UNSUPPORTED_SUBTYPE;
import static com.google.android.exoplayer2.C.FORMAT_UNSUPPORTED_TYPE;
import static com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO;
import static com.google.android.exoplayer2.C.TRACK_TYPE_UNKNOWN;
import static com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO;
import static com.google.android.exoplayer2.RendererCapabilities.ADAPTIVE_NOT_SUPPORTED;
import static com.google.android.exoplayer2.RendererCapabilities.ADAPTIVE_SEAMLESS;
import static com.google.android.exoplayer2.util.MimeTypes.AUDIO_AAC;
import static com.google.android.exoplayer2.util.MimeTypes.AUDIO_OPUS;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_H264;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.TracksInfo.TrackGroupInfo;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link TrackSelectionUtil}. */
@RunWith(AndroidJUnit4.class)
public class TrackSelectionUtilTest {

  @Test
  public void buildTrackInfos_withTestValues_isAsExpected() {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
        new MappingTrackSelector.MappedTrackInfo(
            new String[] {"rendererName1", "rendererName2"},
            new int[] {TRACK_TYPE_AUDIO, TRACK_TYPE_VIDEO},
            new TrackGroupArray[] {
              new TrackGroupArray(
                  new TrackGroup("0", new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
                  new TrackGroup("1", new Format.Builder().setSampleMimeType(AUDIO_OPUS).build())),
              new TrackGroupArray(
                  new TrackGroup(
                      "2",
                      new Format.Builder().setSampleMimeType(VIDEO_H264).build(),
                      new Format.Builder().setSampleMimeType(VIDEO_H264).build()))
            },
            new int[] {ADAPTIVE_SEAMLESS, ADAPTIVE_NOT_SUPPORTED},
            new int[][][] {
              new int[][] {new int[] {FORMAT_HANDLED}, new int[] {FORMAT_UNSUPPORTED_SUBTYPE}},
              new int[][] {new int[] {FORMAT_UNSUPPORTED_DRM, FORMAT_EXCEEDS_CAPABILITIES}}
            },
            new TrackGroupArray(new TrackGroup(new Format.Builder().build())));
    TrackSelection[] selections =
        new TrackSelection[] {
          new FixedTrackSelection(mappedTrackInfo.getTrackGroups(0).get(1), 0),
          new FixedTrackSelection(mappedTrackInfo.getTrackGroups(1).get(0), 1)
        };

    TracksInfo tracksInfo = TrackSelectionUtil.buildTracksInfo(mappedTrackInfo, selections);

    ImmutableList<TracksInfo.TrackGroupInfo> trackGroupInfos = tracksInfo.getTrackGroupInfos();
    assertThat(trackGroupInfos).hasSize(4);
    assertThat(trackGroupInfos.get(0).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getTrackGroups(0).get(0));
    assertThat(trackGroupInfos.get(1).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getTrackGroups(0).get(1));
    assertThat(trackGroupInfos.get(2).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getTrackGroups(1).get(0));
    assertThat(trackGroupInfos.get(3).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getUnmappedTrackGroups().get(0));
    assertThat(trackGroupInfos.get(0).getTrackSupport(0)).isEqualTo(FORMAT_HANDLED);
    assertThat(trackGroupInfos.get(1).getTrackSupport(0)).isEqualTo(FORMAT_UNSUPPORTED_SUBTYPE);
    assertThat(trackGroupInfos.get(2).getTrackSupport(0)).isEqualTo(FORMAT_UNSUPPORTED_DRM);
    assertThat(trackGroupInfos.get(2).getTrackSupport(1)).isEqualTo(FORMAT_EXCEEDS_CAPABILITIES);
    assertThat(trackGroupInfos.get(3).getTrackSupport(0)).isEqualTo(FORMAT_UNSUPPORTED_TYPE);
    assertThat(trackGroupInfos.get(0).isTrackSelected(0)).isFalse();
    assertThat(trackGroupInfos.get(1).isTrackSelected(0)).isTrue();
    assertThat(trackGroupInfos.get(2).isTrackSelected(0)).isFalse();
    assertThat(trackGroupInfos.get(2).isTrackSelected(1)).isTrue();
    assertThat(trackGroupInfos.get(3).isTrackSelected(0)).isFalse();
    assertThat(trackGroupInfos.get(0).getTrackType()).isEqualTo(TRACK_TYPE_AUDIO);
    assertThat(trackGroupInfos.get(1).getTrackType()).isEqualTo(TRACK_TYPE_AUDIO);
    assertThat(trackGroupInfos.get(2).getTrackType()).isEqualTo(TRACK_TYPE_VIDEO);
    assertThat(trackGroupInfos.get(3).getTrackType()).isEqualTo(TRACK_TYPE_UNKNOWN);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"}) // Initialization of array of Lists.
  public void buildTrackInfos_withMultipleSelectionForRenderer_isAsExpected() {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
        new MappingTrackSelector.MappedTrackInfo(
            new String[] {"rendererName1", "rendererName2"},
            new int[] {TRACK_TYPE_AUDIO, TRACK_TYPE_VIDEO},
            new TrackGroupArray[] {
              new TrackGroupArray(
                  new TrackGroup("0", new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
                  new TrackGroup(
                      "1",
                      new Format.Builder().setSampleMimeType(AUDIO_OPUS).setSampleRate(1).build(),
                      new Format.Builder().setSampleMimeType(AUDIO_OPUS).setSampleRate(2).build())),
              new TrackGroupArray()
            },
            new int[] {ADAPTIVE_SEAMLESS, ADAPTIVE_SEAMLESS},
            new int[][][] {
              new int[][] {new int[] {FORMAT_HANDLED}, new int[] {FORMAT_HANDLED, FORMAT_HANDLED}},
              new int[][] {new int[0]}
            },
            new TrackGroupArray());

    List<TrackSelection>[] selections =
        new List[] {
          ImmutableList.of(
              new FixedTrackSelection(mappedTrackInfo.getTrackGroups(0).get(0), 0),
              new FixedTrackSelection(mappedTrackInfo.getTrackGroups(0).get(1), 1)),
          ImmutableList.of()
        };

    TracksInfo tracksInfo = TrackSelectionUtil.buildTracksInfo(mappedTrackInfo, selections);

    ImmutableList<TrackGroupInfo> trackGroupInfos = tracksInfo.getTrackGroupInfos();
    assertThat(trackGroupInfos).hasSize(2);
    assertThat(trackGroupInfos.get(0).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getTrackGroups(0).get(0));
    assertThat(trackGroupInfos.get(1).getTrackGroup())
        .isEqualTo(mappedTrackInfo.getTrackGroups(0).get(1));
    assertThat(trackGroupInfos.get(0).getTrackSupport(0)).isEqualTo(FORMAT_HANDLED);
    assertThat(trackGroupInfos.get(1).getTrackSupport(0)).isEqualTo(FORMAT_HANDLED);
    assertThat(trackGroupInfos.get(1).getTrackSupport(1)).isEqualTo(FORMAT_HANDLED);
    assertThat(trackGroupInfos.get(0).isTrackSelected(0)).isTrue();
    assertThat(trackGroupInfos.get(1).isTrackSelected(0)).isFalse();
    assertThat(trackGroupInfos.get(1).isTrackSelected(1)).isTrue();
    assertThat(trackGroupInfos.get(0).getTrackType()).isEqualTo(TRACK_TYPE_AUDIO);
    assertThat(trackGroupInfos.get(1).getTrackType()).isEqualTo(TRACK_TYPE_AUDIO);
  }
}
