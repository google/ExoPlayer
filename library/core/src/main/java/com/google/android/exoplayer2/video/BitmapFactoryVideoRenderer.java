package com.google.android.exoplayer2.video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.arch.core.util.Function;
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
  static final String TAG = "BitmapFactoryRenderer";

  //Sleep Reasons
  static final String STREAM_END = "Stream End";
  static final String STREAM_EMPTY = "Stream Empty";
  static final String RENDER_WAIT = "Render Wait";

  private static int threadId;

  private final Rect rect = new Rect();
  private final RenderRunnable renderRunnable = new RenderRunnable();

  final VideoRendererEventListener.EventDispatcher eventDispatcher;
  final Thread thread = new Thread(renderRunnable, getClass().getSimpleName() + threadId++);

  @Nullable
  volatile Surface surface;

  private VideoSize lastVideoSize = VideoSize.UNKNOWN;
  private long currentTimeUs;
  private long frameUs;
  private boolean firstFrameRendered;
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
    if (mayRenderStartOfStream) {
      thread.start();
    }
  }

  @Override
  protected void onStarted() throws ExoPlaybackException {
    if (thread.getState() == Thread.State.NEW) {
      thread.start();
    }
  }

  @Override
  protected void onDisabled() {
    renderRunnable.stop();

    @Nullable
    final DecoderCounters decoderCounters = this.decoderCounters;
    if (decoderCounters != null) {
      eventDispatcher.disabled(decoderCounters);
    }
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    //Log.d(TAG, "Render: us=" + positionUs);
    synchronized (renderRunnable) {
      currentTimeUs = positionUs;
      renderRunnable.notify();
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
    return renderRunnable.isEnded();
  }

  @Override
  public int supportsFormat(Format format) throws ExoPlaybackException {
    //Technically could support any format BitmapFactory supports
    if (MimeTypes.VIDEO_MJPEG.equals(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_HANDLED);
    }
    return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
  }

  @WorkerThread
  private void onFormatChanged(@NonNull FormatHolder formatHolder) {
    @Nullable final Format format = formatHolder.format;
    if (format != null) {
      frameUs = (long)(1_000_000L / format.frameRate);
      eventDispatcher.inputFormatChanged(format, null);
    }
  }

  @WorkerThread
  void renderBitmap(@NonNull final Bitmap bitmap) {
    @Nullable
    final Surface surface = this.surface;
    if (surface == null) {
      return;
    }
    //Log.d(TAG, "Drawing: " + bitmap.getWidth() + "x" + bitmap.getHeight());
    final Canvas canvas = surface.lockCanvas(null);

    renderBitmap(bitmap, canvas);

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

  @WorkerThread
  @VisibleForTesting
  void renderBitmap(Bitmap bitmap, Canvas canvas) {
    final VideoSize videoSize = new VideoSize(bitmap.getWidth(), bitmap.getHeight());
    if (!videoSize.equals(lastVideoSize)) {
      lastVideoSize = videoSize;
      eventDispatcher.videoSizeChanged(videoSize);
    }
    rect.set(0,0,canvas.getWidth(), canvas.getHeight());
    canvas.drawBitmap(bitmap, null, rect, null);
  }

  class RenderRunnable implements Runnable, Function<String, Boolean> {
    final DecoderInputBuffer decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);

    private volatile boolean running = true;

    @VisibleForTesting
    Function<String, Boolean> sleepFunction = this;

    void stop() {
      running = false;
      thread.interrupt();
    }

    boolean isEnded() {
      return !running || decoderInputBuffer.isEndOfStream();
    }

    @Nullable
    private Bitmap decodeInputBuffer(final DecoderInputBuffer decoderInputBuffer) {
      @Nullable final ByteBuffer byteBuffer = decoderInputBuffer.data;
      if (byteBuffer != null) {
        final Bitmap bitmap;
        try {
          bitmap = BitmapFactory.decodeByteArray(byteBuffer.array(), byteBuffer.arrayOffset(),
              byteBuffer.arrayOffset() + byteBuffer.position());
          if (bitmap == null) {
            throw new NullPointerException("Decode bytes failed");
          } else {
            return bitmap;
          }
        } catch (Exception e) {
          eventDispatcher.videoCodecError(e);
        }
      }
      return null;
    }

    /**
     *
     * @return true if interrupted
     */
    public synchronized Boolean apply(String why) {
      try {
        wait();
        return false;
      } catch (InterruptedException e) {
        //If we are interrupted, treat as a cancel
        return true;
      }
    }

    private boolean sleep(String why) {
      return sleepFunction.apply(why);
    }

    @WorkerThread
    public void run() {
      final FormatHolder formatHolder = getFormatHolder();
      long start = SystemClock.uptimeMillis();
      main:
      while (running) {
        decoderInputBuffer.clear();
        final int result = readSource(formatHolder, decoderInputBuffer,
            formatHolder.format == null ? SampleStream.FLAG_REQUIRE_FORMAT : 0);
        switch (result) {
          case C.RESULT_BUFFER_READ: {
            if (decoderInputBuffer.isEndOfStream()) {
              //Wait for shutdown or stream to be changed
              sleep(STREAM_END);
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
              if (sleep(RENDER_WAIT)) {
                //Sleep was interrupted, discard Bitmap
                continue main;
              }
            }
            if (running) {
              renderBitmap(bitmap);
            }
          }
          break;
        case C.RESULT_FORMAT_READ:
          onFormatChanged(formatHolder);
          break;
        case C.RESULT_NOTHING_READ:
          sleep(STREAM_EMPTY);
          break;
        }
      }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  Rect getRect() {
    return rect;
  }

  @Nullable
  @VisibleForTesting
  DecoderCounters getDecoderCounters() {
    return decoderCounters;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  Thread getThread() {
    return thread;
  }

  @Nullable
  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  Surface getSurface() {
    return surface;
  }

  RenderRunnable getRenderRunnable() {
    return renderRunnable;
  }
}
