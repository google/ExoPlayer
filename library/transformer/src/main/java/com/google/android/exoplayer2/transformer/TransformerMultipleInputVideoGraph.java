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
import androidx.media3.common.VideoGraph;
import com.google.android.exoplayer2.effect.MultipleInputVideoGraph;
import com.google.android.exoplayer2.effect.VideoCompositorSettings;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A {@link TransformerVideoGraph Transformer}-specific implementation of {@link
 * MultipleInputVideoGraph}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class TransformerMultipleInputVideoGraph extends MultipleInputVideoGraph
    implements TransformerVideoGraph {

  /** A factory for creating {@link TransformerMultipleInputVideoGraph} instances. */
  public static final class Factory implements TransformerVideoGraph.Factory {
    @Override
    public TransformerMultipleInputVideoGraph create(
        Context context,
        ColorInfo inputColorInfo,
        ColorInfo outputColorInfo,
        DebugViewProvider debugViewProvider,
        VideoGraph.Listener listener,
        Executor listenerExecutor,
        VideoCompositorSettings videoCompositorSettings,
        List<Effect> compositionEffects,
        long initialTimestampOffsetUs) {
      return new TransformerMultipleInputVideoGraph(
          context,
          inputColorInfo,
          outputColorInfo,
          debugViewProvider,
          listener,
          listenerExecutor,
          videoCompositorSettings,
          compositionEffects,
          initialTimestampOffsetUs);
    }
  }

  private TransformerMultipleInputVideoGraph(
      Context context,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      DebugViewProvider debugViewProvider,
      Listener listener,
      Executor listenerExecutor,
      VideoCompositorSettings videoCompositorSettings,
      List<Effect> compositionEffects,
      long initialTimestampOffsetUs) {
    super(
        context,
        inputColorInfo,
        outputColorInfo,
        debugViewProvider,
        listener,
        listenerExecutor,
        videoCompositorSettings,
        compositionEffects,
        initialTimestampOffsetUs);
  }

  @Override
  public GraphInput createInput() throws VideoFrameProcessingException {
    int inputId = registerInput();
    return new VideoFrameProcessingWrapper(
        getProcessor(inputId),
        getInputColorInfo(),
        /* presentation= */ null,
        getInitialTimestampOffsetUs());
  }
}
