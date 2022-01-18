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
import java.util.Arrays;

/** A picture parsed from a Vorbis Comment or a FLAC picture block. */
public final class PictureFrame extends com.google.android.exoplayer2.metadata.flac.PictureFrame {
  public PictureFrame(
      int pictureType,
      String mimeType,
      String description,
      int width,
      int height,
      int depth,
      int colors,
      byte[] pictureData) {
    super(pictureType, mimeType, description, width, height, depth, colors, pictureData);
  }

  /* package */ PictureFrame(Parcel in) {
    // Workaround to get parcel instantiation working while retaining backwards compatibility
    // with the old type.
    super(
        in.readInt(),
        castNonNull(in.readString()),
        castNonNull(in.readString()),
        in.readInt(),
        in.readInt(),
        in.readInt(),
        in.readInt(),
        castNonNull(in.createByteArray())
    );
  }

  public static final Parcelable.Creator<PictureFrame> CREATOR =
      new Parcelable.Creator<PictureFrame>() {

        @Override
        public PictureFrame createFromParcel(Parcel in) {
          return new PictureFrame(in);
        }

        @Override
        public PictureFrame[] newArray(int size) {
          return new PictureFrame[size];
        }
      };
}