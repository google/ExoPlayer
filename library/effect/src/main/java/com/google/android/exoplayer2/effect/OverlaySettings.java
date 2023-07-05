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
import com.google.android.exoplayer2.util.GlUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Contains information to control how an {@link TextureOverlay} is displayed on the screen.
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
  public final float[] matrix;
  public final Pair<Float, Float> anchor;

  private OverlaySettings(boolean useHdr, float alpha, float[] matrix, Pair<Float, Float> anchor) {
    this.useHdr = useHdr;
    this.alpha = alpha;
    this.matrix = matrix;
    this.anchor = anchor;
  }

  /** A builder for {@link OverlaySettings} instances. */
  public static final class Builder {
    private boolean useHdr;
    private float alpha;
    private float[] matrix;
    private Pair<Float, Float> anchor;

    /** Creates a new {@link Builder}. */
    public Builder() {
      alpha = 1f;
      matrix = GlUtil.create4x4IdentityMatrix();
      anchor = Pair.create(0f, 0f);
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
     * Sets the {@link android.opengl.Matrix} used to transform the overlay before applying it to a
     * frame.
     *
     * <p>Set to always return the identity matrix by default.
     */
    @CanIgnoreReturnValue
    public Builder setMatrix(float[] matrix) {
      this.matrix = matrix;
      return this;
    }

    /**
     * Sets the alpha value of the overlay, altering its transparency.
     *
     * <p>Alpha values range from 0 (all transparent) to 1 (completely opaque).
     *
     * <p>Set to always return {@code 1} by default.
     */
    @CanIgnoreReturnValue
    public Builder setAlpha(@FloatRange(from = 0, to = 1) float alpha) {
      checkArgument(0 <= alpha && alpha <= 1, "Alpha needs to be in the interval [0, 1].");
      this.alpha = alpha;
      return this;
    }

    /**
     * Sets the coordinates for the anchor point of the overlay.
     *
     * <p>The anchor point is the point inside the overlay that the overlay is positioned from.
     *
     * <p>The coordinates are specified in Normalised Device Coordinates (NDCs). Set to always
     * return {@code (0,0)} (the center) by default.
     *
     * @param x The NDC x-coordinate in the range [-1, 1].
     * @param y The NDC y-coordinate in the range [-1, 1].
     */
    @CanIgnoreReturnValue
    public Builder setAnchor(
        @FloatRange(from = -1, to = 1) float x, @FloatRange(from = -1, to = 1) float y) {
      checkArgument(-1 <= x && x <= 1);
      checkArgument(-1 <= y && y <= 1);
      this.anchor = Pair.create(x, y);
      return this;
    }

    /** Creates an instance of {@link OverlaySettings}, using defaults if values are unset. */
    public OverlaySettings build() {
      return new OverlaySettings(useHdr, alpha, matrix, anchor);
    }
  }
}
