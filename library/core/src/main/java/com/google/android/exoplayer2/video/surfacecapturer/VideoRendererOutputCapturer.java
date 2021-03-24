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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * A capturer that can capture the output of a video {@link SingleFrameMediaCodecVideoRenderer} into
 * bitmaps.
 *
 * <p>Start by setting the output size via {@link #setOutputSize(int, int)}. The capturer will
 * create a surface and set this up as the output for the video renderer.
 *
 * <p>Once the surface setup is done, the capturer will call {@link Callback#onOutputSizeSet(int,
 * int)}. After this call, the capturer will capture all images rendered by the {@link Renderer},
 * and deliver the captured bitmaps via {@link Callback#onSurfaceCaptured(Bitmap)}, or failure via
 * {@link Callback#onSurfaceCaptureError(Exception)}. You can change the output image size at any
 * time by calling {@link #setOutputSize(int, int)}.
 *
 * <p>When this capturer is no longer needed, you need to call {@link #release()} to release all
 * resources it is holding. After this call returns, no callback will be called anymore.
 */
public final class VideoRendererOutputCapturer implements Handler.Callback {

  /** The callback to be notified of the video image capturing result. */
  public interface Callback extends SurfaceCapturer.Callback {
    /** Called when output surface has been set properly. */
    void onOutputSizeSet(int width, int height);
  }

  private static final int MSG_SET_OUTPUT = 1;
  private static final int MSG_RELEASE = 2;

  private final HandlerThread handlerThread;
  private final Handler handler;
  private final ExoPlayer exoPlayer;
  private final EventDispatcher eventDispatcher;
  private final Renderer renderer;

  @Nullable private SurfaceCapturer surfaceCapturer;

  private volatile boolean released;

  /**
   * Constructs a new instance.
   *
   * @param callback The callback to be notified of image capturing result.
   * @param callbackHandler The {@link Handler} that the callback will be called on.
   * @param videoRenderer A {@link SingleFrameMediaCodecVideoRenderer} that will be used to render
   *     video frames, which this capturer will capture.
   * @param exoPlayer The {@link ExoPlayer} instance that is using the video renderer.
   */
  public VideoRendererOutputCapturer(
      Callback callback,
      Handler callbackHandler,
      SingleFrameMediaCodecVideoRenderer videoRenderer,
      ExoPlayer exoPlayer) {
    this.renderer = Assertions.checkNotNull(videoRenderer);
    this.exoPlayer = Assertions.checkNotNull(exoPlayer);
    this.eventDispatcher = new EventDispatcher(callbackHandler, callback);

    // Use a separate thread to handle all operations in this class, because bitmap copying may take
    // time and should not be handled on callbackHandler (which maybe run on main thread).
    handlerThread = new HandlerThread("ExoPlayer:VideoRendererOutputCapturer");
    handlerThread.start();
    handler = Util.createHandler(handlerThread.getLooper(), /* callback= */ this);
  }

  /**
   * Sets the size of the video renderer surface's with and height.
   *
   * <p>This call is performed asynchronously. Only after the {@code callback} receives a call to
   * {@link Callback#onOutputSizeSet(int, int)}, the output frames will conform to the new size.
   * Output frames before the callback will still conform to previous size.
   *
   * @param width The target width of the output frame.
   * @param height The target height of the output frame.
   */
  public void setOutputSize(int width, int height) {
    handler.obtainMessage(MSG_SET_OUTPUT, width, height).sendToTarget();
  }

  /** Releases all kept resources. This instance cannot be used after this call. */
  public synchronized void release() {
    if (released) {
      return;
    }

    // Some long running or waiting operations may run on the handler thread, so we try to
    // interrupt the thread to end these operations quickly.
    handlerThread.interrupt();
    handler.removeCallbacksAndMessages(null);
    handler.sendEmptyMessage(MSG_RELEASE);
    boolean wasInterrupted = false;
    while (!released) {
      try {
        wait();
      } catch (InterruptedException e) {
        wasInterrupted = true;
      }
    }
    if (wasInterrupted) {
      // Restore the interrupted status.
      Thread.currentThread().interrupt();
    }
  }

  // Handler.Callback

  @Override
  public boolean handleMessage(Message message) {
    switch (message.what) {
      case MSG_SET_OUTPUT:
        handleSetOutput(/* width= */ message.arg1, /* height= */ message.arg2);
        return true;
      case MSG_RELEASE:
        handleRelease();
        return true;
      default:
        return false;
    }
  }

  // Internal methods

  private void handleSetOutput(int width, int height) {
    if (surfaceCapturer == null
        || surfaceCapturer.getOutputWidth() != width
        || surfaceCapturer.getOutputHeight() != height) {
      updateSurfaceCapturer(width, height);
    }
    eventDispatcher.onOutputSizeSet(width, height);
  }

  private void updateSurfaceCapturer(int width, int height) {
    SurfaceCapturer oldSurfaceCapturer = surfaceCapturer;
    if (oldSurfaceCapturer != null) {
      blockingSetRendererSurface(/* surface= */ null);
      oldSurfaceCapturer.release();
    }
    surfaceCapturer = createSurfaceCapturer(width, height);
    blockingSetRendererSurface(surfaceCapturer.getSurface());
  }

  private SurfaceCapturer createSurfaceCapturer(int width, int height) {
    if (Util.SDK_INT >= 24) {
      return createSurfaceCapturerV24(width, height);
    } else {
      // TODO: Use different SurfaceCapturer based on API level, flags etc...
      throw new UnsupportedOperationException(
          "Creating Surface Capturer is not supported for API < 24 yet");
    }
  }

  @RequiresApi(24)
  private SurfaceCapturer createSurfaceCapturerV24(int width, int height) {
    return new PixelCopySurfaceCapturerV24(eventDispatcher, width, height, handler);
  }

  private void blockingSetRendererSurface(@Nullable Surface surface) {
    try {
      exoPlayer
          .createMessage(renderer)
          .setType(Renderer.MSG_SET_SURFACE)
          .setPayload(surface)
          .send()
          .blockUntilDelivered();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void handleRelease() {
    eventDispatcher.release();
    handler.removeCallbacksAndMessages(null);
    if (surfaceCapturer != null) {
      surfaceCapturer.release();
    }
    handlerThread.quit();
    synchronized (this) {
      released = true;
      notifyAll();
    }
  }

  /** Dispatches {@link Callback} events using a callback handler. */
  private static final class EventDispatcher implements Callback {

    private final Handler callbackHandler;
    private final Callback callback;

    private volatile boolean released;

    private EventDispatcher(Handler callbackHandler, Callback callback) {
      this.callbackHandler = callbackHandler;
      this.callback = callback;
    }

    @Override
    public void onOutputSizeSet(int width, int height) {
      callbackHandler.post(
          () -> {
            if (released) {
              return;
            }
            callback.onOutputSizeSet(width, height);
          });
    }

    @Override
    public void onSurfaceCaptured(Bitmap bitmap) {
      callbackHandler.post(
          () -> {
            if (released) {
              return;
            }
            callback.onSurfaceCaptured(bitmap);
          });
    }

    @Override
    public void onSurfaceCaptureError(Exception exception) {
      callbackHandler.post(
          () -> {
            if (released) {
              return;
            }
            callback.onSurfaceCaptureError(exception);
          });
    }

    /** Releases this event dispatcher. No event will be dispatched after this call. */
    public void release() {
      released = true;
    }
  }
}
