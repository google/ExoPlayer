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
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaMetadata.PictureType;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.util.MimeTypes;
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
    assertThat(mediaMetadata.artworkDataType).isNull();
    assertThat(mediaMetadata.artworkUri).isNull();
    assertThat(mediaMetadata.trackNumber).isNull();
    assertThat(mediaMetadata.totalTrackCount).isNull();
    assertThat(mediaMetadata.folderType).isNull();
    assertThat(mediaMetadata.isPlayable).isNull();
    assertThat(mediaMetadata.recordingYear).isNull();
    assertThat(mediaMetadata.recordingMonth).isNull();
    assertThat(mediaMetadata.recordingDay).isNull();
    assertThat(mediaMetadata.releaseYear).isNull();
    assertThat(mediaMetadata.releaseMonth).isNull();
    assertThat(mediaMetadata.releaseDay).isNull();
    assertThat(mediaMetadata.composer).isNull();
    assertThat(mediaMetadata.conductor).isNull();
    assertThat(mediaMetadata.writer).isNull();
    assertThat(mediaMetadata.discNumber).isNull();
    assertThat(mediaMetadata.totalDiscCount).isNull();
    assertThat(mediaMetadata.genre).isNull();
    assertThat(mediaMetadata.compilation).isNull();
    assertThat(mediaMetadata.extras).isNull();
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
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder().setArtworkData(new byte[] {35, 12, 6, 77}, null).build();

    assertThat(mediaMetadata.artworkData).isEqualTo(bytes);
  }

  @Test
  public void builderSetArworkUri_setsArtworkUri() {
    Uri uri = Uri.parse("https://www.google.com");
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setArtworkUri(uri).build();

    assertThat(mediaMetadata.artworkUri).isEqualTo(uri);
  }

  @Test
  public void roundTripViaBundle_yieldsEqualInstance() {
    Bundle extras = new Bundle();
    extras.putString("exampleKey", "exampleValue");

    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setTitle("title")
            .setAlbumArtist("the artist")
            .setMediaUri(Uri.parse("https://www.google.com"))
            .setUserRating(new HeartRating(false))
            .setOverallRating(new PercentageRating(87.4f))
            .setArtworkData(
                new byte[] {-88, 12, 3, 2, 124, -54, -33, 69}, MediaMetadata.PICTURE_TYPE_MEDIA)
            .setTrackNumber(4)
            .setTotalTrackCount(12)
            .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
            .setIsPlayable(true)
            .setRecordingYear(2000)
            .setRecordingMonth(11)
            .setRecordingDay(23)
            .setReleaseYear(2001)
            .setReleaseMonth(1)
            .setReleaseDay(2)
            .setComposer("Composer")
            .setConductor("Conductor")
            .setWriter("Writer")
            .setDiscNumber(1)
            .setTotalDiscCount(3)
            .setGenre("Pop")
            .setCompilation("Amazing songs.")
            .setExtras(extras) // Extras is not implemented in MediaMetadata.equals(Object o).
            .build();

    MediaMetadata fromBundle = MediaMetadata.CREATOR.fromBundle(mediaMetadata.toBundle());
    assertThat(fromBundle).isEqualTo(mediaMetadata);
    assertThat(fromBundle.extras.getString("exampleKey")).isEqualTo("exampleValue");
  }

  @Test
  public void builderPopulatedFromApicFrameEntry_setsArtwork() {
    byte[] pictureData = new byte[] {-12, 52, 33, 85, 34, 22, 1, -55};
    @PictureType int pictureType = MediaMetadata.PICTURE_TYPE_LEAFLET_PAGE;
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
  public void builderPopulatedFromApicFrameEntry_considersTypePriority() {
    byte[] data1 = new byte[] {1, 1, 1, 1};
    Metadata.Entry entry1 =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            MediaMetadata.PICTURE_TYPE_BAND_ARTIST_LOGO,
            data1);
    byte[] data2 = new byte[] {2, 2, 2, 2};
    Metadata.Entry entry2 =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            MediaMetadata.PICTURE_TYPE_ARTIST_PERFORMER,
            data2);
    byte[] data3 = new byte[] {3, 3, 3, 3};
    Metadata.Entry entry3 =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            MediaMetadata.PICTURE_TYPE_FRONT_COVER,
            data3);
    byte[] data4 = new byte[] {4, 4, 4, 4};
    Metadata.Entry entry4 =
        new ApicFrame(
            /* mimeType= */ MimeTypes.BASE_TYPE_IMAGE,
            /* description= */ "an image",
            MediaMetadata.PICTURE_TYPE_ILLUSTRATION,
            data4);
    byte[] data5 = new byte[] {5, 5, 5, 5};
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
