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

package androidx.media3.common;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

/** Represents a graph for processing decoded video frames. */
@UnstableApi
public interface VideoGraph {

  /** Listener for video frame processing events. */
  @UnstableApi
  interface Listener {
    /**
     * Called when the output size changes.
     *
     * @param width The new output width in pixels.
     * @param height The new output width in pixels.
     */
    void onOutputSizeChanged(int width, int height);

    /**
     * Called when an output frame with the given {@code presentationTimeUs} becomes available for
     * rendering.
     *
     * @param presentationTimeUs The presentation time of the frame, in microseconds.
     */
    void onOutputFrameAvailableForRendering(long presentationTimeUs);

    /**
     * Called after the {@link VideoGraph} has rendered its final output frame.
     *
     * @param finalFramePresentationTimeUs The timestamp of the last output frame, in microseconds.
     */
    void onEnded(long finalFramePresentationTimeUs);

    /**
     * Called when an exception occurs during video frame processing.
     *
     * <p>If this is called, the calling {@link VideoGraph} must immediately be {@linkplain
     * #release() released}.
     */
    void onError(VideoFrameProcessingException exception);
  }

  /**
   * Initialize the {@code VideoGraph}.
   *
   * <p>This method must be called before calling other methods.
   *
   * <p>If the method throws, the caller must call {@link #release}.
   */
  void initialize() throws VideoFrameProcessingException;

  /**
   * Registers a new input to the {@code VideoGraph}.
   *
   * <p>A underlying processing {@link VideoFrameProcessor} is created every time this method is
   * called.
   *
   * <p>If the method throws, the caller must call {@link #release}.
   *
   * @return The id of the registered input, which can be used to get the underlying {@link
   *     VideoFrameProcessor} via {@link #getProcessor(int)}.
   */
  int registerInput() throws VideoFrameProcessingException;

  /**
   * Returns the {@link VideoFrameProcessor} that handles the processing for an input registered via
   * {@link #registerInput()}. If the {@code inputId} is not {@linkplain #registerInput()
   * registered} before, this method will throw an {@link IllegalStateException}.
   */
  VideoFrameProcessor getProcessor(int inputId);

  /**
   * Sets the output surface and supporting information.
   *
   * <p>The new output {@link SurfaceInfo} is applied from the next output frame rendered onwards.
   * If the output {@link SurfaceInfo} is {@code null}, the {@code VideoGraph} will stop rendering
   * pending frames and resume rendering once a non-null {@link SurfaceInfo} is set.
   *
   * <p>If the dimensions given in {@link SurfaceInfo} do not match the {@linkplain
   * Listener#onOutputSizeChanged(int,int) output size after applying the final effect} the frames
   * are resized before rendering to the surface and letter/pillar-boxing is applied.
   *
   * <p>The caller is responsible for tracking the lifecycle of the {@link SurfaceInfo#surface}
   * including calling this method with a new surface if it is destroyed. When this method returns,
   * the previous output surface is no longer being used and can safely be released by the caller.
   */
  void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo);

  /**
   * Returns whether the {@code VideoGraph} has produced a frame with zero presentation timestamp.
   */
  boolean hasProducedFrameWithTimestampZero();

  /**
   * Releases the associated resources.
   *
   * <p>This {@code VideoGraph} instance must not be used after this method is called.
   */
  void release();
}
