package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * StreamReader abstract class.
 */
/* package */ abstract class StreamReader {

  private static final int STATE_READ_HEADERS = 0;
  private static final int STATE_READ_PAYLOAD = 1;
  private static final int STATE_END_OF_INPUT = 2;

  static class SetupData {
    Format format;
    OggSeeker oggSeeker;
  }

  private OggPacket oggPacket;
  private TrackOutput trackOutput;
  private ExtractorOutput extractorOutput;
  private OggSeeker oggSeeker;
  private long targetGranule;
  private long payloadStartPosition;
  private long currentGranule;
  private int state;
  private int sampleRate;
  private SetupData setupData;
  private long lengthOfReadPacket;
  private boolean seekMapSet;

  void init(ExtractorOutput output, TrackOutput trackOutput) {
    this.extractorOutput = output;
    this.trackOutput = trackOutput;
    this.oggPacket = new OggPacket();
    this.setupData = new SetupData();

    this.state = STATE_READ_HEADERS;
    this.targetGranule = -1;
    this.payloadStartPosition = 0;
  }

  /**
   * @see Extractor#seek()
   */
  final void seek() {
    oggPacket.reset();

    if (state != STATE_READ_HEADERS) {
      targetGranule = oggSeeker.startSeek();
      state = STATE_READ_PAYLOAD;
    }
  }

  /**
   * @see Extractor#read(ExtractorInput, PositionHolder)
   */
  final int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    switch (state) {
      case STATE_READ_HEADERS:
        return readHeaders(input);

      case STATE_READ_PAYLOAD:
        return readPayload(input, seekPosition);

      default:
        // Never happens.
        throw new IllegalStateException();
    }
  }

  private int readHeaders(ExtractorInput input)
      throws IOException, InterruptedException {
    boolean readingHeaders = true;
    while (readingHeaders) {
      if (!oggPacket.populate(input)) {
        state = STATE_END_OF_INPUT;
        return Extractor.RESULT_END_OF_INPUT;
      }
      lengthOfReadPacket = input.getPosition() - payloadStartPosition;

      readingHeaders = readHeaders(oggPacket.getPayload(), payloadStartPosition, setupData);
      if (readingHeaders) {
        payloadStartPosition = input.getPosition();
      }
    }

    sampleRate = setupData.format.sampleRate;
    trackOutput.format(setupData.format);

    if (setupData.oggSeeker != null) {
      oggSeeker = setupData.oggSeeker;
    } else if (input.getLength() == C.LENGTH_UNBOUNDED) {
      oggSeeker = new UnseekableOggSeeker();
    } else {
      oggSeeker = new DefaultOggSeeker(payloadStartPosition, input.getLength(), this);
    }

    setupData = null;
    state = STATE_READ_PAYLOAD;
    return Extractor.RESULT_CONTINUE;
  }

  private int readPayload(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    long position = oggSeeker.read(input);
    if (position >= 0) {
      seekPosition.position = position;
      return Extractor.RESULT_SEEK;
    } else if (position < -1) {
      onSeekEnd(-position - 2);
    }
    if (!seekMapSet) {
      SeekMap seekMap = oggSeeker.createSeekMap();
      extractorOutput.seekMap(seekMap);
      seekMapSet = true;
    }

    if (lengthOfReadPacket > 0 || oggPacket.populate(input)) {
      lengthOfReadPacket = 0;
      ParsableByteArray payload = oggPacket.getPayload();
      long granulesInPacket = preparePayload(payload);
      if (granulesInPacket >= 0 && currentGranule + granulesInPacket >= targetGranule) {
        // calculate time and send payload data to codec
        long timeUs = convertGranuleToTime(currentGranule);
        trackOutput.sampleData(payload, payload.limit());
        trackOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, payload.limit(), 0, null);
        targetGranule = -1;
      }
      currentGranule += granulesInPacket;
    } else {
      state = STATE_END_OF_INPUT;
      return Extractor.RESULT_END_OF_INPUT;
    }
    return Extractor.RESULT_CONTINUE;
  }

  /**
   * Converts granule value to time.
   *
   * @param granule
   *     granule value.
   * @return Returns time in milliseconds.
   */
  protected long convertGranuleToTime(long granule) {
    return (granule * C.MICROS_PER_SECOND) / sampleRate;
  }

  /**
   * Converts time value to granule.
   *
   * @param timeUs
   *     Time in milliseconds.
   * @return Granule value.
   */
  protected long convertTimeToGranule(long timeUs) {
    return (sampleRate * timeUs) / C.MICROS_PER_SECOND;
  }

  /**
   * Prepares payload data in the packet for submitting to TrackOutput and returns number of
   * granules in the packet.
   *
   * @param packet
   *     Ogg payload data packet
   * @return Number of granules in the packet or -1 if the packet doesn't contain payload data.
   */
  protected abstract long preparePayload(ParsableByteArray packet);

  /**
   * Checks if the given packet is a header packet and reads it.
   *
   * @param packet An ogg packet.
   * @param position Position of the given header packet.
   * @param setupData Setup data to be filled.
   * @return Return true if the packet contains header data.
   */
  protected abstract boolean readHeaders(ParsableByteArray packet, long position,
      SetupData setupData) throws IOException, InterruptedException;

  /**
   * Called on end of seeking.
   *
   * @param currentGranule Current granule at the current position of input.
   */
  protected void onSeekEnd(long currentGranule) {
    this.currentGranule = currentGranule;
  }

  private class UnseekableOggSeeker implements OggSeeker {
    @Override
    public long read(ExtractorInput input) throws IOException, InterruptedException {
      return -1;
    }

    @Override
    public long startSeek() {
      return 0;
    }

    @Override
    public SeekMap createSeekMap() {
      return new SeekMap.Unseekable(C.UNSET_TIME_US);
    }
  }

}
