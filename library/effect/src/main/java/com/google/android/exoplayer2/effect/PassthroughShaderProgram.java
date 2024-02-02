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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkState;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlObjectsProvider;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Executor;

/**
 * A {@linkplain GlShaderProgram} that passes a frame from the input to the output listener without
 * copying.
 *
 * <p>This shader program can only process one input frame at a time.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public class PassthroughShaderProgram implements GlShaderProgram {

  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private Executor errorListenerExecutor;
  private int texIdInUse;

  public PassthroughShaderProgram() {
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = (frameProcessingException) -> {};
    errorListenerExecutor = MoreExecutors.directExecutor();
    texIdInUse = C.INDEX_UNSET;
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    if (texIdInUse == C.INDEX_UNSET) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public void setErrorListener(Executor executor, ErrorListener errorListener) {
    this.errorListenerExecutor = executor;
    this.errorListener = errorListener;
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    texIdInUse = inputTexture.texId;
    outputListener.onOutputFrameAvailable(inputTexture, presentationTimeUs);
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    checkState(outputTexture.texId == texIdInUse);
    texIdInUse = C.INDEX_UNSET;
    inputListener.onInputFrameProcessed(outputTexture);
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  public void flush() {
    texIdInUse = C.INDEX_UNSET;
    inputListener.onFlush();
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    texIdInUse = C.INDEX_UNSET;
  }

  protected final InputListener getInputListener() {
    return inputListener;
  }

  protected final void onError(Exception e) {
    errorListenerExecutor.execute(
        () -> errorListener.onError(VideoFrameProcessingException.from(e)));
  }
}
