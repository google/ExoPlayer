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
package com.google.android.exoplayer.testutil;

import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.SeekMap;

import android.util.SparseArray;

import junit.framework.TestCase;

/**
 * A fake {@link ExtractorOutput}.
 */
public final class FakeExtractorOutput implements ExtractorOutput {

  private final boolean allowDuplicateTrackIds;

  public final SparseArray<FakeTrackOutput> trackOutputs;

  public boolean tracksEnded;
  public SeekMap seekMap;
  public DrmInitData drmInitData;
  public int numberOfTracks;

  public FakeExtractorOutput() {
    this(false);
  }

  public FakeExtractorOutput(boolean allowDuplicateTrackIds) {
    this.allowDuplicateTrackIds = allowDuplicateTrackIds;
    trackOutputs = new SparseArray<>();
  }

  @Override
  public FakeTrackOutput track(int trackId) {
    FakeTrackOutput output = trackOutputs.get(trackId);
    if (output == null) {
      numberOfTracks++;
      output = new FakeTrackOutput();
      trackOutputs.put(trackId, output);
    } else {
      TestCase.assertTrue("Duplicate track id: " + trackId, allowDuplicateTrackIds);
    }
    return output;
  }

  @Override
  public void endTracks() {
    tracksEnded = true;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
  }

  @Override
  public void drmInitData(DrmInitData drmInitData) {
    this.drmInitData = drmInitData;
  }

}
