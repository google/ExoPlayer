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
package androidx.media3.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.EGLExt;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Interface for a video frame processor that applies changes to individual video frames.
 *
 * <p>The changes are specified by {@link Effect} instances passed to {@link Factory#create}.
 *
 * <p>Manages its input {@link Surface}, which can be accessed via {@link #getInputSurface()}. The
 * output {@link Surface} must be set by the caller using {@link
 * #setOutputSurfaceInfo(SurfaceInfo)}.
 *
 * <p>The caller must {@linkplain #registerInputFrame() register} input frames before rendering them
 * to the input {@link Surface}.
 */
@UnstableApi
public interface VideoFrameProcessor {
  // TODO(b/243036513): Allow effects to be replaced.

  /** A factory for {@link VideoFrameProcessor} instances. */
  interface Factory {

    /**
     * Sets the {@link GlObjectsProvider}.
     *
     * <p>Must be called before {@link #create}.
     */
    Factory setGlObjectsProvider(GlObjectsProvider glObjectsProvider);

    // TODO(271433904): Turn parameters with default values into setters.
    /**
     * Creates a new {@link VideoFrameProcessor} instance.
     *
     * @param context A {@link Context}.
     * @param effects The {@link Effect} instances to apply to each frame. Applied on the {@code
     *     outputColorInfo}'s color space.
     * @param debugViewProvider A {@link DebugViewProvider}.
     * @param inputColorInfo The {@link ColorInfo} for input frames.
     * @param outputColorInfo The {@link ColorInfo} for output frames.
     * @param isInputTextureExternal Whether the input frames are produced externally (e.g. from a
     *     video) or not (e.g. from a {@link Bitmap}). See <a
     *     href="https://source.android.com/docs/core/graphics/arch-st#ext_texture">the
     *     SurfaceTexture docs</a> for more information on external textures.
     * @param releaseFramesAutomatically If {@code true}, the instance will render output frames to
     *     the {@linkplain #setOutputSurfaceInfo(SurfaceInfo) output surface} automatically as
     *     {@link VideoFrameProcessor} is done processing them. If {@code false}, the {@link
     *     VideoFrameProcessor} will block until {@link #releaseOutputFrame(long)} is called, to
     *     render or drop the frame.
     * @param executor The {@link Executor} on which the {@code listener} is invoked.
     * @param listener A {@link Listener}.
     * @return A new instance.
     * @throws VideoFrameProcessingException If a problem occurs while creating the {@link
     *     VideoFrameProcessor}.
     */
    VideoFrameProcessor create(
        Context context,
        List<Effect> effects,
        DebugViewProvider debugViewProvider,
        ColorInfo inputColorInfo,
        ColorInfo outputColorInfo,
        boolean isInputTextureExternal,
        boolean releaseFramesAutomatically,
        Executor executor,
        Listener listener)
        throws VideoFrameProcessingException;
  }

  /**
   * Listener for asynchronous frame processing events.
   *
   * <p>All listener methods must be called from the {@link Executor} passed in at {@linkplain
   * Factory#create creation}.
   */
  interface Listener {

    /**
     * Called when the output size changes.
     *
     * <p>The output size is the frame size in pixels after applying all {@linkplain Effect
     * effects}.
     *
     * <p>The output size may differ from the size specified using {@link
     * #setOutputSurfaceInfo(SurfaceInfo)}.
     */
    void onOutputSizeChanged(int width, int height);

    /**
     * Called when an output frame with the given {@code presentationTimeUs} becomes available.
     *
     * @param presentationTimeUs The presentation time of the frame, in microseconds.
     */
    void onOutputFrameAvailable(long presentationTimeUs);

    /**
     * Called when an exception occurs during asynchronous video frame processing.
     *
     * <p>If an error occurred, consuming and producing further frames will not work as expected and
     * the {@link VideoFrameProcessor} should be released.
     */
    void onError(VideoFrameProcessingException exception);

    /** Called after the {@link VideoFrameProcessor} has produced its final output frame. */
    void onEnded();
  }

  /**
   * Indicates the frame should be released immediately after {@link #releaseOutputFrame(long)} is
   * invoked.
   */
  long RELEASE_OUTPUT_FRAME_IMMEDIATELY = -1;

  /** Indicates the frame should be dropped after {@link #releaseOutputFrame(long)} is invoked. */
  long DROP_OUTPUT_FRAME = -2;

  /**
   * Provides an input {@link Bitmap} to the {@code VideoFrameProcessor}.
   *
   * <p>This method should only be used for when the {@code VideoFrameProcessor}'s {@code
   * isInputTextureExternal} parameter is set to {@code false}.
   *
   * <p>Can be called on any thread.
   *
   * @param inputBitmap The {@link Bitmap} queued to the {@code VideoFrameProcessor}.
   * @param durationUs The duration for which to display the {@code inputBitmap}, in microseconds.
   * @param frameRate The frame rate at which to display the {@code inputBitmap}, in frames per
   *     second.
   */
  // TODO(b/262693274): Remove duration and frameRate parameters when EditedMediaItem can be
  //  signalled down to the processors.
  void queueInputBitmap(Bitmap inputBitmap, long durationUs, float frameRate);

  /**
   * Returns the input {@link Surface}, where {@code VideoFrameProcessor} consumes input frames
   * from.
   *
   * <p>This method should only be used for when the {@code VideoFrameProcessor}'s {@code
   * isInputTextureExternal} parameter is set to {@code true}.
   *
   * <p>Can be called on any thread.
   */
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
   * <p>The caller should update {@link FrameInfo#streamOffsetUs} when switching to an input stream
   * whose first frame timestamp is less than or equal to the last timestamp received. This stream
   * offset should ensure that frame timestamps are monotonically increasing.
   *
   * <p>Can be called on any thread.
   */
  void setInputFrameInfo(FrameInfo inputFrameInfo);

  /**
   * Informs the {@code VideoFrameProcessor} that a frame will be queued to its {@linkplain
   * #getInputSurface() input surface}.
   *
   * <p>Must be called before rendering a frame to the input surface.
   *
   * <p>This method should only be used for when the {@code VideoFrameProcessor}'s {@code
   * isInputTextureExternal} parameter is set to {@code true}.
   *
   * <p>Can be called on any thread.
   *
   * @throws IllegalStateException If called after {@link #signalEndOfInput()} or before {@link
   *     #setInputFrameInfo(FrameInfo)}.
   */
  void registerInputFrame();

  /**
   * Returns the number of input frames that have been {@linkplain #registerInputFrame() registered}
   * but not processed off the {@linkplain #getInputSurface() input surface} yet.
   *
   * <p>This method should only be used for when the {@code VideoFrameProcessor}'s {@code
   * isInputTextureExternal} parameter is set to {@code true}.
   *
   * <p>Can be called on any thread.
   */
  int getPendingInputFrameCount();

  /**
   * Sets the output surface and supporting information. When output frames are released and not
   * dropped, they will be rendered to this output {@link SurfaceInfo}.
   *
   * <p>The new output {@link SurfaceInfo} is applied from the next output frame rendered onwards.
   * If the output {@link SurfaceInfo} is {@code null}, the {@code VideoFrameProcessor} will stop
   * rendering pending frames and resume rendering once a non-null {@link SurfaceInfo} is set.
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
   * Releases the oldest unreleased output frame that has become {@linkplain
   * Listener#onOutputFrameAvailable(long) available} at the given {@code releaseTimeNs}.
   *
   * <p>This will either render the output frame to the {@linkplain #setOutputSurfaceInfo output
   * surface}, or drop the frame, per {@code releaseTimeNs}.
   *
   * <p>This method must only be called if {@code releaseFramesAutomatically} was set to {@code
   * false} using the {@link Factory} and should be called exactly once for each frame that becomes
   * {@linkplain Listener#onOutputFrameAvailable(long) available}.
   *
   * <p>The {@code releaseTimeNs} may be passed to {@link EGLExt#eglPresentationTimeANDROID}
   * depending on the implementation.
   *
   * @param releaseTimeNs The release time to use for the frame, in nanoseconds. The release time
   *     can be before of after the current system time. Use {@link #DROP_OUTPUT_FRAME} to drop the
   *     frame, or {@link #RELEASE_OUTPUT_FRAME_IMMEDIATELY} to release the frame immediately.
   */
  void releaseOutputFrame(long releaseTimeNs);

  /**
   * Informs the {@code VideoFrameProcessor} that no further input frames should be accepted.
   *
   * <p>Can be called on any thread.
   *
   * @throws IllegalStateException If called more than once.
   */
  void signalEndOfInput();

  /**
   * Flushes the {@code VideoFrameProcessor}.
   *
   * <p>All the frames that are {@linkplain #registerInputFrame() registered} prior to calling this
   * method are no longer considered to be registered when this method returns.
   *
   * <p>This method should only be used for when the {@code VideoFrameProcessor}'s {@code
   * isInputTextureExternal} parameter is set to {@code true}.
   *
   * <p>{@link Listener} methods invoked prior to calling this method should be ignored.
   */
  void flush();

  /**
   * Releases all resources.
   *
   * <p>If the {@code VideoFrameProcessor} is released before it has {@linkplain Listener#onEnded()
   * ended}, it will attempt to cancel processing any input frames that have already become
   * available. Input frames that become available after release are ignored.
   *
   * <p>This method blocks until all resources are released or releasing times out.
   *
   * <p>Can be called on any thread.
   */
  void release();
}
