/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.exoplayer2.extractor;

import static com.google.android.exoplayer2.testutil.TestUtil.getByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.metadata.id3.CommentFrame;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Id3Peeker}. */
@RunWith(AndroidJUnit4.class)
public final class Id3PeekerTest {

  @Test
  public void peekId3Data_returnNull_ifId3TagNotPresentAtBeginningOfInput() throws IOException {
    Id3Peeker id3Peeker = new Id3Peeker();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(new byte[] {1, 'I', 'D', '3', 2, 3, 4, 5, 6, 7, 8, 9, 10})
            .build();

    @Nullable Metadata metadata = id3Peeker.peekId3Data(input, /* id3FramePredicate= */ null);
    assertThat(metadata).isNull();
  }

  @Test
  public void peekId3Data_returnId3Tag_ifId3TagPresent() throws IOException {
    Id3Peeker id3Peeker = new Id3Peeker();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                getByteArray(ApplicationProvider.getApplicationContext(), "media/id3/apic.id3"))
            .build();

    @Nullable Metadata metadata = id3Peeker.peekId3Data(input, /* id3FramePredicate= */ null);
    assertThat(metadata).isNotNull();
    assertThat(metadata.length()).isEqualTo(1);

    ApicFrame apicFrame = (ApicFrame) metadata.get(0);
    assertThat(apicFrame.mimeType).isEqualTo("image/jpeg");
    assertThat(apicFrame.pictureType).isEqualTo(16);
    assertThat(apicFrame.description).isEqualTo("Hello World");
    assertThat(apicFrame.pictureData).hasLength(10);
    assertThat(apicFrame.pictureData).isEqualTo(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0});
  }

  @Test
  public void peekId3Data_returnId3TagAccordingToGivenPredicate_ifId3TagPresent()
      throws IOException {
    Id3Peeker id3Peeker = new Id3Peeker();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                getByteArray(
                    ApplicationProvider.getApplicationContext(), "media/id3/comm_apic.id3"))
            .build();

    @Nullable
    Metadata metadata =
        id3Peeker.peekId3Data(
            input,
            (majorVersion, id0, id1, id2, id3) ->
                id0 == 'C' && id1 == 'O' && id2 == 'M' && id3 == 'M');
    assertThat(metadata).isNotNull();
    assertThat(metadata.length()).isEqualTo(1);

    CommentFrame commentFrame = (CommentFrame) metadata.get(0);
    assertThat(commentFrame.language).isEqualTo("eng");
    assertThat(commentFrame.description).isEqualTo("description");
    assertThat(commentFrame.text).isEqualTo("text");
  }
}
