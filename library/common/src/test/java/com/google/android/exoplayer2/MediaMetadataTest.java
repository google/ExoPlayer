/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MediaMetadata}. */
@RunWith(AndroidJUnit4.class)
public class MediaMetadataTest {

  @Test
  public void builder_minimal_correctDefaults() {
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().build();

    assertThat(mediaMetadata.title).isNull();
    assertThat(mediaMetadata.artist).isNull();
    assertThat(mediaMetadata.albumTitle).isNull();
    assertThat(mediaMetadata.albumArtist).isNull();
    assertThat(mediaMetadata.displayTitle).isNull();
    assertThat(mediaMetadata.subtitle).isNull();
    assertThat(mediaMetadata.description).isNull();
    assertThat(mediaMetadata.mediaUri).isNull();
    assertThat(mediaMetadata.userRating).isNull();
    assertThat(mediaMetadata.overallRating).isNull();
    assertThat(mediaMetadata.artworkData).isNull();
    assertThat(mediaMetadata.artworkUri).isNull();
  }

  @Test
  public void builderSetTitle_setsTitle() {
    String title = "title";

    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle(title).build();

    assertThat(mediaMetadata.title.toString()).isEqualTo(title);
  }

  @Test
  public void builderSetArtworkData_setsArtworkData() {
    byte[] bytes = new byte[] {35, 12, 6, 77};
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setArtworkData(bytes).build();

    assertThat(Arrays.equals(mediaMetadata.artworkData, bytes)).isTrue();
  }

  @Test
  public void builderSetArworkUri_setsArtworkUri() {
    Uri uri = Uri.parse("https://www.google.com");
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setArtworkUri(uri).build();

    assertThat(mediaMetadata.artworkUri).isEqualTo(uri);
  }

  @Test
  public void roundTripViaBundle_yieldsEqualInstance() {
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setTitle("title")
            .setAlbumArtist("the artist")
            .setMediaUri(Uri.parse("https://www.google.com"))
            .setUserRating(new HeartRating(false))
            .setOverallRating(new PercentageRating(87.4f))
            .setArtworkData(new byte[] {-88, 12, 3, 2, 124, -54, -33, 69})
            .build();

    MediaMetadata fromBundle = MediaMetadata.CREATOR.fromBundle(mediaMetadata.toBundle());
    assertThat(fromBundle).isEqualTo(mediaMetadata);
  }

  @Test
  public void builderPopulatedFromTextInformationFrameEntry_setsTitle() {
    String title = "the title";
    Metadata.Entry entry =
        new TextInformationFrame(/* id= */ "TT2", /* description= */ null, /* value= */ title);
    MediaMetadata.Builder builder = MediaMetadata.EMPTY.buildUpon();

    entry.populateMediaMetadata(builder);
    assertThat(builder.build().title.toString()).isEqualTo(title);
  }

  @Test
  public void builderPopulatedFromApicFrameEntry_setsArtwork() {
    byte[] pictureData = new byte[] {-12, 52, 33, 85, 34, 22, 1, -55};
    Metadata.Entry entry =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            /* pictureType= */ 0x03,
            pictureData);

    MediaMetadata.Builder builder = MediaMetadata.EMPTY.buildUpon();
    entry.populateMediaMetadata(builder);

    MediaMetadata mediaMetadata = builder.build();
    assertThat(mediaMetadata.artworkData).isEqualTo(pictureData);
  }
}
