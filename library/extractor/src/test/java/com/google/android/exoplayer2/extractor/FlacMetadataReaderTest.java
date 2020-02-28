/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.FlacMetadataReader.FlacStreamMetadataHolder;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.flac.PictureFrame;
import com.google.android.exoplayer2.metadata.flac.VorbisComment;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.FlacConstants;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link FlacMetadataReader}.
 *
 * <p>Most expected results in these tests have been retrieved using the <a
 * href="https://xiph.org/flac/documentation_tools_metaflac.html">metaflac</a> command.
 */
@RunWith(AndroidJUnit4.class)
public class FlacMetadataReaderTest {

  @Test
  public void peekId3Metadata_updatesPeekPosition() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_with_id3.flac");

    FlacMetadataReader.peekId3Metadata(input, /* parseData= */ false);

    assertThat(input.getPosition()).isEqualTo(0);
    assertThat(input.getPeekPosition()).isNotEqualTo(0);
  }

  @Test
  public void peekId3Metadata_parseData_returnsNonEmptyMetadata() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_with_id3.flac");

    Metadata metadata = FlacMetadataReader.peekId3Metadata(input, /* parseData= */ true);

    assertThat(metadata).isNotNull();
    assertThat(metadata.length()).isNotEqualTo(0);
  }

  @Test
  public void peekId3Metadata_doNotParseData_returnsNull() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_with_id3.flac");

    Metadata metadata = FlacMetadataReader.peekId3Metadata(input, /* parseData= */ false);

    assertThat(metadata).isNull();
  }

  @Test
  public void peekId3Metadata_noId3Metadata_returnsNull() throws Exception {
    String fileWithoutId3Metadata = "flac/bear.flac";
    ExtractorInput input = buildExtractorInput(fileWithoutId3Metadata);

    Metadata metadata = FlacMetadataReader.peekId3Metadata(input, /* parseData= */ true);

    assertThat(metadata).isNull();
  }

  @Test
  public void checkAndPeekStreamMarker_updatesPeekPosition() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");

    FlacMetadataReader.checkAndPeekStreamMarker(input);

    assertThat(input.getPosition()).isEqualTo(0);
    assertThat(input.getPeekPosition()).isEqualTo(FlacConstants.STREAM_MARKER_SIZE);
  }

  @Test
  public void checkAndPeekStreamMarker_validData_isTrue() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");

    boolean result = FlacMetadataReader.checkAndPeekStreamMarker(input);

    assertThat(result).isTrue();
  }

  @Test
  public void checkAndPeekStreamMarker_invalidData_isFalse() throws Exception {
    ExtractorInput input = buildExtractorInput("mp3/bear-vbr-xing-header.mp3");

    boolean result = FlacMetadataReader.checkAndPeekStreamMarker(input);

    assertThat(result).isFalse();
  }

  @Test
  public void readId3Metadata_updatesReadPositionAndAlignsPeekPosition() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_with_id3.flac");
    // Advance peek position after ID3 metadata.
    FlacMetadataReader.peekId3Metadata(input, /* parseData= */ false);
    input.advancePeekPosition(1);

    FlacMetadataReader.readId3Metadata(input, /* parseData= */ false);

    assertThat(input.getPosition()).isNotEqualTo(0);
    assertThat(input.getPeekPosition()).isEqualTo(input.getPosition());
  }

  @Test
  public void readId3Metadata_parseData_returnsNonEmptyMetadata() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_with_id3.flac");

    Metadata metadata = FlacMetadataReader.readId3Metadata(input, /* parseData= */ true);

    assertThat(metadata).isNotNull();
    assertThat(metadata.length()).isNotEqualTo(0);
  }

  @Test
  public void readId3Metadata_doNotParseData_returnsNull() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_with_id3.flac");

    Metadata metadata = FlacMetadataReader.readId3Metadata(input, /* parseData= */ false);

    assertThat(metadata).isNull();
  }

  @Test
  public void readId3Metadata_noId3Metadata_returnsNull() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");

    Metadata metadata = FlacMetadataReader.readId3Metadata(input, /* parseData= */ true);

    assertThat(metadata).isNull();
  }

  @Test
  public void readStreamMarker_updatesReadPosition() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");

    FlacMetadataReader.readStreamMarker(input);

    assertThat(input.getPosition()).isEqualTo(FlacConstants.STREAM_MARKER_SIZE);
    assertThat(input.getPeekPosition()).isEqualTo(input.getPosition());
  }

  @Test
  public void readStreamMarker_invalidData_throwsException() throws Exception {
    ExtractorInput input = buildExtractorInput("mp3/bear-vbr-xing-header.mp3");

    assertThrows(ParserException.class, () -> FlacMetadataReader.readStreamMarker(input));
  }

  @Test
  public void readMetadataBlock_updatesReadPositionAndAlignsPeekPosition() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    input.skipFully(FlacConstants.STREAM_MARKER_SIZE);
    // Advance peek position after metadata block.
    input.advancePeekPosition(FlacConstants.STREAM_INFO_BLOCK_SIZE + 1);

    FlacMetadataReader.readMetadataBlock(
        input, new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null));

    assertThat(input.getPosition()).isNotEqualTo(0);
    assertThat(input.getPeekPosition()).isEqualTo(input.getPosition());
  }

  @Test
  public void readMetadataBlock_lastMetadataBlock_isTrue() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_one_metadata_block.flac");
    input.skipFully(FlacConstants.STREAM_MARKER_SIZE);

    boolean result =
        FlacMetadataReader.readMetadataBlock(
            input, new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null));

    assertThat(result).isTrue();
  }

  @Test
  public void readMetadataBlock_notLastMetadataBlock_isFalse() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    input.skipFully(FlacConstants.STREAM_MARKER_SIZE);

    boolean result =
        FlacMetadataReader.readMetadataBlock(
            input, new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null));

    assertThat(result).isFalse();
  }

  @Test
  public void readMetadataBlock_streamInfoBlock_setsStreamMetadata() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    input.skipFully(FlacConstants.STREAM_MARKER_SIZE);
    FlacStreamMetadataHolder metadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);

    FlacMetadataReader.readMetadataBlock(input, metadataHolder);

    assertThat(metadataHolder.flacStreamMetadata).isNotNull();
    assertThat(metadataHolder.flacStreamMetadata.sampleRate).isEqualTo(48000);
  }

  @Test
  public void readMetadataBlock_seekTableBlock_updatesStreamMetadata() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    // Skip to seek table block.
    input.skipFully(FlacConstants.STREAM_MARKER_SIZE + FlacConstants.STREAM_INFO_BLOCK_SIZE);
    FlacStreamMetadataHolder metadataHolder = new FlacStreamMetadataHolder(buildStreamMetadata());
    long originalSampleRate = metadataHolder.flacStreamMetadata.sampleRate;

    FlacMetadataReader.readMetadataBlock(input, metadataHolder);

    assertThat(metadataHolder.flacStreamMetadata).isNotNull();
    // Check that metadata passed has not been erased.
    assertThat(metadataHolder.flacStreamMetadata.sampleRate).isEqualTo(originalSampleRate);
    assertThat(metadataHolder.flacStreamMetadata.seekTable).isNotNull();
    assertThat(metadataHolder.flacStreamMetadata.seekTable.pointSampleNumbers.length).isEqualTo(32);
  }

  @Test
  public void readMetadataBlock_vorbisCommentBlock_updatesStreamMetadata() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_with_vorbis_comments.flac");
    // Skip to Vorbis comment block.
    input.skipFully(640);
    FlacStreamMetadataHolder metadataHolder = new FlacStreamMetadataHolder(buildStreamMetadata());
    long originalSampleRate = metadataHolder.flacStreamMetadata.sampleRate;

    FlacMetadataReader.readMetadataBlock(input, metadataHolder);

    assertThat(metadataHolder.flacStreamMetadata).isNotNull();
    // Check that metadata passed has not been erased.
    assertThat(metadataHolder.flacStreamMetadata.sampleRate).isEqualTo(originalSampleRate);
    Metadata metadata =
        metadataHolder.flacStreamMetadata.getMetadataCopyWithAppendedEntriesFrom(null);
    assertThat(metadata).isNotNull();
    VorbisComment vorbisComment = (VorbisComment) metadata.get(0);
    assertThat(vorbisComment.key).isEqualTo("TITLE");
    assertThat(vorbisComment.value).isEqualTo("test title");
  }

  @Test
  public void readMetadataBlock_pictureBlock_updatesStreamMetadata() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_with_picture.flac");
    // Skip to picture block.
    input.skipFully(640);
    FlacStreamMetadataHolder metadataHolder = new FlacStreamMetadataHolder(buildStreamMetadata());
    long originalSampleRate = metadataHolder.flacStreamMetadata.sampleRate;

    FlacMetadataReader.readMetadataBlock(input, metadataHolder);

    assertThat(metadataHolder.flacStreamMetadata).isNotNull();
    // Check that metadata passed has not been erased.
    assertThat(metadataHolder.flacStreamMetadata.sampleRate).isEqualTo(originalSampleRate);
    Metadata metadata =
        metadataHolder.flacStreamMetadata.getMetadataCopyWithAppendedEntriesFrom(null);
    assertThat(metadata).isNotNull();
    PictureFrame pictureFrame = (PictureFrame) metadata.get(0);
    assertThat(pictureFrame.pictureType).isEqualTo(3);
    assertThat(pictureFrame.mimeType).isEqualTo("image/png");
    assertThat(pictureFrame.description).isEqualTo("");
    assertThat(pictureFrame.width).isEqualTo(371);
    assertThat(pictureFrame.height).isEqualTo(320);
    assertThat(pictureFrame.depth).isEqualTo(24);
    assertThat(pictureFrame.colors).isEqualTo(0);
    assertThat(pictureFrame.pictureData).hasLength(30943);
  }

  @Test
  public void readMetadataBlock_blockToSkip_updatesReadPosition() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    // Skip to padding block.
    input.skipFully(640);
    FlacStreamMetadataHolder metadataHolder = new FlacStreamMetadataHolder(buildStreamMetadata());

    FlacMetadataReader.readMetadataBlock(input, metadataHolder);

    assertThat(input.getPosition()).isGreaterThan(640);
    assertThat(input.getPeekPosition()).isEqualTo(input.getPosition());
  }

  @Test
  public void readMetadataBlock_nonStreamInfoBlockWithNullStreamMetadata_throwsException()
      throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    // Skip to seek table block.
    input.skipFully(FlacConstants.STREAM_MARKER_SIZE + FlacConstants.STREAM_INFO_BLOCK_SIZE);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            FlacMetadataReader.readMetadataBlock(
                input, new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null)));
  }

  @Test
  public void readSeekTableMetadataBlock_updatesPosition() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    // Skip to seek table block.
    input.skipFully(FlacConstants.STREAM_MARKER_SIZE + FlacConstants.STREAM_INFO_BLOCK_SIZE);
    int seekTableBlockSize = 598;
    ParsableByteArray scratch = new ParsableByteArray(seekTableBlockSize);
    input.read(scratch.data, 0, seekTableBlockSize);

    FlacMetadataReader.readSeekTableMetadataBlock(scratch);

    assertThat(scratch.getPosition()).isEqualTo(seekTableBlockSize);
  }

  @Test
  public void readSeekTableMetadataBlock_returnsCorrectSeekPoints() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    // Skip to seek table block.
    input.skipFully(FlacConstants.STREAM_MARKER_SIZE + FlacConstants.STREAM_INFO_BLOCK_SIZE);
    int seekTableBlockSize = 598;
    ParsableByteArray scratch = new ParsableByteArray(seekTableBlockSize);
    input.read(scratch.data, 0, seekTableBlockSize);

    FlacStreamMetadata.SeekTable seekTable = FlacMetadataReader.readSeekTableMetadataBlock(scratch);

    assertThat(seekTable.pointOffsets[0]).isEqualTo(0);
    assertThat(seekTable.pointSampleNumbers[0]).isEqualTo(0);
    assertThat(seekTable.pointOffsets[31]).isEqualTo(160602);
    assertThat(seekTable.pointSampleNumbers[31]).isEqualTo(126976);
  }

  @Test
  public void readSeekTableMetadataBlock_ignoresPlaceholders() throws IOException {
    byte[] fileData =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), "flac/bear.flac");
    ParsableByteArray scratch = new ParsableByteArray(fileData);
    // Skip to seek table block.
    scratch.skipBytes(FlacConstants.STREAM_MARKER_SIZE + FlacConstants.STREAM_INFO_BLOCK_SIZE);

    FlacStreamMetadata.SeekTable seekTable = FlacMetadataReader.readSeekTableMetadataBlock(scratch);

    // Seek point at index 32 is a placeholder.
    assertThat(seekTable.pointSampleNumbers).hasLength(32);
  }

  @Test
  public void getFrameStartMarker_doesNotUpdateReadPositionAndAlignsPeekPosition()
      throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    int firstFramePosition = 8880;
    input.skipFully(firstFramePosition);
    // Advance the peek position after the frame start marker.
    input.advancePeekPosition(3);

    FlacMetadataReader.getFrameStartMarker(input);

    assertThat(input.getPosition()).isEqualTo(firstFramePosition);
    assertThat(input.getPeekPosition()).isEqualTo(input.getPosition());
  }

  @Test
  public void getFrameStartMarker_returnsCorrectFrameStartMarker() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");
    // Skip to first frame.
    input.skipFully(8880);

    int result = FlacMetadataReader.getFrameStartMarker(input);

    assertThat(result).isEqualTo(0xFFF8);
  }

  @Test
  public void getFrameStartMarker_invalidData_throwsException() throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear.flac");

    // Input position is incorrect.
    assertThrows(ParserException.class, () -> FlacMetadataReader.getFrameStartMarker(input));
  }

  private static ExtractorInput buildExtractorInput(String file) throws IOException {
    byte[] fileData = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), file);
    return new FakeExtractorInput.Builder().setData(fileData).build();
  }

  private static FlacStreamMetadata buildStreamMetadata() {
    return new FlacStreamMetadata(
        /* minBlockSizeSamples= */ 10,
        /* maxBlockSizeSamples= */ 20,
        /* minFrameSize= */ 5,
        /* maxFrameSize= */ 10,
        /* sampleRate= */ 44100,
        /* channels= */ 2,
        /* bitsPerSample= */ 8,
        /* totalSamples= */ 1000,
        /* vorbisComments= */ new ArrayList<>(),
        /* pictureFrames= */ new ArrayList<>());
  }
}
