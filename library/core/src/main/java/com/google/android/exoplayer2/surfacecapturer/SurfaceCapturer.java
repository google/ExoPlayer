/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.surfacecapturer;

import android.graphics.Bitmap;
import android.view.Surface;

/**
 * A surface capturer, which captures image drawn into its surface as bitmaps.
 *
 * <p>It constructs a {@link Surface}, which can be used as the output surface for an image producer
 * to draw images to. As images are being drawn into this surface, this capturer will capture these
 * images, and return them via {@link Callback}. The output images will have a fixed frame size of
 * (width, height), and any image drawn into the surface will be stretched to fit this frame size.
 */
public abstract class SurfaceCapturer {

  /** The callback to be notified of the image capturing result. */
  public interface Callback {

    /**
     * Called when the surface capturer has been able to capture its surface into a {@link Bitmap}.
     * This will happen whenever the producer updates the image on the wrapped surface.
     */
    void onSurfaceCaptured(Bitmap bitmap);

    /** Called when the surface capturer couldn't capture its surface due to an error. */
    void onSurfaceCaptureError(Exception e);
  }

  /** The callback to be notified of the image capturing result. */
  private final Callback callback;
  /** The width of the output images. */
  private final int outputWidth;
  /** The height of the output images. */
  private final int outputHeight;

  /**
   * Constructs a new instance.
   *
   * @param callback See {@link #callback}.
   * @param outputWidth See {@link #outputWidth}.
   * @param outputHeight See {@link #outputHeight}.
   */
  protected SurfaceCapturer(Callback callback, int outputWidth, int outputHeight) {
    this.callback = callback;
    this.outputWidth = outputWidth;
    this.outputHeight = outputHeight;
  }

  /** Returns the callback to be notified of the image capturing result. */
  protected Callback getCallback() {
    return callback;
  }

  /** Returns the width of the output images. */
  public int getOutputWidth() {
    return outputWidth;
  }

  /** Returns the height of the output images. */
  public int getOutputHeight() {
    return outputHeight;
  }

  /** Returns a {@link Surface} that image producers (camera, video codec etc...) can draw to. */
  public abstract Surface getSurface();

  /** Releases all kept resources. This instance cannot be used after this call. */
  public abstract void release();
}
