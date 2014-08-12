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
package com.google.android.exoplayer.parser.mp4;

import com.google.android.exoplayer.upstream.NonBlockingInputStream;

/**
 * A holder for information corresponding to a single fragment of an mp4 file.
 */
/* package */ final class TrackFragment {

  public int sampleDescriptionIndex;

  /**
   * The number of samples contained by the fragment.
   */
  public int length;
  /**
   * The size of each sample in the run.
   */
  public int[] sampleSizeTable;
  /**
   * The decoding time of each sample in the run.
   */
  public int[] sampleDecodingTimeTable;
  /**
   * The composition time offset of each sample in the run.
   */
  public int[] sampleCompositionTimeOffsetTable;
  /**
   * Indicates which samples are sync frames.
   */
  public boolean[] sampleIsSyncFrameTable;
  /**
   * True if the fragment defines encryption data. False otherwise.
   */
  public boolean definesEncryptionData;
  /**
   * If {@link #definesEncryptionData} is true, indicates which samples use sub-sample encryption.
   * Undefined otherwise.
   */
  public boolean[] sampleHasSubsampleEncryptionTable;
  /**
   * If {@link #definesEncryptionData} is true, indicates the length of the sample encryption data.
   * Undefined otherwise.
   */
  public int sampleEncryptionDataLength;
  /**
   * If {@link #definesEncryptionData} is true, contains binary sample encryption data. Undefined
   * otherwise.
   */
  public ParsableByteArray sampleEncryptionData;
  /**
   * Whether {@link #sampleEncryptionData} needs populating with the actual encryption data.
   */
  public boolean sampleEncryptionDataNeedsFill;

  /**
   * Resets the fragment.
   * <p>
   * The {@link #length} is set to 0, and both {@link #definesEncryptionData} and
   * {@link #sampleEncryptionDataNeedsFill} is set to false.
   */
  public void reset() {
    length = 0;
    definesEncryptionData = false;
    sampleEncryptionDataNeedsFill = false;
  }

  /**
   * Configures the fragment for the specified number of samples.
   * <p>
   * The {@link #length} of the fragment is set to the specified sample count, and the contained
   * tables are resized if necessary such that they are at least this length.
   *
   * @param sampleCount The number of samples in the new run.
   */
  public void initTables(int sampleCount) {
    length = sampleCount;
    if (sampleSizeTable == null || sampleSizeTable.length < length) {
      // Size the tables 25% larger than needed, so as to make future resize operations less
      // likely. The choice of 25% is relatively arbitrary.
      int tableSize = (sampleCount * 125) / 100;
      sampleSizeTable = new int[tableSize];
      sampleDecodingTimeTable = new int[tableSize];
      sampleCompositionTimeOffsetTable = new int[tableSize];
      sampleIsSyncFrameTable = new boolean[tableSize];
      sampleHasSubsampleEncryptionTable = new boolean[tableSize];
    }
  }

  /**
   * Configures the fragment to be one that defines encryption data of the specified length.
   * <p>
   * {@link #definesEncryptionData} is set to true, {@link #sampleEncryptionDataLength} is set to
   * the specified length, and {@link #sampleEncryptionData} is resized if necessary such that it
   * is at least this length.
   *
   * @param length The length in bytes of the encryption data.
   */
  public void initEncryptionData(int length) {
    if (sampleEncryptionData == null || sampleEncryptionData.length() < length) {
      sampleEncryptionData = new ParsableByteArray(length);
    }
    sampleEncryptionDataLength = length;
    definesEncryptionData = true;
    sampleEncryptionDataNeedsFill = true;
  }

  /**
   * Fills {@link #sampleEncryptionData} from the provided source.
   *
   * @param source A source from which to read the encryption data.
   */
  public void fillEncryptionData(ParsableByteArray source) {
    source.readBytes(sampleEncryptionData.data, 0, sampleEncryptionDataLength);
    sampleEncryptionData.setPosition(0);
    sampleEncryptionDataNeedsFill = false;
  }

  /**
   * Fills {@link #sampleEncryptionData} for the current run from the provided source.
   *
   * @param source A source from which to read the encryption data.
   * @return True if the encryption data was filled. False if the source had insufficient data.
   */
  public boolean fillEncryptionData(NonBlockingInputStream source) {
    if (source.getAvailableByteCount() < sampleEncryptionDataLength) {
      return false;
    }
    source.read(sampleEncryptionData.data, 0, sampleEncryptionDataLength);
    sampleEncryptionData.setPosition(0);
    sampleEncryptionDataNeedsFill = false;
    return true;
  }

  public int getSamplePresentationTime(int index) {
    return sampleDecodingTimeTable[index] + sampleCompositionTimeOffsetTable[index];
  }

}
