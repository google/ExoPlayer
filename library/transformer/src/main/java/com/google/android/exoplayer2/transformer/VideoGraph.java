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

package com.google.android.exoplayer2.transformer;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Consumer;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Represents a graph for processing decoded video frames.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
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
     */
    void onOutputSizeChanged(int width, int height);

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
