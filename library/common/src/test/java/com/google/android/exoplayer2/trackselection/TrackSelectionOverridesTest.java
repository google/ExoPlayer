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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverrides.TrackSelectionOverride;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link TrackSelectionOverrides}. */
@RunWith(AndroidJUnit4.class)
public final class TrackSelectionOverridesTest {

  public static final TrackGroup AAC_TRACK_GROUP =
      new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build());

  private static TrackGroup newTrackGroupWithIds(int... ids) {
    return new TrackGroup(
        Arrays.stream(ids)
            .mapToObj(id -> new Format.Builder().setId(id).build())
            .toArray(Format[]::new));
  }

  @Test
  public void newTrackSelectionOverride_withJustTrackGroup_selectsAllTracks() {
    TrackSelectionOverride trackSelectionOverride =
        new TrackSelectionOverride(newTrackGroupWithIds(1, 2));

    assertThat(trackSelectionOverride.trackGroup).isEqualTo(newTrackGroupWithIds(1, 2));
    assertThat(trackSelectionOverride.trackIndices).containsExactly(0, 1).inOrder();
  }

  @Test
  public void newTrackSelectionOverride_withTracks_selectsOnlySpecifiedTracks() {
    TrackSelectionOverride trackSelectionOverride =
        new TrackSelectionOverride(newTrackGroupWithIds(1, 2), ImmutableList.of(1));

    assertThat(trackSelectionOverride.trackGroup).isEqualTo(newTrackGroupWithIds(1, 2));
    assertThat(trackSelectionOverride.trackIndices).containsExactly(1);
  }

  @Test
  public void newTrackSelectionOverride_with0Tracks_selectsAllSpecifiedTracks() {
    TrackSelectionOverride trackSelectionOverride =
        new TrackSelectionOverride(newTrackGroupWithIds(1, 2), ImmutableList.of());

    assertThat(trackSelectionOverride.trackGroup).isEqualTo(newTrackGroupWithIds(1, 2));
    assertThat(trackSelectionOverride.trackIndices).isEmpty();
  }

  @Test
  public void newTrackSelectionOverride_withInvalidIndex_throws() {
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> new TrackSelectionOverride(newTrackGroupWithIds(1, 2), ImmutableList.of(2)));
  }

  @Test
  public void roundTripViaBundle_withOverrides_yieldsEqualInstance() {
    TrackSelectionOverrides trackSelectionOverrides =
        new TrackSelectionOverrides.Builder()
            .setOverrideForType(
                new TrackSelectionOverride(newTrackGroupWithIds(3, 4), ImmutableList.of(1)))
            .addOverride(new TrackSelectionOverride(newTrackGroupWithIds(5, 6)))
            .build();

    TrackSelectionOverrides fromBundle =
        TrackSelectionOverrides.CREATOR.fromBundle(trackSelectionOverrides.toBundle());

    assertThat(fromBundle).isEqualTo(trackSelectionOverrides);
    assertThat(fromBundle.asList()).isEqualTo(trackSelectionOverrides.asList());
  }

  @Test
  public void builder_byDefault_isEmpty() {
    TrackSelectionOverrides trackSelectionOverrides = new TrackSelectionOverrides.Builder().build();

    assertThat(trackSelectionOverrides.asList()).isEmpty();
    assertThat(trackSelectionOverrides).isEqualTo(TrackSelectionOverrides.EMPTY);
  }

  @Test
  public void addOverride_onDifferentGroups_addsOverride() {
    TrackSelectionOverride override1 = new TrackSelectionOverride(newTrackGroupWithIds(1));
    TrackSelectionOverride override2 = new TrackSelectionOverride(newTrackGroupWithIds(2));

    TrackSelectionOverrides trackSelectionOverrides =
        new TrackSelectionOverrides.Builder().addOverride(override1).addOverride(override2).build();

    assertThat(trackSelectionOverrides.asList()).containsExactly(override1, override2);
    assertThat(trackSelectionOverrides.getOverride(override1.trackGroup)).isEqualTo(override1);
    assertThat(trackSelectionOverrides.getOverride(override2.trackGroup)).isEqualTo(override2);
  }

  @Test
  public void addOverride_onSameGroup_replacesOverride() {
    TrackGroup trackGroup = newTrackGroupWithIds(1, 2, 3);
    TrackSelectionOverride override1 =
        new TrackSelectionOverride(trackGroup, /* trackIndices= */ ImmutableList.of(0));
    TrackSelectionOverride override2 =
        new TrackSelectionOverride(trackGroup, /* trackIndices= */ ImmutableList.of(1));

    TrackSelectionOverrides trackSelectionOverrides =
        new TrackSelectionOverrides.Builder().addOverride(override1).addOverride(override2).build();

    assertThat(trackSelectionOverrides.asList()).containsExactly(override2);
    assertThat(trackSelectionOverrides.getOverride(override2.trackGroup)).isEqualTo(override2);
  }

  @Test
  public void setOverrideForType_onSameType_replacesOverride() {
    TrackSelectionOverride override1 = new TrackSelectionOverride(newTrackGroupWithIds(1));
    TrackSelectionOverride override2 = new TrackSelectionOverride(newTrackGroupWithIds(2));

    TrackSelectionOverrides trackSelectionOverrides =
        new TrackSelectionOverrides.Builder()
            .setOverrideForType(override1)
            .setOverrideForType(override2)
            .build();

    assertThat(trackSelectionOverrides.asList()).containsExactly(override2);
    assertThat(trackSelectionOverrides.getOverride(override2.trackGroup)).isEqualTo(override2);
  }

  @Test
  public void clearOverridesOfType_ofTypeAudio_removesAudioOverride() {
    TrackSelectionOverride override1 = new TrackSelectionOverride(AAC_TRACK_GROUP);
    TrackSelectionOverride override2 = new TrackSelectionOverride(newTrackGroupWithIds(1));
    TrackSelectionOverrides trackSelectionOverrides =
        new TrackSelectionOverrides.Builder()
            .addOverride(override1)
            .addOverride(override2)
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .build();

    assertThat(trackSelectionOverrides.asList()).containsExactly(override2);
    assertThat(trackSelectionOverrides.getOverride(override2.trackGroup)).isEqualTo(override2);
  }

  @Test
  public void clearOverride_ofTypeGroup_removesOverride() {
    TrackSelectionOverride override1 = new TrackSelectionOverride(AAC_TRACK_GROUP);
    TrackSelectionOverride override2 = new TrackSelectionOverride(newTrackGroupWithIds(1));
    TrackSelectionOverrides trackSelectionOverrides =
        new TrackSelectionOverrides.Builder()
            .addOverride(override1)
            .addOverride(override2)
            .clearOverride(override2.trackGroup)
            .build();

    assertThat(trackSelectionOverrides.asList()).containsExactly(override1);
    assertThat(trackSelectionOverrides.getOverride(override1.trackGroup)).isEqualTo(override1);
  }
}
