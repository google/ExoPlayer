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

import androidx.media3.common.ColorInfo;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;

/**
 * Interface for a video compositor that combines frames from multiple input sources to produce
 * output frames.
 *
 * <p>Input and output are provided via OpenGL textures.
 *
 * <p>Methods may be called from any thread.
 */
@UnstableApi
public interface VideoCompositor extends GlTextureProducer {

  /** Listener for errors. */
  interface Listener {
    /**
     * Called when an exception occurs during asynchronous frame compositing.
     *
     * <p>If this is called, the calling {@link VideoCompositor} must immediately be {@linkplain
     * VideoCompositor#release() released}.
     */
    void onError(VideoFrameProcessingException exception);

    /** Called after {@link VideoCompositor} has output its final output frame. */
    void onEnded();
  }

  /**
   * Registers a new input source, and returns a unique {@code inputId} corresponding to this
   * source, to be used in {@link #queueInputTexture}.
   */
  int registerInputSource();

  /**
   * Signals that no more frames will come from the upstream {@link GlTextureProducer.Listener}.
   *
   * @param inputId The identifier for an input source, returned from {@link #registerInputSource}.
   */
  void signalEndOfInputSource(int inputId);

  /**
   * Queues an input texture to be composited.
   *
   * @param inputId The identifier for an input source, returned from {@link #registerInputSource}.
   * @param textureProducer The source from where the {@code inputTexture} is produced.
   * @param inputTexture The {@link GlTextureInfo} to composite.
   * @param colorInfo The {@link ColorInfo} of {@code inputTexture}.
   * @param presentationTimeUs The presentation time of {@code inputTexture}, in microseconds.
   */
  void queueInputTexture(
      int inputId,
      GlTextureProducer textureProducer,
      GlTextureInfo inputTexture,
      ColorInfo colorInfo,
      long presentationTimeUs);

  /**
   * Releases all resources.
   *
   * <p>This {@link VideoCompositor} instance must not be used after this method is called.
   */
  void release();
}
