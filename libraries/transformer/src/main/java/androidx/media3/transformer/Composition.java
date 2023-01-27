/*
 * Copyright 2023 The Android Open Source Project
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

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;

/**
 * A composition of {@link MediaItem} instances, with transformations to apply to them.
 *
 * <p>The {@linkplain MediaItem} instances can be concatenated or mixed. {@link Effects} can be
 * applied to individual {@linkplain MediaItem} instances, as well as to the composition.
 */
@UnstableApi
public final class Composition {

  /* package */ final ImmutableList<EditedMediaItemSequence> sequences;
  /* package */ final Effects effects;

  /**
   * Creates an instance.
   *
   * @param sequences The {@link EditedMediaItemSequence} instances to compose. {@link MediaItem}
   *     instances from different sequences that are overlapping in time will be mixed in the
   *     output.
   * @param effects The {@link Effects} to apply to the composition.
   */
  public Composition(ImmutableList<EditedMediaItemSequence> sequences, Effects effects) {
    this.sequences = sequences;
    this.effects = effects;
  }
}
