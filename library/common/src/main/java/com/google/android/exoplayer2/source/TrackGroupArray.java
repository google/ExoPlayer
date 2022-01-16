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
package com.google.android.exoplayer2.source;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.BundleableUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/** An immutable array of {@link TrackGroup}s. */
public final class TrackGroupArray implements Bundleable {

  private static final String TAG = "TrackGroupArray";

  /** The empty array. */
  public static final TrackGroupArray EMPTY = new TrackGroupArray();

  /** The number of groups in the array. Greater than or equal to zero. */
  public final int length;

  private final ImmutableList<TrackGroup> trackGroups;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * Construct a {@code TrackGroupArray} from an array of {@link TrackGroup TrackGroups}.
   *
   * <p>The groups must not contain duplicates.
   */
  public TrackGroupArray(TrackGroup... trackGroups) {
    this.trackGroups = ImmutableList.copyOf(trackGroups);
    this.length = trackGroups.length;
    verifyCorrectness();
  }

  /**
   * Returns the group at a given index.
   *
   * @param index The index of the group.
   * @return The group.
   */
  public TrackGroup get(int index) {
    return trackGroups.get(index);
  }

  /**
   * Returns the index of a group within the array.
   *
   * @param group The group.
   * @return The index of the group, or {@link C#INDEX_UNSET} if no such group exists.
   */
  public int indexOf(TrackGroup group) {
    int index = trackGroups.indexOf(group);
    return index >= 0 ? index : C.INDEX_UNSET;
  }

  /** Returns whether this track group array is empty. */
  public boolean isEmpty() {
    return length == 0;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = trackGroups.hashCode();
    }
    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackGroupArray other = (TrackGroupArray) obj;
    return length == other.length && trackGroups.equals(other.trackGroups);
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FIELD_TRACK_GROUPS,
  })
  private @interface FieldNumber {}

  private static final int FIELD_TRACK_GROUPS = 0;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(
        keyForField(FIELD_TRACK_GROUPS), BundleableUtil.toBundleArrayList(trackGroups));
    return bundle;
  }

  /** Object that can restores a TrackGroupArray from a {@link Bundle}. */
  public static final Creator<TrackGroupArray> CREATOR =
      bundle -> {
        List<TrackGroup> trackGroups =
            BundleableUtil.fromBundleNullableList(
                TrackGroup.CREATOR,
                bundle.getParcelableArrayList(keyForField(FIELD_TRACK_GROUPS)),
                /* defaultValue= */ ImmutableList.of());
        return new TrackGroupArray(trackGroups.toArray(new TrackGroup[0]));
      };

  private void verifyCorrectness() {
    for (int i = 0; i < trackGroups.size(); i++) {
      for (int j = i + 1; j < trackGroups.size(); j++) {
        if (trackGroups.get(i).equals(trackGroups.get(j))) {
          Log.e(
              TAG,
              "",
              new IllegalArgumentException(
                  "Multiple identical TrackGroups added to one TrackGroupArray."));
        }
      }
    }
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
