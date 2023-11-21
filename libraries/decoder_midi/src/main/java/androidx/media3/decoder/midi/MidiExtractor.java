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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.TrackOutput;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.PriorityQueue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Extracts data from MIDI containers. */
@UnstableApi
public final class MidiExtractor implements Extractor, SeekMap {

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int FOURCC_MThd = 0x4d546864;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int FOURCC_MTrk = 0x4d54726b;

  /**
   * Extractor state for parsing files. One of {@link #STATE_INITIALIZED}, {@link #STATE_LOADING},
   * {@link #STATE_PREPARING_CHUNKS}, {@link #STATE_PARSING_SAMPLES}, or {@link #STATE_RELEASED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_INITIALIZED,
    STATE_LOADING,
    STATE_PREPARING_CHUNKS,
    STATE_PARSING_SAMPLES,
    STATE_RELEASED
  })
  private @interface State {}

  private static final int STATE_INITIALIZED = 0;
  private static final int STATE_LOADING = 1;
  private static final int STATE_PREPARING_CHUNKS = 2;
  private static final int STATE_PARSING_SAMPLES = 3;
  private static final int STATE_RELEASED = 4;

  /**
   * The maximum timestamp difference between two consecutive samples output to {@link
   * TrackOutput#sampleMetadata}.
   *
   * <p>The {@link MidiDecoder} will only be called for each sample output by this extractor,
   * meaning that the size of the decoder's PCM output buffers is proportional to the time between
   * two samples output by the extractor. In order to make the PCM output buffers manageable, we
   * periodically produce samples (which may be empty) so as to allow the decoder to produce buffers
   * of a small pre-determined size, which at most can be the PCM that corresponds to the period
   * described by this variable.
   */
  private static final long MAX_SAMPLE_DURATION_US = 100_000;

  private static final int HEADER_LEN_BYTES = 14;
  private final ArrayList<TrackChunk> trackChunkList;
  private final PriorityQueue<TrackChunk> trackPriorityQueue;
  private final ParsableByteArray midiFileData;

  private @State int state;
  private int bytesRead;
  private int ticksPerQuarterNote;
  private long currentTimestampUs;
  private long startTimeUs;
  private @MonotonicNonNull SingleKeyFrameTrackOutput trackOutput;

  public MidiExtractor() {
    state = STATE_INITIALIZED;
    trackChunkList = new ArrayList<>();
    trackPriorityQueue = new PriorityQueue<>();
    midiFileData = new ParsableByteArray(/* limit= */ 512);
  }

  // Extractor implementation.

  @Override
  public void init(ExtractorOutput output) {
    if (state != STATE_INITIALIZED) {
      throw new IllegalStateException();
    }

    trackOutput = new SingleKeyFrameTrackOutput(output.track(0, C.TRACK_TYPE_AUDIO));
    trackOutput.format(
        new Format.Builder()
            .setCodecs(MimeTypes.AUDIO_MIDI)
            .setSampleMimeType(MimeTypes.AUDIO_EXOPLAYER_MIDI)
            .build());
    output.endTracks();
    output.seekMap(this);
    state = STATE_LOADING;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    ParsableByteArray buffer = new ParsableByteArray(/* limit= */ 4);
    input.peekFully(buffer.getData(), /* offset= */ 0, 4);

    return isMidiHeaderIdentifier(buffer);
  }

  @Override
  public void seek(long position, long timeUs) {
    checkState(state != STATE_RELEASED);
    startTimeUs = timeUs;
    if (trackOutput != null) {
      trackOutput.reset();
    }
    if (state == STATE_LOADING) {
      midiFileData.setPosition(0);
      bytesRead = 0;
    } else {
      state = STATE_PREPARING_CHUNKS;
    }
  }

