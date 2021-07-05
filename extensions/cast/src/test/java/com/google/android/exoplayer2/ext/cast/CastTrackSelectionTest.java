/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link CastTrackSelection}. */
@RunWith(AndroidJUnit4.class)
public class CastTrackSelectionTest {

  private static final TrackGroup TRACK_GROUP =
      new TrackGroup(new Format.Builder().build(), new Format.Builder().build());

  private static final CastTrackSelection SELECTION = new CastTrackSelection(TRACK_GROUP);

  @Test
  public void length_isOne() {
    assertThat(SELECTION.length()).isEqualTo(1);
  }

  @Test
  public void getTrackGroup_returnsSameGroup() {
    assertThat(SELECTION.getTrackGroup()).isSameInstanceAs(TRACK_GROUP);
  }

  @Test
  public void getFormatSelectedTrack_isFirstTrack() {
    assertThat(SELECTION.getFormat(0)).isSameInstanceAs(TRACK_GROUP.getFormat(0));
  }

  @Test
  public void getIndexInTrackGroup_ofSelectedTrack_returnsFirstTrack() {
    assertThat(SELECTION.getIndexInTrackGroup(0)).isEqualTo(0);
  }

  @Test
  public void getIndexInTrackGroup_onePastTheEnd_returnsIndexUnset() {
    assertThat(SELECTION.getIndexInTrackGroup(1)).isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void indexOf_selectedTrack_returnsFirstTrack() {
    assertThat(SELECTION.indexOf(0)).isEqualTo(0);
  }

  @Test
  public void indexOf_onePastTheEnd_returnsIndexUnset() {
    assertThat(SELECTION.indexOf(1)).isEqualTo(C.INDEX_UNSET);
  }

  @Test(expected = Exception.class)
  public void getFormat_outOfBound_throws() {
    CastTrackSelection selection = new CastTrackSelection(TRACK_GROUP);

    selection.getFormat(1);
  }
}
