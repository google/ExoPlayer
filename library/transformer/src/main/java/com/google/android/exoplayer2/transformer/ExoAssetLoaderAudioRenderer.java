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
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ final class ExoAssetLoaderAudioRenderer extends ExoAssetLoaderBaseRenderer {

  private static final String TAG = "ExoAssetLoaderAudioRenderer";

  private final Codec.DecoderFactory decoderFactory;

  public ExoAssetLoaderAudioRenderer(
      Codec.DecoderFactory decoderFactory,
      TransformerMediaClock mediaClock,
      AssetLoader.Listener assetLoaderListener) {
    super(C.TRACK_TYPE_AUDIO, mediaClock, assetLoaderListener);
    this.decoderFactory = decoderFactory;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected void initDecoder(Format inputFormat) throws TransformationException {
    decoder = decoderFactory.createForAudioDecoding(inputFormat);
  }

  /**
   * Attempts to get decoded audio data and pass it to the sample consumer.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws TransformationException If an error occurs in the decoder.
   */
  @Override
  @RequiresNonNull("sampleConsumer")
  protected boolean feedConsumerFromDecoder() throws TransformationException {
    @Nullable DecoderInputBuffer sampleConsumerInputBuffer = sampleConsumer.getInputBuffer();
    if (sampleConsumerInputBuffer == null) {
      return false;
    }

    Codec decoder = checkNotNull(this.decoder);
    if (decoder.isEnded()) {
      checkNotNull(sampleConsumerInputBuffer.data).limit(0);
      sampleConsumerInputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      sampleConsumer.queueInputBuffer();
      isEnded = true;
      return false;
    }

    ByteBuffer decoderOutputBuffer = decoder.getOutputBuffer();
    if (decoderOutputBuffer == null) {
      return false;
    }

    sampleConsumerInputBuffer.ensureSpaceForWrite(decoderOutputBuffer.limit());
    sampleConsumerInputBuffer.data.put(decoderOutputBuffer).flip();
    MediaCodec.BufferInfo bufferInfo = checkNotNull(decoder.getOutputBufferInfo());
    sampleConsumerInputBuffer.timeUs = bufferInfo.presentationTimeUs;
    sampleConsumerInputBuffer.setFlags(bufferInfo.flags);
    decoder.releaseOutputBuffer(/* render= */ false);
    sampleConsumer.queueInputBuffer();
    return true;
  }
}
