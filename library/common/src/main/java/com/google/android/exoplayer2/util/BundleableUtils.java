/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/** Utilities for {@link Bundleable}. */
public final class BundleableUtils {

  /**
   * Converts a {@link Bundleable} to a {@link Bundle}. It's a convenience wrapper of {@link
   * Bundleable#toBundle} that can take nullable values.
   */
  @Nullable
  public static Bundle toNullableBundle(@Nullable Bundleable bundleable) {
    return bundleable == null ? null : bundleable.toBundle();
  }

  /**
   * Converts a {@link Bundle} to a {@link Bundleable}. It's a convenience wrapper of {@link
   * Bundleable.Creator#fromBundle} that can take nullable values.
   */
  @Nullable
  public static <T extends Bundleable> T fromNullableBundle(
      Bundleable.Creator<T> creator, @Nullable Bundle bundle) {
    return bundle == null ? null : creator.fromBundle(bundle);
  }

  /**
   * Converts a {@link Bundle} to a {@link Bundleable}. It's a convenience wrapper of {@link
   * Bundleable.Creator#fromBundle} that provides default value to ensure non-null.
   */
  public static <T extends Bundleable> T fromNullableBundle(
      Bundleable.Creator<T> creator, @Nullable Bundle bundle, T defaultValue) {
    return bundle == null ? defaultValue : creator.fromBundle(bundle);
  }

  /** Converts a list of {@link Bundleable} to a list {@link Bundle}. */
  public static <T extends Bundleable> ImmutableList<Bundle> toBundleList(List<T> bundleableList) {
    ImmutableList.Builder<Bundle> builder = ImmutableList.builder();
    for (int i = 0; i < bundleableList.size(); i++) {
      Bundleable bundleable = bundleableList.get(i);
      builder.add(bundleable.toBundle());
    }
    return builder.build();
  }

  /** Converts a list of {@link Bundle} to a list of {@link Bundleable}. */
  public static <T extends Bundleable> ImmutableList<T> fromBundleList(
      Bundleable.Creator<T> creator, List<Bundle> bundleList) {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    for (int i = 0; i < bundleList.size(); i++) {
      Bundle bundle = bundleList.get(i);
      T bundleable = creator.fromBundle(bundle);
      builder.add(bundleable);
    }
    return builder.build();
  }

  /**
   * Converts a list of {@link Bundleable} to an {@link ArrayList} of {@link Bundle} so that the
   * returned list can be put to {@link Bundle} using {@link Bundle#putParcelableArrayList}
   * conveniently.
   */
  public static <T extends Bundleable> ArrayList<Bundle> toBundleArrayList(List<T> bundleableList) {
    ArrayList<Bundle> arrayList = new ArrayList<>(bundleableList.size());
    for (int i = 0; i < bundleableList.size(); i++) {
      arrayList.add(bundleableList.get(i).toBundle());
    }
    return arrayList;
  }

  private BundleableUtils() {}
}
