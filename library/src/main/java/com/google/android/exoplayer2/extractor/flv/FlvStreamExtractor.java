package com.google.android.exoplayer2.extractor.flv;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;

/**
 * Facilitates the extraction of data from the FLV streaming.
 */
public class FlvStreamExtractor implements Extractor, SeekMap {

  /**
   * Factory for {@link FlvStreamExtractor} instances.
   */
  public static final ExtractorsFactory FACTORY = new ExtractorsFactory() {

      @Override
      public Extractor[] createExtractors() {
        return new Extractor[] {new FlvStreamExtractor()};
      }

  };

  // Parser states.
  private static final int STATE_READING_FLV_HEADER = 1;
  private static final int STATE_READING_TAG_HEADER = 2;
  private static final int STATE_READING_TAG_DATA = 3;

  // Tag types.
  private static final int TAG_TYPE_AUDIO = 8;
  private static final int TAG_TYPE_VIDEO = 9;

  // FLV streaming identifier.
  private static final int FLV_STREAMING_TAG = Util.getIntegerCodeForString("RTMP");
  private static final int FLV_STREAMING_TAG_SIZE = 4;

  // Temporary buffers.
  private final ParsableByteArray scratch;
  private final ParsableByteArray headerBuffer;
  private final ParsableByteArray tagHeaderBuffer;
  private final ParsableByteArray tagData;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;

  // State variables.
  private int parserState;
  public int tagType;
  public int tagDataSize;
  public long tagTimestampUs;

    // Tags readers.
  private AudioTagPayloadReader audioReader;
  private VideoTagPayloadReader videoReader;

  public FlvStreamExtractor() {
    scratch = new ParsableByteArray(FLV_STREAMING_TAG_SIZE);
    headerBuffer = new ParsableByteArray(FLV_STREAMING_TAG_SIZE);
    tagHeaderBuffer = new ParsableByteArray(9);
    tagData = new ParsableByteArray();
    parserState = STATE_READING_FLV_HEADER;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    // Check if file starts with "RTMP" tag
    input.peekFully(scratch.data, 0, FLV_STREAMING_TAG_SIZE);
    scratch.setPosition(0);
    if (Util.getIntegerCodeForString(scratch.readString(FLV_STREAMING_TAG_SIZE)) != FLV_STREAMING_TAG) {
      return false;
    }

    return true;
  }

  @Override
  public void init(ExtractorOutput output) {
    this.extractorOutput = output;
  }

  @Override
  public void seek(long position, long timeUs) {
    parserState = STATE_READING_FLV_HEADER;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException,
            InterruptedException {
    while (true) {
      switch (parserState) {
        case STATE_READING_FLV_HEADER:
          if (!readFlvHeader(input)) {
            return RESULT_END_OF_INPUT;
          }
          break;
        case STATE_READING_TAG_HEADER:
          if (!readTagHeader(input)) {
            return RESULT_END_OF_INPUT;
          }
          break;
        case STATE_READING_TAG_DATA:
          if (readTagData(input)) {
            return RESULT_CONTINUE;
          }
          break;
      }
    }
  }

  /**
   * Reads an FLV container header from the provided {@link ExtractorInput}.
   *
   * @return True if header was read successfully. False if the end of stream was reached.
   * @throws IOException If an error occurred reading or parsing data from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  private boolean readFlvHeader(ExtractorInput input) throws IOException, InterruptedException {
    if (!input.readFully(headerBuffer.data, 0, FLV_STREAMING_TAG_SIZE, true)) {
      // We've reached the end of the stream.
      return false;
    }

    if (audioReader == null) {
      audioReader = new AudioTagPayloadReader(extractorOutput.track(TAG_TYPE_AUDIO, C.TRACK_TYPE_AUDIO));
    }
    if (videoReader == null) {
      videoReader = new VideoTagPayloadReader(extractorOutput.track(TAG_TYPE_VIDEO, C.TRACK_TYPE_VIDEO));
    }
    extractorOutput.endTracks();
    extractorOutput.seekMap(this);

    parserState = STATE_READING_TAG_HEADER;
    return true;
  }

  /**
   * Reads a tag header from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @return True if tag header was read successfully. Otherwise, false.
   * @throws IOException If an error occurred reading or parsing data from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  private boolean readTagHeader(ExtractorInput input) throws IOException, InterruptedException {
    if (!input.readFully(tagHeaderBuffer.data, 0, tagHeaderBuffer.limit(), true)) {
      // We've reached the end of the stream.
      return false;
    }

    tagHeaderBuffer.setPosition(0);
    tagType = tagHeaderBuffer.readUnsignedByte();
    tagDataSize = tagHeaderBuffer.readInt();
    tagTimestampUs = tagHeaderBuffer.readInt() * 1000L;
    parserState = STATE_READING_TAG_DATA;
    return true;
  }

  /**
   * Reads the body of a tag from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @return True if the data was consumed by a reader. False if it was skipped.
   * @throws IOException If an error occurred reading or parsing data from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  private boolean readTagData(ExtractorInput input) throws IOException, InterruptedException {
    boolean wasConsumed = true;
    if (tagType == TAG_TYPE_AUDIO && audioReader != null) {
      audioReader.consume(prepareTagData(input), tagTimestampUs);
    } else if (tagType == TAG_TYPE_VIDEO && videoReader != null) {
      videoReader.consume(prepareTagData(input), tagTimestampUs);
    } else {
      input.skipFully(tagDataSize);
      wasConsumed = false;
    }
    parserState = STATE_READING_TAG_HEADER;
    return wasConsumed;
  }

  private ParsableByteArray prepareTagData(ExtractorInput input) throws IOException,
      InterruptedException {
    if (tagDataSize > tagData.capacity()) {
      tagData.reset(new byte[Math.max(tagData.capacity() * 2, tagDataSize)], 0);
    } else {
      tagData.setPosition(0);
    }
    tagData.setLimit(tagDataSize);
    input.readFully(tagData.data, 0, tagDataSize);
    return tagData;
  }

  // SeekMap implementation.

  @Override
  public boolean isSeekable() {
    return false;
  }

  @Override
  public long getDurationUs() {
    return C.TIME_UNSET;
  }

  @Override
  public long getPosition(long timeUs) {
    return 0;
  }
}
