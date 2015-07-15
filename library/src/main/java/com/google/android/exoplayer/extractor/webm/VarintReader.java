package com.google.android.exoplayer.extractor.webm;

import com.google.android.exoplayer.extractor.ExtractorInput;

import java.io.EOFException;
import java.io.IOException;

/**
 * Reads EBML variable-length integers (varints) from an {@link ExtractorInput}.
 */
/* package */ class VarintReader {

  private static final int STATE_BEGIN_READING = 0;
  private static final int STATE_READ_CONTENTS = 1;

  /**
   * The first byte of a variable-length integer (varint) will have one of these bit masks
   * indicating the total length in bytes.
   *
   * <p>{@code 0x80} is a one-byte integer, {@code 0x40} is two bytes, and so on up to eight bytes.
   */
  private static final int[] VARINT_LENGTH_MASKS = new int[] {
    0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01
  };

  private final byte[] scratch;

  private int state;
  private int length;

  public VarintReader() {
    scratch = new byte[8];
  }

  /**
   * Resets the reader to start reading a new variable-length integer.
   */
  public void reset() {
    state = STATE_BEGIN_READING;
    length = 0;
  }

  /**
   * Reads an EBML variable-length integer (varint) from an {@link ExtractorInput} such that
   * reading can be resumed later if an error occurs having read only some of it.
   * <p>
   * If an value is successfully read, then the reader will automatically reset itself ready to
   * read another value.
   * <p>
   * If an {@link IOException} or {@link InterruptedException} is throw, the read can be resumed
   * later by calling this method again, passing an {@link ExtractorInput} providing data starting
   * where the previous one left off.
   *
   * @param input The {@link ExtractorInput} from which the integer should be read.
   * @param allowEndOfInput True if encountering the end of the input having read no data is
   *     allowed, and should result in {@code -1} being returned. False if it should be
   *     considered an error, causing an {@link EOFException} to be thrown.
   * @param removeLengthMask Removes the variable-length integer length mask from the value
   * @return The read value, or -1 if {@code allowEndOfStream} is true and the end of the input was
   *     encountered.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  public long readUnsignedVarint(ExtractorInput input, boolean allowEndOfInput,
      boolean removeLengthMask) throws IOException, InterruptedException {
    if (state == STATE_BEGIN_READING) {
      // Read the first byte to establish the length.
      if (!input.readFully(scratch, 0, 1, allowEndOfInput)) {
        return -1;
      }
      int firstByte = scratch[0] & 0xFF;
      length = -1;
      for (int i = 0; i < VARINT_LENGTH_MASKS.length; i++) {
        if ((VARINT_LENGTH_MASKS[i] & firstByte) != 0) {
          length = i + 1;
          break;
        }
      }
      if (length == -1) {
        throw new IllegalStateException("No valid varint length mask found");
      }
      state = STATE_READ_CONTENTS;
    }

    // Read the remaining bytes.
    input.readFully(scratch, 1, length - 1);

    // Parse the value.
    if (removeLengthMask) {
      scratch[0] &= ~VARINT_LENGTH_MASKS[length - 1];
    }
    long varint = 0;
    for (int i = 0; i < length; i++) {
      varint = (varint << 8) | (scratch[i] & 0xFF);
    }
    state = STATE_BEGIN_READING;
    return varint;
  }

  /**
   * Returns the number of bytes occupied by the most recently parsed varint.
   */
  public int getLastLength() {
    return length;
  }

}
