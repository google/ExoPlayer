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
package androidx.media3.effect;

import android.graphics.Gainmap;
import androidx.media3.common.util.GlUtil.GlException;

/** Interface for a {@link GlShaderProgram} that samples from a gainmap. */
/* package */ interface GainmapShaderProgram extends GlShaderProgram {

  /**
   * Sets the {@link Gainmap} that is applied to the output frame.
   *
   * @param gainmap The {@link Gainmap}.
   */
  void setGainmap(Gainmap gainmap) throws GlException;
}
