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
import static com.google.android.exoplayer2.util.Assertions.checkState;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaItem} with the transformations to apply to it. */
public final class EditedMediaItem {

  /** A builder for {@link EditedMediaItem} instances. */
  public static final class Builder {

    private final MediaItem mediaItem;

    private boolean removeAudio;
    private boolean removeVideo;
    private boolean flattenForSlowMotion;
    private @MonotonicNonNull Effects effects;

    /**
     * Creates an instance.
     *
     * @param mediaItem The {@link MediaItem} on which transformations are applied.
     */
    public Builder(MediaItem mediaItem) {
      this.mediaItem = mediaItem;
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
     * Sets the {@link Effects} to apply to the {@link MediaItem}.
     *
     * <p>The default value is an empty {@link Effects} instance.
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
      if (effects == null) {
        effects =
            new Effects(
                /* audioProcessors= */ ImmutableList.of(), /* videoEffects= */ ImmutableList.of());
      }
      return new EditedMediaItem(
          mediaItem, removeAudio, removeVideo, flattenForSlowMotion, effects);
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
  /** The {@link Effects} to apply to the {@link #mediaItem}. */
  public final Effects effects;

  private EditedMediaItem(
      MediaItem mediaItem,
      boolean removeAudio,
      boolean removeVideo,
      boolean flattenForSlowMotion,
      Effects effects) {
    checkState(!removeAudio || !removeVideo, "Audio and video cannot both be removed");
    this.mediaItem = mediaItem;
    this.removeAudio = removeAudio;
    this.removeVideo = removeVideo;
    this.flattenForSlowMotion = flattenForSlowMotion;
    this.effects = effects;
  }
}
