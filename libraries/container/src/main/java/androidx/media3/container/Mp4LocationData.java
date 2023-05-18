/*
 * Copyright (C) 2023 The Android Open Source Project
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
package androidx.media3.container;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import com.google.common.primitives.Floats;

/** Stores MP4 location data. */
@UnstableApi
public final class Mp4LocationData implements Metadata.Entry {
  public final float latitude;
  public final float longitude;

  /**
   * Creates an instance.
   *
   * @param latitude The latitude, in degrees. Its value must be in the range [-90, 90].
   * @param longitude The longitude, in degrees. Its value must be in the range [-180, 180].
   */
  public Mp4LocationData(
      @FloatRange(from = -90.0, to = 90.0) float latitude,
      @FloatRange(from = -180.0, to = 180.0) float longitude) {
    checkArgument(
        latitude >= -90.0f && latitude <= 90.0f && longitude >= -180.0f && longitude <= 180.0f,
        "Invalid latitude or longitude");
    this.latitude = latitude;
    this.longitude = longitude;
  }

  private Mp4LocationData(Parcel in) {
    latitude = in.readFloat();
    longitude = in.readFloat();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Mp4LocationData other = (Mp4LocationData) obj;
    return latitude == other.latitude && longitude == other.longitude;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Floats.hashCode(latitude);
    result = 31 * result + Floats.hashCode(longitude);
    return result;
  }

  @Override
  public String toString() {
    return "xyz: latitude=" + latitude + ", longitude=" + longitude;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeFloat(latitude);
    dest.writeFloat(longitude);
  }

  public static final Parcelable.Creator<Mp4LocationData> CREATOR =
      new Parcelable.Creator<Mp4LocationData>() {

        @Override
        public Mp4LocationData createFromParcel(Parcel in) {
          return new Mp4LocationData(in);
        }

        @Override
        public Mp4LocationData[] newArray(int size) {
          return new Mp4LocationData[size];
        }
      };
}
