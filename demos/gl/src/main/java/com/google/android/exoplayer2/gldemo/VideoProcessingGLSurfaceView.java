/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.gldemo;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.TimedValueQueue;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

/**
 * {@link GLSurfaceView} that creates a GL context (optionally for protected content) and passes
 * video frames to a {@link VideoProcessor} for drawing to the view.
 *
 * <p>This view must be created programmatically, as it is necessary to specify whether a context
 * supporting protected content should be created at construction time.
 */
public final class VideoProcessingGLSurfaceView extends GLSurfaceView {

  /** Processes video frames, provided via a GL texture. */
  public interface VideoProcessor {
    /** Performs any required GL initialization. */
    void initialize();

    /** Sets the size of the output surface in pixels. */
    void setSurfaceSize(int width, int height);

    /**
     * Draws using GL operations.
     *
     * @param frameTexture The ID of a GL texture containing a video frame.
     * @param frameTimestampUs The presentation timestamp of the frame, in microseconds.
     * @param transformMatrix The 4 * 4 transform matrix to be applied to the texture.
     */
    void draw(int frameTexture, long frameTimestampUs, float[] transformMatrix);

    /** Releases any resources associated with this {@link VideoProcessor}. */
    void release();
  }

  private static final int EGL_PROTECTED_CONTENT_EXT = 0x32C0;
  private static final String TAG = "VPGlSurfaceView";

  private final VideoRenderer renderer;
  private final Handler mainHandler;

  @Nullable private SurfaceTexture surfaceTexture;
  @Nullable private Surface surface;
  @Nullable private ExoPlayer player;

