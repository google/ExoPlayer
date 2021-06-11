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
  public void populateMediaMetadata_setsMediaMetadataValue() {
    byte[] pictureData = new byte[] {-12, 52, 33, 85, 34, 22, 1, -55};
    Metadata.Entry entry =
        new PictureFrame(
            /* pictureType= */ 0x03,
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
  }
}
