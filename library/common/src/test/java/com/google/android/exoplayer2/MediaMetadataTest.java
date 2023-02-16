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
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MediaMetadata}. */
@RunWith(AndroidJUnit4.class)
public class MediaMetadataTest {

  private static final String EXTRAS_KEY = "exampleKey";
  private static final String EXTRAS_VALUE = "exampleValue";

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
    assertThat(mediaMetadata.userRating).isNull();
    assertThat(mediaMetadata.overallRating).isNull();
    assertThat(mediaMetadata.artworkData).isNull();
    assertThat(mediaMetadata.artworkDataType).isNull();
    assertThat(mediaMetadata.artworkUri).isNull();
    assertThat(mediaMetadata.trackNumber).isNull();
    assertThat(mediaMetadata.totalTrackCount).isNull();
    assertThat(mediaMetadata.folderType).isNull();
    assertThat(mediaMetadata.isBrowsable).isNull();
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
    assertThat(mediaMetadata.station).isNull();
    assertThat(mediaMetadata.mediaType).isNull();
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
  public void populate_populatesEveryField() {
    MediaMetadata mediaMetadata = getFullyPopulatedMediaMetadata();
    MediaMetadata populated = new MediaMetadata.Builder().populate(mediaMetadata).build();

    // If this assertion fails, it's likely that a field is not being updated in
    // MediaMetadata.Builder#populate(MediaMetadata).
    assertThat(populated).isEqualTo(mediaMetadata);
    assertThat(populated.extras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
  }

  @Test
  public void toBundleSkipsDefaultValues_fromBundleRestoresThem() {
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().build();

    Bundle mediaMetadataBundle = mediaMetadata.toBundle();

    // Check that default values are skipped when bundling.
    assertThat(mediaMetadataBundle.keySet()).isEmpty();

    MediaMetadata mediaMetadataFromBundle = MediaMetadata.CREATOR.fromBundle(mediaMetadataBundle);

    assertThat(mediaMetadataFromBundle).isEqualTo(mediaMetadata);
    // Extras is not implemented in MediaMetadata.equals(Object o).
    assertThat(mediaMetadataFromBundle.extras).isNull();
  }

  @Test
  public void createFullyPopulatedMediaMetadata_roundTripViaBundle_yieldsEqualInstance() {
    MediaMetadata mediaMetadata = getFullyPopulatedMediaMetadata();

    MediaMetadata mediaMetadataFromBundle =
        MediaMetadata.CREATOR.fromBundle(mediaMetadata.toBundle());

    assertThat(mediaMetadataFromBundle).isEqualTo(mediaMetadata);
    // Extras is not implemented in MediaMetadata.equals(Object o).
    assertThat(mediaMetadataFromBundle.extras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
  }

  @Test
  public void builderSetFolderType_toNone_setsIsBrowsableToFalse() {
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder().setFolderType(MediaMetadata.FOLDER_TYPE_NONE).build();

    assertThat(mediaMetadata.isBrowsable).isFalse();
  }

  @Test
  public void builderSetFolderType_toNotNone_setsIsBrowsableToTrueAndMatchingMediaType() {
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder().setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS).build();

    assertThat(mediaMetadata.isBrowsable).isTrue();
    assertThat(mediaMetadata.mediaType).isEqualTo(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS);
  }

  @Test
  public void
      builderSetFolderType_toNotNoneWithManualMediaType_setsIsBrowsableToTrueAndDoesNotOverrideMediaType() {
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
            .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
            .build();

    assertThat(mediaMetadata.isBrowsable).isTrue();
    assertThat(mediaMetadata.mediaType).isEqualTo(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS);
  }

  @Test
  public void builderSetIsBrowsable_toTrueWithoutMediaType_setsFolderTypeToMixed() {
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setIsBrowsable(true).build();

    assertThat(mediaMetadata.folderType).isEqualTo(MediaMetadata.FOLDER_TYPE_MIXED);
  }

  @Test
  public void builderSetIsBrowsable_toTrueWithMediaType_setsFolderTypeToMatchMediaType() {
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setIsBrowsable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
            .build();

    assertThat(mediaMetadata.folderType).isEqualTo(MediaMetadata.FOLDER_TYPE_ARTISTS);
  }

  @Test
  public void builderSetFolderType_toFalse_setsFolderTypeToNone() {
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setIsBrowsable(false).build();

    assertThat(mediaMetadata.folderType).isEqualTo(MediaMetadata.FOLDER_TYPE_NONE);
  }

  private static MediaMetadata getFullyPopulatedMediaMetadata() {
    Bundle extras = new Bundle();
    extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

    return new MediaMetadata.Builder()
        .setTitle("title")
        .setArtist("artist")
        .setAlbumTitle("album title")
        .setAlbumArtist("album artist")
        .setDisplayTitle("display title")
        .setSubtitle("subtitle")
        .setDescription("description")
        .setUserRating(new HeartRating(false))
        .setOverallRating(new PercentageRating(87.4f))
        .setArtworkData(
            new byte[] {-88, 12, 3, 2, 124, -54, -33, 69}, MediaMetadata.PICTURE_TYPE_MEDIA)
        .setArtworkUri(Uri.parse("https://www.google.com"))
        .setTrackNumber(4)
        .setTotalTrackCount(12)
        .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
        .setIsBrowsable(true)
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
        .setStation("radio station")
        .setMediaType(MediaMetadata.MEDIA_TYPE_MIXED)
        .setExtras(extras)
        .build();
  }
}
