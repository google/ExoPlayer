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
import static androidx.media3.common.util.Assertions.checkState;

import androidx.annotation.IntRange;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.extractor.mp4.Mp4Extractor;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** A {@link MediaItem} with the transformations to apply to it. */
@UnstableApi
public final class EditedMediaItem {

  /** A builder for {@link EditedMediaItem} instances. */
  public static final class Builder {

    private MediaItem mediaItem;
    private boolean removeAudio;
    private boolean removeVideo;
    private boolean flattenForSlowMotion;
    private long durationUs;
    private int frameRate;
    private Effects effects;

    /**
     * Creates an instance.
     *
     * <p>For image inputs, the values passed into {@link #setRemoveAudio}, {@link #setRemoveVideo}
     * and {@link #setFlattenForSlowMotion} will be ignored. For multi-picture formats (e.g. gifs),
     * a single image frame from the container is displayed if the {@link DefaultAssetLoaderFactory}
     * is used.
     *
     * @param mediaItem The {@link MediaItem} on which transformations are applied.
     */
    public Builder(MediaItem mediaItem) {
      this.mediaItem = mediaItem;
      durationUs = C.TIME_UNSET;
      frameRate = C.RATE_UNSET_INT;
      effects = Effects.EMPTY;
    }

    private Builder(EditedMediaItem editedMediaItem) {
      this.mediaItem = editedMediaItem.mediaItem;
      this.removeAudio = editedMediaItem.removeAudio;
      this.removeVideo = editedMediaItem.removeVideo;
      this.flattenForSlowMotion = editedMediaItem.flattenForSlowMotion;
      this.durationUs = editedMediaItem.durationUs;
      this.frameRate = editedMediaItem.frameRate;
      this.effects = editedMediaItem.effects;
    }

    /**
     * Sets whether to remove the audio from the {@link MediaItem}.
     *
     * <p>The default value is {@code false}.
     *
     * <p>The audio and video cannot both be removed because the output would not contain any
     * samples.
     *
     * @param removeAudio Whether to remove the audio.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setRemoveAudio(boolean removeAudio) {
      this.removeAudio = removeAudio;
      return this;
    }

    /**
     * Sets whether to remove the video from the {@link MediaItem}.
     *
     * <p>The default value is {@code false}.
     *
     * <p>The audio and video cannot both be removed because the output would not contain any
     * samples.
     *
     * @param removeVideo Whether to remove the video.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setRemoveVideo(boolean removeVideo) {
      this.removeVideo = removeVideo;
      return this;
    }

    /**
     * Sets whether to flatten the {@link MediaItem} if it contains slow motion markers.
     *
     * <p>The default value is {@code false}.
     *
     * <p>See {@link #flattenForSlowMotion} for more information about slow motion flattening.
     *
     * <p>If using an {@link ExoPlayerAssetLoader.Factory} with a provided {@link
     * MediaSource.Factory}, make sure that {@link Mp4Extractor#FLAG_READ_SEF_DATA} is set on the
     * {@link Mp4Extractor} used. Otherwise, the slow motion metadata will be ignored and the input
     * won't be flattened.
     *
     * <p>Slow motion flattening is only supported when the {@link Composition} contains exactly one
     * {@link MediaItem}.
     *
     * <p>Using slow motion flattening together with {@link MediaItem.ClippingConfiguration} is not
     * supported yet.
     *
     * @param flattenForSlowMotion Whether to flatten for slow motion.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      // TODO(b/233986762): Support clipping with SEF flattening.
      checkArgument(
          mediaItem.clippingConfiguration.equals(MediaItem.ClippingConfiguration.UNSET)
              || !flattenForSlowMotion,
          "Slow motion flattening is not supported when clipping is requested");
      this.flattenForSlowMotion = flattenForSlowMotion;
      return this;
    }

    /**
     * Sets the duration of the output video in microseconds.
     *
     * <p>For an input that doesn't have an intrinsic duration (e.g. images), this should be the
     * desired presentation duration. Otherwise, this should be the duration of the full content
     * that the {@linkplain MediaItem.LocalConfiguration#uri media URI} resolves to, before {@link
     * MediaItem#clippingConfiguration} is applied.
     *
     * <p>No duration is set by default.
     */
    @CanIgnoreReturnValue
    public Builder setDurationUs(@IntRange(from = 1) long durationUs) {
      checkArgument(durationUs > 0);
      this.durationUs = durationUs;
      return this;
    }

