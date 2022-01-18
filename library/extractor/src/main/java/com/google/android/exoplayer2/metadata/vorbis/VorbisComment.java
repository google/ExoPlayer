/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.vorbis;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.metadata.Metadata;

/** A vorbis comment, extracted from a FLAC or OGG file. */
public final class VorbisComment extends com.google.android.exoplayer2.metadata.flac.VorbisComment {
  /**
   * @param key The key.
   * @param value The value.
   */
  public VorbisComment(String key, String value) {
    super(key, value);
  }

  /* package */ VorbisComment(Parcel in) {
    // Workaround to get parcel instantiation working while retaining backwards compatibility
    // with the old type.
    super(castNonNull(in.readString()), castNonNull(in.readString()));
  }

  public static final Parcelable.Creator<VorbisComment> CREATOR =
    new Parcelable.Creator<VorbisComment>() {

      @Override
      public VorbisComment createFromParcel(Parcel in) {
        return new VorbisComment(in);
      }

      @Override
      public VorbisComment[] newArray(int size) {
        return new VorbisComment[size];
      }
    };
}
