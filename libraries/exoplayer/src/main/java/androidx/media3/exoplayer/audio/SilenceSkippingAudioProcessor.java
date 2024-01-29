/*
 * Copyright (C) 2018 The Android Open Source Project
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
package androidx.media3.exoplayer.audio;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;

/**
 * An {@link AudioProcessor} that skips silence in the input stream. Input and output are 16-bit
 * PCM.
 */
@UnstableApi
public final class SilenceSkippingAudioProcessor extends BaseAudioProcessor {

  /**
   * Default fraction of the original silence to keep. Between [0, 1]. 1 means keep all silence. 0
   * means remove all silence.
   */
  public static final float DEFAULT_SILENCE_RETENTION_RATIO = 0.2f;

  /**
   * Default volume percentage to keep.
   *
   * <p>Even when modifying the volume to a mute state, it is ideal to decrease the volume instead
   * of making the volume zero. Completely silent audio sounds like playback has stopped. While
   * decreased volume sounds like very light background noise at a recording studio.
   */
  public static final int DEFAULT_MIN_VOLUME_TO_KEEP_PERCENTAGE = 10;

  /** Default absolute level below which an individual PCM sample is classified as silent. */
  public static final short DEFAULT_SILENCE_THRESHOLD_LEVEL = 1024;

  /**
   * Default minimum duration of audio that must be below {@code silenceThresholdLevel} before
   * silence starts being trimmed. Specified in microseconds.
   */
  public static final long DEFAULT_MINIMUM_SILENCE_DURATION_US = 100_000;

  /**
   * Default maximum silence to keep in microseconds. This maximum is applied after {@code
   * silenceRetentionRatio}.
   */
  public static final long DEFAULT_MAX_SILENCE_TO_KEEP_DURATION_US = 2_000_000;

  /**
   * @deprecated Specify silence behaviour via {@code silenceRetentionRatio} instead.
   */
  @Deprecated public static final long DEFAULT_PADDING_SILENCE_US = 20_000;

  /** Trimming states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_NOISY,
    STATE_SHORTENING_SILENCE,
  })
  private @interface State {}

  /** State when the input is not silent. */
  private static final int STATE_NOISY = 0;

  /**
   * State when the input has been silent less than or equal to {@link #maxSilenceToKeepDurationUs}
   * and the silence is being shortened according to {@link #calculateShortenedSilenceLength(int)}.
   */
  private static final int STATE_SHORTENING_SILENCE = 1;

  /** Ways to change the volume of silence. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    FADE_OUT,
    MUTE,
    FADE_IN,
    DO_NOT_CHANGE_VOLUME,
  })
  private @interface VolumeChangeType {}

  private static final int FADE_OUT = 0;
  private static final int MUTE = 1;
  private static final int FADE_IN = 2;
  private static final int DO_NOT_CHANGE_VOLUME = 3;

  /**
   * Used with {@code minVolumeToKeepPercentageWhenMuting} to avoid round off errors. An alternative
   * to this would be to use floats, but integer math is probably faster than floats.
   */
  private static final int AVOID_TRUNCATION_FACTOR = 1000;

  /**
   * Fraction of the original silence to keep. Between [0, 1]. 1 means keep all silence. 0 means
   * remove all silence.
   */
  private final float silenceRetentionRatio;

  /** Absolute level below which an individual PCM sample is classified as silent. */
  private final short silenceThresholdLevel;

  /**
   * Volume percentage to keep. Even when modifying the volume to a mute state, it is ideal to
   * decrease the volume instead of making the volume zero. Completely silent audio sounds like
   * playback has stopped. While decreased volume sounds like very light background noise from a
   * recording studio.
   */
  private final int minVolumeToKeepPercentageWhenMuting;

  /**
   * Duration of audio that must be below {@link #silenceThresholdLevel} before silence starts being
   * trimmed. Specified in microseconds.
   */
  private final long minimumSilenceDurationUs;

  /**
   * Maximum silence to keep in microseconds. This maximum is applied after {@link
   * #silenceRetentionRatio}.
   */
  private final long maxSilenceToKeepDurationUs;

  private AudioFormat inputFormat;
  private int bytesPerFrame;
  private boolean enabled;
  private @State int state;
  private long skippedFrames;

  /**
   * The frames of silence that has been output since the last noise. Used to enforce {@link
   * #maxSilenceToKeepDurationUs}.
   */
  private int outputSilenceFramesSinceNoise = 0;

