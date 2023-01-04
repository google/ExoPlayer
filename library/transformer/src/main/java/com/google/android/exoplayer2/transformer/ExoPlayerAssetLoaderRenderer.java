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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED;
import static com.google.android.exoplayer2.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static com.google.android.exoplayer2.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_DECODED;
import static com.google.android.exoplayer2.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_ENCODED;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.ColorInfo;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
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
  private final List<Long> decodeOnlyPresentationTimestamps;

  private boolean isTransformationRunning;
  private long streamStartPositionUs;
  private long streamOffsetUs;
  private @MonotonicNonNull SefSlowMotionFlattener sefVideoSlowMotionFlattener;
  private @MonotonicNonNull Codec decoder;
  @Nullable private ByteBuffer pendingDecoderOutputBuffer;
  private int maxDecoderPendingFrameCount;
  private @MonotonicNonNull SampleConsumer sampleConsumer;
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
    decodeOnlyPresentationTimestamps = new ArrayList<>();
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

      if (sampleConsumer.expectsDecodedData()) {
        if (getTrackType() == C.TRACK_TYPE_AUDIO) {
          while (feedConsumerAudioFromDecoder() || feedDecoderFromInput()) {}
        } else if (getTrackType() == C.TRACK_TYPE_VIDEO) {
          while (feedConsumerVideoFromDecoder() || feedDecoderFromInput()) {}
        } else {
          throw new IllegalStateException();
        }
      } else {
        while (feedConsumerFromInput()) {}
      }
    } catch (TransformationException e) {
      isTransformationRunning = false;
      assetLoaderListener.onTransformationError(e);
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
    this.streamStartPositionUs = startPositionUs;
    this.streamOffsetUs = offsetUs;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
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

  @EnsuresNonNullIf(expression = "sampleConsumer", result = true)
  private boolean ensureConfigured() throws TransformationException {
    if (sampleConsumer != null) {
      return true;
    }

    FormatHolder formatHolder = getFormatHolder();
    @ReadDataResult
    int result = readSource(formatHolder, decoderInputBuffer, /* readFlags= */ FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    Format inputFormat = checkNotNull(formatHolder.format);
    @AssetLoader.SupportedOutputTypes
    int supportedOutputTypes = SUPPORTED_OUTPUT_TYPE_ENCODED | SUPPORTED_OUTPUT_TYPE_DECODED;
    sampleConsumer =
        assetLoaderListener.onTrackAdded(
            inputFormat, supportedOutputTypes, streamStartPositionUs, streamOffsetUs);
    if (getTrackType() == C.TRACK_TYPE_VIDEO && flattenForSlowMotion) {
      sefVideoSlowMotionFlattener = new SefSlowMotionFlattener(inputFormat);
    }
    if (sampleConsumer.expectsDecodedData()) {
      if (getTrackType() == C.TRACK_TYPE_AUDIO) {
        decoder = decoderFactory.createForAudioDecoding(inputFormat);
      } else if (getTrackType() == C.TRACK_TYPE_VIDEO) {
        boolean isDecoderToneMappingRequired =
            ColorInfo.isTransferHdr(inputFormat.colorInfo)
                && !ColorInfo.isTransferHdr(sampleConsumer.getExpectedColorInfo());
        decoder =
            decoderFactory.createForVideoDecoding(
                inputFormat,
                checkNotNull(sampleConsumer.getInputSurface()),
                isDecoderToneMappingRequired);
        maxDecoderPendingFrameCount = decoder.getMaxPendingFrameCount();
      } else {
        throw new IllegalStateException();
      }
    }
    return true;
  }

  /**
   * Attempts to get decoded audio data and pass it to the sample consumer.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws TransformationException If an error occurs in the decoder.
   */
  @RequiresNonNull("sampleConsumer")
  private boolean feedConsumerAudioFromDecoder() throws TransformationException {
    @Nullable DecoderInputBuffer sampleConsumerInputBuffer = sampleConsumer.dequeueInputBuffer();
    if (sampleConsumerInputBuffer == null) {
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
      sampleConsumerInputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      sampleConsumer.queueInputBuffer();
      isEnded = true;
      return false;
    }

    pendingDecoderOutputBuffer = decoder.getOutputBuffer();
    if (pendingDecoderOutputBuffer == null) {
      return false;
    }

    sampleConsumerInputBuffer.data = pendingDecoderOutputBuffer;
    MediaCodec.BufferInfo bufferInfo = checkNotNull(decoder.getOutputBufferInfo());
    sampleConsumerInputBuffer.timeUs = bufferInfo.presentationTimeUs;
    sampleConsumerInputBuffer.setFlags(bufferInfo.flags);
    sampleConsumer.queueInputBuffer();
    return true;
  }

  /**
   * Attempts to get decoded video data and pass it to the sample consumer.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws TransformationException If an error occurs in the decoder.
   */
  @RequiresNonNull("sampleConsumer")
  private boolean feedConsumerVideoFromDecoder() throws TransformationException {
    Codec decoder = checkNotNull(this.decoder);
    if (decoder.isEnded()) {
      sampleConsumer.signalEndOfVideoInput();
      isEnded = true;
      return false;
    }

    @Nullable MediaCodec.BufferInfo decoderOutputBufferInfo = decoder.getOutputBufferInfo();
    if (decoderOutputBufferInfo == null) {
      return false;
    }

    if (isDecodeOnlyBuffer(decoderOutputBufferInfo.presentationTimeUs)) {
      decoder.releaseOutputBuffer(/* render= */ false);
      return true;
    }

    if (maxDecoderPendingFrameCount != C.UNLIMITED_PENDING_FRAME_COUNT
        && sampleConsumer.getPendingVideoFrameCount() == maxDecoderPendingFrameCount) {
      return false;
    }

    sampleConsumer.registerVideoFrame();
    decoder.releaseOutputBuffer(/* render= */ true);
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

    if (decoderInputBuffer.isDecodeOnly()) {
      decodeOnlyPresentationTimestamps.add(decoderInputBuffer.timeUs);
    }
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
    @Nullable DecoderInputBuffer sampleConsumerInputBuffer = sampleConsumer.dequeueInputBuffer();
    if (sampleConsumerInputBuffer == null) {
      return false;
    }

    if (!readInput(sampleConsumerInputBuffer)) {
      return false;
    }

    if (shouldDropInputBuffer(sampleConsumerInputBuffer)) {
      return true;
    }

    sampleConsumer.queueInputBuffer();
    if (sampleConsumerInputBuffer.isEndOfStream()) {
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
   * Preprocesses an encoded {@linkplain DecoderInputBuffer input buffer} and returns whether it
   * should be dropped.
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

  private boolean isDecodeOnlyBuffer(long presentationTimeUs) {
    // We avoid using decodeOnlyPresentationTimestamps.remove(presentationTimeUs) because it would
    // box presentationTimeUs, creating a Long object that would need to be garbage collected.
    int size = decodeOnlyPresentationTimestamps.size();
    for (int i = 0; i < size; i++) {
      if (decodeOnlyPresentationTimestamps.get(i) == presentationTimeUs) {
        decodeOnlyPresentationTimestamps.remove(i);
        return true;
      }
    }
    return false;
  }
}
