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
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.VideoCompositorSettings;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

/**
 * A composition of {@link MediaItem} instances, with transformations to apply to them.
 *
 * <p>The {@link MediaItem} instances can be concatenated or mixed. {@link Effects} can be applied
 * to individual {@link MediaItem} instances, as well as to the composition.
 */
@UnstableApi
public final class Composition {

  /** A builder for {@link Composition} instances. */
  public static final class Builder {

    private ImmutableList<EditedMediaItemSequence> sequences;
    private VideoCompositorSettings videoCompositorSettings;
    private Effects effects;
    private boolean forceAudioTrack;
    private boolean transmuxAudio;
    private boolean transmuxVideo;
    private @HdrMode int hdrMode;

    /**
     * Creates an instance.
     *
     * @see Builder#Builder(List)
     */
    public Builder(EditedMediaItemSequence... sequences) {
      this(ImmutableList.copyOf(sequences));
    }

    /**
     * Creates an instance.
     *
     * @param sequences The {@link EditedMediaItemSequence} instances to compose. The list must be
     *     non empty. See {@link Composition#sequences} for more details.
     */
    public Builder(List<EditedMediaItemSequence> sequences) {
      checkArgument(
          !sequences.isEmpty(),
          "The composition must contain at least one EditedMediaItemSequence.");
      this.sequences = ImmutableList.copyOf(sequences);
      videoCompositorSettings = VideoCompositorSettings.DEFAULT;
      effects = Effects.EMPTY;
    }

    /** Creates a new instance to build upon the provided {@link Composition}. */
    private Builder(Composition composition) {
      sequences = composition.sequences;
      videoCompositorSettings = composition.videoCompositorSettings;
      effects = composition.effects;
      forceAudioTrack = composition.forceAudioTrack;
      transmuxAudio = composition.transmuxAudio;
      transmuxVideo = composition.transmuxVideo;
      hdrMode = composition.hdrMode;
    }

    /**
     * Sets the {@link VideoCompositorSettings} to apply to the {@link Composition}.
     *
     * <p>The default value is {@link VideoCompositorSettings#DEFAULT}.
     *
     * @param videoCompositorSettings The {@link VideoCompositorSettings}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setVideoCompositorSettings(VideoCompositorSettings videoCompositorSettings) {
      this.videoCompositorSettings = videoCompositorSettings;
      return this;
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
     * Transformer.Builder#setAudioMimeType(String)}. The sample rate and channel count can be set
     * by passing relevant {@link AudioProcessor} instances to the {@link Composition}.
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

    /**
     * Sets the {@link HdrMode} for HDR video input.
     *
     * <p>The default value is {@link #HDR_MODE_KEEP_HDR}. Apps that need to tone-map HDR to SDR
     * should generally prefer {@link #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL} over {@link
     * #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC}, because its behavior is likely to be more
     * consistent across devices.
     *
     * @param hdrMode The {@link HdrMode} used.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setHdrMode(@HdrMode int hdrMode) {
      this.hdrMode = hdrMode;
      return this;
    }

    /** Builds a {@link Composition} instance. */
    public Composition build() {
      return new Composition(
          sequences,
          videoCompositorSettings,
          effects,
          forceAudioTrack,
          transmuxAudio,
          transmuxVideo,
          hdrMode);
    }

    /**
     * Sets {@link Composition#sequences}.
     *
     * @param sequences The {@link EditedMediaItemSequence} instances to compose. The list must not
     *     be empty.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    /* package */ Builder setSequences(List<EditedMediaItemSequence> sequences) {
      checkArgument(
          !sequences.isEmpty(),
          "The composition must contain at least one EditedMediaItemSequence.");
      this.sequences = ImmutableList.copyOf(sequences);
      return this;
    }
  }

