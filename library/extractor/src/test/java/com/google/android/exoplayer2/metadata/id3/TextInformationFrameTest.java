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
import static org.junit.Assert.assertThrows;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TextInformationFrame}. */
@RunWith(AndroidJUnit4.class)
public class TextInformationFrameTest {

  @Test
  public void parcelable() {
    TextInformationFrame textInformationFrameToParcel =
        new TextInformationFrame("", "", ImmutableList.of(""));

    Parcel parcel = Parcel.obtain();
    textInformationFrameToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    TextInformationFrame textInformationFrameFromParcel =
        TextInformationFrame.CREATOR.createFromParcel(parcel);
    assertThat(textInformationFrameFromParcel).isEqualTo(textInformationFrameToParcel);

    parcel.recycle();
  }

  @Test
  public void populateMediaMetadata_setsBuilderValues() {
    String title = "the title";
    String artist = "artist";
    String albumTitle = "album title";
    String albumArtist = "album Artist";
    String trackNumberInfo = "11/17";
    String recordingYear = "2000";
    String recordingMonth = "07";
    String recordingDay = "10";
    String releaseDate = "2001-01-02T00:00:00";
    String composer = "composer";
    String conductor = "conductor";
    String writer = "writer";

    List<Metadata.Entry> entries =
        ImmutableList.of(
            new TextInformationFrame(
                /* id= */ "TT2", /* description= */ null, /* values= */ ImmutableList.of(title)),
            new TextInformationFrame(
                /* id= */ "TP1", /* description= */ null, /* values= */ ImmutableList.of(artist)),
            new TextInformationFrame(
                /* id= */ "TAL",
                /* description= */ null,
                /* values= */ ImmutableList.of(albumTitle)),
            new TextInformationFrame(
                /* id= */ "TP2",
                /* description= */ null,
                /* values= */ ImmutableList.of(albumArtist)),
            new TextInformationFrame(
                /* id= */ "TRK",
                /* description= */ null,
                /* values= */ ImmutableList.of(trackNumberInfo)),
            new TextInformationFrame(
                /* id= */ "TYE",
                /* description= */ null,
                /* values= */ ImmutableList.of(recordingYear)),
            new TextInformationFrame(
                /* id= */ "TDA",
                /* description= */ null,
                /* values= */ ImmutableList.of(recordingDay + recordingMonth)),
            new TextInformationFrame(
                /* id= */ "TDRL",
                /* description= */ null,
                /* values= */ ImmutableList.of(releaseDate)),
            new TextInformationFrame(
                /* id= */ "TCM", /* description= */ null, /* values= */ ImmutableList.of(composer)),
            new TextInformationFrame(
                /* id= */ "TP3",
                /* description= */ null,
                /* values= */ ImmutableList.of(conductor)),
            new TextInformationFrame(
                /* id= */ "TXT", /* description= */ null, /* values= */ ImmutableList.of(writer)));
    MediaMetadata.Builder builder = MediaMetadata.EMPTY.buildUpon();

    for (Metadata.Entry entry : entries) {
      entry.populateMediaMetadata(builder);
    }

    MediaMetadata mediaMetadata = builder.build();

    assertThat(mediaMetadata.title.toString()).isEqualTo(title);
    assertThat(mediaMetadata.artist.toString()).isEqualTo(artist);
    assertThat(mediaMetadata.albumTitle.toString()).isEqualTo(albumTitle);
    assertThat(mediaMetadata.albumArtist.toString()).isEqualTo(albumArtist);
    assertThat(mediaMetadata.trackNumber).isEqualTo(11);
    assertThat(mediaMetadata.totalTrackCount).isEqualTo(17);
    assertThat(mediaMetadata.recordingYear).isEqualTo(2000);
    assertThat(mediaMetadata.recordingMonth).isEqualTo(7);
    assertThat(mediaMetadata.recordingDay).isEqualTo(10);
    assertThat(mediaMetadata.releaseYear).isEqualTo(2001);
    assertThat(mediaMetadata.releaseMonth).isEqualTo(1);
    assertThat(mediaMetadata.releaseDay).isEqualTo(2);
    assertThat(mediaMetadata.composer.toString()).isEqualTo(composer);
    assertThat(mediaMetadata.conductor.toString()).isEqualTo(conductor);
    assertThat(mediaMetadata.writer.toString()).isEqualTo(writer);
  }

  @Test
  public void emptyValuesListThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TextInformationFrame("TXXX", "description", ImmutableList.of()));
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated field
  public void deprecatedValueStillPopulated() {
    TextInformationFrame frame =
        new TextInformationFrame("TXXX", "description", ImmutableList.of("value"));

    assertThat(frame.value).isEqualTo("value");
    assertThat(frame.values).containsExactly("value");
  }

  @Test
  @SuppressWarnings({"deprecation", "InlineMeInliner"}) // Testing deprecated constructor
  public void deprecatedConstructorPopulatesValuesList() {
    TextInformationFrame frame = new TextInformationFrame("TXXX", "description", "value");

    assertThat(frame.value).isEqualTo("value");
    assertThat(frame.values).containsExactly("value");
  }

  @Test
  @SuppressWarnings({"deprecation", "InlineMeInliner"}) // Testing deprecated constructor
  public void deprecatedConstructorCreatesEqualInstance() {
    TextInformationFrame frame1 = new TextInformationFrame("TXXX", "description", "value");
    TextInformationFrame frame2 =
        new TextInformationFrame("TXXX", "description", ImmutableList.of("value"));

    assertThat(frame1).isEqualTo(frame2);
    assertThat(frame1.hashCode()).isEqualTo(frame2.hashCode());
  }
}
