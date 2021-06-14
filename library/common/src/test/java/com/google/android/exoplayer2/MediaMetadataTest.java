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
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
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
    assertThat(mediaMetadata.trackNumber).isNull();
    assertThat(mediaMetadata.totalTrackCount).isNull();
    assertThat(mediaMetadata.folderType).isNull();
    assertThat(mediaMetadata.isPlayable).isNull();
    assertThat(mediaMetadata.year).isNull();
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
    Bundle extras = new Bundle();
    extras.putString("exampleKey", "exampleValue");

    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setTitle("title")
            .setAlbumArtist("the artist")
            .setMediaUri(Uri.parse("https://www.google.com"))
            .setUserRating(new HeartRating(false))
            .setOverallRating(new PercentageRating(87.4f))
            .setArtworkData(new byte[] {-88, 12, 3, 2, 124, -54, -33, 69})
            .setTrackNumber(4)
            .setTotalTrackCount(12)
            .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
            .setIsPlayable(true)
            .setYear(2000)
            .setExtras(extras) // Extras is not implemented in MediaMetadata.equals(Object o).
            .build();

    MediaMetadata fromBundle = MediaMetadata.CREATOR.fromBundle(mediaMetadata.toBundle());
    assertThat(fromBundle).isEqualTo(mediaMetadata);
    assertThat(fromBundle.extras.getString("exampleKey")).isEqualTo("exampleValue");
  }

  @Test
  public void builderPopulatedFromTextInformationFrameEntry_setsValues() {
    String title = "the title";
    String artist = "artist";
    String albumTitle = "album title";
    String albumArtist = "album Artist";
    String trackNumberInfo = "11/17";
    String year = "2000";

    List<Metadata.Entry> entries =
        ImmutableList.of(
            new TextInformationFrame(/* id= */ "TT2", /* description= */ null, /* value= */ title),
            new TextInformationFrame(/* id= */ "TP1", /* description= */ null, /* value= */ artist),
            new TextInformationFrame(
                /* id= */ "TAL", /* description= */ null, /* value= */ albumTitle),
            new TextInformationFrame(
                /* id= */ "TP2", /* description= */ null, /* value= */ albumArtist),
            new TextInformationFrame(
                /* id= */ "TRK", /* description= */ null, /* value= */ trackNumberInfo),
            new TextInformationFrame(/* id= */ "TYE", /* description= */ null, /* value= */ year));
    MediaMetadata.Builder builder = MediaMetadata.EMPTY.buildUpon();

    for (Metadata.Entry entry : entries) {
      entry.populateMediaMetadata(builder);
    }

    assertThat(builder.build().title.toString()).isEqualTo(title);
    assertThat(builder.build().artist.toString()).isEqualTo(artist);
    assertThat(builder.build().albumTitle.toString()).isEqualTo(albumTitle);
    assertThat(builder.build().albumArtist.toString()).isEqualTo(albumArtist);
    assertThat(builder.build().trackNumber).isEqualTo(11);
    assertThat(builder.build().totalTrackCount).isEqualTo(17);
    assertThat(builder.build().year).isEqualTo(2000);
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
