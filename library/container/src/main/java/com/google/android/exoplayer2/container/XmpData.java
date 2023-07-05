/*
 * Copyright 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.container;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;

/**
 * Stores XMP data.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class XmpData implements Metadata.Entry {
  public final byte[] data;

  /** Creates an instance. */
  public XmpData(byte[] data) {
    this.data = data;
  }

  private XmpData(Parcel in) {
    this.data = Util.castNonNull(in.createByteArray());
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }

    return Arrays.equals(data, ((XmpData) obj).data);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(data);
  }

  @Override
  public String toString() {
    return "XMP: " + Util.toHexString(data);
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeByteArray(data);
  }

  public static final Parcelable.Creator<XmpData> CREATOR =
      new Parcelable.Creator<XmpData>() {

        @Override
        public XmpData createFromParcel(Parcel in) {
          return new XmpData(in);
        }

        @Override
        public XmpData[] newArray(int size) {
          return new XmpData[size];
        }
      };
}
