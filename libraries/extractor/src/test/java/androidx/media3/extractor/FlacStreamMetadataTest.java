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
package androidx.media3.extractor;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.Metadata;
import androidx.media3.extractor.flac.FlacConstants;
import androidx.media3.extractor.metadata.vorbis.VorbisComment;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link FlacStreamMetadata}. */
@RunWith(AndroidJUnit4.class)
public final class FlacStreamMetadataTest {

  @Test
  public void constructFromByteArray_setsFieldsCorrectly() throws IOException {
    byte[] fileData =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), "media/flac/bear.flac");

    FlacStreamMetadata streamMetadata =
        new FlacStreamMetadata(
            fileData, FlacConstants.STREAM_MARKER_SIZE + FlacConstants.METADATA_BLOCK_HEADER_SIZE);

    assertThat(streamMetadata.minBlockSizeSamples).isEqualTo(4096);
    assertThat(streamMetadata.maxBlockSizeSamples).isEqualTo(4096);
    assertThat(streamMetadata.minFrameSize).isEqualTo(445);
    assertThat(streamMetadata.maxFrameSize).isEqualTo(5776);
    assertThat(streamMetadata.sampleRate).isEqualTo(48000);
    assertThat(streamMetadata.sampleRateLookupKey).isEqualTo(10);
    assertThat(streamMetadata.channels).isEqualTo(2);
    assertThat(streamMetadata.bitsPerSample).isEqualTo(16);
    assertThat(streamMetadata.bitsPerSampleLookupKey).isEqualTo(4);
    assertThat(streamMetadata.totalSamples).isEqualTo(131568);
  }

  @Test
  public void parseVorbisComments() {
    ArrayList<String> commentsList = new ArrayList<>();
    commentsList.add("Title=Song");
    commentsList.add("Artist=Singer");

    Metadata metadata =
        new FlacStreamMetadata(
                /* minBlockSizeSamples= */ 0,
                /* maxBlockSizeSamples= */ 0,
                /* minFrameSize= */ 0,
                /* maxFrameSize= */ 0,
                /* sampleRate= */ 0,
                /* channels= */ 0,
                /* bitsPerSample= */ 0,
                /* totalSamples= */ 0,
                commentsList,
                /* pictureFrames= */ new ArrayList<>())
            .getMetadataCopyWithAppendedEntriesFrom(/* other= */ null);

    assertThat(metadata.length()).isEqualTo(2);
    VorbisComment commentFrame = (VorbisComment) metadata.get(0);
    assertThat(commentFrame.key).isEqualTo("TITLE");
    assertThat(commentFrame.value).isEqualTo("Song");
    commentFrame = (VorbisComment) metadata.get(1);
    assertThat(commentFrame.key).isEqualTo("ARTIST");
    assertThat(commentFrame.value).isEqualTo("Singer");
  }

  @Test
  public void parseEmptyVorbisComments() {
    ArrayList<String> commentsList = new ArrayList<>();

    Metadata metadata =
        new FlacStreamMetadata(
                /* minBlockSizeSamples= */ 0,
                /* maxBlockSizeSamples= */ 0,
                /* minFrameSize= */ 0,
                /* maxFrameSize= */ 0,
                /* sampleRate= */ 0,
                /* channels= */ 0,
                /* bitsPerSample= */ 0,
                /* totalSamples= */ 0,
                commentsList,
                /* pictureFrames= */ new ArrayList<>())
            .getMetadataCopyWithAppendedEntriesFrom(/* other= */ null);

    assertThat(metadata).isNull();
  }

  @Test
  public void parseVorbisCommentWithEqualsInValue() {
    ArrayList<String> commentsList = new ArrayList<>();
    commentsList.add("Title=So=ng");

    Metadata metadata =
        new FlacStreamMetadata(
                /* minBlockSizeSamples= */ 0,
                /* maxBlockSizeSamples= */ 0,
                /* minFrameSize= */ 0,
                /* maxFrameSize= */ 0,
                /* sampleRate= */ 0,
                /* channels= */ 0,
                /* bitsPerSample= */ 0,
                /* totalSamples= */ 0,
                commentsList,
                /* pictureFrames= */ new ArrayList<>())
            .getMetadataCopyWithAppendedEntriesFrom(/* other= */ null);

    assertThat(metadata.length()).isEqualTo(1);
    VorbisComment commentFrame = (VorbisComment) metadata.get(0);
    assertThat(commentFrame.key).isEqualTo("TITLE");
    assertThat(commentFrame.value).isEqualTo("So=ng");
  }

  @Test
  public void parseInvalidVorbisComment() {
    ArrayList<String> commentsList = new ArrayList<>();
    commentsList.add("TitleSong");
    commentsList.add("Artist=Singer");

    Metadata metadata =
        new FlacStreamMetadata(
                /* minBlockSizeSamples= */ 0,
                /* maxBlockSizeSamples= */ 0,
                /* minFrameSize= */ 0,
                /* maxFrameSize= */ 0,
                /* sampleRate= */ 0,
                /* channels= */ 0,
                /* bitsPerSample= */ 0,
                /* totalSamples= */ 0,
                commentsList,
                /* pictureFrames= */ new ArrayList<>())
            .getMetadataCopyWithAppendedEntriesFrom(/* other= */ null);

    assertThat(metadata.length()).isEqualTo(1);
    VorbisComment commentFrame = (VorbisComment) metadata.get(0);
    assertThat(commentFrame.key).isEqualTo("ARTIST");
    assertThat(commentFrame.value).isEqualTo("Singer");
  }
}
