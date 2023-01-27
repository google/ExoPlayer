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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.video.ColorInfo;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ final class ExoAssetLoaderVideoRenderer extends ExoAssetLoaderBaseRenderer {

  private static final String TAG = "ExoAssetLoaderVideoRenderer";

  private final boolean flattenForSlowMotion;
  private final Codec.DecoderFactory decoderFactory;
  private final List<Long> decodeOnlyPresentationTimestamps;

  private @MonotonicNonNull SefSlowMotionFlattener sefVideoSlowMotionFlattener;
  private int maxDecoderPendingFrameCount;

  public ExoAssetLoaderVideoRenderer(
      boolean flattenForSlowMotion,
      Codec.DecoderFactory decoderFactory,
      TransformerMediaClock mediaClock,
      AssetLoader.Listener assetLoaderListener) {
    super(C.TRACK_TYPE_VIDEO, mediaClock, assetLoaderListener);
    this.flattenForSlowMotion = flattenForSlowMotion;
    this.decoderFactory = decoderFactory;
    decodeOnlyPresentationTimestamps = new ArrayList<>();
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected void onInputFormatRead(Format inputFormat) {
    if (flattenForSlowMotion) {
      sefVideoSlowMotionFlattener = new SefSlowMotionFlattener(inputFormat);
    }
  }

  @Override
  @RequiresNonNull("sampleConsumer")
  protected void initDecoder(Format inputFormat) throws TransformationException {
    boolean isDecoderToneMappingRequired =
        ColorInfo.isTransferHdr(inputFormat.colorInfo)
            && !ColorInfo.isTransferHdr(sampleConsumer.getExpectedInputColorInfo());
    decoder =
        decoderFactory.createForVideoDecoding(
            inputFormat,
            checkNotNull(sampleConsumer.getInputSurface()),
            isDecoderToneMappingRequired);
    maxDecoderPendingFrameCount = decoder.getMaxPendingFrameCount();
  }

  @Override
  protected boolean shouldDropInputBuffer(DecoderInputBuffer inputBuffer) {
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

  @Override
  protected void onDecoderInputReady(DecoderInputBuffer inputBuffer) {
    if (inputBuffer.isDecodeOnly()) {
      decodeOnlyPresentationTimestamps.add(inputBuffer.timeUs);
    }
  }

  @Override
  @RequiresNonNull("sampleConsumer")
  protected boolean feedConsumerFromDecoder() throws TransformationException {
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
