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

import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.min;

import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.TrackOutput;
import com.google.common.collect.Iterables;
import java.util.ArrayList;

/** Parses track chunk bytes from standard MIDI files. */
@UnstableApi
/* package */ final class TrackChunk implements Comparable<TrackChunk> {

  /** A listener for changes to track tempo. */
  public interface TempoChangedListener {
    /**
     * Called when a meta tempo change event is encountered in the chunk.
     *
     * @param tempoBpm The new tempo in beats per minute.
     * @param ticks The elapsed ticks since the start of the file.
     */
    void onTempoChanged(int tempoBpm, long ticks);
  }

  private static final int DEFAULT_TRACK_TEMPO_BPM = 120;

  private final int fileFormat;
  private final int ticksPerQuarterNote;
  private final ParsableByteArray trackEventsBytes;
  private final TempoChangedListener tempoListener;
  private final ParsableByteArray scratch;
  private final TrackEvent currentTrackEvent;
  private final ArrayList<Pair<Long, Integer>> tempoChanges;

  private int previousEventStatus;
  private long lastOutputEventTimestampUs;
  private long totalElapsedTicks;

  /** Creates a new track chunk with event bytes read from a standard MIDI file. */
  public TrackChunk(
      int fileFormat,
      int ticksPerQuarterNote,
      ParsableByteArray trackEventsBytes,
      TempoChangedListener tempoListener) {
    this.fileFormat = fileFormat;
    this.ticksPerQuarterNote = ticksPerQuarterNote;
    this.trackEventsBytes = trackEventsBytes;
    this.tempoListener = tempoListener;
    scratch = new ParsableByteArray(TrackEvent.MIDI_MESSAGE_LENGTH_BYTES);
    currentTrackEvent = new TrackEvent();
    tempoChanges = new ArrayList<>();
    previousEventStatus = TrackEvent.DATA_FIELD_UNSET;
    tempoChanges.add(Pair.create(0L, DEFAULT_TRACK_TEMPO_BPM));
  }

  /**
   * Returns the absolute time of the current track event in microseconds, or {@link C#TIME_UNSET}
   * if it's not populated.
   */
  public long peekNextTimestampUs() {
    if (!currentTrackEvent.isPopulated()) {
      return C.TIME_UNSET;
    }

    return lastOutputEventTimestampUs
        + adjustTicksToUs(
            tempoChanges,
            currentTrackEvent.elapsedTimeDeltaTicks,
            totalElapsedTicks,
            ticksPerQuarterNote);
  }

  /**
   * Outputs the current track event to {@code trackOutput}.
   *
   * @param trackOutput The {@link TrackOutput} to output samples to.
   * @param skipNoteEvents Whether note events should be skipped.
   */
  public void outputFrontSample(TrackOutput trackOutput, boolean skipNoteEvents) {
    if (!currentTrackEvent.isPopulated()) {
      return;
    }
    lastOutputEventTimestampUs +=
        adjustTicksToUs(
            tempoChanges,
            currentTrackEvent.elapsedTimeDeltaTicks,
            totalElapsedTicks,
            ticksPerQuarterNote);
    if (skipNoteEvents && currentTrackEvent.isNoteChannelEvent()) {
      trackEventsBytes.skipBytes(currentTrackEvent.eventFileSizeBytes);
      previousEventStatus = currentTrackEvent.statusByte;
      currentTrackEvent.reset();
      return;
    }

    ParsableByteArray sampleData = trackEventsBytes;
    int sampleSize = currentTrackEvent.eventFileSizeBytes - currentTrackEvent.timestampSize;
    // Skip the delta time data for now, we only want to send event bytes to the decoder.
    trackEventsBytes.skipBytes(currentTrackEvent.timestampSize);

    if (currentTrackEvent.isMidiEvent()) {
      trackEventsBytes.skipBytes(sampleSize);
      scratch.setPosition(0);
      currentTrackEvent.writeTo(scratch.getData());
      sampleData = scratch;
      // The decoder does not keep track of running status being applied to an event message. We
      // need to adjust the sample size to pass the full message, which is otherwise represented
      // by fewer bytes in the file.
      sampleSize = currentTrackEvent.eventDecoderSizeBytes;
    } else if (currentTrackEvent.isMetaEvent()) {
      if (currentTrackEvent.usPerQuarterNote != C.TIME_UNSET) {
        int tempoBpm = (int) (60_000_000 / currentTrackEvent.usPerQuarterNote);
        notifyTempoChange(tempoBpm, totalElapsedTicks);
      }
    }

    trackOutput.sampleData(sampleData, sampleSize);
    trackOutput.sampleMetadata(
        lastOutputEventTimestampUs,
        /* flags= */ 0,
        /* size= */ sampleSize,
        /* offset= */ 0,
        /* cryptoData= */ null);

    if (tempoChanges.size() > 1) {
      // All tempo events up to this point have been accounted for. Update the current tempo to
      // the latest value from the list.
      Pair<Long, Integer> latestTempoChange = Iterables.getLast(tempoChanges);
      tempoChanges.clear();
      tempoChanges.add(latestTempoChange);
    }

    previousEventStatus = currentTrackEvent.statusByte;
    currentTrackEvent.reset();
  }

  /**
   * Populates the current track event data from the next MIDI command in {@code trackEventsBytes}.
   */
  public void populateFrontTrackEvent() throws ParserException {
    if (!currentTrackEvent.isPopulated()) {
      boolean parsingSuccess =
          currentTrackEvent.populateFrom(trackEventsBytes, previousEventStatus);
      if (parsingSuccess) {
        totalElapsedTicks += currentTrackEvent.elapsedTimeDeltaTicks;
      }
    }
  }

  /** Adds a new tempo change, and when it occured in ticks since the start of the file. */
  public void addTempoChange(int tempoBpm, long ticks) {
    tempoChanges.add(Pair.create(ticks, tempoBpm));
  }

  /** Resets the state of the chunk. */
  public void reset() {
    lastOutputEventTimestampUs = 0;
    totalElapsedTicks = 0;
    previousEventStatus = TrackEvent.DATA_FIELD_UNSET;
    trackEventsBytes.setPosition(0);
    scratch.setPosition(0);
    currentTrackEvent.reset();
    tempoChanges.clear();
    tempoChanges.add(Pair.create(0L, DEFAULT_TRACK_TEMPO_BPM));
  }

  @Override
  public int compareTo(TrackChunk otherTrack) {
    long thisTimestampUs = peekNextTimestampUs();
    long otherTimestampUs = otherTrack.peekNextTimestampUs();

    if (thisTimestampUs == otherTimestampUs) {
      return 0;
    } else if (thisTimestampUs == C.TIME_UNSET) {
      return 1;
    } else if (otherTimestampUs == C.TIME_UNSET) {
      return -1;
    } else {
      return Long.compare(thisTimestampUs, otherTimestampUs);
    }
  }

  private void notifyTempoChange(int tempoBpm, long ticks) {
    // Tempo changes in format '2' files do not affect other tracks according to the spec; see page
    // 5, section "Formats 0, 1, and 2".
    // https://www.midi.org/component/edocman/rp-001-v1-0-standard-midi-files-specification-96-1-4-pdf/fdocument?Itemid=9999
    if (fileFormat == 2) {
      addTempoChange(tempoBpm, ticks);
    } else {
      tempoListener.onTempoChanged(tempoBpm, ticks);
    }
  }

  private static long adjustTicksToUs(
      ArrayList<Pair<Long, Integer>> tempoChanges,
      long ticks,
      long ticksOffset,
      int ticksPerQuarterNote) {
    long resultUs = 0;
    for (int i = tempoChanges.size() - 1; i >= 0; i--) {
      Pair<Long, Integer> tempoChange = tempoChanges.get(i);
      long ticksAffectedByThisTempo = min(ticks, ticksOffset - tempoChange.first);
      checkState(ticksAffectedByThisTempo >= 0);
      resultUs += ticksToUs(tempoChange.second, ticksAffectedByThisTempo, ticksPerQuarterNote);
      ticks -= ticksAffectedByThisTempo;
      ticksOffset -= ticksAffectedByThisTempo;
    }
    resultUs += ticksToUs(Iterables.getLast(tempoChanges).second, ticks, ticksPerQuarterNote);
    return resultUs;
  }

  private static long ticksToUs(int tempoBpm, long ticks, int ticksPerQuarterNote) {
    return ticks * 60_000_000 / ((long) tempoBpm * ticksPerQuarterNote);
  }
}
