/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.flv;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import java.io.EOFException;
import java.io.IOException;

/**
 * Facilitates the extraction of data from the FLV container format.
 */
public final class FlvExtractor implements Extractor, SeekMap {
  // Header sizes
  private static final int FLV_MIN_HEADER_SIZE = 9;
  private static final int FLV_TAG_HEADER_SIZE = 11;

  // Parser states.
  private static final int STATE_READING_TAG_HEADER = 1;
  private static final int STATE_READING_SAMPLE = 2;

  // Tag types
  private static final int TAG_TYPE_AUDIO = 8;
  private static final int TAG_TYPE_VIDEO = 9;
  private static final int TAG_TYPE_SCRIPT_DATA = 18;

  // FLV container identifier
  private static final int FLV_TAG = Util.getIntegerCodeForString("FLV");

  // Temporary buffers
  private final ParsableByteArray scratch;
  private final ParsableByteArray headerBuffer;
  private final ParsableByteArray tagHeaderBuffer;
  private ParsableByteArray tagData;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;

  // State variables.
  private int parserState;
  private int dataOffset;
  private TagHeader currentTagHeader;

  // Tags readers
  private AudioTagPayloadReader audioReader;
  private VideoTagPayloadReader videoReader;
  private ScriptTagPayloadReader metadataReader;

  public FlvExtractor() {
    scratch = new ParsableByteArray(4);
    headerBuffer = new ParsableByteArray(FLV_MIN_HEADER_SIZE);
    tagHeaderBuffer = new ParsableByteArray(FLV_TAG_HEADER_SIZE);
    dataOffset = 0;
    currentTagHeader = new TagHeader();
  }

