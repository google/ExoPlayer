/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.media3.common.C;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import java.util.List;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Produces blank frames with the given timestamps. */
/* package */ final class BlankFrameProducer implements GlShaderProgram {
  private final int width;
  private final int height;

  private @MonotonicNonNull GlTextureInfo blankTexture;
  private @MonotonicNonNull OutputListener outputListener;

  public BlankFrameProducer(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public void configureGlObjects() throws VideoFrameProcessingException {
    try {
      int texId = GlUtil.createTexture(width, height, /* useHighPrecisionColorComponents= */ false);
      int fboId = GlUtil.createFboForTexture(texId);
      blankTexture = new GlTextureInfo(texId, fboId, /* rboId= */ C.INDEX_UNSET, width, height);
      GlUtil.focusFramebufferUsingCurrentContext(fboId, width, height);
      GlUtil.clearFocusedBuffers();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  public void produceBlankFrames(List<Long> presentationTimesUs) {
    checkNotNull(outputListener);
    for (long presentationTimeUs : presentationTimesUs) {
      outputListener.onOutputFrameAvailable(checkNotNull(blankTexture), presentationTimeUs);
    }
  }

  @Override
  public void setInputListener(InputListener inputListener) {}

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public void setErrorListener(Executor executor, ErrorListener errorListener) {}

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    // No input is queued in these tests. The BlankFrameProducer is used to produce frames.
    throw new UnsupportedOperationException();
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {}

  @Override
  public void signalEndOfCurrentInputStream() {
    checkNotNull(outputListener).onCurrentOutputStreamEnded();
  }

  @Override
  public void flush() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void release() {
    // Do nothing as destroying the OpenGL context destroys the texture.
  }
}