  /**
   * Buffers audio data that may be classified as silence while in {@link
   * #STATE_SHORTENING_SILENCE}. If the input becomes noisy before the buffer has filled, it will be
   * output without shortening. Otherwise, the buffer will be output when filled as shortened
   * silence and emptied.
   */
  private byte[] maybeSilenceBuffer;

  /**
   * An index into {@link #maybeSilenceBuffer} pointing to the location where silence that has not
   * been output starts.
   */
  private int maybeSilenceBufferStartIndex = 0;

  /**
   * A count of the number of bytes of content in {@link #maybeSilenceBuffer}. The count starts at
   * {@link #maybeSilenceBufferStartIndex}, and the bytes counted may wrap around to the start of
   * the buffer. The count will never be greater than {@link #maybeSilenceBuffer}'s length.
   */
  private int maybeSilenceBufferContentsSize = 0;

  /** Used to hold a subset of the contents of {@link #maybeSilenceBuffer} for convenience. */
  // TODO: This processor can probably be more efficient if this array is not used. Operations like
  //  modifyVolume() can be applied to a non-contiguous contents, the code is just more complex.
  private byte[] contiguousOutputBuffer;

  /** Creates a new silence skipping audio processor. */
  public SilenceSkippingAudioProcessor() {
    this(
        DEFAULT_MINIMUM_SILENCE_DURATION_US,
        DEFAULT_SILENCE_RETENTION_RATIO,
        DEFAULT_MAX_SILENCE_TO_KEEP_DURATION_US,
        DEFAULT_MIN_VOLUME_TO_KEEP_PERCENTAGE,
        DEFAULT_SILENCE_THRESHOLD_LEVEL);
  }

  /**
   * @deprecated Use {@link #SilenceSkippingAudioProcessor(long, float, long, int, short)} instead.
   */
  @Deprecated
  public SilenceSkippingAudioProcessor(
      long minimumSilenceDurationUs, long paddingSilenceUs, short silenceThresholdLevel) {
    this(
        minimumSilenceDurationUs,
        /* silenceRetentionRatio= */ (float) paddingSilenceUs / minimumSilenceDurationUs,
        /* maxSilenceToKeepDurationUs= */ minimumSilenceDurationUs,
        /* minVolumeToKeepPercentageWhenMuting= */ 0,
        silenceThresholdLevel);
  }

  /**
   * Creates a new silence trimming audio processor.
   *
   * @param minimumSilenceDurationUs Duration of audio that must be below {@code
   *     silenceThresholdLevel} before silence starts being trimmed, in microseconds.
   * @param silenceRetentionRatio Fraction of the original silence to keep. Between [0, 1]. 1 means
   *     keep all silence. 0 means remove all silence.
   * @param maxSilenceToKeepDurationUs Maximum silence to keep in microseconds. This maximum is
   *     applied after {@link #silenceRetentionRatio}.
   * @param minVolumeToKeepPercentageWhenMuting Volume percentage to keep. Even when modifying the
   *     volume to a mute state, it is ideal to decrease the volume instead of making the volume
   *     zero. Completely silent audio sounds like playback has stopped. While decreased volume
   *     sounds like very light background noise from a recording studio.
   * @param silenceThresholdLevel Absolute level below which an individual PCM sample is classified
   *     as silent.
   */
  public SilenceSkippingAudioProcessor(
      long minimumSilenceDurationUs,
      float silenceRetentionRatio,
      long maxSilenceToKeepDurationUs,
      int minVolumeToKeepPercentageWhenMuting,
      short silenceThresholdLevel) {
    checkArgument(silenceRetentionRatio >= 0f && silenceRetentionRatio <= 1f);
    this.minimumSilenceDurationUs = minimumSilenceDurationUs;
    this.silenceRetentionRatio = silenceRetentionRatio;
    this.maxSilenceToKeepDurationUs = maxSilenceToKeepDurationUs;
    this.minVolumeToKeepPercentageWhenMuting = minVolumeToKeepPercentageWhenMuting;
    this.silenceThresholdLevel = silenceThresholdLevel;
    inputFormat = AudioFormat.NOT_SET;
    maybeSilenceBuffer = Util.EMPTY_BYTE_ARRAY;
    contiguousOutputBuffer = Util.EMPTY_BYTE_ARRAY;
  }

