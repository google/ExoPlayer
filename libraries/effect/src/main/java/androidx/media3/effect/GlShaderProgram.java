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
package androidx.media3.effect;

import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;
import java.util.concurrent.Executor;

/**
 * Processes frames from one OpenGL 2D texture to another.
 *
 * <p>The {@code GlShaderProgram} consumes input frames it accepts via {@link
 * #queueInputFrame(GlObjectsProvider, GlTextureInfo, long)} and surrenders each texture back to the
 * caller via its {@linkplain InputListener#onInputFrameProcessed(GlTextureInfo) listener} once the
 * texture's contents have been processed.
 *
 * <p>The {@code GlShaderProgram} produces output frames asynchronously and notifies its owner when
 * they are available via its {@linkplain OutputListener#onOutputFrameAvailable(GlTextureInfo, long)
 * listener}. The {@code GlShaderProgram} instance's owner must surrender the texture back to the
 * {@code GlShaderProgram} via {@link #releaseOutputFrame(GlTextureInfo)} when it has finished
 * processing it.
 *
 * <p>{@code GlShaderProgram} implementations can choose to produce output frames before receiving
 * input frames or process several input frames before producing an output frame. However, {@code
 * GlShaderProgram} implementations cannot assume that they will receive more than one input frame
 * at a time, so they must process each input frame they accept even if they cannot produce output
 * yet.
 *
 * <p>The methods in this interface must be called on the thread that owns the parent OpenGL
 * context. If the implementation uses another OpenGL context, e.g., on another thread, it must
 * configure it to share data with the context of thread the interface methods are called on.
 */
@UnstableApi
public interface GlShaderProgram {

  /**
   * Listener for input-related video frame processing events.
   *
   * <p>This listener can be called from any thread.
   */
  interface InputListener {
    /**
     * Called when the {@link GlShaderProgram} is ready to accept another input frame.
     *
     * <p>For each time this method is called, {@link #queueInputFrame(GlObjectsProvider,
     * GlTextureInfo, long)} can be called once.
     */
    default void onReadyToAcceptInputFrame() {}

    /**
     * Called when the {@link GlShaderProgram} has processed an input frame.
     *
     * <p>The implementation shall not assume the {@link GlShaderProgram} is {@linkplain
     * #onReadyToAcceptInputFrame ready to accept another input frame} when this method is called.
     *
     * @param inputTexture The {@link GlTextureInfo} that was used to {@linkplain
     *     #queueInputFrame(GlObjectsProvider, GlTextureInfo, long) queue} the input frame.
     */
    default void onInputFrameProcessed(GlTextureInfo inputTexture) {}

    /**
     * Called when the {@link GlShaderProgram} has been flushed.
     *
     * <p>The implementation shall not assume the {@link GlShaderProgram} is {@linkplain
     * #onReadyToAcceptInputFrame ready to accept another input frame} when this method is called.
     * If the implementation manages a limited input capacity, it must clear all prior {@linkplain
     * #onReadyToAcceptInputFrame input frame capacity}.
     */
    default void onFlush() {}
  }

  /**
   * Listener for output-related video frame processing events.
   *
   * <p>This listener can be called from any thread.
   */
  interface OutputListener {
    /**
     * Called when the {@link GlShaderProgram} has produced an output frame.
     *
     * <p>After the listener's owner has processed the output frame, it must call {@link
     * #releaseOutputFrame(GlTextureInfo)}. The output frame should be released as soon as possible,
     * as there is no guarantee that the {@link GlShaderProgram} will produce further output frames
     * before this output frame is released.
     *
     * @param outputTexture A {@link GlTextureInfo} describing the texture containing the output
     *     frame.
     * @param presentationTimeUs The presentation timestamp of the output frame, in microseconds.
     */
    default void onOutputFrameAvailable(GlTextureInfo outputTexture, long presentationTimeUs) {}

    /**
     * Called when the {@link GlShaderProgram} will not produce further output frames belonging to
     * the current output stream. May be called multiple times for one output stream.
     */
    default void onCurrentOutputStreamEnded() {}
  }

  /**
   * Listener for video frame processing errors.
   *
   * <p>This listener can be called from any thread.
   */
  interface ErrorListener {
    /**
     * Called when an exception occurs during asynchronous video frame processing.
     *
     * <p>If an error occurred, consuming and producing further frames will not work as expected and
     * the {@link GlShaderProgram} should be released.
     */
    void onError(VideoFrameProcessingException e);
  }

  /**
   * Sets the {@link InputListener}.
   *
   * <p>The {@link InputListener} should be invoked on the thread that owns the parent OpenGL
   * context. For example, {@link DefaultVideoFrameProcessor} invokes the {@link InputListener}
   * methods on its internal thread.
   */
  void setInputListener(InputListener inputListener);

  /**
   * Sets the {@link OutputListener}.
   *
   * <p>The {@link OutputListener} should be invoked on the thread that owns the parent OpenGL
   * context. For example, {@link DefaultVideoFrameProcessor} invokes the {@link OutputListener}
   * methods on its internal thread.
   */
  void setOutputListener(OutputListener outputListener);

  /**
   * Sets the {@link ErrorListener}.
   *
   * <p>The {@link ErrorListener} is invoked on the provided {@link Executor}.
   */
  void setErrorListener(Executor executor, ErrorListener errorListener);

  /**
   * Processes an input frame if possible.
   *
   * <p>The {@code GlShaderProgram} owns the accepted frame until it calls {@link
   * InputListener#onInputFrameProcessed(GlTextureInfo)}. The caller should not overwrite or release
   * the texture before the {@code GlShaderProgram} has finished processing it.
   *
   * <p>This method must only be called when the {@code GlShaderProgram} can {@linkplain
   * InputListener#onReadyToAcceptInputFrame() accept an input frame}.
   *
   * @param glObjectsProvider The {@link GlObjectsProvider} for using EGL and GLES.
   * @param inputTexture A {@link GlTextureInfo} describing the texture containing the input frame.
   * @param presentationTimeUs The presentation timestamp of the input frame, in microseconds.
   */
  void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs);

  /**
   * Notifies the {@code GlShaderProgram} that the frame on the given output texture is no longer
   * used and can be overwritten.
   */
  void releaseOutputFrame(GlTextureInfo outputTexture);

  /**
   * Notifies the {@code GlShaderProgram} that no further input frames belonging to the current
   * input stream will be queued.
   *
   * <p>Input frames that are queued after this method is called belong to a different input stream.
   */
  void signalEndOfCurrentInputStream();

  /**
   * Flushes the {@code GlShaderProgram}.
   *
   * <p>The {@code GlShaderProgram} should reclaim the ownership of its allocated textures,
   * {@linkplain InputListener#onFlush notify} its {@link InputListener} about the flush event, and
   * {@linkplain InputListener#onReadyToAcceptInputFrame report its availability} if necessary.
   *
   * <p>The implementation must not {@linkplain OutputListener#onOutputFrameAvailable output frames}
   * until after this method returns.
   */
  void flush();

  /**
   * Releases all resources.
   *
   * @throws VideoFrameProcessingException If an error occurs while releasing resources.
   */
  void release() throws VideoFrameProcessingException;
}
