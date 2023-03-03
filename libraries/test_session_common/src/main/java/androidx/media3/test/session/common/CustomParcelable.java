/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.media3.test.session.common;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

/** Custom Parcelable class to test sending/receiving user parcelables between processes. */
@SuppressLint("BanParcelableUsage")
public class CustomParcelable implements Parcelable {

  private int value;

  public CustomParcelable(int value) {
    this.value = value;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @SuppressLint("UnknownNullness") // Parcel dest
  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(value);
  }

  public static final Parcelable.Creator<CustomParcelable> CREATOR =
      new Parcelable.Creator<CustomParcelable>() {
        @Override
        public CustomParcelable createFromParcel(Parcel in) {
          int value = in.readInt();
          return new CustomParcelable(value);
        }

        @Override
        public CustomParcelable[] newArray(int size) {
          return new CustomParcelable[size];
        }
      };
}
