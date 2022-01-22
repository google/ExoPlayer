package com.google.android.exoplayer2.video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BitmapFactoryVideoRenderer extends BaseRenderer {
  private static final String TAG = "BitmapFactoryRenderer";
  final VideoRendererEventListener.EventDispatcher eventDispatcher;
  @Nullable
  Surface surface;
  private boolean firstFrameRendered;
  private final Rect rect = new Rect();
  private final Point lastSurface = new Point();
  private VideoSize lastVideoSize = VideoSize.UNKNOWN;
  @Nullable
  private ThreadPoolExecutor renderExecutor;
  @Nullable
  private Thread thread;
  private long currentTimeUs;
  private long nextFrameUs;
  private long frameUs;
  private boolean ended;
  private DecoderCounters decoderCounters;

  public BitmapFactoryVideoRenderer(@Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener) {
    super(C.TRACK_TYPE_VIDEO);
    eventDispatcher = new VideoRendererEventListener.EventDispatcher(eventHandler, eventListener);
  }

  @NonNull
  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    firstFrameRendered = ended = false;
    renderExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(3));
    decoderCounters = new DecoderCounters();
    eventDispatcher.enabled(decoderCounters);
  }

  @Override
  protected void onDisabled() {
    renderExecutor.shutdownNow();
    eventDispatcher.disabled(decoderCounters);
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs)
      throws ExoPlaybackException {
    nextFrameUs = startPositionUs;
    for (final Format format : formats) {
      @NonNull final FormatHolder formatHolder = getFormatHolder();
      @Nullable final Format currentFormat = formatHolder.format;
      if (formatHolder.format == null || !currentFormat.equals(format)) {
        getFormatHolder().format = format;
        eventDispatcher.inputFormatChanged(format, null);
        frameUs = (long)(1_000_000L / format.frameRate);
      }
    }
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    synchronized (eventDispatcher) {
      currentTimeUs = positionUs;
      eventDispatcher.notify();
    }
    if (renderExecutor.getActiveCount() > 0) {
      if (positionUs > nextFrameUs) {
        long us = (positionUs - nextFrameUs) + frameUs;
        long dropped = us / frameUs;
        eventDispatcher.droppedFrames((int)dropped, us);
        nextFrameUs += frameUs * dropped;
      }
      return;
    }
    final FormatHolder formatHolder = getFormatHolder();
    final DecoderInputBuffer decoderInputBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    int result = readSource(formatHolder, decoderInputBuffer, 0);
    if (result == C.RESULT_BUFFER_READ) {
      renderExecutor.execute(new RenderRunnable(decoderInputBuffer, nextFrameUs));
      nextFrameUs += frameUs;
    } else if (result == C.RESULT_END_OF_INPUT) {
      ended = true;
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    nextFrameUs = positionUs;
    @Nullable
    final Thread thread = this.thread;
    if (thread != null) {
      thread.interrupt();
    }
  }

  @Override
  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    if (messageType == MSG_SET_VIDEO_OUTPUT) {
      if (message instanceof Surface) {
        surface = (Surface) message;
      } else {
        surface = null;
      }
    }
    super.handleMessage(messageType, message);
  }

  @Override
  public boolean isReady() {
    return surface != null;
  }

  @Override
  public boolean isEnded() {
    return ended && renderExecutor.getActiveCount() == 0;
  }

  @Override
  public int supportsFormat(Format format) throws ExoPlaybackException {
    //Technically could support any format BitmapFactory supports
    if (MimeTypes.VIDEO_JPEG.equals(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_HANDLED);
    }
    return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
  }

  class RenderRunnable implements Runnable {
    final DecoderInputBuffer decoderInputBuffer;
    final long renderUs;

    RenderRunnable(final DecoderInputBuffer decoderInputBuffer, long renderUs) {
      this.decoderInputBuffer = decoderInputBuffer;
      this.renderUs = renderUs;
    }

    public void run() {
      synchronized (eventDispatcher) {
        while (currentTimeUs < renderUs) {
          try {
            thread = Thread.currentThread();
            eventDispatcher.wait();
          } catch (InterruptedException e) {
            //If we are interrupted, treat as a cancel
            return;
          } finally {
            thread = null;
          }
        }
      }
      @Nullable
      final ByteBuffer byteBuffer = decoderInputBuffer.data;
      @Nullable
      final Surface surface = BitmapFactoryVideoRenderer.this.surface;
      if (byteBuffer != null && surface != null) {
        final Bitmap bitmap;
        try {
          bitmap = BitmapFactory.decodeByteArray(byteBuffer.array(), byteBuffer.arrayOffset(), byteBuffer.arrayOffset() + byteBuffer.position());
        } catch (Exception e) {
          eventDispatcher.videoCodecError(e);
          return;
        }
        if (bitmap == null) {
          eventDispatcher.videoCodecError(new NullPointerException("Decode bytes failed"));
          return;
        }
        //Log.d(TAG, "Drawing: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        final Canvas canvas = surface.lockCanvas(null);

        final Rect clipBounds = canvas.getClipBounds();
        final VideoSize videoSize = new VideoSize(bitmap.getWidth(), bitmap.getHeight());
        final boolean videoSizeChanged;
        if (videoSize.equals(lastVideoSize)) {
          videoSizeChanged = false;
        } else {
          lastVideoSize = videoSize;
          eventDispatcher.videoSizeChanged(videoSize);
          videoSizeChanged = true;
        }
        if (lastSurface.x != clipBounds.width() || lastSurface.y != clipBounds.height() ||
            videoSizeChanged) {
          lastSurface.x = clipBounds.width();
          lastSurface.y = clipBounds.height();
          final float scaleX = lastSurface.x / (float)videoSize.width;
          final float scaleY = lastSurface.y / (float)videoSize.height;
          final float scale = Math.min(scaleX, scaleY);
          final float width = videoSize.width * scale;
          final float height = videoSize.height * scale;
          final int x = (int)(lastSurface.x - width) / 2;
          final int y = (int)(lastSurface.y - height) / 2;
          rect.set(x, y, x + (int)width, y + (int) height);
        }
        canvas.drawBitmap(bitmap, null, rect, null);

        surface.unlockCanvasAndPost(canvas);
        decoderCounters.renderedOutputBufferCount++;
        if (!firstFrameRendered) {
          firstFrameRendered = true;
          eventDispatcher.renderedFirstFrame(surface);
        }
      }
    }
  }
}
