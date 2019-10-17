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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import com.google.android.exoplayer2.util.Assertions;

/** Renders a {@link View} on a quad and supports simulated clicks on the view. */
public final class ViewRenderer {

  private final CanvasRenderer canvasRenderer;
  private final View view;
  private final InternalFrameLayout frameLayout;

  /**
   * @param context A context.
   * @param parentView The parent view.
   * @param view The view to render.
   */
  public ViewRenderer(Context context, ViewGroup parentView, View view) {
    this.canvasRenderer = new CanvasRenderer();
    this.view = view;
    // Wrap the view in an internal view that redirects rendering.
    frameLayout = new InternalFrameLayout(context, view, canvasRenderer);
    canvasRenderer.setSize(frameLayout.getMeasuredWidth(), frameLayout.getMeasuredHeight());
    // The internal view must be added to the parent to ensure proper delivery of UI events.
    parentView.addView(frameLayout);
  }

  /** Finishes constructing this object on the GL Thread. */
  public void init() {
    canvasRenderer.init();
  }

  /**
   * Renders the view as a quad.
   *
   * @param viewProjectionMatrix Array of floats containing the quad's 4x4 perspective matrix in the
   *     {@link android.opengl.Matrix} format.
   */
  public void draw(float[] viewProjectionMatrix) {
    canvasRenderer.draw(viewProjectionMatrix);
  }

  /** Frees GL resources. */
  public void shutdown() {
    canvasRenderer.shutdown();
  }

  /** Returns whether the view is currently visible. */
  @UiThread
  public boolean isVisible() {
    return view.getVisibility() == View.VISIBLE;
  }

  /**
   * Simulates a click on the view.
   *
   * @param action Click action.
   * @param yaw Yaw of the click's orientation in radians.
   * @param pitch Pitch of the click's orientation in radians.
   * @return Whether the click was simulated. If false then the view is not visible or the click was
   *     outside of its bounds.
   */
  @UiThread
  public boolean simulateClick(int action, float yaw, float pitch) {
    if (!isVisible()) {
      return false;
    }
    @Nullable PointF point = canvasRenderer.translateClick(yaw, pitch);
    if (point == null) {
      return false;
    }
    long now = SystemClock.uptimeMillis();
    MotionEvent event = MotionEvent.obtain(now, now, action, point.x, point.y, /* metaState= */ 1);
    frameLayout.dispatchTouchEvent(event);
    return true;
  }

  private static final class InternalFrameLayout extends FrameLayout {

    private final CanvasRenderer canvasRenderer;

    public InternalFrameLayout(Context context, View wrappedView, CanvasRenderer canvasRenderer) {
      super(context);
      this.canvasRenderer = canvasRenderer;
      addView(wrappedView);
      measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      Assertions.checkState(width > 0 && height > 0);
      setLayoutParams(new FrameLayout.LayoutParams(width, height));
    }

    @Override
    public void dispatchDraw(Canvas notUsed) {
      @Nullable Canvas glCanvas = canvasRenderer.lockCanvas();
      if (glCanvas == null) {
        // This happens if Android tries to draw this View before GL initialization completes. We
        // need to retry until the draw call happens after GL invalidation.
        postInvalidate();
        return;
      }

      // Clear the canvas first.
      glCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
      // Have Android render the child views.
      super.dispatchDraw(glCanvas);
      // Commit the changes.
      canvasRenderer.unlockCanvasAndPost(glCanvas);
    }
  }
}