  /**
   * Sets whether to shorten silence in the input. This method may only be called after draining
   * data through the processor. The value returned by {@link #isActive()} may change, and the
   * processor must be {@link #flush() flushed} before queueing more data.
   *
   * @param enabled Whether to shorten silence in the input.
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns the total number of frames of input audio that were skipped due to being classified as
   * silence since the last call to {@link #flush()}.
   */
  public long getSkippedFrames() {
    return skippedFrames;
  }

  @Override
  protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    this.inputFormat = inputAudioFormat;
    bytesPerFrame = inputAudioFormat.channelCount * 2;
    return inputAudioFormat;
  }

  @Override
  public boolean isActive() {
    return inputFormat.sampleRate != Format.NO_VALUE && enabled;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    while (inputBuffer.hasRemaining() && !hasPendingOutput()) {
      switch (state) {
        case STATE_NOISY:
          processNoisy(inputBuffer);
          break;
        case STATE_SHORTENING_SILENCE:
          shortenSilenceSilenceUntilNoise(inputBuffer);
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  public void onQueueEndOfStream() {
    // The maybeSilenceBuffer is only written to in the STATE_SHORTENING_SILENCE state, and
    // is always completely flushed before leaving the STATE_SHORTENING_SILENCE.
    if (maybeSilenceBufferContentsSize > 0) {
      // There's bytes in the buffer. So the final chunk of shortened silence will be output to
      // simulate a transition back to the noisy state and the end of output.
      outputShortenedSilenceBuffer(/* shouldTransitionToNoisyState= */ true);
      outputSilenceFramesSinceNoise = 0;
    }
  }

  @Override
  public void onFlush() {
    if (isActive()) {
      // Divide by 2 to allow the buffer to be split into two bytesPerFrame aligned parts.
      int maybeSilenceBufferSize =
          alignToBytePerFrameBoundary(durationUsToFrames(minimumSilenceDurationUs) / 2) * 2;
      if (maybeSilenceBuffer.length != maybeSilenceBufferSize) {
        maybeSilenceBuffer = new byte[maybeSilenceBufferSize];
        contiguousOutputBuffer = new byte[maybeSilenceBufferSize];
      }
    }
    state = STATE_NOISY;
    skippedFrames = 0;
    outputSilenceFramesSinceNoise = 0;
    maybeSilenceBufferStartIndex = 0;
    maybeSilenceBufferContentsSize = 0;
  }

  @Override
  public void onReset() {
    enabled = false;
    inputFormat = AudioFormat.NOT_SET;
    maybeSilenceBuffer = Util.EMPTY_BYTE_ARRAY;
    contiguousOutputBuffer = Util.EMPTY_BYTE_ARRAY;
  }

  /**
   * Incrementally processes new input from {@code inputBuffer} while in {@link #STATE_NOISY},
   * updating the state if needed.
   */
  private void processNoisy(ByteBuffer inputBuffer) {
    int limit = inputBuffer.limit();

    // Check if there's any noise within the maybe silence buffer duration.
    inputBuffer.limit(min(limit, inputBuffer.position() + maybeSilenceBuffer.length));
    int noiseLimit = findNoiseLimit(inputBuffer);
    if (noiseLimit == inputBuffer.position()) {
      // The buffer contains the start of possible silence.
      state = STATE_SHORTENING_SILENCE;
    } else {
      inputBuffer.limit(min(noiseLimit, inputBuffer.capacity()));
      output(inputBuffer);
    }

    // Restore the limit.
    inputBuffer.limit(limit);
  }

  /**
   * Incrementally processes new input from {@code inputBuffer} while in {@link
   * #STATE_SHORTENING_SILENCE}, updating the state if needed.
   *
   * <p>If the amount of silence is less than {@link #minimumSilenceDurationUs}, then {@link
   * #DO_NOT_CHANGE_VOLUME} is used to output the silence.
   *
   * <p>If the amount of silence is more than {@link #minimumSilenceDurationUs}, then the following
   * will be output:
   *
   * <ul>
   *   <li>A half a buffer full of silence using {@link #FADE_OUT}. This padding has no
   *       discontinuities.
   *   <li>A number of bytes between 0 to ({@link #maxSilenceToKeepDurationUs} - padding}. This will
   *       have discontinuities, that are imperceptible due to {@linkplain #MUTE muting} the
   *       content.
   *   <li>If the silence length is over {@link #maxSilenceToKeepDurationUs} - a half buffer (for
   *       padding that will be applied later) then the silence begins to be thrown away entirely.
   *   <li>A final silence with a length of a half buffer will be used with a {@link #FADE_IN}. This
   *       padding has no discontinuities. It will transition with no discontinuities back to the
   *       {@link #STATE_NOISY}.
   * </ul>
   *
   * <p>Transitions to {@link #STATE_NOISY} if noise is encountered. It writes to {@link
   * #maybeSilenceBuffer} in contiguous blocks. If the silence available is enough to wrap around
   * the end of the buffer then the buffer is filled from {@link #maybeSilenceBufferStartIndex} to
   * the buffers end and the beginning of the buffer is filled upon the next call to this method.
   */
  private void shortenSilenceSilenceUntilNoise(ByteBuffer inputBuffer) {
    checkState(maybeSilenceBufferStartIndex < maybeSilenceBuffer.length);

    int limit = inputBuffer.limit();
    int noisePosition = findNoisePosition(inputBuffer);
    int silenceInputSize = noisePosition - inputBuffer.position();

    int indexToWriteTo;
    int contiguousBufferRemaining;
    if (maybeSilenceBufferStartIndex + maybeSilenceBufferContentsSize < maybeSilenceBuffer.length) {
      // ^0---^start---^end---^length
      contiguousBufferRemaining =
          maybeSilenceBuffer.length
              - (maybeSilenceBufferContentsSize + maybeSilenceBufferStartIndex);
      indexToWriteTo = maybeSilenceBufferStartIndex + maybeSilenceBufferContentsSize;
    } else {
      // The bytes have wrapped around.  ^0---^end---^start---^length
      int amountInUpperPartOfBuffer = maybeSilenceBuffer.length - maybeSilenceBufferStartIndex;
      indexToWriteTo = maybeSilenceBufferContentsSize - amountInUpperPartOfBuffer;
      contiguousBufferRemaining = maybeSilenceBufferStartIndex - indexToWriteTo;
    }

    boolean noiseFound = noisePosition < limit;
    // Fill as much of the silence buffer as possible.
    int bytesOfInput = min(silenceInputSize, contiguousBufferRemaining);
    inputBuffer.limit(inputBuffer.position() + bytesOfInput);
    inputBuffer.get(maybeSilenceBuffer, indexToWriteTo, bytesOfInput);
    maybeSilenceBufferContentsSize += bytesOfInput;

    checkState(maybeSilenceBufferContentsSize <= maybeSilenceBuffer.length);

    boolean shouldTransitionToNoisyState =
        noiseFound
            &&
            /* The silence before the noise is not enough to fill the remaining buffer. */
            silenceInputSize < contiguousBufferRemaining;

    outputShortenedSilenceBuffer(shouldTransitionToNoisyState);

    if (shouldTransitionToNoisyState) {
      state = STATE_NOISY;
      outputSilenceFramesSinceNoise = 0;
    }

    // Restore the limit.
    inputBuffer.limit(limit);
  }

  /** See {@link #shortenSilenceSilenceUntilNoise}. */
  private void outputShortenedSilenceBuffer(boolean shouldTransitionToNoisyState) {
    int sizeBeforeOutput = maybeSilenceBufferContentsSize;
    int bytesToOutput;
    @VolumeChangeType int volumeChangeType;
    int bytesConsumed;
    // Only output when buffer is full or transitioning to noisy state.
    if (maybeSilenceBufferContentsSize == maybeSilenceBuffer.length
        || shouldTransitionToNoisyState) {
      if (outputSilenceFramesSinceNoise == 0) {
        // This is the beginning of a silence chunk so keep MINIMUM_SILENCE_DURATION_US / 2 of the
        // silence.
        if (shouldTransitionToNoisyState) {
          volumeChangeType = DO_NOT_CHANGE_VOLUME;
          bytesToOutput = maybeSilenceBufferContentsSize;
          outputSilence(bytesToOutput, volumeChangeType);
          bytesConsumed = bytesToOutput;
        } else {
          checkState(maybeSilenceBufferContentsSize >= maybeSilenceBuffer.length / 2);
          // To keep this block a tad simpler, by always outputting exactly buffer size / 2 to avoid
          // needing to add the shortening code here.
          volumeChangeType = FADE_OUT;
          bytesToOutput = maybeSilenceBuffer.length / 2;
          outputSilence(bytesToOutput, volumeChangeType);
          bytesConsumed = bytesToOutput;
        }
      } else if (shouldTransitionToNoisyState) {
        volumeChangeType = FADE_IN;

        int bytesRemainingAfterOutputtingHalfMin =
            maybeSilenceBufferContentsSize - maybeSilenceBuffer.length / 2;

        bytesConsumed = bytesRemainingAfterOutputtingHalfMin + maybeSilenceBuffer.length / 2;
        int shortenedSilenceLength =
            calculateShortenedSilenceLength(bytesRemainingAfterOutputtingHalfMin);

        // For simplicity we fade in over the shortened silence and the half buffer of padding.
        // This acts to increase the padding a bit which only helps (probably imperceptibly)
        // the sound quality.
        bytesToOutput = maybeSilenceBuffer.length / 2 + shortenedSilenceLength;
        outputSilence(bytesToOutput, volumeChangeType);
      } else {
        volumeChangeType = MUTE;
        // Output as much as possible while still keeping half the buffer full so that half the
        // min silence can be output later as padding.
        bytesConsumed = maybeSilenceBufferContentsSize - maybeSilenceBuffer.length / 2;

        bytesToOutput = calculateShortenedSilenceLength(bytesConsumed);
        outputSilence(bytesToOutput, volumeChangeType);
      }

      checkState(
          bytesConsumed % bytesPerFrame == 0,
          "bytesConsumed is not aligned to frame size: %s" + bytesConsumed);

      checkState((sizeBeforeOutput >= bytesToOutput));

      maybeSilenceBufferContentsSize -= bytesConsumed;
      maybeSilenceBufferStartIndex += bytesConsumed;
      // The start index might wrap back around to the start of the buffer.
      maybeSilenceBufferStartIndex %= maybeSilenceBuffer.length;

      outputSilenceFramesSinceNoise += bytesToOutput / bytesPerFrame;
      skippedFrames += (bytesConsumed - bytesToOutput) / bytesPerFrame;
    }
  }

  /**
   * Returns the appropriate size that a given number of bytes of silence should be shortened to. It
   * calculates this using the {@link #outputSilenceFramesSinceNoise} and the {@link
   * #silenceRetentionRatio}. The {@link #silenceRetentionRatio} multiplied by {@code
   * silenceToShortenBytes} is returned until a max outputted silence length is hit, and then only
   * the remaining silence between the current {@link #outputSilenceFramesSinceNoise} and {@link
   * #maxSilenceToKeepDurationUs} is reached.
   */
  private int calculateShortenedSilenceLength(int silenceToShortenBytes) {
    // Start skipping silence to keep the silence below MAX_SILENCE_DURATION_US long.
    int bytesNeededToReachMax =
        (durationUsToFrames(maxSilenceToKeepDurationUs) - outputSilenceFramesSinceNoise)
                * bytesPerFrame
            - maybeSilenceBuffer.length / 2;

    checkState(bytesNeededToReachMax >= 0);

    return alignToBytePerFrameBoundary(
        min(silenceToShortenBytes * silenceRetentionRatio + .5f, bytesNeededToReachMax));
  }

  /**
   * Method used to avoid rounding errors while calculating output and skipped frames. The given
   * {@code value} is decreased to the nearest value that is divisible by {@link #bytesPerFrame}.
   */
  private int alignToBytePerFrameBoundary(int value) {
    return (value / bytesPerFrame) * bytesPerFrame;
  }

  /**
   * Method used to avoid rounding errors while calculating output and skipped frames. The given
   * {@code value} is decreased to the nearest value that is divisible by {@link #bytesPerFrame}.
   */
  private int alignToBytePerFrameBoundary(float value) {
    return alignToBytePerFrameBoundary((int) value);
  }

  /** Copies elements from {@code data} to populate a new output buffer from the processor. */
  private void outputRange(byte[] data, int size, @VolumeChangeType int rampType) {
    checkArgument(
        size % bytesPerFrame == 0, "byteOutput size is not aligned to frame size " + size);

    modifyVolume(data, size, rampType);
    replaceOutputBuffer(size).put(data, 0, size).flip();
  }

  /**
   * Copies {@code sizeToOutput} elements from the {@link #maybeSilenceBuffer} to {@link
   * #contiguousOutputBuffer}. The contents of {@link #maybeSilenceBuffer} can wrap around from the
   * end of the buffer and back to the beginning. The {@link #contiguousOutputBuffer} content always
   * start from index 0.
   *
   * @param rampType This parameter is used to determine which part of the {@link
   *     #maybeSilenceBuffer} contents need to be kept. For {@link #FADE_IN} the end of the contents
   *     is always kept. Otherwise the beginning of the contents are always kept.
   */
  private void outputSilence(int sizeToOutput, @VolumeChangeType int rampType) {
    if (sizeToOutput == 0) {
      return;
    }

    checkArgument(maybeSilenceBufferContentsSize >= sizeToOutput);

    if (rampType == FADE_IN) {
      // Keeps the end of the buffer because we are padding the start of the next chunk of noise.
      if (maybeSilenceBufferStartIndex + maybeSilenceBufferContentsSize
          <= maybeSilenceBuffer.length) {
        // ^0---^start---^end---^length
        System.arraycopy(
            maybeSilenceBuffer,
            maybeSilenceBufferStartIndex + maybeSilenceBufferContentsSize - sizeToOutput,
            contiguousOutputBuffer,
            0,
            sizeToOutput);
      } else {
        // ^0---^end--^start---^length
        int sizeInUpperPartOfArray = maybeSilenceBuffer.length - maybeSilenceBufferStartIndex;
        int sizeInLowerPartOfArray = maybeSilenceBufferContentsSize - sizeInUpperPartOfArray;
        if (sizeInLowerPartOfArray >= sizeToOutput) {
          // We just need the lower part of the array.
          System.arraycopy(
              maybeSilenceBuffer,
              sizeInLowerPartOfArray - sizeToOutput,
              contiguousOutputBuffer,
              0,
              sizeToOutput);
        } else {
          int sizeToOutputInUpperPart = sizeToOutput - sizeInLowerPartOfArray;
          System.arraycopy(
              maybeSilenceBuffer,
              maybeSilenceBuffer.length - sizeToOutputInUpperPart,
              contiguousOutputBuffer,
              0,
              sizeToOutputInUpperPart);

          // Copy everything from lower part. DO_NOT_CHANGE_VOLUME (which keeps everything) and
          // MUTE (where the content that is kept only provides background noise).
          System.arraycopy(
              maybeSilenceBuffer,
              0,
              contiguousOutputBuffer,
              sizeToOutputInUpperPart,
              sizeInLowerPartOfArray);
        }
      }
    } else {
      if (maybeSilenceBufferStartIndex + sizeToOutput <= maybeSilenceBuffer.length) {
        // ^0---^start---^end---^length
        System.arraycopy(
            maybeSilenceBuffer,
            maybeSilenceBufferStartIndex,
            contiguousOutputBuffer,
            0,
            sizeToOutput);
      } else {
        // ^0---^end (of content to output now)---^start---^length
        int sizeToCopyInUpperPartOfArray = maybeSilenceBuffer.length - maybeSilenceBufferStartIndex;
        // Copy the upper part of the array.
        System.arraycopy(
            maybeSilenceBuffer,
            maybeSilenceBufferStartIndex,
            contiguousOutputBuffer,
            0,
            sizeToCopyInUpperPartOfArray);
        int amountToCopyFromLowerPartOfArray = sizeToOutput - sizeToCopyInUpperPartOfArray;
        System.arraycopy(
            maybeSilenceBuffer,
            0,
            contiguousOutputBuffer,
            sizeToCopyInUpperPartOfArray,
            amountToCopyFromLowerPartOfArray);
      }
    }

    checkArgument(
        sizeToOutput % bytesPerFrame == 0,
        "sizeToOutput is not aligned to frame size: " + sizeToOutput);
    checkState(maybeSilenceBufferStartIndex < maybeSilenceBuffer.length);

    outputRange(contiguousOutputBuffer, sizeToOutput, rampType);
  }

  /**
   * Modifies the amplitude of the samples in {@code sampleBuffer} based on the given {@link
   * VolumeChangeType}.
   */
  private void modifyVolume(byte[] sampleBuffer, int size, @VolumeChangeType int volumeChangeType) {
    if (volumeChangeType == DO_NOT_CHANGE_VOLUME) {
      return;
    }

    for (int idx = 0; idx < size; idx += 2) {
      byte mostSignificantByte = sampleBuffer[idx + 1];
      byte leastSignificantByte = sampleBuffer[idx];
      int sample = twoByteSampleToInt(mostSignificantByte, leastSignificantByte);

      int volumeModificationPercentage;
      if (volumeChangeType == FADE_OUT) {
        volumeModificationPercentage =
            calculateFadeOutPercentage(/* value= */ idx, /* max= */ size - 1);
      } else if (volumeChangeType == FADE_IN) {
        volumeModificationPercentage =
            calculateFadeInPercentage(/* value= */ idx, /* max= */ size - 1);
      } else {
        volumeModificationPercentage = minVolumeToKeepPercentageWhenMuting;
      }

      sample = (sample * volumeModificationPercentage) / 100;
      sampleIntToTwoBigEndianBytes(sampleBuffer, idx, sample);
    }
  }

  private int calculateFadeOutPercentage(int value, int max) {
    return ((minVolumeToKeepPercentageWhenMuting - 100) * ((AVOID_TRUNCATION_FACTOR * value) / max))
            / AVOID_TRUNCATION_FACTOR
        + 100;
  }

  private int calculateFadeInPercentage(int value, int max) {
    return (minVolumeToKeepPercentageWhenMuting
        + ((100 - minVolumeToKeepPercentageWhenMuting) * (AVOID_TRUNCATION_FACTOR * value) / max)
            / AVOID_TRUNCATION_FACTOR);
  }

  private static int twoByteSampleToInt(byte mostSignificantByte, byte leastSignificantByte) {
    return ((leastSignificantByte & 0xFF) | mostSignificantByte << 8);
  }

  /**
   * Converts {@code sample} into the corresponding big-endian 16bit bytes within {@code byteArray}.
   */
  private static void sampleIntToTwoBigEndianBytes(byte[] byteArray, int startIndex, int sample) {
    // Avoid 16-bit-integer overflow when writing back the manipulated data.
    if (sample >= Short.MAX_VALUE) {
      byteArray[startIndex] = (byte) 0xFF;
      byteArray[startIndex + 1] = (byte) 0x7F;
    } else if (sample <= Short.MIN_VALUE) {
      byteArray[startIndex] = (byte) 0x00;
      byteArray[startIndex + 1] = (byte) 0x80;
    } else {
      byteArray[startIndex] = (byte) (sample & 0xFF);
      byteArray[startIndex + 1] = (byte) (sample >> 8);
    }
  }

  /**
   * Copies remaining bytes from {@code data} to populate a new output buffer from the processor.
   */
  private void output(ByteBuffer data) {
    replaceOutputBuffer(data.remaining()).put(data).flip();
  }

  /**
   * Returns the number of input frames corresponding to {@code durationUs} microseconds of audio.
   */
  private int durationUsToFrames(long durationUs) {
    return (int) ((durationUs * inputFormat.sampleRate) / C.MICROS_PER_SECOND);
  }

  /**
   * Returns the earliest byte position in [position, limit) of {@code buffer} that contains a frame
   * classified as a noisy frame, or the limit of the buffer if no such frame exists.
   */
  private int findNoisePosition(ByteBuffer buffer) {
    // The input is in ByteOrder.nativeOrder(), which is little endian on Android.
    for (int i = buffer.position() + 1; i < buffer.limit(); i += 2) {
      if (isNoise(buffer.get(i), buffer.get(i - 1))) {
        // Round to the start of the frame.
        return bytesPerFrame * (i / bytesPerFrame);
      }
    }
    return buffer.limit();
  }

  /**
   * Returns the earliest byte position in [position, limit) of {@code buffer} such that all frames
   * from the byte position to the limit are classified as silent.
   */
  private int findNoiseLimit(ByteBuffer buffer) {
    // The input is in ByteOrder.nativeOrder(), which is little endian on Android.
    for (int i = buffer.limit() - 1; i >= buffer.position(); i -= 2) {
      if (isNoise(buffer.get(i), buffer.get(i - 1))) {
        // Return the start of the next frame.
        return bytesPerFrame * (i / bytesPerFrame) + bytesPerFrame;
      }
    }
    return buffer.position();
  }

  /**
   * Whether the given two bytes represent a short signed PCM value that is greater than {@link
   * #silenceThresholdLevel}.
   */
  private boolean isNoise(byte mostSignificantByte, byte leastSignificantByte) {
    return Math.abs(twoByteSampleToInt(mostSignificantByte, leastSignificantByte))
        > silenceThresholdLevel;
  }
}
