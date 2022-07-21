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
import androidx.media3.common.util.UnstableApi;

/**
 * Interface for a video frame effect with a {@link GlTextureProcessor} implementation.
 *
 * <p>Implementations contain information specifying the effect and can be {@linkplain
 * #toGlTextureProcessor(Context, boolean) converted} to a {@link GlTextureProcessor} which applies
 * the effect.
 */
@UnstableApi
public interface GlEffect {

  /**
   * Returns a {@link SingleFrameGlTextureProcessor} that applies the effect.
   *
   * @param context A {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in gamma RGB BT.709.
   */
  // TODO(b/227624622): PQ input files will actually have the incorrect HLG OETF applied, so that
  // the intermediate color space will be PQ with the HLG OETF applied. This means intermediate
  // GlEffects affecting color will look incorrect on PQ input. Fix this by implementing proper PQ
  // OETF / EOTF support.
  GlTextureProcessor toGlTextureProcessor(Context context, boolean useHdr)
      throws FrameProcessingException;
}
