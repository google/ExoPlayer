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

package androidx.media3.transformer;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.Consumer;
import java.util.List;
import java.util.concurrent.Executor;

/** Represents a graph for processing decoded video frames. */
/* package */ interface VideoGraph {

  /** A factory for creating a {@link VideoGraph}. */
  interface Factory {
    /**
     * Creates a new {@link VideoGraph} instance.
     *
     * @param context A {@link Context}.
     * @param inputColorInfo The {@link ColorInfo} for the input frames.
     * @param outputColorInfo The {@link ColorInfo} for the output frames.
     * @param errorConsumer A {@link Consumer} of {@link ExportException}.
     * @param debugViewProvider A {@link DebugViewProvider}.
     * @param listener A {@link Listener}.
     * @param listenerExecutor The {@link Executor} on which the {@code listener} is invoked.
     * @param compositionEffects A list of {@linkplain Effect effects} to apply to the composition.
     * @return A new instance.
     * @throws VideoFrameProcessingException If a problem occurs while creating the {@link
     *     VideoFrameProcessor}.
     */
    VideoGraph create(
        Context context,
        ColorInfo inputColorInfo,
        ColorInfo outputColorInfo,
        Consumer<ExportException> errorConsumer,
        DebugViewProvider debugViewProvider,
        Listener listener,
        Executor listenerExecutor,
        List<Effect> compositionEffects,
        long initialTimestampOffsetUs)
        throws VideoFrameProcessingException;
  }

  /** Listener for video frame processing events. */
  interface Listener {
    /**
     * Called when the output size changes.
     *
     * @param width The new output width in pixels.
     * @param height The new output width in pixels.
     * @return A {@link SurfaceInfo} to which the {@link VideoGraph} renders to, or {@code null} if
     *     the output is not needed.
     */
    // TODO - b/289985577: Consider returning void from this method.
    @Nullable
    SurfaceInfo onOutputSizeChanged(int width, int height);

    /** Called after the {@link VideoGraph} has rendered its final output frame. */
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
   * Returns a {@link GraphInput} object to which the {@code VideoGraph} inputs are queued.
   *
   * <p>This method must be called after successfully {@linkplain #initialize() initializing} the
   * {@code VideoGraph}.
   *
   * <p>This method must called exactly once for every input stream.
   *
   * <p>If the method throws any {@link Exception}, the caller must call {@link #release}.
   */
  GraphInput createInput() throws VideoFrameProcessingException;

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
