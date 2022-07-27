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

import android.content.Context;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import java.util.List;

/**
 * Interface for a frame processor that applies changes to individual video frames.
 *
 * <p>The changes are specified by {@link Effect} instances passed to the {@link Factory}.
 *
 * <p>The frame processor manages its input {@link Surface} which can be accessed via {@link
 * #getInputSurface()}. The output {@link Surface} must be set by the caller using {@link
 * #setOutputSurfaceInfo(SurfaceInfo)}.
 *
 * <p>The caller must {@linkplain #registerInputFrame() register} input frames before rendering them
 * to the input {@link Surface}.
 */
@UnstableApi
public interface FrameProcessor {
  // TODO(b/227625423): Allow effects to be replaced.

  /** A factory for {@link FrameProcessor} instances. */
  interface Factory {
    /**
     * Creates a new {@link FrameProcessor} instance.
     *
     * @param context A {@link Context}.
     * @param listener A {@link Listener}.
     * @param effects The {@link Effect} instances to apply to each frame.
     * @param debugViewProvider A {@link DebugViewProvider}.
     * @param useHdr Whether to process the input as an HDR signal.
     * @return A new instance.
     * @throws FrameProcessingException If a problem occurs while creating the {@link
     *     FrameProcessor}.
     */
    FrameProcessor create(
        Context context,
        FrameProcessor.Listener listener,
        List<Effect> effects,
        DebugViewProvider debugViewProvider,
        boolean useHdr)
        throws FrameProcessingException;
  }

  /**
   * Listener for asynchronous frame processing events.
   *
   * <p>All listener methods must be called from the same thread.
   */
  interface Listener {

    /**
     * Called when the output size after applying the final effect changes.
     *
     * <p>The output size after applying the final effect can differ from the size specified using
     * {@link #setOutputSurfaceInfo(SurfaceInfo)}.
     */
    void onOutputSizeChanged(int width, int height);

    /**
     * Called when an exception occurs during asynchronous frame processing.
     *
     * <p>If an error occurred, consuming and producing further frames will not work as expected and
     * the {@link FrameProcessor} should be released.
     */
    void onFrameProcessingError(FrameProcessingException exception);

    /** Called after the {@link FrameProcessor} has produced its final output frame. */
    void onFrameProcessingEnded();
  }

  /** Returns the input {@link Surface}. */
  Surface getInputSurface();

  /**
   * Sets information about the input frames.
   *
   * <p>The new input information is applied from the next frame {@linkplain #registerInputFrame()
   * registered} onwards.
   *
   * <p>Pixels are expanded using the {@link FrameInfo#pixelWidthHeightRatio} so that the output
   * frames' pixels have a ratio of 1.
   *
   * <p>The caller should update {@link FrameInfo#streamOffsetUs} when switching input streams to
   * ensure that frame timestamps are always monotonically increasing.
   */
  void setInputFrameInfo(FrameInfo inputFrameInfo);

  /**
   * Informs the {@code FrameProcessor} that a frame will be queued to its input surface.
   *
   * <p>Must be called before rendering a frame to the frame processor's input surface.
   *
   * @throws IllegalStateException If called after {@link #signalEndOfInput()} or before {@link
   *     #setInputFrameInfo(FrameInfo)}.
   */
  void registerInputFrame();

  /**
   * Returns the number of input frames that have been {@linkplain #registerInputFrame() registered}
   * but not processed off the {@linkplain #getInputSurface() input surface} yet.
   */
  int getPendingInputFrameCount();

  /**
   * Sets the output surface and supporting information.
   *
   * <p>The new output {@link SurfaceInfo} is applied from the next output frame rendered onwards.
   * If the output {@link SurfaceInfo} is {@code null}, the {@code FrameProcessor} will stop
   * rendering and resume rendering pending frames once a non-null {@link SurfaceInfo} is set.
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
   * Informs the {@code FrameProcessor} that no further input frames should be accepted.
   *
   * @throws IllegalStateException If called more than once.
   */
  void signalEndOfInput();

  /**
   * Releases all resources.
   *
   * <p>If the frame processor is released before it has {@linkplain
   * Listener#onFrameProcessingEnded() ended}, it will attempt to cancel processing any input frames
   * that have already become available. Input frames that become available after release are
   * ignored.
   *
   * <p>This method blocks until all resources are released or releasing times out.
   */
  void release();
}
