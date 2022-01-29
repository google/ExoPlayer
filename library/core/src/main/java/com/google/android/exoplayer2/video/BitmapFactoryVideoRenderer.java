package com.google.android.exoplayer2.video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
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
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;

public class BitmapFactoryVideoRenderer extends BaseRenderer {
  private static final String TAG = "BitmapFactoryRenderer";
  final VideoRendererEventListener.EventDispatcher eventDispatcher;
  @Nullable
  volatile Surface surface;
  private final Rect rect = new Rect();
  private final Point lastSurface = new Point();
  private final RenderRunnable renderRunnable = new RenderRunnable();
  private final Thread thread = new Thread(renderRunnable, "BitmapFactoryVideoRenderer");
  private VideoSize lastVideoSize = VideoSize.UNKNOWN;
  private long currentTimeUs;
  private long frameUs;
  boolean ended;
  @Nullable
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
    decoderCounters = new DecoderCounters();
    eventDispatcher.enabled(decoderCounters);
    thread.start();
  }

  @Override
  protected void onDisabled() {
    renderRunnable.running = false;
    thread.interrupt();

    @Nullable
    final DecoderCounters decoderCounters = this.decoderCounters;
    if (decoderCounters != null) {
      eventDispatcher.disabled(decoderCounters);
    }
  }

  private void onFormatChanged(@NonNull FormatHolder formatHolder) {
    @Nullable final Format format = formatHolder.format;
    if (format != null) {
      frameUs = (long)(1_000_000L / format.frameRate);
      eventDispatcher.inputFormatChanged(format, null);
    }
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    //Log.d(TAG, "Render: us=" + positionUs);
    synchronized (eventDispatcher) {
      currentTimeUs = positionUs;
      eventDispatcher.notify();
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    thread.interrupt();
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
    return renderRunnable.ended;
  }

  @Override
  public int supportsFormat(Format format) throws ExoPlaybackException {
    //Technically could support any format BitmapFactory supports
    if (MimeTypes.VIDEO_MJPEG.equals(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_HANDLED);
    }
    return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
  }

  class RenderRunnable implements Runnable {
    private volatile boolean ended;
    private boolean firstFrameRendered;
    private volatile boolean running = true;

    @Nullable
    private Bitmap decodeInputBuffer(final DecoderInputBuffer decoderInputBuffer) {
      @Nullable final ByteBuffer byteBuffer = decoderInputBuffer.data;
      if (byteBuffer != null) {
        final Bitmap bitmap;
        try {
          bitmap = BitmapFactory.decodeByteArray(byteBuffer.array(), byteBuffer.arrayOffset(),
              byteBuffer.arrayOffset() + byteBuffer.position());
          if (bitmap == null) {
            eventDispatcher.videoCodecError(new NullPointerException("Decode bytes failed"));
          } else {
            return bitmap;
          }
        } catch (Exception e) {
          eventDispatcher.videoCodecError(e);
        }
      }
      return null;
    }

    private void renderBitmap(final Bitmap bitmap, @Nullable final Surface surface) {
      if (surface == null) {
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
      @Nullable
      final DecoderCounters decoderCounters = BitmapFactoryVideoRenderer.this.decoderCounters;
      if (decoderCounters != null) {
        decoderCounters.renderedOutputBufferCount++;
      }
      if (!firstFrameRendered) {
        firstFrameRendered = true;
        eventDispatcher.renderedFirstFrame(surface);
      }
    }

    /**
     *
     * @return true if interrupted
     */
    private boolean sleep() {
      synchronized (eventDispatcher) {
        try {
          eventDispatcher.wait();
          return false;
        } catch (InterruptedException e) {
          //If we are interrupted, treat as a cancel
          return true;
        }
      }
    }

    public void run() {
      final FormatHolder formatHolder = getFormatHolder();
      @NonNull
      final DecoderInputBuffer decoderInputBuffer =
          new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
      long start = SystemClock.uptimeMillis();
      main:
      while (running) {
        decoderInputBuffer.clear();
        final int result = readSource(formatHolder, decoderInputBuffer,
            formatHolder.format == null ? SampleStream.FLAG_REQUIRE_FORMAT : 0);
        if (result == C.RESULT_BUFFER_READ) {
          if (decoderInputBuffer.isEndOfStream()) {
            ended = true;
            if (!sleep()) {
              ended = false;
            }
            continue;
          }
          final long leadUs = decoderInputBuffer.timeUs - currentTimeUs;
          //If we are more than 1/2 a frame behind, skip the next frame
          if (leadUs < -frameUs / 2) {
            eventDispatcher.droppedFrames(1, SystemClock.uptimeMillis() - start);
            start = SystemClock.uptimeMillis();
            continue;
          }
          start = SystemClock.uptimeMillis();

          @Nullable
          final Bitmap bitmap = decodeInputBuffer(decoderInputBuffer);
          if (bitmap == null) {
            continue;
          }
          while (currentTimeUs < decoderInputBuffer.timeUs) {
            //Log.d(TAG, "Sleep: us=" + currentTimeUs);
            if (sleep()) {
              continue main;
            }
          }
          if (running) {
            renderBitmap(bitmap, surface);
          }
        } else if (result == C.RESULT_FORMAT_READ) {
          onFormatChanged(formatHolder);
        }
      }
      ended = true;
    }
  }
}
