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

package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.android.exoplayer2.video.VideoGraph;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A {@link VideoGraph} that handles one input stream.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public abstract class SingleInputVideoGraph implements VideoGraph {

  /** The ID {@link #registerInput()} returns. */
  public static final int SINGLE_INPUT_INDEX = 0;

  private final Context context;
  private final VideoFrameProcessor.Factory videoFrameProcessorFactory;
  private final ColorInfo inputColorInfo;
  private final ColorInfo outputColorInfo;
  private final Listener listener;
  private final DebugViewProvider debugViewProvider;
  private final Executor listenerExecutor;
  private final boolean renderFramesAutomatically;
  private final long initialTimestampOffsetUs;
  @Nullable private final Presentation presentation;

  @Nullable private VideoFrameProcessor videoFrameProcessor;
  @Nullable private SurfaceInfo outputSurfaceInfo;
  private boolean isEnded;
  private boolean released;
  private volatile boolean hasProducedFrameWithTimestampZero;

  /**
   * Creates an instance.
   *
   * <p>{@code videoCompositorSettings} must be {@link VideoCompositorSettings#DEFAULT}.
   */
  // TODO: b/307952514 - Remove inputColorInfo reference in VideoGraph constructor.
  public SingleInputVideoGraph(
      Context context,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      Listener listener,
      DebugViewProvider debugViewProvider,
      Executor listenerExecutor,
      VideoCompositorSettings videoCompositorSettings,
      boolean renderFramesAutomatically,
      @Nullable Presentation presentation,
      long initialTimestampOffsetUs) {
    checkState(
        VideoCompositorSettings.DEFAULT.equals(videoCompositorSettings),
        "SingleInputVideoGraph does not use VideoCompositor, and therefore cannot apply"
            + " VideoCompositorSettings");
    this.context = context;
    this.videoFrameProcessorFactory = videoFrameProcessorFactory;
    this.inputColorInfo = inputColorInfo;
    this.outputColorInfo = outputColorInfo;
    this.listener = listener;
    this.debugViewProvider = debugViewProvider;
    this.listenerExecutor = listenerExecutor;
    this.renderFramesAutomatically = renderFramesAutomatically;
    this.presentation = presentation;
    this.initialTimestampOffsetUs = initialTimestampOffsetUs;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method must be called at most once.
   */
  @Override
  public void initialize() {
    // Initialization is deferred to registerInput();
  }

  @Override
  public int registerInput() throws VideoFrameProcessingException {
    checkStateNotNull(videoFrameProcessor == null && !released);

    videoFrameProcessor =
        videoFrameProcessorFactory.create(
            context,
            debugViewProvider,
            outputColorInfo,
            renderFramesAutomatically,
            /* listenerExecutor= */ MoreExecutors.directExecutor(),
            new VideoFrameProcessor.Listener() {
              private long lastProcessedFramePresentationTimeUs;

              @Override
              public void onInputStreamRegistered(
                  @VideoFrameProcessor.InputType int inputType,
                  List<Effect> effects,
                  FrameInfo frameInfo) {}

              @Override
              public void onOutputSizeChanged(int width, int height) {
                listenerExecutor.execute(() -> listener.onOutputSizeChanged(width, height));
              }

              @Override
              public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                if (isEnded) {
                  onError(
                      new VideoFrameProcessingException(
                          "onOutputFrameAvailableForRendering() received after onEnded()"));
                  return;
                }
                // Frames are rendered automatically.
                if (presentationTimeUs == 0) {
                  hasProducedFrameWithTimestampZero = true;
                }
                lastProcessedFramePresentationTimeUs = presentationTimeUs;
                listenerExecutor.execute(
                    () -> listener.onOutputFrameAvailableForRendering(presentationTimeUs));
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                listenerExecutor.execute(() -> listener.onError(exception));
              }

              @Override
              public void onEnded() {
                if (isEnded) {
                  onError(new VideoFrameProcessingException("onEnded() received multiple times"));
                  return;
                }
                isEnded = true;
                listenerExecutor.execute(
                    () -> listener.onEnded(lastProcessedFramePresentationTimeUs));
              }
            });
    if (outputSurfaceInfo != null) {
      videoFrameProcessor.setOutputSurfaceInfo(outputSurfaceInfo);
    }
    return SINGLE_INPUT_INDEX;
  }

  @Override
  public VideoFrameProcessor getProcessor(int inputId) {
    return checkStateNotNull(videoFrameProcessor);
  }

  @Override
  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    this.outputSurfaceInfo = outputSurfaceInfo;
    if (videoFrameProcessor != null) {
      videoFrameProcessor.setOutputSurfaceInfo(outputSurfaceInfo);
    }
  }

  @Override
  public boolean hasProducedFrameWithTimestampZero() {
    return hasProducedFrameWithTimestampZero;
  }

  @Override
  public void release() {
    if (released) {
      return;
    }

    if (videoFrameProcessor != null) {
      videoFrameProcessor.release();
      videoFrameProcessor = null;
    }
    released = true;
  }

  protected ColorInfo getInputColorInfo() {
    return inputColorInfo;
  }

  protected long getInitialTimestampOffsetUs() {
    return initialTimestampOffsetUs;
  }

  @Nullable
  protected Presentation getPresentation() {
    return presentation;
  }
}
