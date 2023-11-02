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
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;

/** Utilities for converting collections to and from {@link Bundle} instances. */
@UnstableApi
public final class BundleCollectionUtil {

  /**
   * Bundles a list of objects to a list of {@link Bundle} instances.
   *
   * @param list List of items to be bundled.
   * @param toBundleFunc Function that specifies how to bundle each item.
   * @return The {@link ImmutableList} of bundled items.
   */
  public static <T extends @NonNull Object> ImmutableList<Bundle> toBundleList(
      List<T> list, Function<T, Bundle> toBundleFunc) {
    ImmutableList.Builder<Bundle> builder = ImmutableList.builder();
    for (int i = 0; i < list.size(); i++) {
      T item = list.get(i);
      builder.add(toBundleFunc.apply(item));
    }
    return builder.build();
  }

  /**
   * Unbundles a list of {@link Bundle} instances to a list of objects.
   *
   * @param fromBundleFunc Function that specified how to unbundle each item.
   * @param bundleList List of {@link Bundle} instances to be unbundled.
   * @return The {@link ImmutableList} of unbundled items.
   */
  public static <T extends @NonNull Object> ImmutableList<T> fromBundleList(
      Function<Bundle, T> fromBundleFunc, List<Bundle> bundleList) {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    for (int i = 0; i < bundleList.size(); i++) {
      Bundle bundle = checkNotNull(bundleList.get(i)); // Fail fast during parsing.
      T item = fromBundleFunc.apply(bundle);
      builder.add(item);
    }
    return builder.build();
  }

  /**
   * Bundles a collection of objects to an {@link ArrayList} of {@link Bundle} instances so that the
   * returned list can be put to {@link Bundle} using {@link Bundle#putParcelableArrayList}
   * conveniently.
   *
   * @param items Collection of items to be bundled.
   * @param toBundleFunc Function that specifies how to bundle each item.
   * @return The {@link ArrayList} of bundled items.
   */
  @SuppressWarnings("NonApiType") // Intentionally using ArrayList for putParcelableArrayList.
  public static <T extends @NonNull Object> ArrayList<Bundle> toBundleArrayList(
      Collection<T> items, Function<T, Bundle> toBundleFunc) {
    ArrayList<Bundle> arrayList = new ArrayList<>(items.size());
    for (T item : items) {
      arrayList.add(toBundleFunc.apply(item));
    }
    return arrayList;
  }

  /**
   * Unbundles a {@link SparseArray} of {@link Bundle} instances to a {@link SparseArray} of
   * objects.
   *
   * @param fromBundleFunc Function that specified how to unbundle each item.
   * @param bundleSparseArray {@link SparseArray} of {@link Bundle} instances to be unbundled.
   * @return The {@link SparseArray} of unbundled items.
   */
  public static <T extends @NonNull Object> SparseArray<T> fromBundleSparseArray(
      Function<Bundle, T> fromBundleFunc, SparseArray<Bundle> bundleSparseArray) {
    SparseArray<T> result = new SparseArray<>(bundleSparseArray.size());
    for (int i = 0; i < bundleSparseArray.size(); i++) {
      result.put(bundleSparseArray.keyAt(i), fromBundleFunc.apply(bundleSparseArray.valueAt(i)));
    }
    return result;
  }

  /**
   * Bundles a {@link SparseArray} of objects to a {@link SparseArray} of {@link Bundle} instances
   * so that the returned {@link SparseArray} can be put to {@link Bundle} using {@link
   * Bundle#putSparseParcelableArray} conveniently.
   *
   * @param items Collection of items to be bundled.
   * @param toBundleFunc Function that specifies how to bundle each item.
   * @return The {@link SparseArray} of bundled items.
   */
  public static <T extends @NonNull Object> SparseArray<Bundle> toBundleSparseArray(
      SparseArray<T> items, Function<T, Bundle> toBundleFunc) {
    SparseArray<Bundle> sparseArray = new SparseArray<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      sparseArray.put(items.keyAt(i), toBundleFunc.apply(items.valueAt(i)));
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
   * {@code BundleCollectionUtil}.
   */
  public static void ensureClassLoader(@Nullable Bundle bundle) {
    if (bundle != null) {
      bundle.setClassLoader(castNonNull(BundleCollectionUtil.class.getClassLoader()));
    }
  }

  private BundleCollectionUtil() {}
}
