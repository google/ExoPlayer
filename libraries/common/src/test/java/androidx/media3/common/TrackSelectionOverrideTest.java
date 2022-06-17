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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link TrackSelectionOverride}. */
@RunWith(AndroidJUnit4.class)
public final class TrackSelectionOverrideTest {

  @Test
  public void newTrackSelectionOverride_withOneTrack_selectsOneTrack() {
    TrackSelectionOverride trackSelectionOverride =
        new TrackSelectionOverride(newTrackGroupWithIds(1, 2), /* trackIndex= */ 1);

    assertThat(trackSelectionOverride.mediaTrackGroup).isEqualTo(newTrackGroupWithIds(1, 2));
    assertThat(trackSelectionOverride.trackIndices).containsExactly(1).inOrder();
  }

  @Test
  public void newTrackSelectionOverride_withTracks_selectsOnlySpecifiedTracks() {
    TrackSelectionOverride trackSelectionOverride =
        new TrackSelectionOverride(newTrackGroupWithIds(1, 2), ImmutableList.of(1));

    assertThat(trackSelectionOverride.mediaTrackGroup).isEqualTo(newTrackGroupWithIds(1, 2));
    assertThat(trackSelectionOverride.trackIndices).containsExactly(1);
  }

  @Test
  public void newTrackSelectionOverride_with0Tracks_selectsAllSpecifiedTracks() {
    TrackSelectionOverride trackSelectionOverride =
        new TrackSelectionOverride(newTrackGroupWithIds(1, 2), ImmutableList.of());

    assertThat(trackSelectionOverride.mediaTrackGroup).isEqualTo(newTrackGroupWithIds(1, 2));
    assertThat(trackSelectionOverride.trackIndices).isEmpty();
  }

  @Test
  public void newTrackSelectionOverride_withInvalidIndex_throws() {
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> new TrackSelectionOverride(newTrackGroupWithIds(1, 2), ImmutableList.of(2)));
  }

  private static TrackGroup newTrackGroupWithIds(int... ids) {
    return new TrackGroup(
        Arrays.stream(ids)
            .mapToObj(id -> new Format.Builder().setId(id).build())
            .toArray(Format[]::new));
  }
}
