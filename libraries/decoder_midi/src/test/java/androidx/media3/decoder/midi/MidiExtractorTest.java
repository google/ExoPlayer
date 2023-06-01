/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.decoder.midi;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.ExtractorAsserts;
import androidx.media3.test.utils.ExtractorAsserts.SimulationConfig;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

@RunWith(Enclosed.class)
public final class MidiExtractorTest {

  @RunWith(ParameterizedRobolectricTestRunner.class)
  public static class MidiExtractorAssertsTests {
    @Parameters(name = "{0}")
    public static ImmutableList<SimulationConfig> params() {
      return ExtractorAsserts.configs();
    }

    @Parameter public ExtractorAsserts.SimulationConfig simulationConfig;

    @Test
    public void testExtractorAsserts() throws Exception {
      ExtractorAsserts.assertBehavior(
          MidiExtractor::new, "media/midi/Twinkle.mid", simulationConfig);
    }
  }

  @RunWith(AndroidJUnit4.class)
  public static class MidiExtractorSeekTest {
    /**
     * Tests that when seeking to arbitrary points, the extractor will output all MIDI commands
     * preceding the seek point, excluding channel note events which are omitted. The commands
     * before the seek point are marked as decode only. The remaining samples are output normally
     * until the end of the track.
     */
    @Test
    public void testSeekingToArbitraryPoint() throws IOException {
      // The structure of this file is as follows:
      // Tick 1920 (2 seconds)    -> Note1 ON
      // Tick 4800 (5 seconds)    -> Pitch Event
      // Tick 5760 (6 seconds)    -> Note2 ON
      // Tick 6760 (7.04 seconds) -> End of Track
      byte[] fileBytes =
          TestUtil.getByteArray(
              ApplicationProvider.getApplicationContext(),
              /* fileName= */ "media/midi/seek_test_with_non_note_events.mid");

      FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
      PositionHolder positionHolder = new PositionHolder();
      MidiExtractor midiExtractor = new MidiExtractor();
      FakeExtractorInput fakeExtractorInput =
          new FakeExtractorInput.Builder().setData(fileBytes).build();
      midiExtractor.init(fakeExtractorOutput);
      while (fakeExtractorOutput.trackOutputs.get(0).getSampleCount() == 0) {
        assertThat(midiExtractor.read(fakeExtractorInput, positionHolder))
            .isEqualTo(Extractor.RESULT_CONTINUE);
      }
      // Clear the outputs in preparation for a seek.
      fakeExtractorOutput.clearTrackOutputs();

      // Seek to just after a non-note event (pitch bend).
      midiExtractor.seek(/* position= */ 0, /* timeUs= */ 5_500_000);
      do {} while (midiExtractor.read(fakeExtractorInput, positionHolder)
          != Extractor.RESULT_END_OF_INPUT);

      DumpFileAsserts.assertOutput(
          ApplicationProvider.getApplicationContext(),
          fakeExtractorOutput,
          "extractordumps/midi/seek_test_with_non_note_events.mid.dump");
    }
  }

  @RunWith(AndroidJUnit4.class)
  public static class MidiExtractorTempoTest {
    /**
     * Tests that the absolute output timestamps of events in the file are adjusted according to any
     * tempo changes that might occur mid-note.
     */
    @Test
    public void testMidNoteTempoChanges() throws IOException {
      // The structure of this file is as follows:
      // Tempo is at default (120bpm).
      // (0us)         Tick 0    -> Note ON.                 (+480 ticks at 120bpm = 500_000us)
      // (500_000us)   Tick 480  -> Tempo changes to 180bpm. (+480 ticks at 180bpm = 333_333us)
      // (833_333us)   Tick 960  -> Tempo changes to 240bpm. (+480 ticks at 240bpm = 250_000us)
      // (1_083_333us) Tick 1440 -> Tempo changes to 300bpm. (+480 ticks at 300bpm = 200_000us)
      // (1_283_333us) Tick 1920 -> End of file.
      byte[] fileBytes =
          TestUtil.getByteArray(
              ApplicationProvider.getApplicationContext(),
              /* fileName= */ "media/midi/mid_note_tempo_changes_simple.mid");

      FakeExtractorInput fakeExtractorInput =
          new FakeExtractorInput.Builder().setData(fileBytes).build();
      FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
      PositionHolder positionHolder = new PositionHolder();
      MidiExtractor midiExtractor = new MidiExtractor();
      midiExtractor.init(fakeExtractorOutput);

      do {} while (midiExtractor.read(fakeExtractorInput, positionHolder)
          != Extractor.RESULT_END_OF_INPUT);

      DumpFileAsserts.assertOutput(
          ApplicationProvider.getApplicationContext(),
          fakeExtractorOutput,
          "extractordumps/midi/mid_note_tempo_changes_simple.mid.dump");
    }

    @Test
    public void testMultiNoteTempoChanges() throws IOException {
      byte[] fileBytes =
          TestUtil.getByteArray(
              ApplicationProvider.getApplicationContext(),
              /* fileName= */ "media/midi/multi_note_tempo_changes.mid");

      FakeExtractorInput fakeExtractorInput =
          new FakeExtractorInput.Builder().setData(fileBytes).build();
      FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
      PositionHolder positionHolder = new PositionHolder();
      MidiExtractor midiExtractor = new MidiExtractor();
      midiExtractor.init(fakeExtractorOutput);

      do {} while (midiExtractor.read(fakeExtractorInput, positionHolder)
          != Extractor.RESULT_END_OF_INPUT);

      DumpFileAsserts.assertOutput(
          ApplicationProvider.getApplicationContext(),
          fakeExtractorOutput,
          "extractordumps/midi/multi_note_tempo_changes.mid.dump");
    }
  }
}