    /**
     * Sets the frame rate of the output video in frames per second.
     *
     * <p>This should be set for inputs that don't have an implicit frame rate (e.g. images). It
     * will be ignored for inputs that do have an implicit frame rate (e.g. video).
     *
     * <p>No frame rate is set by default.
     */
    // TODO(b/210593170): Remove/deprecate frameRate parameter when frameRate parameter is added to
    //     transformer.
    @CanIgnoreReturnValue
    public Builder setFrameRate(@IntRange(from = 0) int frameRate) {
      checkArgument(frameRate > 0);
      this.frameRate = frameRate;
      return this;
    }

    /**
     * Sets the {@link Effects} to apply to the {@link MediaItem}.
     *
     * <p>Callers should not interact with underlying {@link Effects#audioProcessors}.
     *
     * <p>The default value is {@link Effects#EMPTY}.
     *
     * @param effects The {@link Effects} to apply.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setEffects(Effects effects) {
      this.effects = effects;
      return this;
    }

    /** Builds an {@link EditedMediaItem} instance. */
    public EditedMediaItem build() {
      return new EditedMediaItem(
          mediaItem,
          removeAudio,
          removeVideo,
          flattenForSlowMotion,
          durationUs,
          frameRate,
          effects);
    }

    /**
     * Sets the {@link MediaItem} on which transformations are applied.
     *
     * @param mediaItem The {@link MediaItem}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    /* package */ Builder setMediaItem(MediaItem mediaItem) {
      this.mediaItem = mediaItem;
      return this;
    }
  }

  /** The {@link MediaItem} on which transformations are applied. */
  public final MediaItem mediaItem;

  /** Whether to remove the audio from the {@link #mediaItem}. */
  public final boolean removeAudio;

  /** Whether to remove the video from the {@link #mediaItem}. */
  public final boolean removeVideo;

  /**
   * Whether to flatten the {@link #mediaItem} if it contains slow motion markers.
   *
   * <p>The flattened output is obtained by removing the slow motion metadata and by actually
   * slowing down the parts of the video and audio streams defined in this metadata.
   *
   * <p>Only Samsung Extension Format (SEF) slow motion metadata type is supported. Flattening has
   * no effect if the input does not contain this metadata type.
   *
   * <p>For SEF slow motion media, the following assumptions are made on the input:
   *
   * <ul>
   *   <li>The input container format is (unfragmented) MP4.
   *   <li>The input contains an AVC video elementary stream with temporal SVC.
   *   <li>The recording frame rate of the video is 120 or 240 fps.
   * </ul>
   */
  public final boolean flattenForSlowMotion;

  /**
   * The duration of the image in the output video for image {@link MediaItem}, or the media
   * duration for other types of {@link MediaItem}, in microseconds.
   */
  // TODO - b/309767764: Consider merging with presentationDurationUs.
  public final long durationUs;

  /** The frame rate of the image in the output video, in frames per second. */
  @IntRange(from = 1)
  public final int frameRate;

  /** The {@link Effects} to apply to the {@link #mediaItem}. */
  public final Effects effects;

  /** The duration for which this {@code EditedMediaItem} should be presented, in microseconds. */
  private long presentationDurationUs;

  private EditedMediaItem(
      MediaItem mediaItem,
      boolean removeAudio,
      boolean removeVideo,
      boolean flattenForSlowMotion,
      long durationUs,
      int frameRate,
      Effects effects) {
    checkState(!removeAudio || !removeVideo, "Audio and video cannot both be removed");
    this.mediaItem = mediaItem;
    this.removeAudio = removeAudio;
    this.removeVideo = removeVideo;
    this.flattenForSlowMotion = flattenForSlowMotion;
    this.durationUs = durationUs;
    this.frameRate = frameRate;
    this.effects = effects;
    presentationDurationUs = C.TIME_UNSET;
  }

  /** Returns a {@link Builder} initialized with the values of this instance. */
  /* package */ Builder buildUpon() {
    return new Builder(this);
  }

  /* package */ long getPresentationDurationUs() {
    if (presentationDurationUs == C.TIME_UNSET) {
      if (mediaItem.clippingConfiguration.equals(MediaItem.ClippingConfiguration.UNSET)
          || durationUs == C.TIME_UNSET) {
        // TODO - b/290734981: Use presentationDurationUs for image presentation
        presentationDurationUs = durationUs;
      } else {
        MediaItem.ClippingConfiguration clippingConfiguration = mediaItem.clippingConfiguration;
        checkArgument(!clippingConfiguration.relativeToDefaultPosition);
        if (clippingConfiguration.endPositionUs == C.TIME_END_OF_SOURCE) {
          presentationDurationUs = durationUs - clippingConfiguration.startPositionUs;
        } else {
          checkArgument(clippingConfiguration.endPositionUs <= durationUs);
          presentationDurationUs =
              clippingConfiguration.endPositionUs - clippingConfiguration.startPositionUs;
        }
      }
    }
    return presentationDurationUs;
  }
}