  /**
   * The strategy to use to transcode or edit High Dynamic Range (HDR) input video.
   *
   * <p>One of {@link #HDR_MODE_KEEP_HDR}, {@link #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC},
   * {@link #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL}, or {@link
   * #HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR}.
   *
   * <p>Standard Dynamic Range (SDR) input video is unaffected by these settings.
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    HDR_MODE_KEEP_HDR,
    HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC,
    HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
    HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR,
  })
  public @interface HdrMode {}

  /**
   * Processes HDR input as HDR, to generate HDR output.
   *
   * <p>The HDR output format (ex. color transfer) will be the same as the HDR input format.
   *
   * <p>Supported on API 31+, by some device and HDR format combinations.
   *
   * <p>If not supported, {@link Transformer} will attempt to use {@link
   * #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL}.
   */
  public static final int HDR_MODE_KEEP_HDR = 0;

  /**
   * Tone map HDR input to SDR before processing, to generate SDR output, using the {@link
   * android.media.MediaCodec} decoder tone-mapper.
   *
   * <p>Supported on API 31+, by some device and HDR format combinations. Tone-mapping is only
   * guaranteed to be supported on API 33+, on devices with HDR capture support.
   *
   * <p>If not supported, {@link Transformer} throws an {@link ExportException}.
   */
  public static final int HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC = 1;

  /**
   * Tone map HDR input to SDR before processing, to generate SDR output, using an OpenGL
   * tone-mapper.
   *
   * <p>Supported on API 29+.
   *
   * <p>This may exhibit mild differences from {@link
   * #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC}, depending on the device's tone-mapping
   * implementation, but should have much wider support and have more consistent results across
   * devices.
   *
   * <p>If not supported, {@link Transformer} throws an {@link ExportException}.
   */
  public static final int HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL = 2;

  /**
   * Interpret HDR input as SDR, likely with a washed out look.
   *
   * <p>This is much more widely supported than {@link #HDR_MODE_KEEP_HDR}, {@link
   * #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC}, and {@link
   * #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL}. However, as HDR transfer functions and metadata
   * will be ignored, contents will be displayed incorrectly, likely with a washed out look.
   *
   * <p>Using this API may lead to codec errors before API 29.
   *
   * <p>Use of this flag may result in {@code ERROR_CODE_DECODING_FORMAT_UNSUPPORTED}.
   *
   * <p>This field is experimental, and will be renamed or removed in a future release.
   */
  public static final int HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR = 3;

  /**
   * The {@link EditedMediaItemSequence} instances to compose.
   *
   * <p>{@link MediaItem} instances from different sequences that are overlapping in time will be
   * mixed in the output.
   */
  public final ImmutableList<EditedMediaItemSequence> sequences;

  /** The {@link VideoCompositorSettings} to apply to the composition. */
  public final VideoCompositorSettings videoCompositorSettings;

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

  /**
   * The {@link HdrMode} specifying how to handle HDR input video.
   *
   * <p>For more information, see {@link Builder#setHdrMode(int)}.
   */
  public final @HdrMode int hdrMode;

  /** Returns a {@link Composition.Builder} initialized with the values of this instance. */
  /* package */ Builder buildUpon() {
    return new Builder(this);
  }

  private Composition(
      List<EditedMediaItemSequence> sequences,
      VideoCompositorSettings videoCompositorSettings,
      Effects effects,
      boolean forceAudioTrack,
      boolean transmuxAudio,
      boolean transmuxVideo,
      @HdrMode int hdrMode) {
    checkArgument(
        !transmuxAudio || !forceAudioTrack,
        "Audio transmuxing and audio track forcing are not allowed together.");
    this.sequences = ImmutableList.copyOf(sequences);
    this.videoCompositorSettings = videoCompositorSettings;
    this.effects = effects;
    this.transmuxAudio = transmuxAudio;
    this.transmuxVideo = transmuxVideo;
    this.forceAudioTrack = forceAudioTrack;
    this.hdrMode = hdrMode;
  }
}
