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
import static org.mockito.Mockito.mock;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TrackChunkTest {
  /**
   * Tests that mid-note-event tempo changes are correctly accounted for in the event's duration.
   * Each duration affected by a tempo change is a segment calculated individually. The duration of
   * the sample is the sum of these segments.
   */
  @Test
  public void testMidNoteTempoChanges() throws IOException {
    FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    fakeTrackOutput.format(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_MIDI).build());
    // Chunk format:
    // Note ON at absolute ticks 0.
    // Note OFF at absolute ticks 1920.
    // End of track.
    ParsableByteArray trackData =
        new ParsableByteArray(new byte[] {0, -112, 72, 127, -113, 0, -128, 72, 127, 0, -1, 47, 0});
    TrackChunk trackChunk =
        new TrackChunk(
            /* fileFormat= */ 1,
            /* ticksPerQuarterNote= */ 480,
            /* trackEventsBytes= */ trackData,
            /* tempoListener= */ mock(TrackChunk.TempoChangedListener.class));

    trackChunk.populateFrontTrackEvent();
    trackChunk.outputFrontSample(fakeTrackOutput, /* skipNoteEvents= */ false);
    assertThat(fakeTrackOutput.getSampleTimeUs(/* index= */ 0)).isEqualTo(/* expected= */ 0);

    trackChunk.addTempoChange(/* tempoBpm= */ 180, /* ticks= */ 480);
    trackChunk.addTempoChange(/* tempoBpm= */ 240, /* ticks= */ 960);
    trackChunk.addTempoChange(/* tempoBpm= */ 300, /* ticks= */ 1440);

    trackChunk.populateFrontTrackEvent();
    trackChunk.outputFrontSample(fakeTrackOutput, /* skipNoteEvents= */ false);
    assertThat(fakeTrackOutput.getSampleTimeUs(/* index= */ 1)).isEqualTo(/* expected= */ 1283333);
  }
}
