/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.text;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.util.BundleableUtil;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/** Class to represent the state of active {@link Cue Cues} at a particular time. */
public final class CueGroup implements Bundleable {

  /** Empty {@link CueGroup}. */
  public static final CueGroup EMPTY = new CueGroup(ImmutableList.of());

  /**
   * The cues in this group.
   *
   * <p>This list is in ascending order of priority. If any of the cue boxes overlap when displayed,
   * the {@link Cue} nearer the end of the list should be shown on top.
   *
   * <p>This list may be empty if the group represents a state with no cues.
   */
  public final ImmutableList<Cue> cues;

  /** Creates a CueGroup. */
  public CueGroup(List<Cue> cues) {
    this.cues = ImmutableList.copyOf(cues);
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({FIELD_CUES})
  private @interface FieldNumber {}

  private static final int FIELD_CUES = 0;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(
        keyForField(FIELD_CUES), BundleableUtil.toBundleArrayList(filterOutBitmapCues(cues)));
    return bundle;
  }

  public static final Creator<CueGroup> CREATOR = CueGroup::fromBundle;

  private static final CueGroup fromBundle(Bundle bundle) {
    @Nullable ArrayList<Bundle> cueBundles = bundle.getParcelableArrayList(keyForField(FIELD_CUES));
    List<Cue> cues =
        cueBundles == null
            ? ImmutableList.of()
            : BundleableUtil.fromBundleList(Cue.CREATOR, cueBundles);
    return new CueGroup(cues);
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }

  /**
   * Filters out {@link Cue} objects containing {@link Bitmap}. It is used when transferring cues
   * between processes to prevent transferring too much data.
   */
  private static ImmutableList<Cue> filterOutBitmapCues(List<Cue> cues) {
    ImmutableList.Builder<Cue> builder = ImmutableList.builder();
    for (int i = 0; i < cues.size(); i++) {
      if (cues.get(i).bitmap != null) {
        continue;
      }
      builder.add(cues.get(i));
    }
    return builder.build();
  }
}
