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

import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.SeekMap;

import android.app.Instrumentation;
import android.util.SparseArray;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.IOException;

/**
 * A fake {@link ExtractorOutput}.
 */
public final class FakeExtractorOutput implements ExtractorOutput, Dumper.Dumpable {

  private final boolean allowDuplicateTrackIds;

  public final SparseArray<FakeTrackOutput> trackOutputs;

  public int numberOfTracks;
  public boolean tracksEnded;
  public SeekMap seekMap;

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

  public void assertEquals(FakeExtractorOutput expected) {
    Assert.assertEquals(expected.numberOfTracks, numberOfTracks);
    Assert.assertEquals(expected.tracksEnded, tracksEnded);
    if (expected.seekMap == null) {
      Assert.assertNull(seekMap);
    } else {
      // TODO: Bulk up this check if possible.
      Assert.assertNotNull(seekMap);
      Assert.assertEquals(expected.seekMap.getClass(), seekMap.getClass());
      Assert.assertEquals(expected.seekMap.isSeekable(), seekMap.isSeekable());
      Assert.assertEquals(expected.seekMap.getPosition(0), seekMap.getPosition(0));
    }
    for (int i = 0; i < numberOfTracks; i++) {
      Assert.assertEquals(expected.trackOutputs.keyAt(i), trackOutputs.keyAt(i));
      trackOutputs.valueAt(i).assertEquals(expected.trackOutputs.valueAt(i));
    }
  }

  public void assertOutput(Instrumentation instrumentation, String dumpFile) throws IOException {
    String actual = new Dumper().add(this).toString();
    String expected = TestUtil.getString(instrumentation, dumpFile);
    Assert.assertEquals(expected, actual);
  }

  @Override
  public void dump(Dumper dumper) {
    if (seekMap != null) {
      dumper.startBlock("seekMap")
          .add("isSeekable", seekMap.isSeekable())
          .addTime("duration", seekMap.getDurationUs())
          .add("getPosition(0)", seekMap.getPosition(0))
          .endBlock();
    }
    dumper.add("numberOfTracks", numberOfTracks);
    for (int i = 0; i < numberOfTracks; i++) {
      dumper.startBlock("track " + trackOutputs.keyAt(i))
          .add(trackOutputs.valueAt(i))
          .endBlock();
    }
    dumper.add("tracksEnded", tracksEnded);
  }

}
