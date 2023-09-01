package androidx.media3.effect;

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
import static androidx.media3.common.util.Assertions.checkArgument;

import android.util.Pair;
import androidx.annotation.FloatRange;
import androidx.media3.common.util.UnstableApi;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Contains information to control how an input texture (for example, a {@link VideoCompositor} or
 * {@link TextureOverlay}) is displayed on a background.
 */
@UnstableApi
public final class OverlaySettings {
  public final boolean useHdr;
  public final float alphaScale;
  public final Pair<Float, Float> backgroundFrameAnchor;
  public final Pair<Float, Float> overlayFrameAnchor;
  public final Pair<Float, Float> scale;
  public final float rotationDegrees;

  private OverlaySettings(
      boolean useHdr,
      float alphaScale,
      Pair<Float, Float> backgroundFrameAnchor,
      Pair<Float, Float> overlayFrameAnchor,
      Pair<Float, Float> scale,
      float rotationDegrees) {
    this.useHdr = useHdr;
    this.alphaScale = alphaScale;
    this.backgroundFrameAnchor = backgroundFrameAnchor;
    this.overlayFrameAnchor = overlayFrameAnchor;
    this.scale = scale;
    this.rotationDegrees = rotationDegrees;
  }

  /** Returns a new {@link Builder} initialized with the values of this instance. */
  /* package */ Builder buildUpon() {
    return new Builder(this);
  }

  /** A builder for {@link OverlaySettings} instances. */
  public static final class Builder {
    private boolean useHdr;
    private float alphaScale;
    private Pair<Float, Float> backgroundFrameAnchor;
    private Pair<Float, Float> overlayFrameAnchor;
    private Pair<Float, Float> scale;
    private float rotationDegrees;

    /** Creates a new {@link Builder}. */
    public Builder() {
      alphaScale = 1f;
      backgroundFrameAnchor = Pair.create(0f, 0f);
      overlayFrameAnchor = Pair.create(0f, 0f);
      scale = Pair.create(1f, 1f);
      rotationDegrees = 0f;
    }

    private Builder(OverlaySettings overlaySettings) {
      this.useHdr = overlaySettings.useHdr;
      this.alphaScale = overlaySettings.alphaScale;
      this.backgroundFrameAnchor = overlaySettings.backgroundFrameAnchor;
      this.overlayFrameAnchor = overlaySettings.overlayFrameAnchor;
      this.scale = overlaySettings.scale;
      this.rotationDegrees = overlaySettings.rotationDegrees;
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
     * <p>An {@code alphaScale} value of {@code 1} means no change is applied. A value below {@code
     * 1} increases translucency, and a value above {@code 1} reduces translucency.
     *
     * <p>Set to always return {@code 1} by default.
     */
    @CanIgnoreReturnValue
    public Builder setAlphaScale(@FloatRange(from = 0) float alphaScale) {
      checkArgument(0 <= alphaScale, "alphaScale needs to be greater than or equal to zero.");
      this.alphaScale = alphaScale;
      return this;
    }

    /**
     * Sets the coordinates for the anchor point of the overlay within the background frame.
     *
     * <p>The coordinates are specified in Normalised Device Coordinates (NDCs) relative to the
     * background frame. Set to always return {@code (0,0)} (the center of the background frame) by
     * default.
     *
     * <p>For example, a value of {@code (+1,+1)} will move the overlay frames's {@linkplain
     * #setOverlayFrameAnchor anchor point} to the top right corner of the background frame.
     *
     * @param x The NDC x-coordinate in the range [-1, 1].
     * @param y The NDC y-coordinate in the range [-1, 1].
     */
    @CanIgnoreReturnValue
    public Builder setBackgroundFrameAnchor(
        @FloatRange(from = -1, to = 1) float x, @FloatRange(from = -1, to = 1) float y) {
      checkArgument(-1 <= x && x <= 1);
      checkArgument(-1 <= y && y <= 1);
      this.backgroundFrameAnchor = Pair.create(x, y);
      return this;
    }

    /**
     * Sets the coordinates for the anchor point of the overlay frame.
     *
     * <p>The anchor point is the point inside the overlay frame that is placed on the {@linkplain
     * #setBackgroundFrameAnchor background frame anchor}
     *
     * <p>The coordinates are specified in Normalised Device Coordinates (NDCs) relative to the
     * overlay frame. Set to return {@code (0,0)} (the center of the overlay frame) by default.
     *
     * <p>For example, a value of {@code (+1,-1)} will result in the overlay frame being positioned
     * with its bottom right corner positioned at the background frame anchor.
     *
     * @param x The NDC x-coordinate in the range [-1, 1].
     * @param y The NDC y-coordinate in the range [-1, 1].
     */
    @CanIgnoreReturnValue
    public Builder setOverlayFrameAnchor(
        @FloatRange(from = -1, to = 1) float x, @FloatRange(from = -1, to = 1) float y) {
      checkArgument(-1 <= x && x <= 1);
      checkArgument(-1 <= y && y <= 1);
      this.overlayFrameAnchor = Pair.create(x, y);
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
          useHdr, alphaScale, backgroundFrameAnchor, overlayFrameAnchor, scale, rotationDegrees);
    }
  }
}
