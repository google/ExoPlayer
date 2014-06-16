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

/**
 * A holder for information corresponding to a single fragment of an mp4 file.
 */
/* package */ class TrackFragment {

  public int sampleDescriptionIndex;

  public int length;
  public int[] sampleSizeTable;
  public int[] sampleDecodingTimeTable;
  public int[] sampleCompositionTimeOffsetTable;
  public boolean[] sampleIsSyncFrameTable;

  public int auxiliarySampleInfoTotalSize;
  public int[] auxiliarySampleInfoSizeTable;

  public boolean smoothStreamingUsesSubsampleEncryption;
  public ParsableByteArray smoothStreamingSampleEncryptionData;

  public void setSampleDescriptionIndex(int sampleDescriptionIndex) {
    this.sampleDescriptionIndex = sampleDescriptionIndex;
  }

  public void setSampleTables(int[] sampleSizeTable, int[] sampleDecodingTimeTable,
      int[] sampleCompositionTimeOffsetTable, boolean[] sampleIsSyncFrameTable) {
    this.sampleSizeTable = sampleSizeTable;
    this.sampleDecodingTimeTable = sampleDecodingTimeTable;
    this.sampleCompositionTimeOffsetTable = sampleCompositionTimeOffsetTable;
    this.sampleIsSyncFrameTable = sampleIsSyncFrameTable;
    this.length = sampleSizeTable.length;
  }

  public void setAuxiliarySampleInfoTables(int totalAuxiliarySampleInfoSize,
      int[] auxiliarySampleInfoSizeTable) {
    this.auxiliarySampleInfoTotalSize = totalAuxiliarySampleInfoSize;
    this.auxiliarySampleInfoSizeTable = auxiliarySampleInfoSizeTable;
  }

  public void setSmoothStreamingSampleEncryptionData(ParsableByteArray data,
      boolean usesSubsampleEncryption) {
    this.smoothStreamingSampleEncryptionData = data;
    this.smoothStreamingUsesSubsampleEncryption = usesSubsampleEncryption;
  }

  public int getSamplePresentationTime(int index) {
    return sampleDecodingTimeTable[index] + sampleCompositionTimeOffsetTable[index];
  }

}
