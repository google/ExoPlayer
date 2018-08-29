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
package com.google.android.exoplayer2.ui.spherical;

import static com.google.android.exoplayer2.ui.spherical.GlUtil.checkGlError;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.ui.spherical.ProjectionRenderer.EyeType;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.TimedValueQueue;
import com.google.android.exoplayer2.video.spherical.FrameRotationQueue;
import com.google.android.exoplayer2.video.spherical.Projection;
import java.util.concurrent.atomic.AtomicBoolean;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Renders a GL Scene.
 *
 * <p>All methods should be called only on the GL thread unless GL thread is stopped.
 */
/*package*/ final class SceneRenderer {

  private final AtomicBoolean frameAvailable;
  private final ProjectionRenderer projectionRenderer;
  private final FrameRotationQueue frameRotationQueue;
  private final TimedValueQueue<Long> sampleTimestampQueue;
  private final float[] rotationMatrix;
  private final float[] tempMatrix;

  private int textureId;
  private @MonotonicNonNull SurfaceTexture surfaceTexture;
  private @Nullable Projection pendingProjection;
  private long pendingProjectionTimeNs;
  private long lastFrameTimestamp;
  private boolean resetRotationAtNextFrame;

  public SceneRenderer(
      Projection projection,
      FrameRotationQueue frameRotationQueue,
      TimedValueQueue<Long> sampleTimestampQueue) {
    this.frameRotationQueue = frameRotationQueue;
    this.sampleTimestampQueue = sampleTimestampQueue;
    frameAvailable = new AtomicBoolean();
    projectionRenderer = new ProjectionRenderer();
    projectionRenderer.setProjection(projection);
    rotationMatrix = new float[16];
    tempMatrix = new float[16];
    resetRotation();
  }

  /** Initializes the renderer. */
  public SurfaceTexture init() {
    // Set the background frame color. This is only visible if the display mesh isn't a full sphere.
    GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
    checkGlError();

    projectionRenderer.init();
    checkGlError();

    textureId = GlUtil.createExternalTexture();
    surfaceTexture = new SurfaceTexture(textureId);
    surfaceTexture.setOnFrameAvailableListener(surfaceTexture -> frameAvailable.set(true));
    return surfaceTexture;
  }

  public void resetRotation() {
    resetRotationAtNextFrame = true;
  }

  /** Sets a {@link Projection} to be used to display video. */
  public void setProjection(Projection projection, long timeNs) {
    pendingProjection = projection;
    pendingProjectionTimeNs = timeNs;
  }

  /**
   * Draws the scene with a given eye pose and type.
   *
   * @param viewProjectionMatrix 16 element GL matrix.
   * @param eyeType an {@link EyeType} value
   */
  public void drawFrame(float[] viewProjectionMatrix, int eyeType) {
    // glClear isn't strictly necessary when rendering fully spherical panoramas, but it can improve
    // performance on tiled renderers by causing the GPU to discard previous data.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    checkGlError();

    if (frameAvailable.compareAndSet(true, false)) {
      Assertions.checkNotNull(surfaceTexture).updateTexImage();
      checkGlError();
      if (resetRotationAtNextFrame) {
        Matrix.setIdentityM(rotationMatrix, 0);
      }
      lastFrameTimestamp = surfaceTexture.getTimestamp();
      Long sampleTimestamp = sampleTimestampQueue.poll(lastFrameTimestamp);
      if (sampleTimestamp != null) {
        frameRotationQueue.pollRotationMatrix(rotationMatrix, sampleTimestamp);
      }
    }
    if (pendingProjection != null && pendingProjectionTimeNs <= lastFrameTimestamp) {
      projectionRenderer.setProjection(pendingProjection);
      pendingProjection = null;
    }
    Matrix.multiplyMM(tempMatrix, 0, viewProjectionMatrix, 0, rotationMatrix, 0);
    projectionRenderer.draw(textureId, tempMatrix, eyeType);
  }
}
