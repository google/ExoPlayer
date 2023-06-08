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
package androidx.media3.transformer.mh;

import android.content.Context;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;
import androidx.media3.effect.ScaleAndRotateTransformation;

/**
 * Wraps a {@link GlEffect} to prevent the {@link DefaultVideoFrameProcessor} from detecting its
 * class and optimizing it.
 *
 * <p>This ensures that {@link DefaultVideoFrameProcessor} uses a separate {@link GlShaderProgram}
 * for the wrapped {@link GlEffect} rather than merging it with preceding or subsequent {@link
 * GlEffect} instances and applying them in one combined {@link GlShaderProgram}.
 */
// TODO(b/263395272): Move this to effects/mh tests.
public final class UnoptimizedGlEffect implements GlEffect {
  // A passthrough effect allows for testing having an intermediate effect injected, which uses
  // different OpenGL shaders from having no effects.
  public static final GlEffect NO_OP_EFFECT =
      new UnoptimizedGlEffect(new ScaleAndRotateTransformation.Builder().build());

  private final GlEffect effect;

  public UnoptimizedGlEffect(GlEffect effect) {
    this.effect = effect;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return effect.toGlShaderProgram(context, useHdr);
  }
}
