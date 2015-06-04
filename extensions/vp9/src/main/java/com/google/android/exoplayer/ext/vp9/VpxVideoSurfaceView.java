/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.ext.vp9;

import com.google.android.exoplayer.ext.vp9.VpxDecoderWrapper.OutputBuffer;

import android.annotation.TargetApi;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

/**
 * A GLSurfaceView extension that scales itself to the given aspect ratio.
 */
@TargetApi(11)
public class VpxVideoSurfaceView extends GLSurfaceView {

  private static final float MAX_ASPECT_RATIO_DEFORMATION_PERCENT = 0.01f;

  private final VpxRenderer renderer;

  private float videoAspectRatio;

  public VpxVideoSurfaceView(Context context) {
    this(context, null);
  }

  public VpxVideoSurfaceView(Context context, AttributeSet attrs) {
    super(context, attrs);
    renderer = new VpxRenderer();
    setPreserveEGLContextOnPause(true);
    setEGLContextClientVersion(2);
    setRenderer(renderer);
    setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
  }

  public void renderFrame(OutputBuffer outputBuffer) {
    renderer.setFrame(outputBuffer);
    requestRender();
  }

  /**
   * Set the aspect ratio that this view should satisfy.
   *
   * @param widthHeightRatio The width to height ratio.
   */
  public void setVideoWidthHeightRatio(float widthHeightRatio) {
    if (this.videoAspectRatio != widthHeightRatio) {
      this.videoAspectRatio = widthHeightRatio;
      requestLayout();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    if (videoAspectRatio != 0) {
      float viewAspectRatio = (float) width / height;
      float aspectDeformation = videoAspectRatio / viewAspectRatio - 1;
      if (aspectDeformation > MAX_ASPECT_RATIO_DEFORMATION_PERCENT) {
        height = (int) (width / videoAspectRatio);
      } else if (aspectDeformation < -MAX_ASPECT_RATIO_DEFORMATION_PERCENT) {
        width = (int) (height * videoAspectRatio);
      }
    }
    setMeasuredDimension(width, height);
  }

}