  @Override
  public int read(final ExtractorInput input, PositionHolder seekPosition) throws IOException {
    switch (state) {
      case STATE_LOADING:
        int inputFileSize = Ints.checkedCast(input.getLength());
        int currentDataLength = midiFileData.getData().length;

        // Increase the size of the input byte array if needed.
        if (bytesRead == currentDataLength) {
          // Resize the array to the final file size length, or if unknown, to the current_size *
          // 1.5.
          midiFileData.ensureCapacity(
              (inputFileSize != C.LENGTH_UNSET ? inputFileSize : currentDataLength) * 3 / 2);
        }

        int actualBytesRead =
            input.read(
                /* buffer= */ midiFileData.getData(),
                /* offset= */ bytesRead,
                /* length= */ midiFileData.capacity() - bytesRead);

        if (actualBytesRead != C.RESULT_END_OF_INPUT) {
          bytesRead += actualBytesRead;
          // Continue reading if the final file size is unknown or the amount already read isn't
          // equal to the final file size yet.
          if (inputFileSize == C.LENGTH_UNSET || bytesRead != inputFileSize) {
            return RESULT_CONTINUE;
          }
        }

        midiFileData.setLimit(bytesRead);
        parseTracks();

        state = STATE_PREPARING_CHUNKS;
        return RESULT_CONTINUE;
      case STATE_PREPARING_CHUNKS:
        trackPriorityQueue.clear();
        for (TrackChunk chunk : trackChunkList) {
          chunk.reset();
          chunk.populateFrontTrackEvent();
        }
        trackPriorityQueue.addAll(trackChunkList);

        seekChunksTo(startTimeUs);
        currentTimestampUs = startTimeUs;

        long nextTimestampUs = checkNotNull(trackPriorityQueue.peek()).peekNextTimestampUs();
        if (nextTimestampUs > currentTimestampUs) {
          outputEmptySample();
        }
        state = STATE_PARSING_SAMPLES;
        return RESULT_CONTINUE;
      case STATE_PARSING_SAMPLES:
        TrackChunk nextChunk = checkNotNull(trackPriorityQueue.poll());
        int result = RESULT_END_OF_INPUT;
        long nextCommandTimestampUs = nextChunk.peekNextTimestampUs();

        if (nextCommandTimestampUs != C.TIME_UNSET) {
          if (currentTimestampUs + MAX_SAMPLE_DURATION_US < nextCommandTimestampUs) {
            currentTimestampUs += MAX_SAMPLE_DURATION_US;
            outputEmptySample();
          } else { // Event time is sooner than the maximum threshold.
            currentTimestampUs = nextCommandTimestampUs;
            nextChunk.outputFrontSample(
                checkStateNotNull(trackOutput), /* skipNoteEvents= */ false);
            nextChunk.populateFrontTrackEvent();
          }

          result = RESULT_CONTINUE;
        }

        trackPriorityQueue.add(nextChunk);

        return result;
      case STATE_INITIALIZED:
      case STATE_RELEASED:
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public void release() {
    trackChunkList.clear();
    trackPriorityQueue.clear();
    midiFileData.reset(/* data= */ Util.EMPTY_BYTE_ARRAY);
    state = STATE_RELEASED;
  }

  // SeekMap implementation.

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return C.TIME_UNSET;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    if (state == STATE_PREPARING_CHUNKS || state == STATE_PARSING_SAMPLES) {
      return new SeekPoints(new SeekPoint(timeUs, HEADER_LEN_BYTES));
    }
    return new SeekPoints(SeekPoint.START);
  }

  // Internal methods.

  private void parseTracks() throws ParserException {
    if (midiFileData.bytesLeft() < HEADER_LEN_BYTES) {
      throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
    }

    if (!isMidiHeaderIdentifier(midiFileData)) {
      throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
    }

    midiFileData.skipBytes(4); // length (4 bytes)
    int fileFormat = midiFileData.readShort();
    int trackCount = midiFileData.readShort();

    if (trackCount <= 0) {
      throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
    }

    ticksPerQuarterNote = midiFileData.readShort();

    for (int currTrackIndex = 0; currTrackIndex < trackCount; currTrackIndex++) {
      int trackLengthBytes = parseTrackChunkHeader();
      byte[] trackEventsBytes = new byte[trackLengthBytes];

      if (midiFileData.bytesLeft() < trackLengthBytes) {
        throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
      }

      midiFileData.readBytes(
          /* buffer= */ trackEventsBytes, /* offset= */ 0, /* length= */ trackLengthBytes);

      // TODO(b/228838584): Parse slices of midiFileData instead of instantiating a new array of the
      // event bytes from the entire track.
      ParsableByteArray currentChunkData = new ParsableByteArray(trackEventsBytes);

      TrackChunk trackChunk =
          new TrackChunk(fileFormat, ticksPerQuarterNote, currentChunkData, this::onTempoChanged);
      trackChunkList.add(trackChunk);
    }
  }

  private int parseTrackChunkHeader() throws ParserException {
    if (midiFileData.bytesLeft() < 8) {
      throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
    }

    int trackHeaderIdentifier = midiFileData.readInt();

    if (trackHeaderIdentifier != FOURCC_MTrk) {
      throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
    }

    int trackLength = midiFileData.readInt();

    if (trackLength <= 0) {
      throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
    }

    return trackLength;
  }

  private static boolean isMidiHeaderIdentifier(ParsableByteArray input) {
    int fileHeaderIdentifier = input.readInt();
    return fileHeaderIdentifier == FOURCC_MThd;
  }

  private void onTempoChanged(int tempoBpm, long ticks) {
    // Use the list to notify all chunks because the priority queue has a chunk removed from it
    // in the parsing samples state.
    for (TrackChunk trackChunk : trackChunkList) {
      trackChunk.addTempoChange(tempoBpm, ticks);
    }
  }

  private void outputEmptySample() {
    checkStateNotNull(trackOutput)
        .sampleMetadata(
            currentTimestampUs,
            /* flags= */ 0,
            /* size= */ 0,
            /* offset= */ 0,
            /* cryptoData= */ null);
  }

  private void seekChunksTo(long seekTimeUs) throws ParserException {
    while (!trackPriorityQueue.isEmpty()) {
      TrackChunk nextChunk = checkNotNull(trackPriorityQueue.poll());
      long nextTimestampUs = nextChunk.peekNextTimestampUs();

      if (nextTimestampUs != C.TIME_UNSET && nextTimestampUs < seekTimeUs) {
        nextChunk.outputFrontSample(checkStateNotNull(trackOutput), /* skipNoteEvents= */ true);
        nextChunk.populateFrontTrackEvent();
        trackPriorityQueue.add(nextChunk);
      }
    }
    trackPriorityQueue.addAll(trackChunkList);
  }

  /**
   * A {@link TrackOutput} wrapper that marks only the first sample as a key-frame.
   *
   * <p>Only the first sample is marked as a key-frame so that seeking requires the player to seek
   * to the beginning of the MIDI input and output all non Note-On and Note-Off events to the {@link
   * MidiDecoder}.
   */
  private static final class SingleKeyFrameTrackOutput implements TrackOutput {
    private final TrackOutput trackOutput;
    private int outputSampleCount;

    private SingleKeyFrameTrackOutput(TrackOutput trackOutput) {
      this.trackOutput = trackOutput;
    }

    @Override
    public void format(Format format) {
      trackOutput.format(format);
    }

    @Override
    public int sampleData(
        DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
        throws IOException {
      return trackOutput.sampleData(input, length, allowEndOfInput, sampleDataPart);
    }

    @Override
    public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
      trackOutput.sampleData(data, length, sampleDataPart);
    }

    @Override
    public void sampleMetadata(
        long timeUs,
        @C.BufferFlags int flags,
        int size,
        int offset,
        @Nullable CryptoData cryptoData) {
      // No MIDI sample should be marked as key-frame
      checkState((flags & C.BUFFER_FLAG_KEY_FRAME) == 0);
      if (outputSampleCount == 0) {
        flags |= C.BUFFER_FLAG_KEY_FRAME;
      }
      trackOutput.sampleMetadata(timeUs, flags, size, offset, cryptoData);
      outputSampleCount++;
    }

    public void reset() {
      outputSampleCount = 0;
    }
  }
}
