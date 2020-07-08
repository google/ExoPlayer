package com.google.android.exoplayer2.extractor.ogg;

import com.google.android.exoplayer2.extractor.ExtractorInput;

/**
 * Ogg File read verification tool
 */
class OggReadCheckUtil {

  /**
   * Verify that the length of the read exceeds the file length
   * @param input {@link ExtractorInput}
   * @param readLength read length
   * @return true by File is incomplete
   */
  public static boolean checkReadLengthValidity(ExtractorInput input, int readLength) {
    return input.getPeekPosition() + readLength > input.getLength() ;
  }

  /**
   * Fix read length
   * @param input {@link ExtractorInput}
   * @return Length of read after fixed
   */
  public static int fixFileReadLength(ExtractorInput input) {
    return (int) (input.getLength() - input.getPeekPosition());
  }
}