  @Override
  public void init(ExtractorOutput output) {
    this.extractorOutput = output;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    // Check if file starts with "FLV" tag
    input.peekFully(scratch.data, 0, 3);
    scratch.setPosition(0);
    if (scratch.readUnsignedInt24() != FLV_TAG) {
      return false;
    }

    // Checking reserved flags are set to 0
    input.peekFully(scratch.data, 0, 2);
    scratch.setPosition(0);
    if ((scratch.readUnsignedShort() & 0xFA) != 0) {
      return false;
    }

    // Read data offset
    input.peekFully(scratch.data, 0, 4);
    scratch.setPosition(0);
    int dataOffset = scratch.readInt();

    input.resetPeekPosition();
    input.advancePeekPosition(dataOffset);

    // Checking first "previous tag size" is set to 0
    input.peekFully(scratch.data, 0, 4);
    scratch.setPosition(0);

    return scratch.readInt() == 0;
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException,
      InterruptedException {
    if (dataOffset == 0
        && !readHeader(input)) {
      return RESULT_END_OF_INPUT;
    }

    try {
      while (true) {
        if (parserState == STATE_READING_TAG_HEADER) {
          if (!readTagHeader(input)) {
            return RESULT_END_OF_INPUT;
          }
        } else {
          return readSample(input);
        }
      }
    } catch (AudioTagPayloadReader.UnsupportedTrack unsupportedTrack) {
      unsupportedTrack.printStackTrace();
      return RESULT_END_OF_INPUT;
    }
  }

  @Override
  public void seek() {
    dataOffset = 0;
  }

  /**
   * Reads FLV container header from the provided {@link ExtractorInput}.
   * @param input The {@link ExtractorInput} from which to read.
   * @return True if header was read successfully. Otherwise, false.
   * @throws IOException If an error occurred reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  private boolean readHeader(ExtractorInput input) throws IOException, InterruptedException {
    try {
      input.readFully(headerBuffer.data, 0, FLV_MIN_HEADER_SIZE);
      headerBuffer.setPosition(0);
      headerBuffer.skipBytes(4);
      int flags = headerBuffer.readUnsignedByte();
      boolean hasAudio = (flags & 0x04) != 0;
      boolean hasVideo = (flags & 0x01) != 0;

      if (hasAudio && audioReader == null) {
        audioReader = new AudioTagPayloadReader(extractorOutput.track(TAG_TYPE_AUDIO));
      }
      if (hasVideo && videoReader == null) {
        videoReader = new VideoTagPayloadReader(extractorOutput.track(TAG_TYPE_VIDEO));
      }
      if (metadataReader == null) {
        metadataReader = new ScriptTagPayloadReader(null);
      }
      extractorOutput.endTracks();
      extractorOutput.seekMap(this);

      // Store payload start position and start extended header (if there is one)
      dataOffset = headerBuffer.readInt();

      input.skipFully(dataOffset - FLV_MIN_HEADER_SIZE);
      parserState = STATE_READING_TAG_HEADER;
    } catch (EOFException eof) {
      return false;
    }

    return true;
  }

  /**
   * Reads a tag header from the provided {@link ExtractorInput}.
   * @param input The {@link ExtractorInput} from which to read.
   * @return True if tag header was read successfully. Otherwise, false.
   * @throws IOException If an error occurred reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   * @throws TagPayloadReader.UnsupportedTrack If payload of the tag is using a codec non
   * supported codec.
   */
  private boolean readTagHeader(ExtractorInput input) throws IOException, InterruptedException,
      TagPayloadReader.UnsupportedTrack {
    try {
      // skipping previous tag size field
      input.skipFully(4);

      // Read the tag header from the input.
      input.readFully(tagHeaderBuffer.data, 0, FLV_TAG_HEADER_SIZE);

      tagHeaderBuffer.setPosition(0);
      int type = tagHeaderBuffer.readUnsignedByte();
      int dataSize = tagHeaderBuffer.readUnsignedInt24();
      long timestamp = tagHeaderBuffer.readUnsignedInt24();
      timestamp = (tagHeaderBuffer.readUnsignedByte() << 24) | timestamp;
      int streamId = tagHeaderBuffer.readUnsignedInt24();

      currentTagHeader.type = type;
      currentTagHeader.dataSize = dataSize;
      currentTagHeader.timestamp = timestamp * 1000;
      currentTagHeader.streamId = streamId;

      // Sanity checks.
      Assertions.checkState(type == TAG_TYPE_AUDIO || type == TAG_TYPE_VIDEO
          || type == TAG_TYPE_SCRIPT_DATA);
      // Reuse tagData buffer to avoid lot of memory allocation (performance penalty).
      if (tagData == null || dataSize > tagData.capacity()) {
        tagData = new ParsableByteArray(dataSize);
      } else {
        tagData.setPosition(0);
      }
      tagData.setLimit(dataSize);
      parserState = STATE_READING_SAMPLE;

    } catch (EOFException eof) {
      return false;
    }

    return true;
  }

  /**
   * Reads payload of an FLV tag from the provided {@link ExtractorInput}.
   * @param input The {@link ExtractorInput} from which to read.
   * @return One of {@link Extractor#RESULT_CONTINUE} and {@link Extractor#RESULT_END_OF_INPUT}.
   * @throws IOException If an error occurred reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   * @throws TagPayloadReader.UnsupportedTrack If payload of the tag is using a codec non
   * supported codec.
   */
  private int readSample(ExtractorInput input) throws IOException,
      InterruptedException, AudioTagPayloadReader.UnsupportedTrack {
    if (tagData != null) {
      if (!input.readFully(tagData.data, 0, currentTagHeader.dataSize, true)) {
        return RESULT_END_OF_INPUT;
      }
      tagData.setPosition(0);
    } else {
      input.skipFully(currentTagHeader.dataSize);
      return RESULT_CONTINUE;
    }

    // Pass payload to the right payload reader.
    if (currentTagHeader.type == TAG_TYPE_AUDIO && audioReader != null) {
      audioReader.consume(tagData, currentTagHeader.timestamp);
    } else if (currentTagHeader.type == TAG_TYPE_VIDEO && videoReader != null) {
      videoReader.consume(tagData, currentTagHeader.timestamp);
    } else if (currentTagHeader.type == TAG_TYPE_SCRIPT_DATA && metadataReader != null) {
      metadataReader.consume(tagData, currentTagHeader.timestamp);
      if (metadataReader.getDurationUs() != C.UNKNOWN_TIME_US) {
        if (audioReader != null) {
          audioReader.setDurationUs(metadataReader.getDurationUs());
        }
        if (videoReader != null) {
          videoReader.setDurationUs(metadataReader.getDurationUs());
        }
      }
    } else {
      tagData.reset();
    }

    parserState = STATE_READING_TAG_HEADER;

    return RESULT_CONTINUE;
  }

  // SeekMap implementation.
  // TODO: Add seeking support
  @Override
  public boolean isSeekable() {
    return false;
  }

  @Override
  public long getPosition(long timeUs) {
    return 0;
  }


  /**
   * Defines header of a FLV tag
   */
  final class TagHeader {
    public int type;
    public int dataSize;
    public long timestamp;
    public int streamId;
  }


}
