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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.view.Surface;
import androidx.annotation.Nullable;

/** Immutable value class for a {@link Surface} and supporting information. */
/* package */ final class SurfaceInfo {

  /** The {@link Surface}. */
  public final Surface surface;
  /** The width of frames rendered to the {@link #surface}, in pixels. */
  public final int width;
  /** The height of frames rendered to the {@link #surface}, in pixels. */
  public final int height;
  /**
   * A counter-clockwise rotation to apply to frames before rendering them to the {@link #surface}.
   *
   * <p>Must be 0, 90, 180, or 270 degrees. Default is 0.
   */
  public final int orientationDegrees;

  /** Creates a new instance. */
  public SurfaceInfo(Surface surface, int width, int height) {
    this(surface, width, height, /* orientationDegrees= */ 0);
  }

  /** Creates a new instance. */
  public SurfaceInfo(Surface surface, int width, int height, int orientationDegrees) {
    checkArgument(
        orientationDegrees == 0
            || orientationDegrees == 90
            || orientationDegrees == 180
            || orientationDegrees == 270,
        "orientationDegrees must be 0, 90, 180, or 270");
    this.surface = surface;
    this.width = width;
    this.height = height;
    this.orientationDegrees = orientationDegrees;
  }

  /** A provider for a {@link SurfaceInfo} instance. */
  public interface Provider {
    /**
     * Provides a {@linkplain SurfaceInfo surface} for the requested dimensions.
     *
     * <p>The dimensions given in the provided {@link SurfaceInfo} may differ from the requested
     * dimensions. It is up to the caller to transform frames from the requested dimensions to the
     * provided dimensions before rendering them to the {@link SurfaceInfo#surface}.
     */
    @Nullable
    SurfaceInfo getSurfaceInfo(int requestedWidth, int requestedHeight);
  }
}
