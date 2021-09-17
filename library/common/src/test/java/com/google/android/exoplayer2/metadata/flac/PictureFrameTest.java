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
package com.google.android.exoplayer2.metadata.flac;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link PictureFrame}. */
@RunWith(AndroidJUnit4.class)
public final class PictureFrameTest {

  @Test
  public void parcelable() {
    PictureFrame pictureFrameToParcel = new PictureFrame(0, "", "", 0, 0, 0, 0, new byte[0]);

    Parcel parcel = Parcel.obtain();
    pictureFrameToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    PictureFrame pictureFrameFromParcel = PictureFrame.CREATOR.createFromParcel(parcel);
    assertThat(pictureFrameFromParcel).isEqualTo(pictureFrameToParcel);

    parcel.recycle();
  }

  @Test
  public void populateMediaMetadata_setsBuilderValues() {
    byte[] pictureData = new byte[] {-12, 52, 33, 85, 34, 22, 1, -55};
    Metadata.Entry entry =
        new PictureFrame(
            /* pictureType= */ MediaMetadata.PICTURE_TYPE_FRONT_COVER,
            /* mimeType= */ MimeTypes.IMAGE_JPEG,
            /* description= */ "an image",
            /* width= */ 4,
            /* height= */ 2,
            /* depth= */ 1,
            /* colors= */ 1,
            pictureData);

    MediaMetadata.Builder builder = MediaMetadata.EMPTY.buildUpon();
    entry.populateMediaMetadata(builder);

    MediaMetadata mediaMetadata = builder.build();
    assertThat(mediaMetadata.artworkData).isEqualTo(pictureData);
    assertThat(mediaMetadata.artworkDataType).isEqualTo(MediaMetadata.PICTURE_TYPE_FRONT_COVER);
  }

  @Test
  public void populateMediaMetadata_considersTypePriority() {
    byte[] data1 = new byte[] {1, 1, 1, 1};
    byte[] data2 = new byte[] {2, 2, 2, 2};
    byte[] data3 = new byte[] {3, 3, 3, 3};
    byte[] data4 = new byte[] {4, 4, 4, 4};
    byte[] data5 = new byte[] {5, 5, 5, 5};

    Metadata.Entry entry1 =
        new PictureFrame(
            /* pictureType= */ MediaMetadata.PICTURE_TYPE_BAND_ORCHESTRA,
            /* mimeType= */ MimeTypes.IMAGE_JPEG,
            /* description= */ "an image",
            /* width= */ 2,
            /* height= */ 2,
            /* depth= */ 1,
            /* colors= */ 1,
            data1);
    Metadata.Entry entry2 =
        new PictureFrame(
            /* pictureType= */ MediaMetadata.PICTURE_TYPE_DURING_RECORDING,
            /* mimeType= */ MimeTypes.IMAGE_JPEG,
            /* description= */ "an image",
            /* width= */ 2,
            /* height= */ 2,
            /* depth= */ 1,
            /* colors= */ 1,
            data2);
    Metadata.Entry entry3 =
        new PictureFrame(
            /* pictureType= */ MediaMetadata.PICTURE_TYPE_FRONT_COVER,
            /* mimeType= */ MimeTypes.IMAGE_JPEG,
            /* description= */ "an image",
            /* width= */ 2,
            /* height= */ 2,
            /* depth= */ 1,
            /* colors= */ 1,
            data3);
    Metadata.Entry entry4 =
        new PictureFrame(
            /* pictureType= */ MediaMetadata.PICTURE_TYPE_ARTIST_PERFORMER,
            /* mimeType= */ MimeTypes.IMAGE_JPEG,
            /* description= */ "an image",
            /* width= */ 2,
            /* height= */ 2,
            /* depth= */ 1,
            /* colors= */ 1,
            data4);
    Metadata.Entry entry5 =
        new PictureFrame(
            /* pictureType= */ MediaMetadata.PICTURE_TYPE_FRONT_COVER,
            /* mimeType= */ MimeTypes.IMAGE_JPEG,
            /* description= */ "an image",
            /* width= */ 2,
            /* height= */ 2,
            /* depth= */ 1,
            /* colors= */ 1,
            data5);

    MediaMetadata.Builder builder = MediaMetadata.EMPTY.buildUpon();

    entry1.populateMediaMetadata(builder);
    assertThat(builder.build().artworkData).isEqualTo(data1);

    // Data updates when any type is given, if the current type is not front cover.
    entry2.populateMediaMetadata(builder);
    assertThat(builder.build().artworkData).isEqualTo(data2);

    // Data updates because this entry picture type is front cover.
    entry3.populateMediaMetadata(builder);
    assertThat(builder.build().artworkData).isEqualTo(data3);

    // Data does not update because the current type is front cover, and this entry type is not.
    entry4.populateMediaMetadata(builder);
    assertThat(builder.build().artworkData).isEqualTo(data3);

    // Data updates because this entry picture type is front cover.
    entry5.populateMediaMetadata(builder);
    assertThat(builder.build().artworkData).isEqualTo(data5);
  }
}
