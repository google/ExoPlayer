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
import com.google.android.exoplayer2.effect.Presentation;
import com.google.android.exoplayer2.effect.VideoCompositorSettings;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.List;
import java.util.concurrent.Executor;

@Deprecated
/* package */ final class TransformerSingleInputVideoGraph extends SingleInputVideoGraph
    implements TransformerVideoGraph {

  /** A factory for creating a {@link SingleInputVideoGraph}. */
  public static final class Factory implements TransformerVideoGraph.Factory {

    private final VideoFrameProcessor.Factory videoFrameProcessorFactory;

    public Factory(VideoFrameProcessor.Factory videoFrameProcessorFactory) {
      this.videoFrameProcessorFactory = videoFrameProcessorFactory;
    }

    @Override
    public TransformerVideoGraph create(
        Context context,
        ColorInfo inputColorInfo,
        ColorInfo outputColorInfo,
        DebugViewProvider debugViewProvider,
        Listener listener,
        Executor listenerExecutor,
        VideoCompositorSettings videoCompositorSettings,
        List<Effect> compositionEffects,
        long initialTimestampOffsetUs) {
      @Nullable Presentation presentation = null;
      for (int i = 0; i < compositionEffects.size(); i++) {
        Effect effect = compositionEffects.get(i);
        if (effect instanceof Presentation) {
          presentation = (Presentation) effect;
        }
      }
      return new TransformerSingleInputVideoGraph(
          context,
          videoFrameProcessorFactory,
          inputColorInfo,
          outputColorInfo,
          listener,
          debugViewProvider,
          listenerExecutor,
          videoCompositorSettings,
          /* renderFramesAutomatically= */ true,
          presentation,
          initialTimestampOffsetUs);
    }
  }

  private TransformerSingleInputVideoGraph(
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
    super(
        context,
        videoFrameProcessorFactory,
        inputColorInfo,
        outputColorInfo,
        listener,
        debugViewProvider,
        listenerExecutor,
        videoCompositorSettings,
        renderFramesAutomatically,
        presentation,
        initialTimestampOffsetUs);
  }

  @Override
  public GraphInput createInput() {
    return getVideoFrameProcessingWrapper();
  }
}
