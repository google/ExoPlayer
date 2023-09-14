/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.image;

import static androidx.media3.common.C.FIRST_FRAME_NOT_RENDERED;
import static androidx.media3.common.C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED;
import static androidx.media3.common.C.FIRST_FRAME_RENDERED;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.Bitmap;
import androidx.annotation.IntDef;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.LongArrayQueue;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.BaseRenderer;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A {@link Renderer} implementation for images. */
@UnstableApi
public final class ImageRenderer extends BaseRenderer {

  private static final String TAG = "ImageRenderer";

  /** Decoder reinitialization states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    REINITIALIZATION_STATE_NONE,
    REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM_THEN_WAIT,
    REINITIALIZATION_STATE_WAIT_END_OF_STREAM
  })
  private @interface ReinitializationState {}

  /** The decoder does not need to be re-initialized. */
  private static final int REINITIALIZATION_STATE_NONE = 0;

  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, but we
   * haven't yet signaled an end of stream to the existing decoder. We need to do so in order to
   * ensure that it outputs any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM_THEN_WAIT = 2;

  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, and we've
   * signaled an end of stream to the existing decoder. We're waiting for the decoder to output an
   * end of stream signal to indicate that it has output any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 3;

  private final ImageDecoder.Factory decoderFactory;
  private final ImageOutput imageOutput;
  private final DecoderInputBuffer flagsOnlyBuffer;
  private final LongArrayQueue offsetQueue;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private @ReinitializationState int decoderReinitializationState;
  private @C.FirstFrameState int firstFrameState;
  private @Nullable Format inputFormat;
  private @Nullable ImageDecoder decoder;
  private @Nullable DecoderInputBuffer inputBuffer;
  private @Nullable ImageOutputBuffer outputBuffer;

  /**
   * Creates an instance.
   *
   * @param decoderFactory A {@link ImageDecoder.Factory} that supplies a decoder depending on the
   *     format provided.
   * @param imageOutput The rendering component to send the {@link Bitmap} and rendering commands
   *     to.
   */
  public ImageRenderer(ImageDecoder.Factory decoderFactory, ImageOutput imageOutput) {
    super(C.TRACK_TYPE_IMAGE);
    this.decoderFactory = decoderFactory;
    this.imageOutput = imageOutput;
    flagsOnlyBuffer = DecoderInputBuffer.newNoDataInstance();
    offsetQueue = new LongArrayQueue();
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    firstFrameState = FIRST_FRAME_NOT_RENDERED;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public @Capabilities int supportsFormat(Format format) {
    return decoderFactory.supportsFormat(format);
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }
    // If the offsetQueue is empty, we haven't been given a stream to render.
    checkState(!offsetQueue.isEmpty());
    if (inputFormat == null) {
      // We don't have a format yet, so try and read one.
      FormatHolder formatHolder = getFormatHolder();
      flagsOnlyBuffer.clear();
      @SampleStream.ReadDataResult
      int result = readSource(formatHolder, flagsOnlyBuffer, FLAG_REQUIRE_FORMAT);
      if (result == C.RESULT_FORMAT_READ) {
        // Note that this works because we only expect to enter this if-condition once per playback.
        inputFormat = checkNotNull(formatHolder.format);
        initDecoder();
      } else if (result == C.RESULT_BUFFER_READ) {
        // End of stream read having not read a format.
        checkState(flagsOnlyBuffer.isEndOfStream());
        inputStreamEnded = true;
        outputStreamEnded = true;
        return;
      } else {
        // We still don't have a format and can't make progress without one.
        return;
      }
    }
    try {
      // Rendering loop.
      TraceUtil.beginSection("drainAndFeedDecoder");
      while (drainOutputBuffer(positionUs, elapsedRealtimeUs)) {}
      while (feedInputBuffer()) {}
      TraceUtil.endSection();
    } catch (ImageDecoderException e) {
      throw createRendererException(e, null, PlaybackException.ERROR_CODE_DECODING_FAILED);
    }
  }

  @Override
  public boolean isReady() {
    return firstFrameState == FIRST_FRAME_RENDERED
        || (firstFrameState == FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED
            && outputBuffer != null);
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
    firstFrameState =
        mayRenderStartOfStream
            ? C.FIRST_FRAME_NOT_RENDERED
            : C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED;
  }

