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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A sequence of {@link EditedMediaItem} instances.
 *
 * <p>{@linkplain EditedMediaItem} instances in a sequence don't overlap in time.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class EditedMediaItemSequence {

  /**
   * The {@link EditedMediaItem} instances in the sequence.
   *
   * <p>This list must not be empty.
   */
  public final ImmutableList<EditedMediaItem> editedMediaItems;
  /**
   * Whether this sequence is looping.
   *
   * <p>This value indicates whether to loop over the {@link EditedMediaItem} instances in this
   * sequence until all the non-looping sequences in the {@link Composition} have ended.
   *
   * <p>A looping sequence ends at the same time as the longest non-looping sequence. This means
   * that the last exported {@link EditedMediaItem} from a looping sequence can be only partially
   * exported.
   */
  public final boolean isLooping;

  /**
   * Creates an instance.
   *
   * @param editedMediaItems The {@link #editedMediaItems}.
   */
  public EditedMediaItemSequence(List<EditedMediaItem> editedMediaItems) {
    this(editedMediaItems, /* isLooping= */ false);
  }

  /**
   * Creates an instance.
   *
   * @param editedMediaItems The {@link #editedMediaItems}.
   * @param isLooping Whether the sequence {@linkplain #isLooping is looping}.
   */
  public EditedMediaItemSequence(List<EditedMediaItem> editedMediaItems, boolean isLooping) {
    checkArgument(!editedMediaItems.isEmpty());
    this.editedMediaItems = ImmutableList.copyOf(editedMediaItems);
    this.isLooping = isLooping;
  }
}
