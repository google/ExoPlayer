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
package androidx.media3.exoplayer.mediacodec;

import android.media.MediaCodec;
import android.os.Bundle;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.CryptoInfo;

@RequiresApi(23)
@UnstableApi
/* package */ class SynchronousMediaCodecBufferEnqueuer implements MediaCodecBufferEnqueuer {

  private final MediaCodec codec;

  public SynchronousMediaCodecBufferEnqueuer(MediaCodec codec) {
    this.codec = codec;
  }

  @Override
  public void start() {
    // Do nothing.
  }

  @Override
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    codec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
  }

  @Override
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    codec.queueSecureInputBuffer(
        index, offset, info.getFrameworkCryptoInfo(), presentationTimeUs, flags);
  }

  @Override
  public void setParameters(Bundle parameters) {
    codec.setParameters(parameters);
  }

  @Override
  public void flush() {
    // Do nothing.
  }

  @Override
  public void shutdown() {
    // Do nothing.
  }

  @Override
  public void waitUntilQueueingComplete() {
    // Do nothing.
  }

  @Override
  public void maybeThrowException() {
    // Do nothing.
  }
}
