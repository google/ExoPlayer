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
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.util.Assertions;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A fake {@link ExtractorOutput}.
 */
public final class FakeExtractorOutput implements ExtractorOutput, Dumper.Dumpable {

  /**
   * If true, makes {@link #assertOutput(Context, String)} method write the output to the dump file,
   * rather than validating that the output matches what the dump file already contains.
   *
   * <p>Enabling this option works when tests are run in Android Studio. It may not work when the
   * tests are run in another environment.
   */
  private static final boolean WRITE_DUMP = false;

  public final SparseArray<FakeTrackOutput> trackOutputs;

  public int numberOfTracks;
  public boolean tracksEnded;
  public @MonotonicNonNull SeekMap seekMap;

  public FakeExtractorOutput() {
    trackOutputs = new SparseArray<>();
  }

  @Override
  public FakeTrackOutput track(int id, int type) {
    @Nullable FakeTrackOutput output = trackOutputs.get(id);
    if (output == null) {
      assertThat(tracksEnded).isFalse();
      numberOfTracks++;
      output = new FakeTrackOutput();
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

  public void assertEquals(FakeExtractorOutput expected) {
    assertThat(numberOfTracks).isEqualTo(expected.numberOfTracks);
    assertThat(tracksEnded).isEqualTo(expected.tracksEnded);
    if (expected.seekMap == null) {
      assertThat(seekMap).isNull();
    } else {
      // TODO: Bulk up this check if possible.
      SeekMap expectedSeekMap = Assertions.checkNotNull(expected.seekMap);
      SeekMap seekMap = Assertions.checkNotNull(this.seekMap);
      assertThat(seekMap.getClass()).isEqualTo(expectedSeekMap.getClass());
      assertThat(seekMap.isSeekable()).isEqualTo(expectedSeekMap.isSeekable());
      assertThat(seekMap.getSeekPoints(0)).isEqualTo(expectedSeekMap.getSeekPoints(0));
    }
    for (int i = 0; i < numberOfTracks; i++) {
      assertThat(trackOutputs.keyAt(i)).isEqualTo(expected.trackOutputs.keyAt(i));
      trackOutputs.valueAt(i).assertEquals(expected.trackOutputs.valueAt(i));
    }
  }

  /**
   * Asserts that dump of this {@link FakeExtractorOutput} is equal to expected dump which is read
   * from {@code dumpFile}.
   *
   * <p>If assertion fails because of an intended change in the output or a new dump file needs to
   * be created, set {@link #WRITE_DUMP} flag to true and run the test again. Instead of assertion,
   * actual dump will be written to {@code dumpFile}. This new dump file needs to be copied to the
   * project, {@code library/src/androidTest/assets} folder manually.
   */
  public void assertOutput(Context context, String dumpFile) throws IOException {
    String actual = new Dumper().add(this).toString();

    if (WRITE_DUMP) {
      File file = new File(System.getProperty("user.dir"), "../../testdata/src/test/assets");
      file = new File(file, dumpFile);
      Assertions.checkStateNotNull(file.getParentFile()).mkdirs();
      PrintWriter out = new PrintWriter(file);
      out.print(actual);
      out.close();
    } else {
      String expected = TestUtil.getString(context, dumpFile);
      assertWithMessage(dumpFile).that(actual).isEqualTo(expected);
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
      dumper.startBlock("track " + trackOutputs.keyAt(i))
          .add(trackOutputs.valueAt(i))
          .endBlock();
    }
    dumper.add("tracksEnded", tracksEnded);
  }

}
