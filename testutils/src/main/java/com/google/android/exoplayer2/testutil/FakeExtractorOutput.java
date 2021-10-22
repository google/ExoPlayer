/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A fake {@link ExtractorOutput}. */
public final class FakeExtractorOutput implements ExtractorOutput, Dumper.Dumpable {

  public final SparseArray<FakeTrackOutput> trackOutputs;
  private final FakeTrackOutput.Factory trackOutputFactory;

  public int numberOfTracks;
  public boolean tracksEnded;
  public @MonotonicNonNull SeekMap seekMap;

  public FakeExtractorOutput() {
    this(FakeTrackOutput.DEFAULT_FACTORY);
  }

  public FakeExtractorOutput(FakeTrackOutput.Factory trackOutputFactory) {
    this.trackOutputFactory = trackOutputFactory;
    trackOutputs = new SparseArray<>();
  }

  @Override
  public FakeTrackOutput track(int id, int type) {
    @Nullable FakeTrackOutput output = trackOutputs.get(id);
    if (output == null) {
      assertThat(tracksEnded).isFalse();
      numberOfTracks++;
      output = trackOutputFactory.create(id, type);
      trackOutputs.put(id, output);
    }
    return output;
  }

  @Override
  public void endTracks() {
    tracksEnded = true;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    if (seekMap.isSeekable()) {
      SeekMap.SeekPoints seekPoints = seekMap.getSeekPoints(0);
      if (!seekPoints.first.equals(seekPoints.second)) {
        throw new IllegalStateException("SeekMap defines two seek points for t=0");
      }
      long durationUs = seekMap.getDurationUs();
      if (durationUs != C.TIME_UNSET) {
        seekPoints = seekMap.getSeekPoints(durationUs);
        if (!seekPoints.first.equals(seekPoints.second)) {
          throw new IllegalStateException("SeekMap defines two seek points for t=durationUs");
        }
      }
    }
    this.seekMap = seekMap;
  }

  public void clearTrackOutputs() {
    for (int i = 0; i < numberOfTracks; i++) {
      trackOutputs.valueAt(i).clear();
    }
  }

  @Override
  public void dump(Dumper dumper) {
    if (seekMap != null) {
      dumper
          .startBlock("seekMap")
          .add("isSeekable", seekMap.isSeekable())
          .addTime("duration", seekMap.getDurationUs())
          .add("getPosition(0)", seekMap.getSeekPoints(0));
      if (seekMap.isSeekable()) {
        dumper.add("getPosition(1)", seekMap.getSeekPoints(1));
        if (seekMap.getDurationUs() != C.TIME_UNSET) {
          // Dump seek points at the mid point and duration.
          long durationUs = seekMap.getDurationUs();
          long midPointUs = durationUs / 2;
          dumper.add("getPosition(" + midPointUs + ")", seekMap.getSeekPoints(midPointUs));
          dumper.add("getPosition(" + durationUs + ")", seekMap.getSeekPoints(durationUs));
        }
      }
      dumper.endBlock();
    }
    dumper.add("numberOfTracks", numberOfTracks);
    for (int i = 0; i < numberOfTracks; i++) {
      dumper.startBlock("track " + trackOutputs.keyAt(i)).add(trackOutputs.valueAt(i)).endBlock();
    }
    dumper.add("tracksEnded", tracksEnded);
  }
}
