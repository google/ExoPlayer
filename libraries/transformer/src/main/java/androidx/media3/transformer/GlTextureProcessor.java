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

import androidx.media3.common.util.UnstableApi;

/**
 * Processes frames from one OpenGL 2D texture to another.
 *
 * <p>The {@code GlTextureProcessor} consumes input frames it accepts via {@link
 * #maybeQueueInputFrame(TextureInfo, long)} and surrenders each texture back to the caller via its
 * {@linkplain Listener#onInputFrameProcessed(TextureInfo) listener} once the texture's contents
 * have been processed.
 *
 * <p>The {@code GlTextureProcessor} produces output frames asynchronously and notifies its owner
 * when they are available via its {@linkplain Listener#onOutputFrameAvailable(TextureInfo, long)
 * listener}. The {@code GlTextureProcessor} instance's owner must surrender the texture back to the
 * {@code GlTextureProcessor} via {@link #releaseOutputFrame(TextureInfo)} when it has finished
 * processing it.
 *
 * <p>{@code GlTextureProcessor} implementations can choose to produce output frames before
 * receiving input frames or process several input frames before producing an output frame. However,
 * {@code GlTextureProcessor} implementations cannot assume that they will receive more than one
 * input frame at a time, so they must process each input frame they accept even if they cannot
 * produce output yet.
 *
 * <p>The methods in this interface must be called on the thread that owns the parent OpenGL
 * context. If the implementation uses another OpenGL context, e.g., on another thread, it must
 * configure it to share data with the context of thread the interface methods are called on.
 */
@UnstableApi
public interface GlTextureProcessor {

  /**
   * Listener for frame processing events.
   *
   * <p>This listener can be called from any thread.
   */
  interface Listener {
    /**
     * Called when the {@link GlTextureProcessor} has processed an input frame.
     *
     * @param inputTexture The {@link TextureInfo} that was used to {@linkplain
     *     #maybeQueueInputFrame(TextureInfo, long) queue} the input frame.
     */
    void onInputFrameProcessed(TextureInfo inputTexture);

    /**
     * Called when the {@link GlTextureProcessor} has produced an output frame.
     *
     * <p>After the listener's owner has processed the output frame, it must call {@link
     * #releaseOutputFrame(TextureInfo)}. The output frame should be released as soon as possible,
     * as there is no guarantee that the {@link GlTextureProcessor} will produce further output
     * frames before this output frame is released.
     *
     * @param outputTexture A {@link TextureInfo} describing the texture containing the output
     *     frame.
     * @param presentationTimeUs The presentation timestamp of the output frame, in microseconds.
     */
    void onOutputFrameAvailable(TextureInfo outputTexture, long presentationTimeUs);

    /** Called when the {@link GlTextureProcessor} will not produce further output frames. */
    void onOutputStreamEnded();

    /**
     * Called when an exception occurs during asynchronous frame processing.
     *
     * <p>If an error occurred, consuming and producing further frames will not work as expected and
     * the {@link GlTextureProcessor} should be released.
     */
    void onFrameProcessingError(FrameProcessingException e);
  }

  /** Sets the {@link Listener} for frame processing events. */
  void setListener(Listener listener);

  /**
   * Processes an input frame if possible.
   *
   * <p>If this method returns {@code true} the input frame has been accepted. The {@code
   * GlTextureProcessor} owns the accepted frame until it calls {@link
   * Listener#onInputFrameProcessed(TextureInfo)}. The caller should not overwrite or release the
   * texture before the {@code GlTextureProcessor} has finished processing it.
   *
   * <p>If this method returns {@code false}, the input frame could not be accepted and the caller
   * should decide whether to drop the frame or try again later.
   *
   * @param inputTexture A {@link TextureInfo} describing the texture containing the input frame.
   * @param presentationTimeUs The presentation timestamp of the input frame, in microseconds.
   * @return Whether the frame was accepted.
   */
  boolean maybeQueueInputFrame(TextureInfo inputTexture, long presentationTimeUs);

  /**
   * Notifies the texture processor that the frame on the given output texture is no longer used and
   * can be overwritten.
   */
  void releaseOutputFrame(TextureInfo outputTexture);

  /** Notifies the texture processor that no further input frames will become available. */
  void signalEndOfInputStream();

  /**
   * Releases all resources.
   *
   * @throws FrameProcessingException If an error occurs while releasing resources.
   */
  void release() throws FrameProcessingException;
}
