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
package com.google.android.exoplayer2.util;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A {@link android.os.Parcelable} wrapper around an array. */
public final class ParcelableArray<V extends Parcelable> implements Parcelable {

  @SuppressWarnings("rawtypes") // V cannot be obtained from static context
  public static final Creator<ParcelableArray> CREATOR =
      new Creator<ParcelableArray>() {
        @SuppressWarnings("unchecked")
        @Override
        public ParcelableArray createFromParcel(Parcel in) {
          ClassLoader classLoader = ParcelableArray.class.getClassLoader();
          Parcelable[] elements = in.readParcelableArray(classLoader);
          return new ParcelableArray(elements);
        }

        @Override
        public ParcelableArray[] newArray(int size) {
          return new ParcelableArray[size];
        }
      };

  private final V[] elements;

  public ParcelableArray(V[] elements) {
    this.elements = elements;
  }

  /** Returns an unmodifiable list containing all elements. */
  public List<V> asList() {
    return Collections.unmodifiableList(Arrays.asList(elements));
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelableArray(elements, flags);
  }
}
