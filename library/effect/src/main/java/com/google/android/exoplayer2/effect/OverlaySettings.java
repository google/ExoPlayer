package com.google.android.exoplayer2.effect;

/*
 * Copyright 2022 The Android Open Source Project
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
import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.util.Pair;
import androidx.annotation.FloatRange;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Contains information to control how a {@link TextureOverlay} is displayed on the screen.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class OverlaySettings {
  public final boolean useHdr;
  public final float alpha;
  public final Pair<Float, Float> videoFrameAnchor;
  public final Pair<Float, Float> overlayAnchor;
  public final Pair<Float, Float> scale;
  public final float rotationDegrees;

  private OverlaySettings(
      boolean useHdr,
      float alpha,
      Pair<Float, Float> videoFrameAnchor,
      Pair<Float, Float> overlayAnchor,
      Pair<Float, Float> scale,
      float rotationDegrees) {
    this.useHdr = useHdr;
    this.alpha = alpha;
    this.videoFrameAnchor = videoFrameAnchor;
    this.overlayAnchor = overlayAnchor;
    this.scale = scale;
    this.rotationDegrees = rotationDegrees;
  }

  /** A builder for {@link OverlaySettings} instances. */
  public static final class Builder {
    private boolean useHdr;
    private float alpha;
    private Pair<Float, Float> videoFrameAnchor;
    private Pair<Float, Float> overlayAnchor;
    private Pair<Float, Float> scale;
    private float rotationDegrees;

    /** Creates a new {@link Builder}. */
    public Builder() {
      alpha = 1f;
      videoFrameAnchor = Pair.create(0f, 0f);
      overlayAnchor = Pair.create(0f, 0f);
      scale = Pair.create(1f, 1f);
      rotationDegrees = 0f;
    }

    /**
     * Sets whether input overlay comes from an HDR source. If {@code true}, colors will be in
     * linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
     *
     * <p>Set to {@code false} by default.
     */
    @CanIgnoreReturnValue
    public Builder setUsesHdr(boolean useHdr) {
      this.useHdr = useHdr;
      return this;
    }

    /**
     * Sets the alpha scale value of the overlay, altering its translucency.
     *
     * <p>An {@code alpha} value of {@code 1} means no change is applied. A value below {@code 1}
     * increases translucency, and a value above {@code 1} reduces translucency.
     *
     * <p>Set to always return {@code 1} by default.
     */
    @CanIgnoreReturnValue
    public Builder setAlpha(@FloatRange(from = 0) float alpha) {
      checkArgument(0 <= alpha, "Alpha needs to be more than or equal to zero.");
      this.alpha = alpha;
      return this;
    }

    /**
     * Sets the coordinates for the anchor point of the overlay within the video frame.
     *
     * <p>The coordinates are specified in Normalised Device Coordinates (NDCs) relative to the
     * video frame. Set to always return {@code (0,0)} (the center of the video frame) by default.
     *
     * <p>For example, a value of {@code (+1,+1)} will move the overlay's {@linkplain
     * #setOverlayAnchor anchor point} to the top right corner of the video frame.
     *
     * @param x The NDC x-coordinate in the range [-1, 1].
     * @param y The NDC y-coordinate in the range [-1, 1].
     */
    @CanIgnoreReturnValue
    public Builder setVideoFrameAnchor(
        @FloatRange(from = -1, to = 1) float x, @FloatRange(from = -1, to = 1) float y) {
      checkArgument(-1 <= x && x <= 1);
      checkArgument(-1 <= y && y <= 1);
      this.videoFrameAnchor = Pair.create(x, y);
      return this;
    }

    /**
     * Sets the coordinates for the anchor point of the overlay.
     *
     * <p>The anchor point is the point inside the overlay that is placed on the {@linkplain
     * #setVideoFrameAnchor video frame anchor}
     *
     * <p>The coordinates are specified in Normalised Device Coordinates (NDCs) relative to the
     * overlay frame. Set to return {@code (0,0)} (the center of the overlay) by default.
     *
     * <p>For example, a value of {@code (+1,-1)} will result in the overlay being positioned from
     * the bottom right corner of its frame.
     *
     * @param x The NDC x-coordinate in the range [-1, 1].
     * @param y The NDC y-coordinate in the range [-1, 1].
     */
    @CanIgnoreReturnValue
    public Builder setOverlayAnchor(
        @FloatRange(from = -1, to = 1) float x, @FloatRange(from = -1, to = 1) float y) {
      checkArgument(-1 <= x && x <= 1);
      checkArgument(-1 <= y && y <= 1);
      this.overlayAnchor = Pair.create(x, y);
      return this;
    }

    /**
     * Sets the scaling of the overlay.
     *
     * @param x The desired scaling in the x axis of the overlay.
     * @param y The desired scaling in the y axis of the overlay.
     */
    @CanIgnoreReturnValue
    public Builder setScale(float x, float y) {
      this.scale = Pair.create(x, y);
      return this;
    }

    /**
     * Sets the rotation of the overlay, counter-clockwise.
     *
     * <p>The overlay is rotated at the center of its frame.
     *
     * @param rotationDegree The desired degrees of rotation, counter-clockwise.
     */
    @CanIgnoreReturnValue
    public Builder setRotationDegrees(float rotationDegree) {
      this.rotationDegrees = rotationDegree;
      return this;
    }

    /** Creates an instance of {@link OverlaySettings}, using defaults if values are unset. */
    public OverlaySettings build() {
      return new OverlaySettings(
          useHdr, alpha, videoFrameAnchor, overlayAnchor, scale, rotationDegrees);
    }
  }
}
