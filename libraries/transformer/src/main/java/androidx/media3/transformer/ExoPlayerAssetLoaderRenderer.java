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

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.BaseRenderer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.MediaClock;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.source.SampleStream.ReadDataResult;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ final class ExoPlayerAssetLoaderRenderer extends BaseRenderer {

  private static final String TAG = "ExoPlayerAssetLoaderRenderer";

  private final boolean flattenForSlowMotion;
  private final Codec.DecoderFactory decoderFactory;
  private final TransformerMediaClock mediaClock;
  private final AssetLoader.Listener assetLoaderListener;
  private final DecoderInputBuffer decoderInputBuffer;

  private boolean isTransformationRunning;
  private long streamStartPositionUs;
  private long streamOffsetUs;
  private @MonotonicNonNull SefSlowMotionFlattener sefVideoSlowMotionFlattener;
  private @MonotonicNonNull Codec decoder;
  @Nullable private ByteBuffer pendingDecoderOutputBuffer;
  private SamplePipeline.@MonotonicNonNull Input samplePipelineInput;
  private boolean isEnded;

  public ExoPlayerAssetLoaderRenderer(
      int trackType,
      boolean flattenForSlowMotion,
      Codec.DecoderFactory decoderFactory,
      TransformerMediaClock mediaClock,
      AssetLoader.Listener assetLoaderListener) {
    super(trackType);
    this.flattenForSlowMotion = flattenForSlowMotion;
    this.decoderFactory = decoderFactory;
    this.mediaClock = mediaClock;
    this.assetLoaderListener = assetLoaderListener;
    decoderInputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  @Override
  public String getName() {
    return TAG;
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
      if (!isTransformationRunning || isEnded() || !ensureConfigured()) {
        return;
      }

      if (samplePipelineInput.expectsDecodedData()) {
        while (feedPipelineFromDecoder() || feedDecoderFromInput()) {}
      } else {
        while (feedPipelineFromInput()) {}
      }
    } catch (TransformationException e) {
      isTransformationRunning = false;
      assetLoaderListener.onError(e);
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
    this.streamStartPositionUs = startPositionUs;
    this.streamOffsetUs = offsetUs;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
    assetLoaderListener.onTrackRegistered();
    mediaClock.updateTimeForTrackType(getTrackType(), 0L);
  }

  @Override
  protected void onStarted() {
    isTransformationRunning = true;
  }

  @Override
  protected void onStopped() {
    isTransformationRunning = false;
  }

  @Override
  protected void onReset() {
    if (decoder != null) {
      decoder.release();
    }
  }

  @EnsuresNonNullIf(expression = "samplePipelineInput", result = true)
  private boolean ensureConfigured() throws TransformationException {
    if (samplePipelineInput != null) {
      return true;
    }

    FormatHolder formatHolder = getFormatHolder();
    @ReadDataResult
    int result = readSource(formatHolder, decoderInputBuffer, /* readFlags= */ FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    Format inputFormat = checkNotNull(formatHolder.format);
    samplePipelineInput =
        assetLoaderListener.onTrackAdded(inputFormat, streamStartPositionUs, streamOffsetUs);
    if (getTrackType() == C.TRACK_TYPE_VIDEO && flattenForSlowMotion) {
      sefVideoSlowMotionFlattener = new SefSlowMotionFlattener(inputFormat);
    }
    if (samplePipelineInput.expectsDecodedData()) {
      decoder = decoderFactory.createForAudioDecoding(inputFormat);
    }
    return true;
  }

  /**
   * Attempts to read decoded data and pass it to the sample pipeline.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws TransformationException If an error occurs in the decoder or in the {@link
   *     SamplePipeline}.
   */
  @RequiresNonNull("samplePipelineInput")
  private boolean feedPipelineFromDecoder() throws TransformationException {
    @Nullable
    DecoderInputBuffer samplePipelineInputBuffer = samplePipelineInput.dequeueInputBuffer();
    if (samplePipelineInputBuffer == null) {
      return false;
    }

    Codec decoder = checkNotNull(this.decoder);
    if (pendingDecoderOutputBuffer != null) {
      if (pendingDecoderOutputBuffer.hasRemaining()) {
        return false;
      } else {
        decoder.releaseOutputBuffer(/* render= */ false);
        pendingDecoderOutputBuffer = null;
      }
    }

    if (decoder.isEnded()) {
      samplePipelineInputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      samplePipelineInput.queueInputBuffer();
      isEnded = true;
      return false;
    }

    pendingDecoderOutputBuffer = decoder.getOutputBuffer();
    if (pendingDecoderOutputBuffer == null) {
      return false;
    }

    samplePipelineInputBuffer.data = pendingDecoderOutputBuffer;
    MediaCodec.BufferInfo bufferInfo = checkNotNull(decoder.getOutputBufferInfo());
    samplePipelineInputBuffer.timeUs = bufferInfo.presentationTimeUs;
    samplePipelineInputBuffer.setFlags(bufferInfo.flags);
    samplePipelineInput.queueInputBuffer();
    return true;
  }

  /**
   * Attempts to read input data and pass it to the decoder.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws TransformationException If an error occurs in the decoder.
   */
  private boolean feedDecoderFromInput() throws TransformationException {
    Codec decoder = checkNotNull(this.decoder);
    if (!decoder.maybeDequeueInputBuffer(decoderInputBuffer)) {
      return false;
    }

    if (!readInput(decoderInputBuffer)) {
      return false;
    }

    if (shouldDropInputBuffer(decoderInputBuffer)) {
      return true;
    }

    decoder.queueInputBuffer(decoderInputBuffer);
    return true;
  }

  /**
   * Attempts to read input data and pass the input data to the sample pipeline.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   */
  @RequiresNonNull("samplePipelineInput")
  private boolean feedPipelineFromInput() {
    @Nullable
    DecoderInputBuffer samplePipelineInputBuffer = samplePipelineInput.dequeueInputBuffer();
    if (samplePipelineInputBuffer == null) {
      return false;
    }

    if (!readInput(samplePipelineInputBuffer)) {
      return false;
    }

    if (shouldDropInputBuffer(samplePipelineInputBuffer)) {
      return true;
    }

    samplePipelineInput.queueInputBuffer();
    if (samplePipelineInputBuffer.isEndOfStream()) {
      isEnded = true;
      return false;
    }
    return true;
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

  /**
   * Preprocesses an {@linkplain DecoderInputBuffer input buffer} queued to the pipeline and returns
   * whether it should be dropped.
   *
   * <p>The input buffer is cleared if it should be dropped.
   */
  private boolean shouldDropInputBuffer(DecoderInputBuffer inputBuffer) {
    ByteBuffer inputBytes = checkNotNull(inputBuffer.data);

    if (sefVideoSlowMotionFlattener == null || inputBuffer.isEndOfStream()) {
      return false;
    }

    long presentationTimeUs = inputBuffer.timeUs - streamOffsetUs;
    boolean shouldDropInputBuffer =
        sefVideoSlowMotionFlattener.dropOrTransformSample(inputBytes, presentationTimeUs);
    if (shouldDropInputBuffer) {
      inputBytes.clear();
    } else {
      inputBuffer.timeUs =
          streamOffsetUs + sefVideoSlowMotionFlattener.getSamplePresentationTimeUs();
    }
    return shouldDropInputBuffer;
  }
}
