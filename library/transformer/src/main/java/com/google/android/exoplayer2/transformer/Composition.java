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

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A composition of {@link MediaItem} instances, with transformations to apply to them.
 *
 * <p>The {@linkplain MediaItem} instances can be concatenated or mixed. {@link Effects} can be
 * applied to individual {@linkplain MediaItem} instances, as well as to the composition.
 */
public final class Composition {

  /**
   * The {@link EditedMediaItemSequence} instances to compose. {@link MediaItem} instances from
   * different sequences that are overlapping in time will be mixed in the output.
   *
   * <p>This list must not be empty.
   */
  public final ImmutableList<EditedMediaItemSequence> sequences;
  /** The {@link Effects} to apply to the composition. */
  public final Effects effects;
  /**
   * Whether the output file should always contain an audio track.
   *
   * <ul>
   *   <li>If {@code false}:
   *       <ul>
   *         <li>If the {@link Composition} export doesn't produce any audio at timestamp 0, the
   *             output won't contain any audio, and audio tracks from the {@link MediaItem}
   *             instances in the {@link Composition} will be ignored.
   *         <li>If the {@link Composition} export produces audio at timestamp 0, the output will
   *             contain an audio track.
   *       </ul>
   *   <li>If {@code true}, the output will always contain an audio track.
   * </ul>
   *
   * If the output contains an audio track, silent audio will be generated for the segments where
   * the {@link Composition} export doesn't produce any audio.
   *
   * <p>The MIME type of the output's audio track can be set using {@link
   * TransformationRequest.Builder#setAudioMimeType(String)}. The sample rate and channel count can
   * be set by passing relevant {@link AudioProcessor} instances to the {@link Composition}.
   *
   * <p>This parameter is experimental and may be removed or changed without warning.
   */
  public final boolean experimentalForceAudioTrack;

  /**
   * Creates an instance.
   *
   * <p>This is equivalent to calling {@link Composition#Composition(List, Effects, boolean)} with
   * {@link #experimentalForceAudioTrack} set to {@code false}.
   */
  public Composition(List<EditedMediaItemSequence> sequences, Effects effects) {
    this(sequences, effects, /* experimentalForceAudioTrack= */ false);
  }

  /**
   * Creates an instance.
   *
   * @param sequences The {@link #sequences}.
   * @param effects The {@link #effects}.
   * @param experimentalForceAudioTrack Whether to {@linkplain #experimentalForceAudioTrack always
   *     add an audio track in the output}.
   */
  public Composition(
      List<EditedMediaItemSequence> sequences,
      Effects effects,
      boolean experimentalForceAudioTrack) {
    checkArgument(!sequences.isEmpty());
    this.sequences = ImmutableList.copyOf(sequences);
    this.effects = effects;
    this.experimentalForceAudioTrack = experimentalForceAudioTrack;
  }
}
