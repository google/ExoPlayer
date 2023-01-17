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

/** A {@link MediaItem} with the transformations to apply to it. */
@UnstableApi
public class EditedMediaItem {

  /* package */ final MediaItem mediaItem;
  /* package */ final Effects effects;

  /**
   * Creates an instance with no {@link Effects}.
   *
   * @param mediaItem The {@link MediaItem} to edit.
   */
  public EditedMediaItem(MediaItem mediaItem) {
    this(mediaItem, new Effects(ImmutableList.of(), ImmutableList.of()));
  }

  /**
   * Creates an instance.
   *
   * @param mediaItem The {@link MediaItem} to edit.
   * @param effects The {@link Effects} to apply to the {@code mediaItem}.
   */
  public EditedMediaItem(MediaItem mediaItem, Effects effects) {
    this.mediaItem = mediaItem;
    this.effects = effects;
  }
}
