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
package com.google.android.exoplayer2.ext.image;

import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK;
import static com.google.android.exoplayer2.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.graphics.Bitmap;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.TraceUtil;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

// TODO(b/289989736): Currently works for one stream only. Refactor so that it works for multiple
//   inputs streams.
/**
 * A {@link Renderer} implementation for images.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class ImageRenderer extends BaseRenderer {
  private static final String TAG = "ImageRenderer";

  private final DecoderInputBuffer flagsOnlyBuffer;
  private final ImageDecoder.Factory decoderFactory;
  private final ImageOutput imageOutput;

  private @C.FirstFrameState int firstFrameState;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private long durationUs;
  private long offsetUs;
  private @Nullable ImageDecoder decoder;
  private @Nullable DecoderInputBuffer inputBuffer;
  private @Nullable ImageOutputBuffer outputBuffer;
  private @MonotonicNonNull Format inputFormat;

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
    flagsOnlyBuffer = DecoderInputBuffer.newNoDataInstance();
    this.decoderFactory = decoderFactory;
    this.imageOutput = imageOutput;
    durationUs = C.TIME_UNSET;
    firstFrameState = C.FIRST_FRAME_NOT_RENDERED;
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
    checkState(durationUs != C.TIME_UNSET);
    if (outputStreamEnded) {
      return;
    }

    if (inputFormat == null) {
      // We don't have a format yet, so try and read one.
      FormatHolder formatHolder = getFormatHolder();
      flagsOnlyBuffer.clear();
      @SampleStream.ReadDataResult
      int result = readSource(formatHolder, flagsOnlyBuffer, FLAG_REQUIRE_FORMAT);
      if (result == C.RESULT_FORMAT_READ) {
        // Note that this works because we only expect to enter this if-condition once per playback
        // for now.
        maybeInitDecoder(checkNotNull(formatHolder.format));
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
    return firstFrameState == C.FIRST_FRAME_RENDERED;
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  protected void onStreamChanged(
      Format[] formats,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    // TODO(b/289989736): when the mediaPeriodId is signalled to the renders, collect and set
    //   durationUs here.
    durationUs = 2 * C.MICROS_PER_SECOND;
    this.offsetUs = offsetUs;
    super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    // Since the renderer only supports playing one image from, this is currently a no-op (don't
    // need to consider a new stream because it will be the same as the last one).
  }

  @Override
  protected void onDisabled() {
    releaseResources();
  }

  @Override
  protected void onReset() {
    releaseResources();
  }

  @Override
  protected void onRelease() {
    releaseResources();
  }

  /**
   * Attempts to dequeue an output buffer from the decoder and, if successful, renders it.
   *
   * @param positionUs The player's current position.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @return Whether it may be possible to drain more output data.
   * @throws ImageDecoderException If an error occurs draining the output buffer.
   */
  private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ImageDecoderException {
    if (outputBuffer == null) {
      checkStateNotNull(decoder);
      outputBuffer = decoder.dequeueOutputBuffer();
      if (outputBuffer == null) {
        return false;
      }
    }
    if (outputBuffer.isEndOfStream()) {
      outputBuffer.release();
      outputBuffer = null;
      outputStreamEnded = true;
      return false;
    }

    if (!processOutputBuffer(positionUs, elapsedRealtimeUs)) {
      return false;
    }

    firstFrameState = C.FIRST_FRAME_RENDERED;
    return true;
  }

  @RequiresNonNull("outputBuffer")
  @SuppressWarnings("unused") // Will be used or removed when the integrated with the videoSink.
  private boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs) {
    checkStateNotNull(
        outputBuffer.bitmap, "Non-EOS buffer came back from the decoder without bitmap.");
    imageOutput.onImageAvailable(positionUs - offsetUs, outputBuffer.bitmap);
    checkNotNull(outputBuffer).release();
    outputBuffer = null;
    return true;
  }

  /**
   * @return Whether we can feed more input data to the decoder.
   */
  private boolean feedInputBuffer() throws ExoPlaybackException, ImageDecoderException {
    FormatHolder formatHolder = getFormatHolder();
    if (decoder == null || inputStreamEnded) {
      return false;
    }
    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
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
        if (checkNotNull(formatHolder.format).equals(inputFormat)) {
          return true;
        }
        throw createRendererException(
            new UnsupportedOperationException(
                "Changing format is not supported in the ImageRenderer."),
            formatHolder.format,
            ERROR_CODE_FAILED_RUNTIME_CHECK);
      default:
        throw new IllegalStateException();
    }
  }

  @EnsuresNonNull("decoder")
  private void maybeInitDecoder(Format format) throws ExoPlaybackException {
    if (inputFormat != null && inputFormat.equals(format) && decoder != null) {
      return;
    }
    inputFormat = format;
    if (canCreateDecoderForFormat(format)) {
      if (decoder != null) {
        decoder.release();
      }
      decoder = decoderFactory.createImageDecoder();
    } else {
      throw createRendererException(
          new ImageDecoderException("Provided decoder factory can't create decoder for format."),
          format,
          PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
    }
  }

  private boolean canCreateDecoderForFormat(Format format) {
    @Capabilities int supportsFormat = decoderFactory.supportsFormat(format);
    return supportsFormat == RendererCapabilities.create(C.FORMAT_HANDLED)
        || supportsFormat == RendererCapabilities.create(C.FORMAT_EXCEEDS_CAPABILITIES);
  }

  private void releaseResources() {
    inputBuffer = null;
    if (outputBuffer != null) {
      outputBuffer.release();
    }
    outputBuffer = null;
    if (decoder != null) {
      decoder.release();
      decoder = null;
    }
  }
}
