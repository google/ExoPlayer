package com.google.android.exoplayer.extractor.flv;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import java.io.EOFException;
import java.io.IOException;

/**
 * Created by joliva on 9/26/15.
 */
public final class FlvExtractor implements Extractor {
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

  private static final int FLV_TAG = Util.getIntegerCodeForString("FLV");

  private final ParsableByteArray scratch;
  private final ParsableByteArray headerBuffer;
  private final ParsableByteArray tagHeaderBuffer;
  private ParsableByteArray tagData;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;

  private boolean hasAudio;
  private boolean hasVideo;
  private int dataOffset;

  private int parserState;
  private TagHeader currentTagHeader;

  private AudioTagReader audioReader;
  private VideoTagReader videoReader;
  private MetadataReader metadataReader;

  public FlvExtractor() {
    scratch = new ParsableByteArray(4);
    headerBuffer = new ParsableByteArray(FLV_MIN_HEADER_SIZE);
    tagHeaderBuffer = new ParsableByteArray(FLV_TAG_HEADER_SIZE);
    dataOffset = 0;
    hasAudio = false;
    hasVideo = false;
    currentTagHeader = new TagHeader();
  }

  @Override
  public void init(ExtractorOutput output) {
    this.extractorOutput = output;
    trackOutput = extractorOutput.track(0);
    extractorOutput.endTracks();

    output.seekMap(SeekMap.UNSEEKABLE);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    // Check if file starts with "FLV" tag
    input.peekFully(scratch.data, 0, 3);
    scratch.setPosition(0);
    if (scratch.readUnsignedInt24() != FLV_TAG) {
      return false;
    }
/*

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
    input.peekFully(scratch.data, 0, 1);
    scratch.setPosition(0);
    if (scratch.readInt() != 0) {
      return false;
    }
*/
    return true;
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
        switch (parserState) {
          case STATE_READING_TAG_HEADER:
            if (!readTagHeader(input)) {
              return RESULT_END_OF_INPUT;
            }
            break;

          default:
             return readSample(input, seekPosition);
        }
      }
    } catch (AudioTagReader.UnsupportedTrack unsupportedTrack) {
      unsupportedTrack.printStackTrace();
        return RESULT_END_OF_INPUT;

    }
  }

  @Override
  public void seek() {
    dataOffset = 0;
  }

  private boolean readHeader(ExtractorInput input) throws IOException, InterruptedException {
    try {
      input.readFully(headerBuffer.data, 0, FLV_MIN_HEADER_SIZE);
      headerBuffer.setPosition(0);
      headerBuffer.skipBytes(4);
      int flags = headerBuffer.readUnsignedByte();
      hasAudio = (flags & 0x04) != 0;
      hasVideo = (flags & 0x01) != 0;

      if (hasAudio) {
        audioReader = new AudioTagReader(trackOutput);
      }
      if (hasVideo) {
        //videoReader = new VideoTagReader(trackOutput);
      }
      metadataReader = new MetadataReader(trackOutput);

      dataOffset = headerBuffer.readInt();

      input.skipFully(dataOffset - FLV_MIN_HEADER_SIZE);
      parserState = STATE_READING_TAG_HEADER;
    } catch (EOFException eof) {
      return false;
    }

    return true;
  }

  private boolean readTagHeader(ExtractorInput input) throws IOException, InterruptedException,
      TagReader.UnsupportedTrack {
    try {
      input.skipFully(4);
      input.readFully(tagHeaderBuffer.data, 0, FLV_TAG_HEADER_SIZE);

      tagHeaderBuffer.setPosition(0);
      // skipping previous tag size field.
      int type = tagHeaderBuffer.readUnsignedByte();
      int dataSize = tagHeaderBuffer.readUnsignedInt24();
      long timestamp = tagHeaderBuffer.readUnsignedInt24();
      timestamp = (tagHeaderBuffer.readUnsignedByte() << 24) | timestamp;
      int streamId = tagHeaderBuffer.readUnsignedInt24();

      currentTagHeader.type = type;
      currentTagHeader.dataSize = dataSize;
      currentTagHeader.timestamp = timestamp * 1000;
      currentTagHeader.streamId = streamId;

      Assertions.checkState(dataSize <= Integer.MAX_VALUE);
      tagData = new ParsableByteArray((int) dataSize);
      parserState = STATE_READING_SAMPLE;

    } catch (EOFException eof) {
      return false;
    }

    return true;
  }

  private int readSample(ExtractorInput input, PositionHolder seekPosition) throws IOException,
      InterruptedException, AudioTagReader.UnsupportedTrack {
    if (tagData != null) {
      if (!input.readFully(tagData.data, 0, currentTagHeader.dataSize, true)) {
        return RESULT_END_OF_INPUT;
      }
      tagData.setPosition(0);
    } else {
      input.skipFully(currentTagHeader.dataSize);
      return RESULT_CONTINUE;
    }

    if (currentTagHeader.type == TAG_TYPE_AUDIO && audioReader != null) {
      audioReader.consume(tagData, currentTagHeader.timestamp);
    } else if (currentTagHeader.type == TAG_TYPE_VIDEO && videoReader != null) {
      videoReader.consume(tagData, currentTagHeader.timestamp);
    } else if (currentTagHeader.type == TAG_TYPE_SCRIPT_DATA && metadataReader != null) {
      metadataReader.consume(tagData, currentTagHeader.timestamp);
      if (metadataReader.durationUs != C.UNKNOWN_TIME_US) {
        if (audioReader != null) {
          audioReader.durationUs = metadataReader.durationUs;
        }
        if (videoReader != null) {
          videoReader.durationUs = metadataReader.durationUs;
        }
      }
    } else {
      tagData.reset();
    }

    parserState = STATE_READING_TAG_HEADER;

    return RESULT_CONTINUE;
  }

}