  /**
   * Creates a new instance. Pass {@code true} for {@code requireSecureContext} if the {@link
   * GLSurfaceView GLSurfaceView's} associated GL context should handle secure content (if the
   * device supports it).
   *
   * @param context The {@link Context}.
   * @param requireSecureContext Whether a GL context supporting protected content should be
   *     created, if supported by the device.
   * @param videoProcessor Processor that draws to the view.
   */
  @SuppressWarnings("InlinedApi")
  public VideoProcessingGLSurfaceView(
      Context context, boolean requireSecureContext, VideoProcessor videoProcessor) {
    super(context);
    renderer = new VideoRenderer(videoProcessor);
    mainHandler = new Handler();
    setEGLContextClientVersion(2);
    setEGLConfigChooser(
        /* redSize= */ 8,
        /* greenSize= */ 8,
        /* blueSize= */ 8,
        /* alphaSize= */ 8,
        /* depthSize= */ 0,
        /* stencilSize= */ 0);
    setEGLContextFactory(
        new EGLContextFactory() {
          @Override
          public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
            int[] glAttributes;
            if (requireSecureContext) {
              glAttributes =
                  new int[] {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION,
                    2,
                    EGL_PROTECTED_CONTENT_EXT,
                    EGL14.EGL_TRUE,
                    EGL14.EGL_NONE
                  };
            } else {
              glAttributes = new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            }
            return egl.eglCreateContext(
                display, eglConfig, /* share_context= */ EGL10.EGL_NO_CONTEXT, glAttributes);
          }

          @Override
          public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);
          }
        });
    setEGLWindowSurfaceFactory(
        new EGLWindowSurfaceFactory() {
          @Override
          public EGLSurface createWindowSurface(
              EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow) {
            int[] attribsList =
                requireSecureContext
                    ? new int[] {EGL_PROTECTED_CONTENT_EXT, EGL14.EGL_TRUE, EGL10.EGL_NONE}
                    : new int[] {EGL10.EGL_NONE};
            return egl.eglCreateWindowSurface(display, config, nativeWindow, attribsList);
          }

          @Override
          public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
            egl.eglDestroySurface(display, surface);
          }
        });
    setRenderer(renderer);
    setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
  }

  /**
   * Attaches or detaches (if {@code player} is {@code null}) this view from the player.
   *
   * @param player The new player, or {@code null} to detach this view.
   */
  public void setPlayer(@Nullable ExoPlayer player) {
    if (player == this.player) {
      return;
    }
    if (this.player != null) {
      if (surface != null) {
        this.player.clearVideoSurface(surface);
      }
      this.player.clearVideoFrameMetadataListener(renderer);
    }
    this.player = player;
    if (this.player != null) {
      this.player.setVideoFrameMetadataListener(renderer);
      this.player.setVideoSurface(surface);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    // Post to make sure we occur in order with any onSurfaceTextureAvailable calls.
    mainHandler.post(
        () -> {
          if (surface != null) {
            if (player != null) {
              player.setVideoSurface(null);
            }
            releaseSurface(surfaceTexture, surface);
            surfaceTexture = null;
            surface = null;
          }
        });
  }

  private void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture) {
    mainHandler.post(
        () -> {
          SurfaceTexture oldSurfaceTexture = this.surfaceTexture;
          Surface oldSurface = VideoProcessingGLSurfaceView.this.surface;
          this.surfaceTexture = surfaceTexture;
          this.surface = new Surface(surfaceTexture);
          releaseSurface(oldSurfaceTexture, oldSurface);
          if (player != null) {
            player.setVideoSurface(surface);
          }
        });
  }

  private static void releaseSurface(
      @Nullable SurfaceTexture oldSurfaceTexture, @Nullable Surface oldSurface) {
    if (oldSurfaceTexture != null) {
      oldSurfaceTexture.release();
    }
    if (oldSurface != null) {
      oldSurface.release();
    }
  }

  private final class VideoRenderer implements GLSurfaceView.Renderer, VideoFrameMetadataListener {

    private final VideoProcessor videoProcessor;
    private final AtomicBoolean frameAvailable;
    private final TimedValueQueue<Long> sampleTimestampQueue;
    private final float[] transformMatrix;

    private int texture;
    @Nullable private SurfaceTexture surfaceTexture;

    private boolean initialized;
    private int width;
    private int height;
    private long frameTimestampUs;

    public VideoRenderer(VideoProcessor videoProcessor) {
      this.videoProcessor = videoProcessor;
      frameAvailable = new AtomicBoolean();
      sampleTimestampQueue = new TimedValueQueue<>();
      width = -1;
      height = -1;
      frameTimestampUs = C.TIME_UNSET;
      transformMatrix = new float[16];
    }

    @Override
    public synchronized void onSurfaceCreated(GL10 gl, EGLConfig config) {
      try {
        texture = GlUtil.createExternalTexture();
      } catch (GlUtil.GlException e) {
        Log.e(TAG, "Failed to create an external texture", e);
      }
      surfaceTexture = new SurfaceTexture(texture);
      surfaceTexture.setOnFrameAvailableListener(
          surfaceTexture -> {
            frameAvailable.set(true);
            requestRender();
          });
      onSurfaceTextureAvailable(surfaceTexture);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
      GLES20.glViewport(0, 0, width, height);
      this.width = width;
      this.height = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
      if (videoProcessor == null) {
        return;
      }

      if (!initialized) {
        videoProcessor.initialize();
        initialized = true;
      }

      if (width != -1 && height != -1) {
        videoProcessor.setSurfaceSize(width, height);
        width = -1;
        height = -1;
      }

      if (frameAvailable.compareAndSet(true, false)) {
        SurfaceTexture surfaceTexture = Assertions.checkNotNull(this.surfaceTexture);
        surfaceTexture.updateTexImage();
        long lastFrameTimestampNs = surfaceTexture.getTimestamp();
        @Nullable Long frameTimestampUs = sampleTimestampQueue.poll(lastFrameTimestampNs);
        if (frameTimestampUs != null) {
          this.frameTimestampUs = frameTimestampUs;
        }
        surfaceTexture.getTransformMatrix(transformMatrix);
      }

      videoProcessor.draw(texture, frameTimestampUs, transformMatrix);
    }

    @Override
    public void onVideoFrameAboutToBeRendered(
        long presentationTimeUs,
        long releaseTimeNs,
        Format format,
        @Nullable MediaFormat mediaFormat) {
      sampleTimestampQueue.add(releaseTimeNs, presentationTimeUs);
    }
  }
}
