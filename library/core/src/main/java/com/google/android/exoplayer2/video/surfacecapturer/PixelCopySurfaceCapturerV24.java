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
package com.google.android.exoplayer2.video.surfacecapturer;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.view.PixelCopy;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.util.EGLSurfaceTexture;

/**
 * A {@link SurfaceCapturer} implementation that uses {@link PixelCopy} APIs to perform image copy
 * from a {@link SurfaceTexture} into a {@link Bitmap}.
 */
@RequiresApi(24)
/* package */ final class PixelCopySurfaceCapturerV24 extends SurfaceCapturer
    implements EGLSurfaceTexture.TextureImageListener, PixelCopy.OnPixelCopyFinishedListener {

  /** Exception to be thrown if there is some problem capturing images from the surface. */
  public static final class SurfaceCapturerException extends Exception {

    /**
     * One of the {@link PixelCopy} {@code ERROR_*} values return from the {@link
     * PixelCopy#request(Surface, Bitmap, PixelCopy.OnPixelCopyFinishedListener, Handler)}
     */
    public final int errorCode;

    /**
     * Constructs a new instance.
     *
     * @param message The error message.
     * @param errorCode The error code.
     */
    public SurfaceCapturerException(String message, int errorCode) {
      super(message);
      this.errorCode = errorCode;
    }
  }

  private final EGLSurfaceTexture eglSurfaceTexture;
  private final Handler handler;
  private final Surface decoderSurface;

  @Nullable private Bitmap bitmap;

  @SuppressWarnings("nullness")
  /* package */ PixelCopySurfaceCapturerV24(
      Callback callback, int outputWidth, int outputHeight, Handler imageRenderingHandler) {
    super(callback, outputWidth, outputHeight);
    this.handler = imageRenderingHandler;
    eglSurfaceTexture = new EGLSurfaceTexture(imageRenderingHandler, /* callback= */ this);
    eglSurfaceTexture.init(EGLSurfaceTexture.SECURE_MODE_NONE);
    decoderSurface = new Surface(eglSurfaceTexture.getSurfaceTexture());
  }

  @Override
  public Surface getSurface() {
    return decoderSurface;
  }

  @Override
  public void release() {
    eglSurfaceTexture.release();
    decoderSurface.release();
  }

  /** @see SurfaceTexture#setDefaultBufferSize(int, int) */
  public void setDefaultSurfaceTextureBufferSize(int width, int height) {
    eglSurfaceTexture.getSurfaceTexture().setDefaultBufferSize(width, height);
  }

  // TextureImageListener

  @Override
  public void onFrameAvailable() {
    bitmap = Bitmap.createBitmap(getOutputWidth(), getOutputHeight(), Bitmap.Config.ARGB_8888);
    PixelCopy.request(decoderSurface, bitmap, this, handler);
  }

  // OnPixelCopyFinishedListener

  @Override
  public void onPixelCopyFinished(int copyResult) {
    Callback callback = getCallback();
    if (copyResult == PixelCopy.SUCCESS && bitmap != null) {
      callback.onSurfaceCaptured(bitmap);
    } else {
      callback.onSurfaceCaptureError(
          new SurfaceCapturerException("Couldn't copy image from surface", copyResult));
    }
  }
}
