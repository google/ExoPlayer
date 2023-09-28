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
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoGraph;
import androidx.media3.effect.MultipleInputVideoGraph;
import androidx.media3.effect.VideoCompositorSettings;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A {@link TransformerVideoGraph Transformer}-specific implementation of {@link
 * MultipleInputVideoGraph}.
 */
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
