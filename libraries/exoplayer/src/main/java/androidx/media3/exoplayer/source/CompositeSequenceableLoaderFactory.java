/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.media3.exoplayer.source;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import java.util.List;

/** A factory to create composite {@link SequenceableLoader}s. */
@UnstableApi
public interface CompositeSequenceableLoaderFactory {

  /** Returns an empty composite {@link SequenceableLoader}, with no delegate loaders. */
  SequenceableLoader empty();

  /**
   * @deprecated Use {@link #empty()} for an empty composite loader, or {@link #create(List, List)}
   *     for a non-empty one.
   */
  @Deprecated
  SequenceableLoader createCompositeSequenceableLoader(SequenceableLoader... loaders);

  /**
   * Creates a composite {@link SequenceableLoader}.
   *
   * @param loaders The sub-loaders that make up the {@link SequenceableLoader} to be built.
   * @param loaderTrackTypes The track types handled by each entry in {@code loaders}. Must be the
   *     same as {@code loaders}.
   * @return A composite {@link SequenceableLoader} that comprises the given loaders.
   */
  SequenceableLoader create(
      List<? extends SequenceableLoader> loaders,
      List<List<@C.TrackType Integer>> loaderTrackTypes);
}
