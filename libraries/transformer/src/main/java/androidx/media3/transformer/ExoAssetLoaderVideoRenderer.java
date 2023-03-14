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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.decoder.DecoderInputBuffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ final class ExoAssetLoaderVideoRenderer extends ExoAssetLoaderBaseRenderer {

  private static final String TAG = "ExoAssetLoaderVideoRenderer";

  private final boolean flattenForSlowMotion;
  private final Codec.DecoderFactory decoderFactory;
  private final boolean forceInterpretHdrAsSdr;
  private final List<Long> decodeOnlyPresentationTimestamps;

  private @MonotonicNonNull SefSlowMotionFlattener sefVideoSlowMotionFlattener;
  private int maxDecoderPendingFrameCount;

  public ExoAssetLoaderVideoRenderer(
      boolean flattenForSlowMotion,
      Codec.DecoderFactory decoderFactory,
      boolean forceInterpretHdrAsSdr,
      TransformerMediaClock mediaClock,
      AssetLoader.Listener assetLoaderListener) {
    super(C.TRACK_TYPE_VIDEO, mediaClock, assetLoaderListener);
    this.flattenForSlowMotion = flattenForSlowMotion;
    this.decoderFactory = decoderFactory;
    this.forceInterpretHdrAsSdr = forceInterpretHdrAsSdr;
    decodeOnlyPresentationTimestamps = new ArrayList<>();
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected Format overrideFormat(Format inputFormat) {
    if (forceInterpretHdrAsSdr && ColorInfo.isTransferHdr(inputFormat.colorInfo)) {
      return inputFormat.buildUpon().setColorInfo(ColorInfo.SDR_BT709_LIMITED).build();
    }
    return inputFormat;
  }

  @Override
  protected void onInputFormatRead(Format inputFormat) {
    if (flattenForSlowMotion) {
      sefVideoSlowMotionFlattener = new SefSlowMotionFlattener(inputFormat);
    }
  }

  @Override
  protected void initDecoder(Format inputFormat) throws ExportException {
    // TODO(b/237674316): Move surface creation out of sampleConsumer. Init decoder before
    // sampleConsumer.
    checkStateNotNull(sampleConsumer);
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
  @RequiresNonNull({"sampleConsumer", "decoder"})
  protected boolean feedConsumerFromDecoder() throws ExportException {
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

    if (!sampleConsumer.registerVideoFrame(decoderOutputBufferInfo.presentationTimeUs)) {
      return false;
    }

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
