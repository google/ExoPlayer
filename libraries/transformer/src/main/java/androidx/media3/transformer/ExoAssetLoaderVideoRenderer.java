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
import androidx.media3.effect.DebugTraceUtil;
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
    DebugTraceUtil.logEvent(
        DebugTraceUtil.EVENT_VIDEO_INPUT_FORMAT, C.TIME_UNSET, inputFormat.toString());
    if (flattenForSlowMotion) {
      sefVideoSlowMotionFlattener = new SefSlowMotionFlattener(inputFormat);
    }
  }

  @Override
  protected void initDecoder(Format inputFormat) throws ExportException {
    // TODO(b/278259383): Move surface creation out of sampleConsumer. Init decoder before
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
    if (inputBuffer.isEndOfStream()) {
      return false;
    }

    ByteBuffer inputBytes = checkNotNull(inputBuffer.data);
    if (sefVideoSlowMotionFlattener != null) {
      long presentationTimeUs = inputBuffer.timeUs - streamOffsetUs;
      boolean shouldDropInputBuffer =
          sefVideoSlowMotionFlattener.dropOrTransformSample(inputBytes, presentationTimeUs);
      if (shouldDropInputBuffer) {
        inputBytes.clear();
        return true;
      }
      inputBuffer.timeUs =
          streamOffsetUs + sefVideoSlowMotionFlattener.getSamplePresentationTimeUs();
    }

    if (decoder == null) {
      inputBuffer.timeUs -= streamStartPositionUs;
    }
    return false;
  }

  @Override
  protected void onDecoderInputReady(DecoderInputBuffer inputBuffer) {
    if (inputBuffer.timeUs < getLastResetPositionUs()) {
      decodeOnlyPresentationTimestamps.add(inputBuffer.timeUs);
    }
  }

  @Override
  @RequiresNonNull({"sampleConsumer", "decoder"})
  protected boolean feedConsumerFromDecoder() throws ExportException {
    if (decoder.isEnded()) {
      DebugTraceUtil.logEvent(DebugTraceUtil.EVENT_DECODER_SIGNAL_EOS, C.TIME_END_OF_SOURCE);
      sampleConsumer.signalEndOfVideoInput();
      isEnded = true;
      return false;
    }

    @Nullable MediaCodec.BufferInfo decoderOutputBufferInfo = decoder.getOutputBufferInfo();
    if (decoderOutputBufferInfo == null) {
      return false;
    }

    long presentationTimeUs = decoderOutputBufferInfo.presentationTimeUs - streamStartPositionUs;
    // Drop samples with negative timestamp in the transcoding case, to prevent encoder failures.
    if (presentationTimeUs < 0 || isDecodeOnlyBuffer(decoderOutputBufferInfo.presentationTimeUs)) {
      decoder.releaseOutputBuffer(/* render= */ false);
      return true;
    }

    if (sampleConsumer.getPendingVideoFrameCount() == maxDecoderPendingFrameCount) {
      return false;
    }

    if (!sampleConsumer.registerVideoFrame(presentationTimeUs)) {
      return false;
    }

    decoder.releaseOutputBuffer(presentationTimeUs);
    DebugTraceUtil.logEvent(DebugTraceUtil.EVENT_DECODER_DECODED_FRAME, presentationTimeUs);
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
