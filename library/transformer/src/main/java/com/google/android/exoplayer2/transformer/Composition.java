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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;

/**
 * A composition of {@link MediaItem} instances, with transformations to apply to them.
 *
 * <p>The {@link MediaItem} instances can be concatenated or mixed. {@link Effects} can be applied
 * to individual {@link MediaItem} instances, as well as to the composition.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class Composition {

  /** A builder for {@link Composition} instances. */
  public static final class Builder {

    private final ImmutableList<EditedMediaItemSequence> sequences;

    private Effects effects;
    private boolean forceAudioTrack;
    private boolean transmuxAudio;
    private boolean transmuxVideo;

    /**
     * Creates an instance.
     *
     * @param sequences The {@link EditedMediaItemSequence} instances to compose. {@link MediaItem}
     *     instances from different sequences that are overlapping in time will be mixed in the
     *     output. This list must not be empty.
     */
    public Builder(List<EditedMediaItemSequence> sequences) {
      checkArgument(
          !sequences.isEmpty(),
          "The composition must contain at least one EditedMediaItemSequence.");
      this.sequences = ImmutableList.copyOf(sequences);
      effects = Effects.EMPTY;
    }

    /**
     * Sets the {@link Effects} to apply to the {@link Composition}.
     *
     * <p>The default value is {@link Effects#EMPTY}.
     *
     * @param effects The {@link Composition} {@link Effects}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setEffects(Effects effects) {
      this.effects = effects;
      return this;
    }

    /**
     * Sets whether the output file should always contain an audio track.
     *
     * <p>The default value is {@code false}.
     *
     * <ul>
     *   <li>If {@code false}:
     *       <ul>
     *         <li>If the {@link Composition} export doesn't produce any audio at timestamp 0, but
     *             produces audio later on, the export is {@linkplain
     *             Transformer.Listener#onError(Composition, ExportResult, ExportException)
     *             aborted}.
     *         <li>If the {@link Composition} doesn't produce any audio during the entire export,
     *             the output won't contain any audio.
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
     * TransformationRequest.Builder#setAudioMimeType(String)}. The sample rate and channel count
     * can be set by passing relevant {@link AudioProcessor} instances to the {@link Composition}.
     *
     * <p>Forcing an audio track and {@linkplain #setTransmuxAudio(boolean) requesting audio
     * transmuxing} are not allowed together because generating silence requires transcoding.
     *
     * <p>This method is experimental and may be removed or changed without warning.
     *
     * @param forceAudioTrack Whether to force an audio track in the output.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder experimentalSetForceAudioTrack(boolean forceAudioTrack) {
      this.forceAudioTrack = forceAudioTrack;
      return this;
    }

    /**
     * Sets whether to transmux the {@linkplain MediaItem media items'} audio tracks.
     *
     * <p>The default value is {@code false}.
     *
     * <p>If the {@link Composition} contains one {@link MediaItem}, the value set is ignored. The
     * audio track will only be transcoded if necessary.
     *
     * <p>If the input {@link Composition} contains multiple {@linkplain MediaItem media items}, all
     * the audio tracks are transcoded by default. They are all transmuxed if {@code transmuxAudio}
     * is {@code true}. Transmuxed tracks must be compatible (typically, all the {@link MediaItem}
     * instances containing the track to transmux are concatenated in a single {@link
     * EditedMediaItemSequence} and have the same sample format for that track).
     *
     * <p>Requesting audio transmuxing and {@linkplain #experimentalSetForceAudioTrack(boolean)
     * forcing an audio track} are not allowed together because generating silence requires
     * transcoding.
     *
     * @param transmuxAudio Whether to transmux the audio tracks.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setTransmuxAudio(boolean transmuxAudio) {
      this.transmuxAudio = transmuxAudio;
      return this;
    }

    /**
     * Sets whether to transmux the {@linkplain MediaItem media items'} video tracks.
     *
     * <p>The default value is {@code false}.
     *
     * <p>If the {@link Composition} contains one {@link MediaItem}, the value set is ignored. The
     * video track will only be transcoded if necessary.
     *
     * <p>If the input {@link Composition} contains multiple {@linkplain MediaItem media items}, all
     * the video tracks are transcoded by default. They are all transmuxed if {@code transmuxVideo}
     * is {@code true}. Transmuxed tracks must be compatible (typically, all the {@link MediaItem}
     * instances containing the track to transmux are concatenated in a single {@link
     * EditedMediaItemSequence} and have the same sample format for that track).
     *
     * @param transmuxVideo Whether to transmux the video tracks.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setTransmuxVideo(boolean transmuxVideo) {
      this.transmuxVideo = transmuxVideo;
      return this;
    }

    /** Builds a {@link Composition} instance. */
    public Composition build() {
      return new Composition(sequences, effects, forceAudioTrack, transmuxAudio, transmuxVideo);
    }
  }

  /**
   * The {@link EditedMediaItemSequence} instances to compose.
   *
   * <p>For more information, see {@link Builder#Builder(List)}.
   */
  public final ImmutableList<EditedMediaItemSequence> sequences;
  /** The {@link Effects} to apply to the composition. */
  public final Effects effects;
  /**
   * Whether the output file should always contain an audio track.
   *
   * <p>For more information, see {@link Builder#experimentalSetForceAudioTrack(boolean)}.
   */
  public final boolean forceAudioTrack;
  /**
   * Whether to transmux the {@linkplain MediaItem media items'} audio tracks.
   *
   * <p>For more information, see {@link Builder#setTransmuxAudio(boolean)}.
   */
  public final boolean transmuxAudio;
  /**
   * Whether to transmux the {@linkplain MediaItem media items'} video tracks.
   *
   * <p>For more information, see {@link Builder#setTransmuxVideo(boolean)}.
   */
  public final boolean transmuxVideo;

  private Composition(
      List<EditedMediaItemSequence> sequences,
      Effects effects,
      boolean forceAudioTrack,
      boolean transmuxAudio,
      boolean transmuxVideo) {
    checkArgument(
        !transmuxAudio || !forceAudioTrack,
        "Audio transmuxing and audio track forcing are not allowed together.");
    this.sequences = ImmutableList.copyOf(sequences);
    this.effects = effects;
    this.transmuxAudio = transmuxAudio;
    this.transmuxVideo = transmuxVideo;
    this.forceAudioTrack = forceAudioTrack;
  }
}
