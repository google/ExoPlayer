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

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A sequence of {@link EditedMediaItem} instances.
 *
 * <p>{@linkplain EditedMediaItem} instances in a sequence don't overlap in time.
 */
@UnstableApi
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

  /** Creates a {@linkplain #isLooping non-looping} instance. */
  public EditedMediaItemSequence(EditedMediaItem... editedMediaItems) {
    this(ImmutableList.copyOf(editedMediaItems));
  }

  /** Creates a {@linkplain #isLooping non-looping} instance. */
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
