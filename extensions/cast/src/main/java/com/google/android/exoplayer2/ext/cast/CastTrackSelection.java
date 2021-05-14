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
package com.google.android.exoplayer2.ext.cast;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.util.Assertions;

/**
 * {@link TrackSelection} that only selects the first track of the provided {@link TrackGroup}.
 *
 * <p>This relies on {@link CastPlayer} track groups only having one track.
 */
/* package */ class CastTrackSelection implements TrackSelection {

  private final TrackGroup trackGroup;

  /** @param trackGroup The {@link TrackGroup} from which the first track will only be selected. */
  public CastTrackSelection(TrackGroup trackGroup) {
    this.trackGroup = trackGroup;
  }

  @Override
  public int getType() {
    return TYPE_UNSET;
  }

  @Override
  public TrackGroup getTrackGroup() {
    return trackGroup;
  }

  @Override
  public int length() {
    return 1;
  }

  @Override
  public Format getFormat(int index) {
    Assertions.checkArgument(index == 0);
    return trackGroup.getFormat(0);
  }

  @Override
  public int getIndexInTrackGroup(int index) {
    return index == 0 ? 0 : C.INDEX_UNSET;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public int indexOf(Format format) {
    return format == trackGroup.getFormat(0) ? 0 : C.INDEX_UNSET;
  }

  @Override
  public int indexOf(int indexInTrackGroup) {
    return indexInTrackGroup == 0 ? 0 : C.INDEX_UNSET;
  }

  // Object overrides.

  @Override
  public int hashCode() {
    return System.identityHashCode(trackGroup);
  }

  // Track groups are compared by identity not value, as distinct groups may have the same value.
  @Override
  @SuppressWarnings({"ReferenceEquality", "EqualsGetClass"})
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    CastTrackSelection other = (CastTrackSelection) obj;
    return trackGroup == other.trackGroup;
  }
}
