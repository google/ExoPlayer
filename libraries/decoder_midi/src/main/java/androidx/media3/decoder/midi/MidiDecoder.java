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

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.midi.MidiSynthesizer;
import com.jsyn.util.AudioStreamReader;
import com.jsyn.util.MultiChannelSynthesizer;
import java.nio.ByteBuffer;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;

/** Decodes MIDI commands into PCM. */
/* package */ final class MidiDecoder
    extends SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, MidiDecoderException> {

  /** The number of channels output by the decoder. */
  public static final int NUM_OUTPUT_CHANNELS = 2;

  /** The default input buffer count. */
  public static final int DEFAULT_INPUT_BUFFER_COUNT = 16;

  /** The default output buffer count. */
  public static final int DEFAULT_OUTPUT_BUFFER_COUNT = 16;

  /** The standard number of MIDI channels. */
  public static final int CHANNEL_COUNT = 16;

  /** The default sample rate, measured in Hertz. */
  public static final int DEFAULT_SAMPLE_RATE = 44100;

  /** The time interval in seconds for the synthesizer to produce PCM samples for. */
  private static final double PCM_GENERATION_STEP_SECS = .1;

  private static final int DEFAULT_AUDIO_OUTPUT_BUFFER_SIZE =
      (int) (PCM_GENERATION_STEP_SECS * DEFAULT_SAMPLE_RATE * NUM_OUTPUT_CHANNELS);
  private static final int PCM_SAMPLE_SIZE_BYTES = 2;

  /** Returns the format output by MIDI Decoders. */
  public static Format getDecoderOutputFormat() {
    // MidiDecoder only supports outputting float PCM, two channels, and the specified sample rate.
    return Util.getPcmFormat(C.ENCODING_PCM_FLOAT, NUM_OUTPUT_CHANNELS, DEFAULT_SAMPLE_RATE);
  }

  private final Context context;
  private Synthesizer synth;
  private MultiChannelSynthesizer multiSynth;
  private MidiSynthesizer midiSynthesizer;
  private AudioStreamReader reader;
  private double[] audioStreamOutputBuffer;
  private long lastReceivedTimestampUs;
  private long outputTimeUs;

  /**
   * Creates a MIDI decoder with {@link #DEFAULT_INPUT_BUFFER_COUNT} input buffers and {@link
   * #DEFAULT_OUTPUT_BUFFER_COUNT} output buffers.
   */
  public MidiDecoder(Context context) throws MidiDecoderException {
    this(
        context,
        /* inputBufferCount= */ DEFAULT_INPUT_BUFFER_COUNT,
        /* outputBufferCount= */ DEFAULT_OUTPUT_BUFFER_COUNT);
  }

  /**
   * Creates an instance.
   *
   * @param context The application context.
   * @param inputBufferCount The {@link DecoderInputBuffer} size.
   * @param outputBufferCount The {@link SimpleDecoderOutputBuffer} size.
   * @throws MidiDecoderException if there is an error initializing the decoder.
   */
  public MidiDecoder(Context context, int inputBufferCount, int outputBufferCount)
      throws MidiDecoderException {
    super(
        new DecoderInputBuffer[inputBufferCount], new SimpleDecoderOutputBuffer[outputBufferCount]);
    this.context = context;
    audioStreamOutputBuffer = new double[DEFAULT_AUDIO_OUTPUT_BUFFER_SIZE];
    lastReceivedTimestampUs = C.TIME_UNSET;
    createSynthesizers();
  }

  @Override
  public String getName() {
    return "MidiDecoder";
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @Override
  protected SimpleDecoderOutputBuffer createOutputBuffer() {
    return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected MidiDecoderException createUnexpectedDecodeException(Throwable error) {
    return new MidiDecoderException("Unexpected decode error", error);
  }

  @Override
  @Nullable
  @SuppressWarnings(
      "ByteBufferBackingArray") // ByteBuffers are created using allocate. See createInputBuffer().
  protected MidiDecoderException decode(
      DecoderInputBuffer inputBuffer, SimpleDecoderOutputBuffer outputBuffer, boolean reset) {
    ByteBuffer inputBufferData = checkNotNull(inputBuffer.data);
    if (reset) {
      lastReceivedTimestampUs = C.TIME_UNSET;
      try {
        resetSynthesizers();
      } catch (MidiDecoderException e) {
        return e;
      }
    }
    if (lastReceivedTimestampUs == C.TIME_UNSET) {
      outputTimeUs = inputBuffer.timeUs;
    }
    boolean isDecodeOnly = !isAtLeastOutputStartTimeUs(inputBuffer.timeUs);
    try {
      if (!isDecodeOnly) {
        // Yield the thread to the Synthesizer to produce PCM samples up to this buffer's timestamp.
        if (lastReceivedTimestampUs != C.TIME_UNSET) {
          double timeToSleepSecs = (inputBuffer.timeUs - lastReceivedTimestampUs) * 0.000001D;
          synth.sleepFor(timeToSleepSecs);
        }
        lastReceivedTimestampUs = inputBuffer.timeUs;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    // Only pass buffers populated with MIDI command data to the synthesizer, ignoring empty buffers
    // that act as a flag to render previously passed commands for the correct time.
    if (inputBufferData.remaining() > 0) {
      midiSynthesizer.onReceive(
          /* bytes= */ inputBufferData.array(),
          /* offset= */ inputBufferData.position(),
          /* length= */ inputBufferData.remaining());
    }

    int availableSamples = reader.available();
    // Ensure there are no remaining bytes if the input buffer is decode only.
    checkState(!isDecodeOnly || availableSamples == 0);

    if (availableSamples > audioStreamOutputBuffer.length) {
      // Increase the size of the buffer by 25% of the availableSamples (arbitrary number).
      // This way we give some overhead so that having to resizing it again is less likely.
      int newSize = (availableSamples * 125) / 100;
      audioStreamOutputBuffer = new double[newSize];
    }

    int synthOutputSamplesRead = 0;
    while (synthOutputSamplesRead < availableSamples) {
      synthOutputSamplesRead +=
          reader.read(
              /* buffer= */ audioStreamOutputBuffer,
              /* start= */ synthOutputSamplesRead,
              /* count= */ availableSamples - synthOutputSamplesRead);
    }

    outputBuffer.init(
        outputTimeUs,
        synthOutputSamplesRead * /* bytesPerSample= */ PCM_SAMPLE_SIZE_BYTES * NUM_OUTPUT_CHANNELS);
    ByteBuffer outputBufferData = checkNotNull(outputBuffer.data);

    for (int i = 0; i < synthOutputSamplesRead; i++) {
      outputBufferData.putFloat((float) audioStreamOutputBuffer[i]);
    }

    outputBufferData.flip();
    // Divide synthOutputSamplesRead by channel count to get the frame rate,
    // and then divide by the sample rate to get the duration in seconds.
    // Multiply by 1_000_000 to convert to microseconds.
    outputTimeUs =
        outputTimeUs
            + synthOutputSamplesRead * 1_000_000L / NUM_OUTPUT_CHANNELS / DEFAULT_SAMPLE_RATE;
    return null;
  }

  @Override
  public void release() {
    synth.stop();
    super.release();
  }

  private void resetSynthesizers() throws MidiDecoderException {
    synth.stop();
    multiSynth.getOutput().disconnectAll();
    createSynthesizers();
  }

  @EnsuresNonNull({"synth", "multiSynth", "reader", "midiSynthesizer"})
  private void createSynthesizers(@UnknownInitialization MidiDecoder this)
      throws MidiDecoderException {
    synth = JSyn.createSynthesizer();
    synth.setRealTime(false);
    multiSynth = new MultiChannelSynthesizer();
    multiSynth.setup(
        synth,
        /* startChannel= */ 0,
        /* numChannels= */ CHANNEL_COUNT,
        /* voicesPerChannel= */ 4,
        SonivoxVoiceDescription.getInstance(checkNotNull(context)));
    midiSynthesizer = new MidiSynthesizer(multiSynth);
    reader = new AudioStreamReader(synth, /* samplesPerFrame= */ 2);
    multiSynth.getOutput().connect(/* thisPartNum= */ 0, reader.getInput(), /* otherPartNum= */ 0);
    multiSynth.getOutput().connect(/* thisPartNum= */ 0, reader.getInput(), /* otherPartNum= */ 1);
    synth.start();
  }
}
