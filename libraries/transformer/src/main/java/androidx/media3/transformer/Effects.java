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

import android.util.Pair;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.SpeedChangingAudioProcessor;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.SpeedChangeEffect;
import androidx.media3.effect.TimestampAdjustment;
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
   * Creates an instance.
   *
   * @param audioProcessors The {@link #audioProcessors}.
   * @param videoEffects The {@link #videoEffects}.
   */
  public Effects(List<AudioProcessor> audioProcessors, List<Effect> videoEffects) {
    this.audioProcessors = ImmutableList.copyOf(audioProcessors);
    this.videoEffects = ImmutableList.copyOf(videoEffects);
  }

  /**
   * Creates an interlinked {@linkplain AudioProcessor audio processor} and {@linkplain Effect video
   * effect} that changes the speed to media samples in segments of the input file specified by the
   * given {@link SpeedProvider}.
   *
   * <p>The {@linkplain AudioProcessor audio processor} and {@linkplain Effect video effect} are
   * interlinked to help maintain A/V sync. When using Transformer, if the input file doesn't have
   * audio, or audio is being removed, you may have to {@linkplain
   * Composition.Builder#experimentalSetForceAudioTrack force an audio track} for the interlinked
   * effects to function correctly. Alternatively, you can use {@link SpeedChangeEffect} when input
   * has no audio.
   *
   * @param speedProvider The {@link SpeedProvider} determining the speed for the media at specific
   *     timestamps.
   */
  public static Pair<AudioProcessor, Effect> createExperimentalSpeedChangingEffect(
      SpeedProvider speedProvider) {
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        new SpeedChangingAudioProcessor(speedProvider);
    Effect audioDrivenvideoEffect =
        new TimestampAdjustment(speedChangingAudioProcessor::getSpeedAdjustedTimeAsync);
    return Pair.create(speedChangingAudioProcessor, audioDrivenvideoEffect);
  }
}