  @Override
  protected void onStreamChanged(
      Format[] formats,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId);
    offsetQueue.add(offsetUs);
    inputStreamEnded = false;
    outputStreamEnded = false;
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    lowerFirstFrameState(FIRST_FRAME_NOT_RENDERED);
  }

  @Override
  protected void onDisabled() {
    offsetQueue.clear();
    inputFormat = null;
    releaseDecoderResources();
    imageOutput.onDisabled();
  }

  @Override
  protected void onReset() {
    offsetQueue.clear();
    releaseDecoderResources();
    lowerFirstFrameState(FIRST_FRAME_NOT_RENDERED);
  }

  @Override
  protected void onRelease() {
    offsetQueue.clear();
    releaseDecoderResources();
  }

  /**
   * Attempts to dequeue an output buffer from the decoder and, if successful and permitted to,
   * renders it.
   *
   * @param positionUs The player's current position.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @return Whether it may be possible to drain more output data.
   * @throws ImageDecoderException If an error occurs draining the output buffer.
   */
  private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ImageDecoderException, ExoPlaybackException {
    if (outputBuffer == null) {
      checkStateNotNull(decoder);
      outputBuffer = decoder.dequeueOutputBuffer();
      if (outputBuffer == null) {
        return false;
      }
    }
    if (firstFrameState == FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED
        && getState() != STATE_STARTED) {
      return false;
    }
    if (checkNotNull(outputBuffer).isEndOfStream()) {
      offsetQueue.remove();
      if (decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
        // We're waiting to re-initialize the decoder, and have now processed all final buffers.
        releaseDecoderResources();
        checkStateNotNull(inputFormat);
        initDecoder();
      } else {
        checkNotNull(outputBuffer).release();
        outputBuffer = null;
        if (offsetQueue.isEmpty()) {
          outputStreamEnded = true;
        }
      }
      return false;
    }
    checkStateNotNull(outputBuffer);
    if (!processOutputBuffer(positionUs, elapsedRealtimeUs)) {
      return false;
    }
    firstFrameState = FIRST_FRAME_RENDERED;
    return true;
  }

  @SuppressWarnings("unused") // Will be used or removed when the integrated with the videoSink.
  @RequiresNonNull("outputBuffer")
  private boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs) {
    Bitmap outputBitmap =
        checkNotNull(
            outputBuffer.bitmap, "Non-EOS buffer came back from the decoder without bitmap.");
    if (positionUs < outputBuffer.timeUs) {
      // It's too early to render the buffer.
      return false;
    }
    imageOutput.onImageAvailable(outputBuffer.timeUs - offsetQueue.element(), outputBitmap);
    checkNotNull(outputBuffer).release();
    outputBuffer = null;
    return true;
  }

  /**
   * @return Whether we can feed more input data to the decoder.
   */
  private boolean feedInputBuffer() throws ImageDecoderException {
    FormatHolder formatHolder = getFormatHolder();
    if (decoder == null
        || decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM
        || inputStreamEnded) {
      // We need to reinitialize the decoder or the input stream has ended.
      return false;
    }
    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }
    if (decoderReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM_THEN_WAIT) {
      checkStateNotNull(inputBuffer);
      inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      checkNotNull(decoder).queueInputBuffer(inputBuffer);
      inputBuffer = null;
      decoderReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
      return false;
    }
    switch (readSource(formatHolder, inputBuffer, /* readFlags= */ 0)) {
      case C.RESULT_NOTHING_READ:
        return false;
      case C.RESULT_BUFFER_READ:
        inputBuffer.flip();
        checkNotNull(decoder).queueInputBuffer(inputBuffer);
        if (inputBuffer.isEndOfStream()) {
          inputStreamEnded = true;
          inputBuffer = null;
          return false;
        }
        inputBuffer = null;
        return true;
      case C.RESULT_FORMAT_READ:
        inputFormat = checkNotNull(formatHolder.format);
        decoderReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM_THEN_WAIT;
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  @RequiresNonNull("inputFormat")
  @EnsuresNonNull("decoder")
  private void initDecoder() throws ExoPlaybackException {
    if (canCreateDecoderForFormat(inputFormat)) {
      if (decoder != null) {
        decoder.release();
      }
      decoder = decoderFactory.createImageDecoder();
    } else {
      throw createRendererException(
          new ImageDecoderException("Provided decoder factory can't create decoder for format."),
          inputFormat,
          PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
    }
  }

  private boolean canCreateDecoderForFormat(Format format) {
    @Capabilities int supportsFormat = decoderFactory.supportsFormat(format);
    return supportsFormat == RendererCapabilities.create(C.FORMAT_HANDLED)
        || supportsFormat == RendererCapabilities.create(C.FORMAT_EXCEEDS_CAPABILITIES);
  }

  private void lowerFirstFrameState(@C.FirstFrameState int firstFrameState) {
    this.firstFrameState = min(this.firstFrameState, firstFrameState);
  }

  private void releaseDecoderResources() {
    inputBuffer = null;
    if (outputBuffer != null) {
      outputBuffer.release();
    }
    outputBuffer = null;
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    if (decoder != null) {
      decoder.release();
      decoder = null;
    }
  }
}
