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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MediaMetadata}. */
@RunWith(AndroidJUnit4.class)
public class MediaMetadataTest {

  @Test
  public void builder_minimal_correctDefaults() {
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().build();

    assertThat(mediaMetadata.title).isNull();
  }

  @Test
  public void builderSetTitle_setsTitle() {
    String title = "title";

    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle(title).build();

    assertThat(mediaMetadata.title.toString()).isEqualTo(title);
  }

  @Test
  public void roundTripViaBundle_yieldsEqualInstance() {
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("title").build();

    assertThat(MediaMetadata.CREATOR.fromBundle(mediaMetadata.toBundle())).isEqualTo(mediaMetadata);
  }

  @Test
  public void builderPopulatedFromMetadataEntry_setsTitleCorrectly() {
    String title = "the title";
    Metadata.Entry entry =
        new TextInformationFrame(/* id= */ "TT2", /* description= */ null, /* value= */ title);
    MediaMetadata.Builder builder = MediaMetadata.EMPTY.buildUpon();

    entry.populateMediaMetadata(builder);
    assertThat(builder.build().title.toString()).isEqualTo(title);
  }
}
