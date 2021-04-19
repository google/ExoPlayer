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

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import java.util.Arrays;

/** Defines an immutable group of tracks identified by their format identity. */
public final class TrackGroup implements Parcelable {

  private static final String TAG = "TrackGroup";

  /** The number of tracks in the group. */
  public final int length;

  private final Format[] formats;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * @param formats The track formats. At least one {@link Format} must be provided.
   */
  public TrackGroup(Format... formats) {
    Assertions.checkState(formats.length > 0);
    this.formats = formats;
    this.length = formats.length;
    verifyCorrectness();
  }

  /* package */ TrackGroup(Parcel in) {
    length = in.readInt();
    formats = new Format[length];
    for (int i = 0; i < length; i++) {
      formats[i] = in.readParcelable(Format.class.getClassLoader());
    }
  }

  /**
   * Returns the format of the track at a given index.
   *
   * @param index The index of the track.
   * @return The track's format.
   */
  public Format getFormat(int index) {
    return formats[index];
  }

  /**
   * Returns the index of the track with the given format in the group. The format is located by
   * identity so, for example, {@code group.indexOf(group.getFormat(index)) == index} even if
   * multiple tracks have formats that contain the same values.
   *
   * @param format The format.
   * @return The index of the track, or {@link C#INDEX_UNSET} if no such track exists.
   */
  @SuppressWarnings("ReferenceEquality")
  public int indexOf(Format format) {
    for (int i = 0; i < formats.length; i++) {
      if (format == formats[i]) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + Arrays.hashCode(formats);
      hashCode = result;
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
    TrackGroup other = (TrackGroup) obj;
    return length == other.length && Arrays.equals(formats, other.formats);
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(length);
    for (int i = 0; i < length; i++) {
      dest.writeParcelable(formats[i], 0);
    }
  }

  public static final Parcelable.Creator<TrackGroup> CREATOR =
      new Parcelable.Creator<TrackGroup>() {

        @Override
        public TrackGroup createFromParcel(Parcel in) {
          return new TrackGroup(in);
        }

        @Override
        public TrackGroup[] newArray(int size) {
          return new TrackGroup[size];
        }
      };

  private void verifyCorrectness() {
    // TrackGroups should only contain tracks with exactly the same content (but in different
    // qualities). We only log an error instead of throwing to not break backwards-compatibility for
    // cases where malformed TrackGroups happen to work by chance (e.g. because adaptive selections
    // are always disabled).
    String language = normalizeLanguage(formats[0].language);
    @C.RoleFlags int roleFlags = normalizeRoleFlags(formats[0].roleFlags);
    for (int i = 1; i < formats.length; i++) {
      if (!language.equals(normalizeLanguage(formats[i].language))) {
        logErrorMessage(
            /* mismatchField= */ "languages",
            /* valueIndex0= */ formats[0].language,
            /* otherValue=* */ formats[i].language,
            /* otherIndex= */ i);
        return;
      }
      if (roleFlags != normalizeRoleFlags(formats[i].roleFlags)) {
        logErrorMessage(
            /* mismatchField= */ "role flags",
            /* valueIndex0= */ Integer.toBinaryString(formats[0].roleFlags),
            /* otherValue=* */ Integer.toBinaryString(formats[i].roleFlags),
            /* otherIndex= */ i);
        return;
      }
    }
  }

  private static String normalizeLanguage(@Nullable String language) {
    // Treat all variants of undetermined or unknown languages as compatible.
    return language == null || language.equals(C.LANGUAGE_UNDETERMINED) ? "" : language;
  }

  @C.RoleFlags
  private static int normalizeRoleFlags(@C.RoleFlags int roleFlags) {
    // Treat trick-play and non-trick-play formats as compatible.
    return roleFlags | C.ROLE_FLAG_TRICK_PLAY;
  }

  private static void logErrorMessage(
      String mismatchField,
      @Nullable String valueIndex0,
      @Nullable String otherValue,
      int otherIndex) {
    Log.e(
        TAG,
        "",
        new IllegalStateException(
            "Different "
                + mismatchField
                + " combined in one TrackGroup: '"
                + valueIndex0
                + "' (track 0) and '"
                + otherValue
                + "' (track "
                + otherIndex
                + ")"));
  }
}
