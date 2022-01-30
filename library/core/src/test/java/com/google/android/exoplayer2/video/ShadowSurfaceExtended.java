package com.google.android.exoplayer2.video;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.Surface;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowSurface;

@Implements(Surface.class)
public class ShadowSurfaceExtended extends ShadowSurface {
  private final Semaphore postSemaphore = new Semaphore(0);
  private int width;
  private int height;

  public static Surface newInstance() {
    return Shadow.newInstanceOf(Surface.class);
  }

  public void setSize(final int width, final int height) {
    this.width = width;
    this.height = height;
  }

  public Canvas lockCanvas(Rect canvas) {
    return new Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
  }

  public void unlockCanvasAndPost(Canvas canvas) {
    postSemaphore.release();
  }

  public boolean waitForPost(long millis) {
    try {
      return postSemaphore.tryAcquire(millis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      return false;
    }
  }
}
