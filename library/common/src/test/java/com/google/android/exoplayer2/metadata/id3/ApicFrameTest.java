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
package com.google.android.exoplayer2.metadata.id3;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ApicFrame}. */
@RunWith(AndroidJUnit4.class)
public class ApicFrameTest {

  @Test
  public void parcelable() {
    ApicFrame apicFrameToParcel = new ApicFrame("", "", 0, new byte[0]);

    Parcel parcel = Parcel.obtain();
    apicFrameToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    ApicFrame apicFrameFromParcel = ApicFrame.CREATOR.createFromParcel(parcel);
    assertThat(apicFrameFromParcel).isEqualTo(apicFrameToParcel);

    parcel.recycle();
  }

  @Test
  public void populateMediaMetadata_setsBuilderValues() {
    byte[] pictureData = new byte[] {-12, 52, 33, 85, 34, 22, 1, -55};
    @MediaMetadata.PictureType int pictureType = MediaMetadata.PICTURE_TYPE_LEAFLET_PAGE;
    Metadata.Entry entry =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            pictureType,
            pictureData);

    MediaMetadata.Builder builder = MediaMetadata.EMPTY.buildUpon();
    entry.populateMediaMetadata(builder);

    MediaMetadata mediaMetadata = builder.build();
    assertThat(mediaMetadata.artworkData).isEqualTo(pictureData);
    assertThat(mediaMetadata.artworkDataType).isEqualTo(pictureType);
  }

  @Test
  public void populateMediaMetadata_considersTypePriority() {
    byte[] data1 = new byte[] {1, 1, 1, 1};
    byte[] data2 = new byte[] {2, 2, 2, 2};
    byte[] data3 = new byte[] {3, 3, 3, 3};
    byte[] data4 = new byte[] {4, 4, 4, 4};
    byte[] data5 = new byte[] {5, 5, 5, 5};
    Metadata.Entry entry1 =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            MediaMetadata.PICTURE_TYPE_BAND_ARTIST_LOGO,
            data1);
    Metadata.Entry entry2 =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            MediaMetadata.PICTURE_TYPE_ARTIST_PERFORMER,
            data2);
    Metadata.Entry entry3 =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            MediaMetadata.PICTURE_TYPE_FRONT_COVER,
            data3);
    Metadata.Entry entry4 =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            MediaMetadata.PICTURE_TYPE_ILLUSTRATION,
            data4);
    Metadata.Entry entry5 =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            MediaMetadata.PICTURE_TYPE_FRONT_COVER,
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
