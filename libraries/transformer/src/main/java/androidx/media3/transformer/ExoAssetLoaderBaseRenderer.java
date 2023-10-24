/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static androidx.media3.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_DECODED;
import static androidx.media3.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_ENCODED;
import static androidx.media3.transformer.TransformerUtil.getProcessedTrackType;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.BaseRenderer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.MediaClock;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream.ReadDataResult;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ abstract class ExoAssetLoaderBaseRenderer extends BaseRenderer {

  protected long streamStartPositionUs;
  protected long streamOffsetUs;
  protected @MonotonicNonNull SampleConsumer sampleConsumer;
  protected @MonotonicNonNull Codec decoder;
  protected boolean isEnded;
  private @MonotonicNonNull Format inputFormat;
  private @MonotonicNonNull Format outputFormat;

  private final TransformerMediaClock mediaClock;
  private final AssetLoader.Listener assetLoaderListener;
  private final DecoderInputBuffer decoderInputBuffer;

  private boolean isRunning;
  private boolean shouldInitDecoder;
  private boolean hasPendingConsumerInput;

  public ExoAssetLoaderBaseRenderer(
      @C.TrackType int trackType,
      TransformerMediaClock mediaClock,
      AssetLoader.Listener assetLoaderListener) {
    super(trackType);
    this.mediaClock = mediaClock;
    this.assetLoaderListener = assetLoaderListener;
    decoderInputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  /**
   * Returns whether the renderer supports the track type of the given format.
   *
   * @param format The format.
   * @return The {@link Capabilities} for this format.
   */
  @Override
  public @Capabilities int supportsFormat(Format format) {
    return RendererCapabilities.create(
        MimeTypes.getTrackType(format.sampleMimeType) == getTrackType()
            ? C.FORMAT_HANDLED
            : C.FORMAT_UNSUPPORTED_TYPE);
  }

  @Override
  public MediaClock getMediaClock() {
    return mediaClock;
  }

  @Override
  public boolean isReady() {
    return isSourceReady();
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) {
    try {
      if (!isRunning || isEnded() || !readInputFormatAndInitDecoderIfNeeded()) {
        return;
      }

      if (decoder != null) {
        boolean progressMade;
        do {
          progressMade = false;
          if (ensureSampleConsumerInitialized()) {
            progressMade = feedConsumerFromDecoder();
          }
          progressMade |= feedDecoderFromInput();
        } while (progressMade);
      } else {
        if (ensureSampleConsumerInitialized()) {
          while (feedConsumerFromInput()) {}
        }
      }

    } catch (ExportException e) {
      isRunning = false;
      assetLoaderListener.onError(e);
    }
  }

  @Override
  protected void onStreamChanged(
      Format[] formats,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId) {
    this.streamStartPositionUs = startPositionUs;
    this.streamOffsetUs = offsetUs;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
    mediaClock.updateTimeForTrackType(getTrackType(), 0L);
  }

  @Override
  protected void onStarted() {
    isRunning = true;
  }

  @Override
  protected void onStopped() {
    isRunning = false;
  }

  @Override
  protected void onReset() {
    if (decoder != null) {
      decoder.release();
    }
  }

  /** Overrides the {@code inputFormat}. */
  protected Format overrideFormat(Format inputFormat) {
    return inputFormat;
  }

  /** Called when the {@link Format} of the samples fed to the renderer is known. */
  protected void onInputFormatRead(Format inputFormat) {}

  /** Initializes {@link #decoder} with an appropriate {@linkplain Codec decoder}. */
  @EnsuresNonNull("decoder")
  protected abstract void initDecoder(Format inputFormat) throws ExportException;

  /**
   * Preprocesses an encoded {@linkplain DecoderInputBuffer input buffer} and returns whether it
   * should be dropped.
   *
   * <p>The input buffer is cleared if it should be dropped.
   */
  protected abstract boolean shouldDropInputBuffer(DecoderInputBuffer inputBuffer);

  /** Called before a {@link DecoderInputBuffer} is queued to the decoder. */
  protected void onDecoderInputReady(DecoderInputBuffer inputBuffer) {}

  /**
   * Attempts to get decoded data and pass it to the sample consumer.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws ExportException If an error occurs in the decoder.
   */
  @RequiresNonNull({"sampleConsumer", "decoder"})
  protected abstract boolean feedConsumerFromDecoder() throws ExportException;

  /**
   * Attempts to read the input {@link Format} from the source, if not read.
   *
   * <p>After reading the format, {@link AssetLoader.Listener#onTrackAdded} is notified, and, if
   * needed, the decoder is {@linkplain #initDecoder(Format) initialized}.
   *
   * @return Whether the input {@link Format} is available.
   * @throws ExportException If an error occurs {@linkplain #initDecoder initializing} the
   *     {@linkplain Codec decoder}.
   */
  @EnsuresNonNullIf(expression = "inputFormat", result = true)
  private boolean readInputFormatAndInitDecoderIfNeeded() throws ExportException {
    if (inputFormat != null && !shouldInitDecoder) {
      return true;
    }

    if (inputFormat == null) {
      FormatHolder formatHolder = getFormatHolder();
      @ReadDataResult
      int result =
          readSource(formatHolder, decoderInputBuffer, /* readFlags= */ FLAG_REQUIRE_FORMAT);
      if (result != C.RESULT_FORMAT_READ) {
        return false;
      }
      inputFormat = overrideFormat(checkNotNull(formatHolder.format));
      onInputFormatRead(inputFormat);
      shouldInitDecoder =
          assetLoaderListener.onTrackAdded(
              inputFormat, SUPPORTED_OUTPUT_TYPE_DECODED | SUPPORTED_OUTPUT_TYPE_ENCODED);
    }

    if (shouldInitDecoder) {
      if (getProcessedTrackType(inputFormat.sampleMimeType) == C.TRACK_TYPE_VIDEO) {
        // TODO(b/278259383): Move surface creation out of video sampleConsumer. Init decoder and
        // get decoder output Format before init sampleConsumer.
        if (!ensureSampleConsumerInitialized()) {
          return false;
        }
      }
      initDecoder(inputFormat);
      shouldInitDecoder = false;
    }

    return true;
  }

  /**
   * Attempts to initialize the {@link SampleConsumer}, if not initialized.
   *
   * @return Whether the {@link SampleConsumer} is initialized.
   * @throws ExportException If the {@linkplain Codec decoder} errors getting it's {@linkplain
   *     Codec#getOutputFormat() output format}.
   * @throws ExportException If the {@link AssetLoader.Listener} errors providing a {@link
   *     SampleConsumer}.
   */
  @RequiresNonNull("inputFormat")
  @EnsuresNonNullIf(expression = "sampleConsumer", result = true)
  private boolean ensureSampleConsumerInitialized() throws ExportException {
    if (sampleConsumer != null) {
      return true;
    }

    if (outputFormat == null) {
      if (decoder != null
          && getProcessedTrackType(inputFormat.sampleMimeType) == C.TRACK_TYPE_AUDIO) {
        @Nullable Format decoderOutputFormat = decoder.getOutputFormat();
        if (decoderOutputFormat == null) {
          return false;
        }
        outputFormat = decoderOutputFormat;
      } else {
        // TODO(b/278259383): Move surface creation out of video sampleConsumer. Init decoder and
        // get decoderOutput Format before init sampleConsumer.
        outputFormat = inputFormat;
      }
    }

    SampleConsumer sampleConsumer = assetLoaderListener.onOutputFormat(outputFormat);
    if (sampleConsumer == null) {
      return false;
    }
    this.sampleConsumer = sampleConsumer;
    return true;
  }

  /**
   * Attempts to read input data and pass it to the decoder.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws ExportException If an error occurs in the decoder.
   */
  @RequiresNonNull("decoder")
  private boolean feedDecoderFromInput() throws ExportException {
    if (!decoder.maybeDequeueInputBuffer(decoderInputBuffer)) {
      return false;
    }

    if (!readInput(decoderInputBuffer)) {
      return false;
    }

    if (shouldDropInputBuffer(decoderInputBuffer)) {
      return true;
    }

    onDecoderInputReady(decoderInputBuffer);
    decoder.queueInputBuffer(decoderInputBuffer);
    return true;
  }

  /**
   * Attempts to read input data and pass it to the sample consumer.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   */
  @RequiresNonNull("sampleConsumer")
  private boolean feedConsumerFromInput() {
    @Nullable DecoderInputBuffer sampleConsumerInputBuffer = sampleConsumer.getInputBuffer();
    if (sampleConsumerInputBuffer == null) {
      return false;
    }

    if (!hasPendingConsumerInput) {
      if (!readInput(sampleConsumerInputBuffer)) {
        return false;
      }
      if (shouldDropInputBuffer(sampleConsumerInputBuffer)) {
        return true;
      }
      hasPendingConsumerInput = true;
    }

    boolean isInputEnded = sampleConsumerInputBuffer.isEndOfStream();
    if (!sampleConsumer.queueInputBuffer()) {
      return false;
    }

    hasPendingConsumerInput = false;
    isEnded = isInputEnded;
    return !isEnded;
  }

  /**
   * Attempts to populate {@code buffer} with input data.
   *
   * @param buffer The buffer to populate.
   * @return Whether the {@code buffer} has been populated.
   */
  private boolean readInput(DecoderInputBuffer buffer) {
    @ReadDataResult int result = readSource(getFormatHolder(), buffer, /* readFlags= */ 0);
    switch (result) {
      case C.RESULT_BUFFER_READ:
        buffer.flip();
        if (!buffer.isEndOfStream()) {
          mediaClock.updateTimeForTrackType(getTrackType(), buffer.timeUs);
        }
        return true;
      case C.RESULT_FORMAT_READ:
        throw new IllegalStateException("Format changes are not supported.");
      case C.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }
}
