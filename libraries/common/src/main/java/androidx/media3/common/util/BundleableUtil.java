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
package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;

import android.os.Bundle;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.media3.common.Bundleable;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utilities for {@link Bundleable}. */
@UnstableApi
public final class BundleableUtil {

  /** Converts a list of {@link Bundleable} to a list {@link Bundle}. */
  public static <T extends Bundleable> ImmutableList<Bundle> toBundleList(List<T> bundleableList) {
    return toBundleList(bundleableList, Bundleable::toBundle);
  }

  /**
   * Converts a list of {@link Bundleable} to a list {@link Bundle}
   *
   * @param bundleableList list of Bundleable items to be converted
   * @param customToBundleFunc function that specifies how to bundle up each {@link Bundleable}
   */
  public static <T extends Bundleable> ImmutableList<Bundle> toBundleList(
      List<T> bundleableList, Function<T, Bundle> customToBundleFunc) {
    ImmutableList.Builder<Bundle> builder = ImmutableList.builder();
    for (int i = 0; i < bundleableList.size(); i++) {
      T bundleable = bundleableList.get(i);
      builder.add(customToBundleFunc.apply(bundleable));
    }
    return builder.build();
  }

  /** Converts a list of {@link Bundle} to a list of {@link Bundleable}. */
  public static <T extends Bundleable> ImmutableList<T> fromBundleList(
      Bundleable.Creator<T> creator, List<Bundle> bundleList) {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    for (int i = 0; i < bundleList.size(); i++) {
      Bundle bundle = checkNotNull(bundleList.get(i)); // Fail fast during parsing.
      T bundleable = creator.fromBundle(bundle);
      builder.add(bundleable);
    }
    return builder.build();
  }

  /**
   * Converts a collection of {@link Bundleable} to an {@link ArrayList} of {@link Bundle} so that
   * the returned list can be put to {@link Bundle} using {@link Bundle#putParcelableArrayList}
   * conveniently.
   */
  public static <T extends Bundleable> ArrayList<Bundle> toBundleArrayList(
      Collection<T> bundleables) {
    ArrayList<Bundle> arrayList = new ArrayList<>(bundleables.size());
    for (T element : bundleables) {
      arrayList.add(element.toBundle());
    }
    return arrayList;
  }

  /**
   * Converts a {@link SparseArray} of {@link Bundle} to a {@link SparseArray} of {@link
   * Bundleable}.
   */
  public static <T extends Bundleable> SparseArray<T> fromBundleSparseArray(
      Bundleable.Creator<T> creator, SparseArray<Bundle> bundleSparseArray) {
    SparseArray<T> result = new SparseArray<>(bundleSparseArray.size());
    for (int i = 0; i < bundleSparseArray.size(); i++) {
      result.put(bundleSparseArray.keyAt(i), creator.fromBundle(bundleSparseArray.valueAt(i)));
    }
    return result;
  }

  /**
   * Converts a {@link SparseArray} of {@link Bundleable} to an {@link SparseArray} of {@link
   * Bundle} so that the returned {@link SparseArray} can be put to {@link Bundle} using {@link
   * Bundle#putSparseParcelableArray} conveniently.
   */
  public static <T extends Bundleable> SparseArray<Bundle> toBundleSparseArray(
      SparseArray<T> bundleableSparseArray) {
    SparseArray<Bundle> sparseArray = new SparseArray<>(bundleableSparseArray.size());
    for (int i = 0; i < bundleableSparseArray.size(); i++) {
      sparseArray.put(bundleableSparseArray.keyAt(i), bundleableSparseArray.valueAt(i).toBundle());
    }
    return sparseArray;
  }

  public static Bundle stringMapToBundle(Map<String, String> bundleableMap) {
    Bundle bundle = new Bundle();
    for (Map.Entry<String, String> entry : bundleableMap.entrySet()) {
      bundle.putString(entry.getKey(), entry.getValue());
    }
    return bundle;
  }

  public static HashMap<String, String> bundleToStringHashMap(Bundle bundle) {
    HashMap<String, String> map = new HashMap<>();
    if (bundle == Bundle.EMPTY) {
      return map;
    }
    for (String key : bundle.keySet()) {
      @Nullable String value = bundle.getString(key);
      if (value != null) {
        map.put(key, value);
      }
    }
    return map;
  }

  public static ImmutableMap<String, String> bundleToStringImmutableMap(Bundle bundle) {
    if (bundle == Bundle.EMPTY) {
      return ImmutableMap.of();
    }
    HashMap<String, String> map = bundleToStringHashMap(bundle);
    return ImmutableMap.copyOf(map);
  }

  public static Bundle getBundleWithDefault(Bundle bundle, String field, Bundle defaultValue) {
    @Nullable Bundle result = bundle.getBundle(field);
    return result != null ? result : defaultValue;
  }

  public static ArrayList<Integer> getIntegerArrayListWithDefault(
      Bundle bundle, String field, ArrayList<Integer> defaultValue) {
    @Nullable ArrayList<Integer> result = bundle.getIntegerArrayList(field);
    return result != null ? result : defaultValue;
  }

  /**
   * Sets the application class loader to the given {@link Bundle} if no class loader is present.
   *
   * <p>This assumes that all classes unparceled from {@code bundle} are sharing the class loader of
   * {@code BundleableUtils}.
   */
  public static void ensureClassLoader(@Nullable Bundle bundle) {
    if (bundle != null) {
      bundle.setClassLoader(castNonNull(BundleableUtil.class.getClassLoader()));
    }
  }

  private BundleableUtil() {}
}
