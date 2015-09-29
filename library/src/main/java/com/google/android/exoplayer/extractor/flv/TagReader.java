package com.google.android.exoplayer.extractor.flv;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Extracts individual samples from FLV tags.
 */
/* package */ abstract class TagReader {

  protected final TrackOutput output;
  public long durationUs;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  protected TagReader(TrackOutput output) {
    this.output = output;
    this.durationUs = C.UNKNOWN_TIME_US;
  }

  /**
   * Notifies the reader that a seek has occurred.
   * <p>
   * Following a call to this method, the data passed to the next invocation of
   * {@link #consume(ParsableByteArray, long)} will not be a continuation of the data that
   * was previously passed. Hence the reader should reset any internal state.
   */
  public abstract void seek();

  /**
   * Parses tag header
   * @param data Buffer where the tag header is stored
   */
  protected abstract void parseHeader(ParsableByteArray data) throws UnsupportedTrack;

  /**
   * Parses tag payload
   * @param data Buffer where tag payload is stored
   * @param timeUs Time position of the frame
   */
  protected abstract void parsePayload(ParsableByteArray data, long timeUs);

  /**
   * Evaluate if for the current tag, payload should be parsed
   * @return
   */
  protected abstract boolean shouldParsePayload();

  /**
   * Consumes (possibly partial) payload data.
   *
   * @param data The payload data to consume.
   * @param timeUs The timestamp associated with the payload.
   */
  public void consume(ParsableByteArray data, long timeUs) throws UnsupportedTrack {
    parseHeader(data);

    if (shouldParsePayload()) {
      parsePayload(data, timeUs);
    }
  }

  /**
   * Thrown when format described in the AudioTrack is not supported
   */
  public static final class UnsupportedTrack extends Exception {

    public UnsupportedTrack(String msg) {
      super(msg);
    }

  }
}
