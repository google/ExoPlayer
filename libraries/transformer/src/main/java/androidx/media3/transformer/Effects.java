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

import androidx.media3.common.Effect;
import androidx.media3.common.FrameProcessor;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.GlEffectsFrameProcessor;
import com.google.common.collect.ImmutableList;

/** Effects to apply to a {@link MediaItem}. */
@UnstableApi
public final class Effects {

  /* package */ final ImmutableList<AudioProcessor> audioProcessors;
  /* package */ final ImmutableList<Effect> videoEffects;
  /* package */ final FrameProcessor.Factory frameProcessorFactory;

  /**
   * Creates an instance using a {@link GlEffectsFrameProcessor.Factory}.
   *
   * <p>This is equivalent to calling {@link Effects#Effects(ImmutableList, ImmutableList,
   * FrameProcessor.Factory)} with a {@link GlEffectsFrameProcessor.Factory}.
   */
  public Effects(
      ImmutableList<AudioProcessor> audioProcessors, ImmutableList<Effect> videoEffects) {
    this(audioProcessors, videoEffects, new GlEffectsFrameProcessor.Factory());
  }

  /**
   * Creates an instance.
   *
   * @param audioProcessors The list of {@link AudioProcessor} instances to apply to audio buffers.
   *     They are applied in the order of the list, and buffers will only be modified by that {@link
   *     AudioProcessor} if it {@link AudioProcessor#isActive()} based on the current configuration.
   * @param videoEffects The list of {@link Effect} instances to apply to each video frame. They are
   *     applied in the order of the list.
   * @param frameProcessorFactory The {@link FrameProcessor.Factory} for the {@link FrameProcessor}
   *     to use when applying the {@code videoEffects} to the video frames.
   */
  public Effects(
      ImmutableList<AudioProcessor> audioProcessors,
      ImmutableList<Effect> videoEffects,
      FrameProcessor.Factory frameProcessorFactory) {
    this.audioProcessors = audioProcessors;
    this.videoEffects = videoEffects;
    this.frameProcessorFactory = frameProcessorFactory;
  }
}
