/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.offline;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.util.Util;

/**
 * A key for a subset of media that can be separately loaded (a "stream").
 *
 * <p>The stream key consists of a period index, a group index within the period and a stream index
 * within the group. The interpretation of these indices depends on the type of media for which the
 * stream key is used. Note that they are <em>not</em> the same as track group and track indices,
 * because multiple tracks can be multiplexed into a single stream.
 *
 * <p>Application code should not generally attempt to build StreamKey instances directly. Instead,
 * {@code DownloadHelper.getDownloadRequest} can be used to generate download requests with the
 * correct StreamKeys for the track selections that have been configured on the helper. {@code
 * MediaPeriod.getStreamKeys} provides a lower level way of generating StreamKeys corresponding to a
 * particular track selection.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class StreamKey implements Comparable<StreamKey>, Parcelable, Bundleable {

  /** The period index. */
  public final int periodIndex;
  /** The group index. */
  public final int groupIndex;
  /** The stream index. */
  public final int streamIndex;

  /**
   * Creates an instance with {@link #periodIndex} set to 0.
   *
   * @param groupIndex The group index.
   * @param streamIndex The stream index.
   */
  public StreamKey(int groupIndex, int streamIndex) {
    this(0, groupIndex, streamIndex);
  }

  /**
   * Creates an instance of {@link StreamKey} using 3 indices.
   *
   * @param periodIndex The period index.
   * @param groupIndex The group index.
   * @param streamIndex The stream index.
   */
  public StreamKey(int periodIndex, int groupIndex, int streamIndex) {
    this.periodIndex = periodIndex;
    this.groupIndex = groupIndex;
    this.streamIndex = streamIndex;
  }

  /* package */ StreamKey(Parcel in) {
    periodIndex = in.readInt();
    groupIndex = in.readInt();
    streamIndex = in.readInt();
  }

  @Override
  public String toString() {
    return periodIndex + "." + groupIndex + "." + streamIndex;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StreamKey that = (StreamKey) o;
    return periodIndex == that.periodIndex
        && groupIndex == that.groupIndex
        && streamIndex == that.streamIndex;
  }

  @Override
  public int hashCode() {
    int result = periodIndex;
    result = 31 * result + groupIndex;
    result = 31 * result + streamIndex;
    return result;
  }

  // Comparable implementation.

  @Override
  public int compareTo(StreamKey o) {
    int result = periodIndex - o.periodIndex;
    if (result == 0) {
      result = groupIndex - o.groupIndex;
      if (result == 0) {
        result = streamIndex - o.streamIndex;
      }
    }
    return result;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(periodIndex);
    dest.writeInt(groupIndex);
    dest.writeInt(streamIndex);
  }

  public static final Parcelable.Creator<StreamKey> CREATOR =
      new Parcelable.Creator<StreamKey>() {

        @Override
        public StreamKey createFromParcel(Parcel in) {
          return new StreamKey(in);
        }

        @Override
        public StreamKey[] newArray(int size) {
          return new StreamKey[size];
        }
      };

  // Bundleable implementation.

  private static final String FIELD_PERIOD_INDEX = Util.intToStringMaxRadix(0);
  private static final String FIELD_GROUP_INDEX = Util.intToStringMaxRadix(1);
  private static final String FIELD_STREAM_INDEX = Util.intToStringMaxRadix(2);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (periodIndex != 0) {
      bundle.putInt(FIELD_PERIOD_INDEX, periodIndex);
    }
    if (groupIndex != 0) {
      bundle.putInt(FIELD_GROUP_INDEX, groupIndex);
    }
    if (streamIndex != 0) {
      bundle.putInt(FIELD_STREAM_INDEX, streamIndex);
    }
    return bundle;
  }

  /**
   * Constructs an instance of {@link StreamKey} from a {@link Bundle} produced by {@link
   * #toBundle()}.
   */
  public static StreamKey fromBundle(Bundle bundle) {
    return new StreamKey(
        bundle.getInt(FIELD_PERIOD_INDEX, /* defaultValue= */ 0),
        bundle.getInt(FIELD_GROUP_INDEX, /* defaultValue= */ 0),
        bundle.getInt(FIELD_STREAM_INDEX, /* defaultValue= */ 0));
  }
}
