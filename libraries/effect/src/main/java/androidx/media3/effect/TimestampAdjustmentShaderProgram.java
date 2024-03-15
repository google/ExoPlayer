/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import androidx.annotation.Nullable;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.TimestampAdjustment.TimestampMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Changes the frame timestamps using the {@link TimestampMap}. */
@UnstableApi
public class TimestampAdjustmentShaderProgram implements GlShaderProgram {

  private final TimestampMap timestampMap;
  private final AtomicInteger pendingCallbacksCount;
  private final AtomicBoolean pendingEndOfStream;

  @Nullable private GlTextureInfo inputTexture;
  private InputListener inputListener;
  private OutputListener outputListener;

  public TimestampAdjustmentShaderProgram(TimestampMap timestampMap) {
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};

    this.timestampMap = timestampMap;
    pendingCallbacksCount = new AtomicInteger();
    pendingEndOfStream = new AtomicBoolean();
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    if (inputTexture == null) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public void setErrorListener(Executor executor, ErrorListener errorListener) {
    // No checked exceptions thrown.
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    this.inputTexture = inputTexture;
    timestampMap.calculateOutputTimeUs(
        presentationTimeUs, /* outputTimeConsumer= */ this::onOutputTimeAvailable);
    pendingCallbacksCount.incrementAndGet();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    if (pendingCallbacksCount.get() == 0) {
      outputListener.onCurrentOutputStreamEnded();
    } else {
      pendingEndOfStream.set(true);
    }
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    checkState(outputTexture.texId == checkNotNull(inputTexture).texId);
    inputListener.onInputFrameProcessed(outputTexture);
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void flush() {
    // TODO - b/320242819: Investigate support for previewing.
    throw new UnsupportedOperationException("This effect is not supported for previewing.");
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    inputTexture = null;
  }

  private void onOutputTimeAvailable(long outputTimeUs) {
    outputListener.onOutputFrameAvailable(checkNotNull(inputTexture), outputTimeUs);
    if (pendingEndOfStream.get()) {
      outputListener.onCurrentOutputStreamEnded();
      pendingEndOfStream.set(false);
    }
    pendingCallbacksCount.decrementAndGet();
  }
}
