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
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Effects to apply to a {@link MediaItem} or to a {@link Composition}. */
@UnstableApi
public final class Effects {

  /** An empty {@link Effects} instance. */
  public static final Effects EMPTY =
      new Effects(
          /* audioProcessors= */ ImmutableList.of(), /* videoEffects= */ ImmutableList.of());

  /**
   * The list of {@linkplain AudioProcessor audio processors} to apply to audio buffers. They are
   * applied in the order of the list, and buffers will only be modified by that {@link
   * AudioProcessor} if it {@link AudioProcessor#isActive()} based on the current configuration.
   */
  public final ImmutableList<AudioProcessor> audioProcessors;
  /**
   * The list of {@linkplain Effect video effects} to apply to each frame. They are applied in the
   * order of the list.
   */
  public final ImmutableList<Effect> videoEffects;
  /**
   * The {@link VideoFrameProcessor.Factory} for the {@link VideoFrameProcessor} to use when
   * applying the {@code videoEffects} to the video frames.
   */
  public final VideoFrameProcessor.Factory videoFrameProcessorFactory;
  /**
   * The {@link GlObjectsProvider} used to create and maintain certain GL Objects in the {@link
   * VideoFrameProcessor}.
   */
  public final GlObjectsProvider glObjectsProvider;

  /**
   * Creates an instance using a {@link DefaultVideoFrameProcessor.Factory}.
   *
   * <p>This is equivalent to calling {@link Effects#Effects(List, List,
   * VideoFrameProcessor.Factory, GlObjectsProvider)} with a {@link
   * DefaultVideoFrameProcessor.Factory} and {@link GlObjectsProvider#DEFAULT}.
   */
  public Effects(List<AudioProcessor> audioProcessors, List<Effect> videoEffects) {
    this(
        audioProcessors,
        videoEffects,
        new DefaultVideoFrameProcessor.Factory(),
        GlObjectsProvider.DEFAULT);
  }

  /**
   * Creates an instance.
   *
   * @param audioProcessors The {@link #audioProcessors}.
   * @param videoEffects The {@link #videoEffects}.
   * @param videoFrameProcessorFactory The {@link #videoFrameProcessorFactory}.
   * @param glObjectsProvider The {@link GlObjectsProvider}.
   */
  public Effects(
      List<AudioProcessor> audioProcessors,
      List<Effect> videoEffects,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      GlObjectsProvider glObjectsProvider) {
    this.audioProcessors = ImmutableList.copyOf(audioProcessors);
    this.videoEffects = ImmutableList.copyOf(videoEffects);
    this.videoFrameProcessorFactory = videoFrameProcessorFactory;
    this.glObjectsProvider = glObjectsProvider;
  }
}
