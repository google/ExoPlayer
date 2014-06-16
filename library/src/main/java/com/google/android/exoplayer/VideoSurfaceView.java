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
package com.google.android.exoplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

/**
 * A SurfaceView that resizes itself to match a specified aspect ratio.
 */
public class VideoSurfaceView extends SurfaceView {

  /**
   * The surface view will not resize itself if the fractional difference between its default
   * aspect ratio and the aspect ratio of the video falls below this threshold.
   * <p>
   * This tolerance is useful for fullscreen playbacks, since it ensures that the surface will
   * occupy the whole of the screen when playing content that has the same (or virtually the same)
   * aspect ratio as the device. This typically reduces the number of view layers that need to be
   * composited by the underlying system, which can help to reduce power consumption.
   */
  private static final float MAX_ASPECT_RATIO_DEFORMATION_PERCENT = 0.01f;

  private float videoAspectRatio;

  public VideoSurfaceView(Context context) {
    super(context);
  }

  public VideoSurfaceView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /**
   * Set the aspect ratio that this {@link VideoSurfaceView} should satisfy.
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
