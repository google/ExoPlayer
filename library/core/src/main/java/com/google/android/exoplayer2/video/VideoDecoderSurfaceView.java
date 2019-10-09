/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import androidx.annotation.Nullable;

/** A GLSurfaceView extension that scales itself to the given aspect ratio. */
public class VideoDecoderSurfaceView extends GLSurfaceView {

  private final VideoDecoderRenderer renderer;

  /**
   * Creates VideoDecoderSurfaceView.
   *
   * @param context A {@link Context}.
   */
  public VideoDecoderSurfaceView(Context context) {
    this(context, /* attrs= */ null);
  }

  /**
   * Creates VideoDecoderSurfaceView.
   *
   * @param context A {@link Context}.
   * @param attrs Custom attributes.
   */
  public VideoDecoderSurfaceView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    renderer = new VideoDecoderRenderer(this);
    setPreserveEGLContextOnPause(true);
    setEGLContextClientVersion(2);
    setRenderer(renderer);
    setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
  }

  /**
   * Returns the output buffer renderer used.
   *
   * @return {@link VideoDecoderOutputBuffer}.
   */
  public VideoDecoderOutputBufferRenderer getOutputBufferRenderer() {
    return renderer;
  }
}
